package io.github.creepydutchboy.covebattle.update;

/**
 * Contents of the {@code <modid>.update.json} release asset, written by the build's packageDist task.
 * Its presence is what marks a GitHub release as something this updater may install, and its
 * {@code minecraft} field is the compatibility gate that stops a future build for another
 * Minecraft version from being pulled down.
 */
public record UpdateManifest(String version, String minecraft, String neoforgeMin, String jar, String sha256) {

    public boolean isValid() {
        return notBlank(version) && notBlank(minecraft) && notBlank(jar) && notBlank(sha256);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
