package io.github.creepydutchboy.covebattle.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared behaviour for the thrown battle items.
 *
 * <p>These are real projectile entities rather than an instant effect at the crosshair: they arc,
 * they can be dodged, they can miss, and they land where they land. Subclasses only describe what
 * happens at the point of impact.
 */
public abstract class BattleProjectile extends ThrowableItemProjectile {

    protected BattleProjectile(EntityType<? extends BattleProjectile> type, Level level) {
        super(type, level);
    }

    protected BattleProjectile(EntityType<? extends BattleProjectile> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    /** What happens where it lands. Only called on the server. */
    protected abstract void burst(ServerLevel level, Vec3 at);

    protected abstract ParticleOptions trail();

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && tickCount % 2 == 0) {
            level().addParticle(trail(), getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel serverLevel)) return;
        burst(serverLevel, result.getLocation());
        discard();
    }

    /** Players who are not on the thrower's side, within range of the impact. */
    protected List<ServerPlayer> opponentsNear(ServerLevel level, Vec3 at, double range) {
        List<ServerPlayer> result = new ArrayList<>();
        double rangeSq = range * range;
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        for (ServerPlayer player : level.players()) {
            if (player == owner) continue;
            if (player.isSpectator()) continue;
            if (owner != null && owner.isAlliedTo(player)) continue;
            if (player.distanceToSqr(at) > rangeSq) continue;
            result.add(player);
        }
        return result;
    }
}
