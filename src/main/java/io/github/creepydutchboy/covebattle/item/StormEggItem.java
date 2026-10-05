package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.entity.BattleProjectile;
import io.github.creepydutchboy.covebattle.entity.ThrownStormEgg;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;


/** Throw it; lightning lands where it does. */
public class StormEggItem extends ThrowableBattleItem {

    public StormEggItem(Properties properties) {
        super(properties, 60);
    }

    @Override
    protected BattleProjectile create(Level level, Player player) {
        return new ThrownStormEgg(level, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Throw it. Lightning lands where it does.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  Damage ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("7.0 (3.5 hearts), lightning type").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Splash radius ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("3.0 blocks").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Cooldown ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("3.0s, scaled by the ability cooldown mutator").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Hits ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("opponents only, never your own side").withStyle(ChatFormatting.GOLD)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
