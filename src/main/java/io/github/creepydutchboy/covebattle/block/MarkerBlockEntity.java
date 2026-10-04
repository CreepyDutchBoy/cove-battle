package io.github.creepydutchboy.covebattle.block;

import io.github.creepydutchboy.covebattle.registry.CBBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries no data. It exists purely so markers appear in {@code LevelChunk#getBlockEntities()},
 * which is how the arena scan finds them cheaply instead of sweeping millions of block positions.
 */
public class MarkerBlockEntity extends BlockEntity {

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(CBBlockEntities.MARKER.get(), pos, state);
    }

    public MarkerKind kind() {
        return state(getBlockState());
    }

    public static MarkerKind state(BlockState state) {
        return state.getBlock() instanceof MarkerBlock marker ? marker.kind() : MarkerKind.ARENA_CENTRE;
    }
}
