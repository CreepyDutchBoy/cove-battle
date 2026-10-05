package io.github.creepydutchboy.covebattle.entity;

import io.github.creepydutchboy.covebattle.registry.CBEntities;
import io.github.creepydutchboy.covebattle.registry.CBItems;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Lands in a cloud: hides whoever threw it, blinds whoever it lands next to. */
public class ThrownSmokeBomb extends BattleProjectile {

    private static final double RADIUS = 9.0D;

    public ThrownSmokeBomb(EntityType<? extends ThrownSmokeBomb> type, Level level) {
        super(type, level);
    }

    public ThrownSmokeBomb(Level level, LivingEntity owner) {
        super(CBEntities.SMOKE_BOMB.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return CBItems.SMOKE_BOMB.get();
    }

    @Override
    protected ParticleOptions trail() {
        return ParticleTypes.SMOKE;
    }

    @Override
    protected void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 160, 1.6, 1.0, 1.6, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);

        if (getOwner() instanceof ServerPlayer thrower) {
            thrower.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false, true));
            thrower.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1, false, false, true));
        }
        for (ServerPlayer target : opponentsNear(level, at, RADIUS)) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 110, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 110, 0, false, false, true));
            // The actual flashbang: three seconds where they genuinely cannot see.
            io.github.creepydutchboy.covebattle.net.CBNetwork.sendFlash(target, 60);
        }
    }
}
