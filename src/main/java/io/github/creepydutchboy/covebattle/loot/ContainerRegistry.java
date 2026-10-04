package io.github.creepydutchboy.covebattle.loot;

import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every chest and barrel in the arena, found once per round by walking the block entities of the
 * chunks the arena covers.
 *
 * <p>This is the part the datapack could never get right. There, containers were discovered by
 * scanning thousands of block positions around each player every tick and flagged with invisible
 * marker entities, which left stale markers in unloaded chunks and silently skipped containers
 * on later rounds — barrels especially. A mod can simply ask the chunk for its block entities,
 * so discovery is exact, cheap, and identical for chests, trapped chests and barrels: they all
 * answer to the same {@link Container} interface.
 */
public final class ContainerRegistry {

    /** A known container and which loot tier it belongs to. */
    public record Slot(BlockPos pos, boolean centre) {}

    private final List<Slot> slots = new ArrayList<>();
    private final Set<BlockPos> looted = new HashSet<>();

    private ContainerRegistry() {}

    /**
     * Walks the chunks covering the arena and records every chest and barrel inside the radius.
     * Chunks are loaded as needed, which is deliberate: the whole arena should be live for the
     * match anyway.
     */
    public static ContainerRegistry scan(ServerLevel level, BlockPos centre, int radius, int centreRadius) {
        ContainerRegistry registry = new ContainerRegistry();

        int minChunkX = (centre.getX() - radius) >> 4;
        int maxChunkX = (centre.getX() + radius) >> 4;
        int minChunkZ = (centre.getZ() - radius) >> 4;
        int maxChunkZ = (centre.getZ() + radius) >> 4;

        long radiusSq = (long) radius * radius;
        long centreSq = (long) centreRadius * centreRadius;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isBattleContainer(be)) continue;
                    BlockPos pos = be.getBlockPos().immutable();
                    long dx = pos.getX() - centre.getX();
                    long dz = pos.getZ() - centre.getZ();
                    long distSq = dx * dx + dz * dz;
                    if (distSq > radiusSq) continue;
                    registry.slots.add(new Slot(pos, distSq <= centreSq));
                }
            }
        }

        CoveBattle.LOGGER.info("Arena scan found {} containers ({} centre, {} outer) in {} chunks",
                registry.slots.size(), registry.centreCount(), registry.size() - registry.centreCount(),
                (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        return registry;
    }

    /** Chests, trapped chests (a subclass) and barrels. Shulker boxes are deliberately excluded. */
    private static boolean isBattleContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    }

    public int size() {
        return slots.size();
    }

    public int centreCount() {
        return (int) slots.stream().filter(Slot::centre).count();
    }

    public int lootedCount() {
        return looted.size();
    }

    public List<Slot> slots() {
        return Collections.unmodifiableList(slots);
    }

    public boolean contains(BlockPos pos) {
        for (Slot slot : slots) {
            if (slot.pos().equals(pos)) return true;
        }
        return false;
    }

    public void markLooted(BlockPos pos) {
        looted.add(pos.immutable());
    }

    /** Fills every known container. Used at the start of a round. */
    public int fillAll(ServerLevel level, RandomSource random) {
        looted.clear();
        int filled = 0;
        for (Slot slot : slots) {
            if (fill(level, slot, random)) filled++;
        }
        return filled;
    }

    /**
     * Console Battle restocks four random chests every thirty seconds, and favours ones no player
     * is standing next to. Same here: only containers already looted are candidates.
     *
     * @return how many were refilled
     */
    public int restock(ServerLevel level, int count, double avoidPlayersWithin, RandomSource random) {
        if (looted.isEmpty()) return 0;

        List<Slot> candidates = new ArrayList<>();
        for (Slot slot : slots) {
            if (!looted.contains(slot.pos())) continue;
            if (level.getNearestPlayer(slot.pos().getX() + 0.5, slot.pos().getY() + 0.5, slot.pos().getZ() + 0.5,
                    avoidPlayersWithin, false) != null) {
                continue;
            }
            candidates.add(slot);
        }
        Collections.shuffle(candidates);

        int refilled = 0;
        for (Slot slot : candidates) {
            if (refilled >= count) break;
            if (fill(level, slot, random)) {
                looted.remove(slot.pos());
                refilled++;
            }
        }
        return refilled;
    }

    private boolean fill(ServerLevel level, Slot slot, RandomSource random) {
        BlockEntity be = level.getBlockEntity(slot.pos());
        if (!(be instanceof Container container)) return false;
        BattleLoot.fill(level, container, slot.centre(), random);
        return true;
    }

    /** Empties every known container. Used when a match ends so the map is not left stocked. */
    public int clearAll(ServerLevel level) {
        int cleared = 0;
        for (Slot slot : slots) {
            if (level.getBlockEntity(slot.pos()) instanceof Container container) {
                container.clearContent();
                container.setChanged();
                cleared++;
            }
        }
        looted.clear();
        return cleared;
    }
}
