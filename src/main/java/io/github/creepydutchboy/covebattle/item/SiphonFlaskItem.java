package io.github.creepydutchboy.covebattle.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/** Drains the nearest opponent to top yourself up. */
public class SiphonFlaskItem extends AbilityItem {

    private static final double RANGE = 12.0D;
    private static final float LEECH = 4.0F;
    private static final float HEAL = 6.0F;

    public SiphonFlaskItem(Properties properties) {
        super(properties, 80);
    }

    @Override
    protected boolean activate(ServerLevel level, ServerPlayer player) {
        List<ServerPlayer> targets = opponentsNear(level, player, player.getX(), player.getY(), player.getZ(), RANGE);

        player.heal(HEAL);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0, false, false, true));
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
        sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_DRINK, 1.0F, 1.2F);

        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer target : targets) {
            double d = target.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = target;
            }
        }
        if (nearest != null) {
            nearest.hurt(level.damageSources().magic(), LEECH);
            nearest.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false, true));
            level.sendParticles(ParticleTypes.SCULK_SOUL, nearest.getX(), nearest.getY() + 1.0, nearest.getZ(),
                    30, 0.4, 0.6, 0.4, 0.05);
        }
        return true;
    }
}
