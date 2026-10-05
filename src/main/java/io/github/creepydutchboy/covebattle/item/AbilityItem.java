package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.rules.RulesState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared plumbing for the single-use ability items: cooldown, consumption, and finding opponents.
 *
 * <p>None of these spawn a projectile entity. They resolve immediately against what the player is
 * looking at or standing near, which keeps the behaviour readable and avoids registering entity
 * types for what is really an instant effect.
 */
public abstract class AbilityItem extends Item {

    private final int cooldownTicks;

    protected AbilityItem(Properties properties, int cooldownTicks) {
        super(properties);
        this.cooldownTicks = cooldownTicks;
    }

    /** Cooldowns move with the ability-cooldown mutator. */
    protected int scaledCooldown() {
        return Math.max(1, Math.round(cooldownTicks * RulesState.active().abilityCooldown()));
    }

    /** @return true when the ability fired and the item should be spent */
    protected abstract boolean activate(ServerLevel level, ServerPlayer player);

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!activate(serverLevel, serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            serverPlayer.getCooldowns().addCooldown(this, scaledCooldown());
            if (!serverPlayer.isCreative()) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Players who are not on the user's team, within range. Spectators are ignored. */
    protected static List<ServerPlayer> opponentsNear(ServerLevel level, ServerPlayer user, double x, double y, double z, double range) {
        List<ServerPlayer> result = new ArrayList<>();
        double rangeSq = range * range;
        for (ServerPlayer other : level.players()) {
            if (other == user) continue;
            if (other.isSpectator()) continue;
            if (user.isAlliedTo(other)) continue;
            if (other.distanceToSqr(x, y, z) > rangeSq) continue;
            result.add(other);
        }
        return result;
    }

    protected static List<ServerPlayer> alliesNear(ServerLevel level, ServerPlayer user, double range) {
        List<ServerPlayer> result = new ArrayList<>();
        double rangeSq = range * range;
        for (ServerPlayer other : level.players()) {
            if (other.isSpectator()) continue;
            if (other != user && !user.isAlliedTo(other)) continue;
            if (other.distanceToSqr(user.getX(), user.getY(), user.getZ()) > rangeSq) continue;
            result.add(other);
        }
        return result;
    }

    protected static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
