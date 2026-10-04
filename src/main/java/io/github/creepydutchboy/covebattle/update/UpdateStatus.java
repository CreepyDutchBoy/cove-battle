package io.github.creepydutchboy.covebattle.update;

public enum UpdateStatus {
    /** Auto-update switched off in config. */
    DISABLED,
    /** Newest compatible release is not newer than what is installed. */
    UP_TO_DATE,
    /** A newer release exists; it was not installed (check-only mode). */
    UPDATE_AVAILABLE,
    /** A newer release was downloaded, verified and written into mods/. Applies on next launch. */
    UPDATE_INSTALLED,
    /** Releases exist but none target this Minecraft version. */
    INCOMPATIBLE,
    /** Network, parsing, checksum or filesystem problem. Never fatal to the game. */
    FAILED
}
