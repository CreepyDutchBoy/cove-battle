package io.github.creepydutchboy.covebattle.update;

import java.nio.file.Path;

/**
 * Everything the updater needs, passed in from outside so this package stays free of
 * Minecraft and NeoForge types and can be exercised standalone.
 *
 * @param owner           GitHub account that owns the repository
 * @param repo            repository name
 * @param allowPrerelease whether releases flagged as prerelease are candidates
 * @param mcVersion       Minecraft version currently running, matched against the release manifest
 * @param currentVersion  version of the mod that is running
 * @param modsDir         directory the jar lives in
 * @param jarName         fixed jar filename; a versioned name would risk duplicate-mod boot failures
 * @param manifestName    release asset describing the build
 */
public record UpdaterSettings(
        String owner,
        String repo,
        boolean allowPrerelease,
        String mcVersion,
        String currentVersion,
        Path modsDir,
        String jarName,
        String manifestName
) {
    public String releasesApiUrl() {
        return "https://api.github.com/repos/" + owner + "/" + repo + "/releases?per_page=20";
    }

    public String userAgent() {
        return "covebattle-updater/" + currentVersion + " (+https://github.com/" + owner + "/" + repo + ")";
    }
}
