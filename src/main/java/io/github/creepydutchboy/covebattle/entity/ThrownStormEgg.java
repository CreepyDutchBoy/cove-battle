package io.github.creepydutchboy.covebattle.entity;

import io.github.creepydutchboy.covebattle.registry.CBEntities;
import io.github.creepydutchboy.covebattle.registry.CBItems;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Lands, then calls down a visual-only bolt and hurts whoever is standing in it. */
public class ThrownStormEgg extends BattleProjectile {

    private static final double SPLASH = 3.0D;
    private static final float DAMAGE = 7.0F;

    public ThrownStormEgg(EntityType<? extends ThrownStormEgg> type, Level level) {
        super(type, level);
    }

    public ThrownStormEgg(Level level, LivingEntity owner) {
        super(CBEntities.STORM_EGG.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return CBItems.STORM_EGG.get();
    }

    @Override
    protected ParticleOptions trail() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    @Override
    protected void burst(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            // Visual only: the damage below is applied by hand so a map can never catch fire.
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 80, 0.7, 1.0, 0.7, 0.5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 1.1F);

        for (ServerPlayer target : opponentsNear(level, at, SPLASH)) {
            target.hurt(level.damageSources().lightningBolt(), DAMAGE);
        }
    }
}
