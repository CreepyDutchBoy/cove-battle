package io.github.creepydutchboy.covebattle.game;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.block.MarkerBlockEntity;
import io.github.creepydutchboy.covebattle.block.MarkerKind;
import io.github.creepydutchboy.covebattle.loot.ContainerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One walk over the arena's chunks that finds both the loot containers and the markers.
 *
 * <p>Both are block entities, so a single pass over {@code LevelChunk#getBlockEntities()} gets
 * everything. Markers are read first so the arena centre they define can decide which containers
 * count as centre tier.
 */
public final class ArenaScan {

    private final ContainerRegistry containers;
    private final ArenaLayout layout;

    private ArenaScan(ContainerRegistry containers, ArenaLayout layout) {
        this.containers = containers;
        this.layout = layout;
    }

    public ContainerRegistry containers() {
        return containers;
    }

    public ArenaLayout layout() {
        return layout;
    }

    public static ArenaScan run(ServerLevel level, BlockPos searchCentre, int searchRadius, int centreTierRadius) {
        int minChunkX = (searchCentre.getX() - searchRadius) >> 4;
        int maxChunkX = (searchCentre.getX() + searchRadius) >> 4;
        int minChunkZ = (searchCentre.getZ() - searchRadius) >> 4;
        int maxChunkZ = (searchCentre.getZ() + searchRadius) >> 4;

        List<BlockPos> containerPositions = new ArrayList<>();
        Map<MarkerKind, List<BlockPos>> markers = new EnumMap<>(MarkerKind.class);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos().immutable();
                    if (be instanceof MarkerBlockEntity marker) {
                        markers.computeIfAbsent(marker.kind(), k -> new ArrayList<>()).add(pos);
                    } else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                        containerPositions.add(pos);
                    }
                }
            }
        }

        ArenaLayout layout = ArenaLayout.resolve(markers, searchCentre, searchRadius);
        ContainerRegistry containers = ContainerRegistry.of(containerPositions, layout.centre(),
                layout.radius(), centreTierRadius);

        CoveBattle.LOGGER.info("Arena scan: {} containers ({} centre), {} markers — {}",
                containers.size(), containers.centreCount(), layout.markerCount(), layout.summary());
        return new ArenaScan(containers, layout);
    }
}
