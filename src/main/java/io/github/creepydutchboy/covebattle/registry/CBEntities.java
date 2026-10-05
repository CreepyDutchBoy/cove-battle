package io.github.creepydutchboy.covebattle.registry;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.entity.ThrownSmokeBomb;
import io.github.creepydutchboy.covebattle.entity.ThrownStormEgg;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The thrown battle items are real entities, so they fly, miss, and can be dodged. */
public final class CBEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CoveBattle.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownStormEgg>> STORM_EGG =
            ENTITIES.register("storm_egg", () -> EntityType.Builder
                    .<ThrownStormEgg>of(ThrownStormEgg::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("storm_egg"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSmokeBomb>> SMOKE_BOMB =
            ENTITIES.register("smoke_bomb", () -> EntityType.Builder
                    .<ThrownSmokeBomb>of(ThrownSmokeBomb::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("smoke_bomb"));

    private CBEntities() {}
}
