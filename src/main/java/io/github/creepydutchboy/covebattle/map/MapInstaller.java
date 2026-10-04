package io.github.creepydutchboy.covebattle.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks the Cove map that ships inside the jar into a saves directory.
 *
 * <p>The map is a 1.20.1 world, which 1.21.1 upgrades on first load. No Minecraft types are used
 * here so the unpacking can be exercised without the game.
 */
public final class MapInstaller {

    /** Where the map sits inside the jar. */
    public static final String RESOURCE = "/covebattle/map/cove_battle.zip";
    /** Folder name inside that zip. */
    private static final String ZIP_ROOT = "Cove_Remastered_PMC";
    /** Default save folder name. */
    public static final String DEFAULT_FOLDER = "Cove Battle";

    private MapInstaller() {}

    public static boolean isAvailable() {
        try (InputStream in = MapInstaller.class.getResourceAsStream(RESOURCE)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** @return the folder that would be used, skipping names already taken */
    public static Path freeFolder(Path savesDir, String preferred) {
        Path candidate = savesDir.resolve(preferred);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = savesDir.resolve(preferred + " (" + suffix + ")");
            suffix++;
        }
        return candidate;
    }

    /**
     * Extracts the bundled map.
     *
     * @return the created world folder
     * @throws IOException if the jar has no map, or writing fails
     */
    public static Path install(Path savesDir, String preferredFolder) throws IOException {
        return install(savesDir, preferredFolder, message -> {});
    }

    /** As above, with somewhere to send progress notes. */
    public static Path install(Path savesDir, String preferredFolder, Consumer<String> log) throws IOException {
        Files.createDirectories(savesDir);
        // Normalise the base before any comparison: a game directory can legitimately contain ".."
        // segments, and comparing a normalised entry path against an unnormalised base made every
        // directory entry look like a zip-slip attempt.
        Path target = freeFolder(savesDir, preferredFolder).toAbsolutePath().normalize();
        Files.createDirectories(target);

        try (InputStream raw = MapInstaller.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IOException("the bundled map is missing from the jar");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                int files = 0;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    if (name.startsWith(ZIP_ROOT + "/")) name = name.substring(ZIP_ROOT.length() + 1);
                    if (name.isEmpty()) continue;

                    Path out = target.resolve(name).normalize();
                    // Refuse anything that would escape the target directory.
                    if (!out.startsWith(target)) {
                        throw new IOException("refusing suspicious zip entry " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                        continue;
                    }
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                    files++;
                }
                log.accept("Installed the bundled Cove map to " + target + " (" + files + " files)");
            }
        }
        return target;
    }

    /** Standalone check, so the bundled map can be verified without launching the game. */
    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "/tmp/covebattle-saves");
        System.out.println("map present in classpath: " + isAvailable());
        Path made = install(dir, DEFAULT_FOLDER, System.out::println);
        long bytes = Files.walk(made).filter(Files::isRegularFile).mapToLong(p -> {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }).sum();
        System.out.println("installed to " + made + " (" + bytes / 1024 / 1024 + " MB)");
        System.out.println("level.dat present: " + Files.exists(made.resolve("level.dat")));
        System.out.println("region files: " + Files.list(made.resolve("region")).count());
    }
}
