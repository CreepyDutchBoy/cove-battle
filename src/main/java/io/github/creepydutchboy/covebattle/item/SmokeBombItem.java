package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.entity.BattleProjectile;
import io.github.creepydutchboy.covebattle.entity.ThrownSmokeBomb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;


/** Throw it to break line of sight: you vanish, they go blind. */
public class SmokeBombItem extends ThrowableBattleItem {

    public SmokeBombItem(Properties properties) {
        super(properties, 40);
    }

    @Override
    protected BattleProjectile create(Level level, Player player) {
        return new ThrownSmokeBomb(level, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Throw it to break line of sight.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  You get ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Invisibility I + Speed II for 6.0s").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  They get ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Blindness I + Nausea I for 4.0s").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Cloud radius ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("7.0 blocks from the impact").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Cooldown ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("2.0s, scaled by the ability cooldown mutator").withStyle(ChatFormatting.GOLD)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
