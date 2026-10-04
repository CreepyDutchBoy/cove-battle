package io.github.creepydutchboy.covebattle.game;

/** Where a match is in its life cycle. */
public enum GamePhase {
    /** No match running. Players wait, the border is open, the start prompt is posted. */
    LOBBY,
    /** Frozen and invulnerable on the podiums while the countdown runs. */
    GRACE,
    /** Open fighting, main timer counting down. */
    FIGHT,
    /** Main timer expired: the border closes in steps and the remaining players glow. */
    SHOWDOWN,
    /** A round has been decided; short pause before the next one. */
    ROUND_END,
    /** Someone took the match; leaderboard is up before returning to the lobby. */
    MATCH_END;

    public boolean isLive() {
        return this == GRACE || this == FIGHT || this == SHOWDOWN;
    }

    public boolean allowsCombat() {
        return this == FIGHT || this == SHOWDOWN;
    }
}
