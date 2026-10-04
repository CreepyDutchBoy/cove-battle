package io.github.creepydutchboy.covebattle.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Checks a public GitHub repository's releases and, when asked, installs a newer build into the
 * mods directory.
 *
 * <p>Deliberately contains no Minecraft or NeoForge types: everything it needs arrives through
 * {@link UpdaterSettings}, which lets the whole flow be exercised from {@link UpdaterCli} without
 * launching the game.
 *
 * <p>A replaced jar takes effect on the next launch. No JVM can swap a jar it has already loaded,
 * so there is no attempt to pretend otherwise. On Linux and macOS replacing the running jar is
 * safe because the open file handle keeps pointing at the old inode; on Windows the move is
 * expected to fail, and that is reported as {@link UpdateStatus#FAILED} rather than crashing.
 */
public final class GitHubUpdater {

    private static final int MAX_JAR_BYTES = 64 * 1024 * 1024;

    private final UpdaterSettings settings;
    private final Consumer<String> log;
    private final HttpClient http;

    public GitHubUpdater(UpdaterSettings settings) {
        this(settings, msg -> {});
    }

    public GitHubUpdater(UpdaterSettings settings, Consumer<String> log) {
        this.settings = settings;
        this.log = log;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Looks for a newer release without touching the mods directory. */
    public UpdateOutcome check() {
        return run(false);
    }

    /** Looks for a newer release and installs it if there is one. */
    public UpdateOutcome checkAndInstall() {
        return run(true);
    }

    private UpdateOutcome run(boolean install) {
        try {
            List<Release> releases = fetchReleases();
            if (releases.isEmpty()) {
                return UpdateOutcome.of(UpdateStatus.UP_TO_DATE, "no releases published yet");
            }

            boolean sawRelease = false;
            for (Release release : releases) {
                if (release.draft) continue;
                if (release.prerelease && !settings.allowPrerelease()) continue;

                String manifestUrl = release.assets.get(settings.manifestName());
                if (manifestUrl == null) {
                    log.accept("Release " + release.tag + " has no " + settings.manifestName() + " asset; skipping it.");
                    continue;
                }

                UpdateManifest manifest = fetchManifest(manifestUrl);
                if (manifest == null || !manifest.isValid()) {
                    log.accept("Release " + release.tag + " has an unreadable manifest; skipping it.");
                    continue;
                }

                sawRelease = true;
                if (!settings.mcVersion().equals(manifest.minecraft())) {
                    log.accept("Release " + release.tag + " targets Minecraft " + manifest.minecraft()
                            + ", this game is " + settings.mcVersion() + "; skipping it.");
                    continue;
                }

                // Releases arrive newest-first, so the first compatible one decides the answer.
                if (!Semver.isNewer(manifest.version(), settings.currentVersion())) {
                    return new UpdateOutcome(UpdateStatus.UP_TO_DATE,
                            "running the newest build for Minecraft " + settings.mcVersion(),
                            settings.currentVersion());
                }

                if (!install) {
                    return new UpdateOutcome(UpdateStatus.UPDATE_AVAILABLE,
                            "version " + manifest.version() + " is available", manifest.version());
                }

                String jarUrl = release.assets.get(manifest.jar());
                if (jarUrl == null) {
                    return new UpdateOutcome(UpdateStatus.FAILED,
                            "release " + release.tag + " is missing its " + manifest.jar() + " asset",
                            manifest.version());
                }
                return install(manifest, jarUrl);
            }

            if (sawRelease) {
                return UpdateOutcome.of(UpdateStatus.INCOMPATIBLE,
                        "no release targets Minecraft " + settings.mcVersion());
            }
            return UpdateOutcome.of(UpdateStatus.UP_TO_DATE, "no installable release found");
        } catch (Exception e) {
            return UpdateOutcome.of(UpdateStatus.FAILED, describe(e));
        }
    }

    private UpdateOutcome install(UpdateManifest manifest, String jarUrl) throws IOException, InterruptedException {
        Path modsDir = settings.modsDir();
        Files.createDirectories(modsDir);
        Path target = modsDir.resolve(settings.jarName());
        Path temp = modsDir.resolve(settings.jarName() + ".part");
        Files.deleteIfExists(temp);

        try {
            HttpResponse<Path> response = http.send(
                    get(jarUrl, "application/octet-stream"),
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                return new UpdateOutcome(UpdateStatus.FAILED,
                        "download returned HTTP " + response.statusCode(), manifest.version());
            }

            long size = Files.size(temp);
            if (size <= 0 || size > MAX_JAR_BYTES) {
                return new UpdateOutcome(UpdateStatus.FAILED,
                        "downloaded file has an implausible size (" + size + " bytes)", manifest.version());
            }
            if (!looksLikeZip(temp)) {
                return new UpdateOutcome(UpdateStatus.FAILED,
                        "downloaded file is not a jar", manifest.version());
            }

            String actual = sha256(temp);
            if (!actual.equalsIgnoreCase(manifest.sha256())) {
                return new UpdateOutcome(UpdateStatus.FAILED,
                        "checksum mismatch (expected " + manifest.sha256() + ", got " + actual + ")",
                        manifest.version());
            }

            move(temp, target);
            int removed = removeStaleJars(modsDir, target);
            log.accept("Installed " + settings.jarName() + " " + manifest.version()
                    + (removed > 0 ? " and removed " + removed + " superseded jar(s)" : ""));

            return new UpdateOutcome(UpdateStatus.UPDATE_INSTALLED,
                    "installed version " + manifest.version() + "; restart to apply", manifest.version());
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // A leftover .part file is harmless; NeoForge only scans .jar files.
            }
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deletes other jars whose name starts with the jar's base name, so that an older versioned
     * file cannot sit alongside the new fixed-name one and trip NeoForge's duplicate-mod check.
     */
    private int removeStaleJars(Path modsDir, Path keep) {
        String base = settings.jarName().toLowerCase(Locale.ROOT).replace(".jar", "");
        int removed = 0;
        try (Stream<Path> files = Files.list(modsDir)) {
            for (Path p : files.toList()) {
                if (!Files.isRegularFile(p)) continue;
                try {
                    if (Files.isSameFile(p, keep)) continue;
                } catch (IOException e) {
                    continue;
                }
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith(base) && name.endsWith(".jar")) {
                    try {
                        Files.delete(p);
                        removed++;
                        log.accept("Removed superseded jar " + p.getFileName());
                    } catch (IOException e) {
                        log.accept("Could not remove superseded jar " + p.getFileName() + ": " + e);
                    }
                }
            }
        } catch (IOException e) {
            log.accept("Could not scan the mods directory: " + e);
        }
        return removed;
    }

    private List<Release> fetchReleases() throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                get(settings.releasesApiUrl(), "application/vnd.github+json"),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IOException("repository " + settings.owner() + "/" + settings.repo() + " not found");
        }
        if (response.statusCode() == 403) {
            throw new IOException("GitHub rate limit reached; will try again next launch");
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode());
        }

        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonArray()) return List.of();

        List<Release> releases = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            Map<String, String> assets = new HashMap<>();
            JsonArray assetArray = obj.getAsJsonArray("assets");
            if (assetArray != null) {
                for (JsonElement a : assetArray) {
                    if (!a.isJsonObject()) continue;
                    JsonObject asset = a.getAsJsonObject();
                    String name = string(asset, "name");
                    String url = string(asset, "browser_download_url");
                    if (name != null && url != null) assets.put(name, url);
                }
            }
            releases.add(new Release(
                    string(obj, "tag_name"),
                    bool(obj, "draft"),
                    bool(obj, "prerelease"),
                    assets));
        }
        return releases;
    }

    private UpdateManifest fetchManifest(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                get(url, "application/octet-stream"),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject();
        return new UpdateManifest(
                string(obj, "version"),
                string(obj, "minecraft"),
                string(obj, "neoforge_min"),
                string(obj, "jar"),
                string(obj, "sha256"));
    }

    private HttpRequest get(String url, String accept) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", settings.userAgent())
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
    }

    private static boolean looksLikeZip(Path path) throws IOException {
        byte[] head = new byte[2];
        try (var in = Files.newInputStream(path)) {
            return in.read(head) == 2 && head[0] == 'P' && head[1] == 'K';
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    private static String string(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static boolean bool(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    private record Release(String tag, boolean draft, boolean prerelease, Map<String, String> assets) {}
}
