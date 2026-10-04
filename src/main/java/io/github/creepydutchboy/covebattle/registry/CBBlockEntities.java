package io.github.creepydutchboy.covebattle.registry;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.block.MarkerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One shared block entity type for every marker block. */
public final class CBBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CoveBattle.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
            BLOCK_ENTITIES.register("marker", () -> BlockEntityType.Builder
                    .of(MarkerBlockEntity::new, CBBlocks.MARKERS.values().stream()
                            .map(DeferredBlockHelper::get).toArray(Block[]::new))
                    .build(null));

    private CBBlockEntities() {}

    /** Tiny indirection so the stream above stays readable. */
    private static final class DeferredBlockHelper {
        private static Block get(net.neoforged.neoforge.registries.DeferredBlock<Block> holder) {
            return holder.get();
        }
    }
}
