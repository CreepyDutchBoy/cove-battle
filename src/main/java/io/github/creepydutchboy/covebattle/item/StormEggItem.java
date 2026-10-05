package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.entity.BattleProjectile;
import io.github.creepydutchboy.covebattle.entity.ThrownStormEgg;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Throw it; lightning lands where it does. */
public class StormEggItem extends ThrowableBattleItem {

    public StormEggItem(Properties properties) {
        super(properties, 60);
    }

    @Override
    protected BattleProjectile create(Level level, Player player) {
        return new ThrownStormEgg(level, player);
    }
}
