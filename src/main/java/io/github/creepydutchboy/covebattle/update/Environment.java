package io.github.creepydutchboy.covebattle.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the game is actually running, which decides whether an update can be written at all.
 *
 * <p>Flatpak is the case worth naming: PrismLauncher as a Flatpak is sandboxed, so the mods
 * directory lives under {@code ~/.var/app/...} rather than {@code ~/.local/share/...}. The mod runs
 * inside that sandbox, so the path it is handed is already the correct one — nothing needs
 * translating. What does matter is that the download is staged inside the mods directory itself,
 * so the final move never crosses a filesystem boundary, and that a sandbox without write access
 * fails with something a human can act on instead of a stack trace.
 */
public final class Environment {

    private Environment() {}

    public static boolean isFlatpak() {
        if (System.getenv("FLATPAK_ID") != null) return true;
        return Files.exists(Path.of("/.flatpak-info"));
    }

    public static boolean isSnap() {
        return System.getenv("SNAP") != null;
    }

    public static String describe() {
        if (isFlatpak()) {
            String id = System.getenv("FLATPAK_ID");
            return "flatpak" + (id == null ? "" : " (" + id + ")");
        }
        if (isSnap()) return "snap";
        return System.getProperty("os.name", "unknown");
    }

    /**
     * Checks the mods directory can actually be written to, by creating and deleting a probe file.
     *
     * @return null when writable, otherwise a message explaining what to do about it
     */
    public static String checkWritable(Path modsDir) {
        try {
            Files.createDirectories(modsDir);
            Path probe = modsDir.resolve(".covebattle-write-test");
            Files.writeString(probe, "ok");
            Files.delete(probe);
            return null;
        } catch (IOException e) {
            String where = isFlatpak()
                    ? " The game is running under Flatpak; grant the launcher write access to its"
                      + " instance folder, or update the jar by hand."
                    : "";
            return "cannot write to " + modsDir + " (" + e.getMessage() + ")." + where;
        }
    }
}
