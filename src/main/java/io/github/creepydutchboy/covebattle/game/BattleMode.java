package io.github.creepydutchboy.covebattle.game;

/** How sides are drawn up for a match. */
public enum BattleMode {
    /** Everyone against everyone; the last player standing takes the round. */
    SOLO("Free-for-all"),
    /** Red against blue; the last team with anyone standing takes the round. */
    TEAMS("Teams");

    private final String label;

    BattleMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BattleMode byName(String name) {
        for (BattleMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) return mode;
        }
        return SOLO;
    }
}
