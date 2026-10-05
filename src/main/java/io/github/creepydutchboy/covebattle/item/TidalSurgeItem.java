package io.github.creepydutchboy.covebattle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Replaces the old War Horn. Rather than buffing your side, it clears the space you are standing
 * in: everyone hostile nearby is thrown outward and left stumbling, and you get a short hop out of
 * the scrum.
 */
public class TidalSurgeItem extends AbilityItem {

    private static final double RADIUS = 9.0D;
    private static final double PUSH = 2.1D;
    private static final float DAMAGE = 4.0F;

    public TidalSurgeItem(Properties properties) {
        super(properties, 140);
    }

    @Override
    protected boolean activate(ServerLevel level, ServerPlayer player) {
        Vec3 origin = player.position();

        level.sendParticles(ParticleTypes.SPLASH, origin.x, origin.y + 0.6, origin.z, 180, 2.2, 0.6, 2.2, 0.4);
        level.sendParticles(ParticleTypes.BUBBLE_POP, origin.x, origin.y + 0.4, origin.z, 90, 1.8, 0.4, 1.8, 0.3);
        sound(level, origin.x, origin.y, origin.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.4F, 0.7F);

        for (ServerPlayer target : opponentsNear(level, player, origin.x, origin.y, origin.z, RADIUS)) {
            Vec3 away = target.position().subtract(origin);
            if (away.lengthSqr() < 0.01D) away = new Vec3(0.0D, 0.0D, 1.0D);
            away = away.normalize();

            double falloff = 1.0D - Math.min(1.0D, target.distanceTo(player) / RADIUS) * 0.5D;
            target.push(away.x * PUSH * falloff, 0.72D * falloff, away.z * PUSH * falloff);
            target.hurtMarked = true;
            target.hurt(level.damageSources().playerAttack(player), DAMAGE);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
        }

        player.push(0.0D, 0.42D, 0.0D);
        player.hurtMarked = true;
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Clears the space around you.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  Radius ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("9.0 blocks").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Knockback ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("strong, outward and upward").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Damage ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("4.0 (2 hearts) + Slowness II for 3.0s").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  Cooldown ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("7.0s, scaled by the ability cooldown mutator").withStyle(ChatFormatting.GOLD)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
