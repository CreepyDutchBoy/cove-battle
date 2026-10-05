package io.github.creepydutchboy.covebattle.rules;

/** Which rule set a match runs under. */
public enum MatchMode {
    /**
     * As close to the legacy console Battle mini game as this can get: vanilla loot only, no
     * custom items, no screen effects, and console pacing.
     */
    CLASSIC("Classic", "The console mini game: vanilla loot, no custom gear, console pacing."),
    /** The default. Everything Classic has, plus the custom items and the modern presentation. */
    REMASTERED("Remastered", "Classic plus custom items, screen effects and the closing border."),
    /** Remastered, but every number is yours to move. */
    MUTATORS("Mutators", "Tune every knob yourself with the sliders.");

    private final String label;
    private final String blurb;

    MatchMode(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }

    public String label() {
        return label;
    }

    public String blurb() {
        return blurb;
    }

    public MatchMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static MatchMode byName(String name) {
        for (MatchMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) return mode;
        }
        return REMASTERED;
    }
}
