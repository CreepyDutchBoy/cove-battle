package io.github.creepydutchboy.covebattle.update;

/**
 * Result of one update check. {@code version} is the remote version involved, when there is one.
 */
public record UpdateOutcome(UpdateStatus status, String message, String version) {

    public static UpdateOutcome of(UpdateStatus status, String message) {
        return new UpdateOutcome(status, message, null);
    }

    public boolean isActionable() {
        return status == UpdateStatus.UPDATE_AVAILABLE || status == UpdateStatus.UPDATE_INSTALLED;
    }

    @Override
    public String toString() {
        return version == null ? status + ": " + message : status + ": " + message + " (" + version + ")";
    }
}
