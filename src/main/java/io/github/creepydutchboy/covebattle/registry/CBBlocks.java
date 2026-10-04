package io.github.creepydutchboy.covebattle.registry;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.block.MarkerBlock;
import io.github.creepydutchboy.covebattle.block.MarkerKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Marker blocks, one per {@link MarkerKind}. */
public final class CBBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CoveBattle.MODID);

    public static final Map<MarkerKind, DeferredBlock<Block>> MARKERS = new EnumMap<>(MarkerKind.class);

    static {
        for (MarkerKind kind : MarkerKind.values()) {
            MARKERS.put(kind, BLOCKS.register(kind.id(), () -> new MarkerBlock(kind,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(0.6F)
                            .sound(SoundType.METAL)
                            .noOcclusion())));
        }
    }

    private CBBlocks() {}
}
