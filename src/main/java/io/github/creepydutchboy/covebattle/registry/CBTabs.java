package io.github.creepydutchboy.covebattle.registry;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.block.MarkerKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Two creative tabs: one for the map-making markers, one for the battle items. Keeping them apart
 * means a map maker laying out an arena never has to scroll past combat gear, and vice versa.
 */
public final class CBTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CoveBattle.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MARKERS = TABS.register("markers",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.covebattle.markers"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(CBItems.MARKERS.get(MarkerKind.ARENA_CENTRE).get()))
                    .displayItems((parameters, output) -> {
                        for (MarkerKind kind : MarkerKind.values()) {
                            output.accept(CBItems.MARKERS.get(kind).get());
                        }
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS = TABS.register("items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.covebattle.items"))
                    .withTabsBefore(MARKERS.getKey())
                    .icon(() -> new ItemStack(CBItems.COVESLAYER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(CBItems.COVESLAYER.get());
                        output.accept(CBItems.GHOST_BOOTS.get());
                        output.accept(CBItems.STORM_EGG.get());
                        output.accept(CBItems.SMOKE_BOMB.get());
                        output.accept(CBItems.WAR_HORN.get());
                        output.accept(CBItems.SIPHON_FLASK.get());
                    })
                    .build());

    private CBTabs() {}
}
