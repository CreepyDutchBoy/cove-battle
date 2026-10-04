package io.github.creepydutchboy.covebattle.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.ChatFormatting;

/** Rallies your side and paints the other one. Reusable, so it only takes a cooldown. */
public class WarHornItem extends AbilityItem {

    private static final double ALLY_RANGE = 24.0D;
    private static final double MARK_RANGE = 64.0D;

    public WarHornItem(Properties properties) {
        super(properties, 200);
    }

    @Override
    protected boolean activate(ServerLevel level, ServerPlayer player) {
        sound(level, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), 3.0F, 1.0F);
        level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.6, player.getZ(), 30, 0.6, 0.4, 0.6, 1.0);

        for (ServerPlayer ally : alliesNear(level, player, ALLY_RANGE)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, false, true));
        }
        for (ServerPlayer target : opponentsNear(level, player, player.getX(), player.getY(), player.getZ(), MARK_RANGE)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false, true));
            target.displayClientMessage(Component.literal("A war horn sounds — you are marked.")
                    .withStyle(ChatFormatting.RED), true);
        }
        return false; // reusable: cooldown only, never consumed
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
            net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            activate(serverLevel, serverPlayer);
            serverPlayer.getCooldowns().addCooldown(this, 200);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
