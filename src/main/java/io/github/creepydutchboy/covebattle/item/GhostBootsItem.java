package io.github.creepydutchboy.covebattle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Boots that do something now: real armour, quicker on your feet, and no fall damage at all while
 * they are on (handled on the damage event so it holds against any fall).
 */
public class GhostBootsItem extends ArmorItem {

    public GhostBootsItem(Properties properties) {
        super(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Light as sea mist.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  Armour ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("diamond-tier boots").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Fall damage ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("none while worn").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Move speed ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("+12%").withStyle(ChatFormatting.GOLD)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
