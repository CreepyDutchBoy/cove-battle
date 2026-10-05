package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.entity.BattleProjectile;
import io.github.creepydutchboy.covebattle.rules.RulesState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** An item you throw. The projectile does the work when it lands. */
public abstract class ThrowableBattleItem extends Item {

    private final int cooldownTicks;

    protected ThrowableBattleItem(Properties properties, int cooldownTicks) {
        super(properties);
        this.cooldownTicks = cooldownTicks;
    }

    protected abstract BattleProjectile create(Level level, Player player);

    protected int scaledCooldown() {
        return Math.max(1, Math.round(cooldownTicks * RulesState.active().abilityCooldown()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide()) {
            BattleProjectile projectile = create(level, player);
            projectile.setItem(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.4F, 1.0F);
            level.addFreshEntity(projectile);
        }

        player.getCooldowns().addCooldown(this, scaledCooldown());
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
