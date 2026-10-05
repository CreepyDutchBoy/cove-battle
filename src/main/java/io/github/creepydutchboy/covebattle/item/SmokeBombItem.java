package io.github.creepydutchboy.covebattle.item;

import io.github.creepydutchboy.covebattle.entity.BattleProjectile;
import io.github.creepydutchboy.covebattle.entity.ThrownSmokeBomb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Throw it to break line of sight: you vanish, they go blind. */
public class SmokeBombItem extends ThrowableBattleItem {

    public SmokeBombItem(Properties properties) {
        super(properties, 40);
    }

    @Override
    protected BattleProjectile create(Level level, Player player) {
        return new ThrownSmokeBomb(level, player);
    }
}
