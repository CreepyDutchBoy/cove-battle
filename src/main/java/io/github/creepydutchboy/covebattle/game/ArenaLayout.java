package io.github.creepydutchboy.covebattle.game;

import io.github.creepydutchboy.covebattle.block.MarkerKind;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where everything is, worked out from the markers a map maker placed. Anything they did not place
 * falls back to the config, so a bare world still works and a marked-up map needs no config at all.
 */
public final class ArenaLayout {

    private final BlockPos centre;
    private final int radius;
    @Nullable
    private final BlockPos lobby;
    private final Map<MarkerKind, List<BlockPos>> spawns;
    private final boolean centreFromMarker;
    private final boolean radiusFromMarker;

    private ArenaLayout(BlockPos centre, int radius, @Nullable BlockPos lobby,
                        Map<MarkerKind, List<BlockPos>> spawns,
                        boolean centreFromMarker, boolean radiusFromMarker) {
        this.centre = centre;
        this.radius = radius;
        this.lobby = lobby;
        this.spawns = spawns;
        this.centreFromMarker = centreFromMarker;
        this.radiusFromMarker = radiusFromMarker;
    }

    /**
     * @param markers  every marker found in the scanned area
     * @param fallbackCentre arena centre from the config
     * @param fallbackRadius play radius from the config
     */
    public static ArenaLayout resolve(Map<MarkerKind, List<BlockPos>> markers,
                                      BlockPos fallbackCentre, int fallbackRadius) {
        List<BlockPos> centres = markers.getOrDefault(MarkerKind.ARENA_CENTRE, List.of());
        boolean centreFromMarker = !centres.isEmpty();
        BlockPos centre = centreFromMarker ? centres.get(0) : fallbackCentre;

        List<BlockPos> bounds = markers.getOrDefault(MarkerKind.ARENA_BOUND, List.of());
        int radius = fallbackRadius;
        boolean radiusFromMarker = false;
        if (!bounds.isEmpty()) {
            double furthest = 0;
            for (BlockPos bound : bounds) {
                double dx = bound.getX() - centre.getX();
                double dz = bound.getZ() - centre.getZ();
                furthest = Math.max(furthest, Math.sqrt(dx * dx + dz * dz));
            }
            if (furthest >= 8) {
                radius = (int) Math.ceil(furthest);
                radiusFromMarker = true;
            }
        }

        List<BlockPos> lobbies = markers.getOrDefault(MarkerKind.LOBBY_SPAWN, List.of());
        BlockPos lobby = lobbies.isEmpty() ? null : lobbies.get(0);

        Map<MarkerKind, List<BlockPos>> spawns = new EnumMap<>(MarkerKind.class);
        for (MarkerKind kind : List.of(MarkerKind.TEAM_A_SPAWN, MarkerKind.TEAM_B_SPAWN, MarkerKind.SOLO_SPAWN)) {
            spawns.put(kind, new ArrayList<>(markers.getOrDefault(kind, List.of())));
        }

        return new ArenaLayout(centre, radius, lobby, spawns, centreFromMarker, radiusFromMarker);
    }

    public BlockPos centre() {
        return centre;
    }

    public int radius() {
        return radius;
    }

    @Nullable
    public BlockPos lobby() {
        return lobby;
    }

    public List<BlockPos> spawns(MarkerKind kind) {
        return spawns.getOrDefault(kind, List.of());
    }

    public boolean centreFromMarker() {
        return centreFromMarker;
    }

    public boolean radiusFromMarker() {
        return radiusFromMarker;
    }

    public int markerCount() {
        return spawns.values().stream().mapToInt(List::size).sum()
                + (centreFromMarker ? 1 : 0) + (lobby != null ? 1 : 0);
    }

    public String summary() {
        return "centre " + centre.getX() + " " + centre.getY() + " " + centre.getZ()
                + (centreFromMarker ? " (marker)" : " (config)")
                + ", radius " + radius + (radiusFromMarker ? " (marker)" : " (config)")
                + ", lobby " + (lobby == null ? "config" : "marker")
                + ", spawns red " + spawns(MarkerKind.TEAM_A_SPAWN).size()
                + " / blue " + spawns(MarkerKind.TEAM_B_SPAWN).size()
                + " / ffa " + spawns(MarkerKind.SOLO_SPAWN).size();
    }
}
