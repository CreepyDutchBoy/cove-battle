package io.github.creepydutchboy.covebattle.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** A placed configuration point. See {@link MarkerKind}. */
public class MarkerBlock extends Block implements EntityBlock {

    private final MarkerKind kind;

    public MarkerBlock(MarkerKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public MarkerKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }

    /** Clicking a marker says what it is, which saves hunting through the config. */
    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                               Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.literal(kind.label() + " marker at "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.AQUA), false);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}
