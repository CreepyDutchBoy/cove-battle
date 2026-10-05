package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.mirage.MirageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Four copies of you, doing a convincing impression of you, for twelve seconds. */
public class MirageTotemItem extends AbilityItem {

    private static final int COPIES = 4;
    private static final int LIFETIME_TICKS = 12 * 20;

    public MirageTotemItem(Properties properties) {
        super(properties, 200);
    }

    @Override
    protected boolean activate(ServerLevel level, ServerPlayer player) {
        MirageManager.summon(player, COPIES, LIFETIME_TICKS);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                90, 1.2, 1.0, 1.2, 0.08);
        sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 1.0F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Four copies of you, doing their best impression.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  Copies ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("4, with your skin and your visible gear").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Lasts ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("12.0s, then they fade").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Behaviour ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("wander, chase, or stand — and they swing at people").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Note ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("cannot be hurt or killed, and drop nothing").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Cooldown ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("10.0s, scaled by the ability cooldown mutator").withStyle(ChatFormatting.GOLD)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
