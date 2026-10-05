package io.github.creepydutchboy.covebattle.world;

import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A copy of the arena exactly as it was, used to put it back between rounds.
 *
 * <p>The first attempt at this listened for block events and remembered what was there before each
 * change. That failed for the case that matters most: an explosion reports its affected positions
 * only once the blocks are already gone, so what got remembered was air, and restoring wrote air
 * back over air. This takes the honest route instead — read the whole arena once when a match
 * starts, then at the start of each round write back every block that no longer matches.
 *
 * <p>Cost is one pass over the arena volume per match and one comparison pass per round. For a
 * radius of eighty that is under a million blocks, a few megabytes of references, and well under a
 * tick's worth of work spread over the round transition.
 */
public final class ArenaSnapshot {

    private final BlockPos origin;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockState[] states;

    private ArenaSnapshot(BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.states = new BlockState[sizeX * sizeY * sizeZ];
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /**
     * Reads the arena.
     *
     * @param below how far under the arena centre to include
     * @param above how far over it to include
     */
    public static ArenaSnapshot capture(ServerLevel level, BlockPos centre, int radius, int below, int above) {
        int sizeX = radius * 2 + 1;
        int sizeZ = radius * 2 + 1;
        int sizeY = below + above + 1;
        BlockPos origin = new BlockPos(centre.getX() - radius, centre.getY() - below, centre.getZ() - radius);

        ArenaSnapshot snapshot = new ArenaSnapshot(origin, sizeX, sizeY, sizeZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long start = System.nanoTime();

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    snapshot.states[snapshot.index(x, y, z)] = level.getBlockState(cursor);
                }
            }
        }

        CoveBattle.LOGGER.info("Captured the arena: {} x {} x {} blocks in {} ms",
                sizeX, sizeY, sizeZ, (System.nanoTime() - start) / 1_000_000);
        return snapshot;
    }

    /**
     * Writes back everything that has changed, and puts out any fire on the way.
     *
     * @return how many blocks were put back
     */
    public int restore(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int restored = 0;
        long start = System.nanoTime();

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState original = states[index(x, y, z)];
                    if (original == null) continue;
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState current = level.getBlockState(cursor);
                    if (current == original) continue;
                    // Flag 2 updates clients without kicking off neighbour cascades for every block.
                    level.setBlock(cursor, original, 2);
                    restored++;
                }
            }
        }

        if (restored > 0) {
            CoveBattle.LOGGER.info("Restored {} block(s) in {} ms", restored, (System.nanoTime() - start) / 1_000_000);
        }
        return restored;
    }

    /** Blocks covered, for reporting. */
    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    public boolean covers(BlockPos pos) {
        return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + sizeX
                && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + sizeY
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + sizeZ;
    }

    /** Convenience for the common case of a fire left burning. */
    public static int extinguish(ServerLevel level, BlockPos centre, int radius) {
        int cleared = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -8; y <= 24; y++) {
                    cursor.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (level.getBlockState(cursor).is(Blocks.FIRE)) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }
}
