package io.github.creepydutchboy.covebattle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A fishing rod that yanks like the old ones did.
 *
 * <p>Modern rods barely move a hooked player. This one reads who is on the hook before the vanilla
 * retrieve clears it, then adds the pull back on top, so reeling someone in actually drags them.
 * It is deliberately fragile — a handful of casts and it is gone.
 */
public class TideRodItem extends FishingRodItem {

    private static final double PULL = 0.9D;
    private static final double LIFT = 0.32D;

    public TideRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Vanilla's retrieve clears the hook, so note the target first.
        Entity hooked = player.fishing != null ? player.fishing.getHookedIn() : null;

        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);

        if (!level.isClientSide() && hooked instanceof Player caught) {
            Vec3 towards = player.position().subtract(caught.position());
            if (towards.lengthSqr() > 0.01D) {
                towards = towards.normalize();
                caught.push(towards.x * PULL, LIFT, towards.z * PULL);
                caught.hurtMarked = true;
            }
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Reels players in the way old rods did.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  Pull ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("drags a hooked player toward you, with lift").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Durability ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("18 — it will not last a whole match").withStyle(ChatFormatting.GOLD)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
