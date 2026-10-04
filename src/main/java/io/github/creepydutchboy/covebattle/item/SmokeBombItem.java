package io.github.creepydutchboy.covebattle.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Breaks contact: you go invisible and quick, anyone hostile nearby loses their sight. */
public class SmokeBombItem extends AbilityItem {

    private static final double RADIUS = 10.0D;

    public SmokeBombItem(Properties properties) {
        super(properties, 40);
    }

    @Override
    protected boolean activate(ServerLevel level, ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, false, false, true));

        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                120, 1.2, 1.0, 1.2, 0.02);
        sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);

        for (ServerPlayer target : opponentsNear(level, player, player.getX(), player.getY(), player.getZ(), RADIUS)) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, true));
        }
        return true;
    }
}
