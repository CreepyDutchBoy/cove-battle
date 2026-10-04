package io.github.creepydutchboy.covebattle.game;

/** Per-player tally for the current match. */
public final class PlayerStats {
    public String name;
    public int roundWins;
    public int kills;
    public int containersLooted;

    public PlayerStats(String name) {
        this.name = name;
    }
}
