package io.github.creepydutchboy.covebattleboot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fetches and installs the real Cove Battle mod.
 *
 * <p>Deliberately standalone. The full mod carries the Cove map and so is around twenty megabytes,
 * which is awkward to pass around on chat apps; this jar is a few tens of kilobytes, so it can be
 * sent anywhere and pull the rest down itself on first launch.
 *
 * <p>It shares no classes with the main mod on purpose: two jars carrying the same package would
 * be asking for a loader conflict. It is a deliberate duplicate of the parts that matter, and it
 * has no Minecraft imports, so it can be exercised on its own.
 */
public final class BootstrapUpdater {

    public static final String OWNER = "CreepyDutchBoy";
    public static final String REPO = "cove-battle";
    public static final String JAR = "covebattle.jar";
    public static final String MANIFEST = "covebattle.update.json";

    private static final int MAX_BYTES = 128 * 1024 * 1024;

    private final Consumer<String> log;
    private final HttpClient http;

    public BootstrapUpdater(Consumer<String> log) {
        this.log = log;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Outcome of one attempt, for logging and for the in-game notice. */
    public record Result(boolean installed, String version, String message) {}

    /**
     * Installs the newest release built for {@code mcVersion}, unless an equal or newer jar is
     * already present.
     *
     * @param modsDir       the mods folder to install into
     * @param mcVersion     the Minecraft version running
     * @param presentVersion version already installed, or null when nothing is there
     */
    public Result run(Path modsDir, String mcVersion, String presentVersion) {
        try {
            String problem = writeCheck(modsDir);
            if (problem != null) return new Result(false, null, problem);

            JsonObject manifest = findManifest(mcVersion);
            if (manifest == null) {
                return new Result(false, null, "no release found for Minecraft " + mcVersion);
            }
            String version = manifest.get("version").getAsString();
            String sha = manifest.get("sha256").getAsString();
            String jarName = manifest.has("jar") ? manifest.get("jar").getAsString() : JAR;

            if (presentVersion != null && compare(presentVersion, version) >= 0) {
                return new Result(false, version, "Cove Battle " + presentVersion + " is already installed");
            }

            String jarUrl = assets.get(jarName);
            if (jarUrl == null) return new Result(false, version, "release is missing " + jarName);

            Path temp = modsDir.resolve(JAR + ".part");
            Files.deleteIfExists(temp);
            HttpResponse<Path> response = http.send(get(jarUrl), HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(temp);
                return new Result(false, version, "download returned HTTP " + response.statusCode());
            }
            long size = Files.size(temp);
            if (size <= 0 || size > MAX_BYTES || !looksLikeZip(temp)) {
                Files.deleteIfExists(temp);
                return new Result(false, version, "downloaded file is not a usable jar");
            }
            String actual = sha256(temp);
            if (!actual.equalsIgnoreCase(sha)) {
                Files.deleteIfExists(temp);
                return new Result(false, version, "checksum mismatch");
            }

            Path target = modsDir.resolve(JAR);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.accept("Installed Cove Battle " + version + " to " + target);
            return new Result(true, version, "Cove Battle " + version + " installed — restart to play");
        } catch (Exception e) {
            return new Result(false, null, e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    private final Map<String, String> assets = new HashMap<>();

    /** Newest non-prerelease whose manifest targets this Minecraft version. */
    private JsonObject findManifest(String mcVersion) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                get("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases?per_page=20"),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("GitHub returned HTTP " + response.statusCode());

        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonArray()) return null;

        for (JsonElement element : root.getAsJsonArray()) {
            JsonObject release = element.getAsJsonObject();
            if (bool(release, "draft") || bool(release, "prerelease")) continue;

            assets.clear();
            JsonArray array = release.getAsJsonArray("assets");
            if (array == null) continue;
            for (JsonElement a : array) {
                JsonObject asset = a.getAsJsonObject();
                assets.put(asset.get("name").getAsString(), asset.get("browser_download_url").getAsString());
            }

            String manifestUrl = assets.get(MANIFEST);
            if (manifestUrl == null) continue;

            HttpResponse<String> manifestResponse = http.send(get(manifestUrl), HttpResponse.BodyHandlers.ofString());
            if (manifestResponse.statusCode() != 200) continue;
            JsonObject manifest = JsonParser.parseString(manifestResponse.body()).getAsJsonObject();
            if (!manifest.has("minecraft") || !manifest.get("minecraft").getAsString().equals(mcVersion)) continue;
            return manifest;
        }
        return null;
    }

    private HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "covebattle-bootstrap (+https://github.com/" + OWNER + "/" + REPO + ")")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
    }

    private static String writeCheck(Path modsDir) {
        try {
            Files.createDirectories(modsDir);
            Path probe = modsDir.resolve(".covebattle-boot-test");
            Files.writeString(probe, "ok");
            Files.delete(probe);
            return null;
        } catch (IOException e) {
            boolean flatpak = System.getenv("FLATPAK_ID") != null || Files.exists(Path.of("/.flatpak-info"));
            return "cannot write to " + modsDir + (flatpak
                    ? " (running under Flatpak — grant the launcher access to its instance folder)"
                    : " (" + e.getMessage() + ")");
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement e = object.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    private static boolean looksLikeZip(Path path) throws IOException {
        byte[] head = new byte[2];
        try (var in = Files.newInputStream(path)) {
            return in.read(head) == 2 && head[0] == 'P' && head[1] == 'K';
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Same tolerant comparison the main mod uses. */
    public static int compare(String a, String b) {
        String[] pa = strip(a).split("\\.");
        String[] pb = strip(b).split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int na = i < pa.length ? parse(pa[i]) : 0;
            int nb = i < pb.length ? parse(pb[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }

    private static String strip(String v) {
        String s = v == null ? "" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        StringBuilder core = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c) || c == '.') core.append(c);
            else break;
        }
        return core.length() == 0 ? "0" : core.toString();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Standalone entry point, so the bootstrap can be tested without Minecraft. */
    public static void main(String[] args) {
        Path mods = Path.of(args.length > 0 ? args[0] : "/tmp/covebattle-boot-mods");
        String mc = args.length > 1 ? args[1] : "1.21.1";
        String present = args.length > 2 && !args[2].equals("none") ? args[2] : null;
        Result result = new BootstrapUpdater(System.out::println).run(mods, mc, present);
        System.out.println("installed: " + result.installed());
        System.out.println("version  : " + result.version());
        System.out.println("message  : " + result.message());
    }
}
