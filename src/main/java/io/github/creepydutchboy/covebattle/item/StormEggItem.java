package io.github.creepydutchboy.covebattle.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Calls lightning down on the spot you are looking at. The bolt is visual only and the damage is
 * applied by hand, so it cannot set a map on fire.
 */
public class StormEggItem extends AbilityItem {

    private static final double RANGE = 40.0D;
    private static final double SPLASH = 3.0D;
    private static final float DAMAGE = 7.0F;

    public StormEggItem(Properties properties) {
        super(properties, 60);
    }

    @Override
    protected boolean activate(ServerLevel level, ServerPlayer player) {
        HitResult hit = player.pick(RANGE, 0.0F, false);
        Vec3 at = hit.getLocation();

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 60, 0.6, 1.0, 0.6, 0.4);
        sound(level, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.1F);

        for (ServerPlayer target : opponentsNear(level, player, at.x, at.y, at.z, SPLASH)) {
            target.hurt(level.damageSources().lightningBolt(), DAMAGE);
        }
        return true;
    }
}
