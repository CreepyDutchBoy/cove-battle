package io.github.creepydutchboy.covebattle.block;

/**
 * What a marker block tells the game. Map makers place these in creative instead of editing
 * coordinates in a config file; the arena scan picks them up because every marker carries a block
 * entity, so they turn up in the same chunk walk that finds the chests.
 *
 * <p>Chests and barrels are deliberately <em>not</em> markers — they are detected automatically,
 * so there is nothing to place or maintain for loot.
 */
public enum MarkerKind {
    /** The middle of the arena: the loot tier origin and the fallback spawn ring. One of these. */
    ARENA_CENTRE("arena_centre_marker"),
    /** Where players wait between matches. One of these. */
    LOBBY_SPAWN("lobby_spawn_marker"),
    /** A spawn for the red team. Place up to sixteen. */
    TEAM_A_SPAWN("team_a_spawn_marker"),
    /** A spawn for the blue team. Place up to sixteen. */
    TEAM_B_SPAWN("team_b_spawn_marker"),
    /** A free-for-all spawn. Place as many as you want players. */
    SOLO_SPAWN("solo_spawn_marker"),
    /** Place at the edge of the playable area; the furthest one sets the play radius. */
    ARENA_BOUND("arena_bound_marker");

    private final String id;

    MarkerKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return switch (this) {
            case ARENA_CENTRE -> "Arena Centre";
            case LOBBY_SPAWN -> "Lobby Spawn";
            case TEAM_A_SPAWN -> "Red Team Spawn";
            case TEAM_B_SPAWN -> "Blue Team Spawn";
            case SOLO_SPAWN -> "Free-for-all Spawn";
            case ARENA_BOUND -> "Arena Bound";
        };
    }
}
