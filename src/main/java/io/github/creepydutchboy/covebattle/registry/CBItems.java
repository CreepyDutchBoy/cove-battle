package io.github.creepydutchboy.covebattle.registry;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.block.MarkerKind;
import io.github.creepydutchboy.covebattle.item.GhostBootsItem;
import io.github.creepydutchboy.covebattle.item.MirageTotemItem;
import io.github.creepydutchboy.covebattle.item.SiphonFlaskItem;
import io.github.creepydutchboy.covebattle.item.SmokeBombItem;
import io.github.creepydutchboy.covebattle.item.StormEggItem;
import io.github.creepydutchboy.covebattle.item.TidalSurgeItem;
import io.github.creepydutchboy.covebattle.item.TideRodItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Marker block items and the custom battle items. */
public final class CBItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CoveBattle.MODID);

    public static final Map<MarkerKind, DeferredItem<BlockItem>> MARKERS = new EnumMap<>(MarkerKind.class);

    static {
        for (MarkerKind kind : MarkerKind.values()) {
            MARKERS.put(kind, ITEMS.registerSimpleBlockItem(kind.id(), CBBlocks.MARKERS.get(kind)));
        }
    }

    public static final DeferredItem<Item> STORM_EGG = ITEMS.register("storm_egg",
            () -> new StormEggItem(new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> SMOKE_BOMB = ITEMS.register("smoke_bomb",
            () -> new SmokeBombItem(new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> TIDAL_SURGE = ITEMS.register("tidal_surge",
            () -> new TidalSurgeItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE)));

    public static final DeferredItem<Item> MIRAGE_TOTEM = ITEMS.register("mirage_totem",
            () -> new MirageTotemItem(new Item.Properties().stacksTo(2).rarity(Rarity.EPIC)));

    /** Poor durability on purpose: a handful of casts and it is gone. */
    public static final DeferredItem<Item> TIDE_ROD = ITEMS.register("tide_rod",
            () -> new TideRodItem(new Item.Properties().durability(18).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> SIPHON_FLASK = ITEMS.register("siphon_flask",
            () -> new SiphonFlaskItem(new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON)));

    /**
     * Temporarily disabled: still registered so existing stacks and saves do not break, but kept
     * out of loot and out of the creative tab until it is retuned.
     */
    public static final DeferredItem<Item> COVESLAYER = ITEMS.register("coveslayer",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4, -2.2F))));

    public static final DeferredItem<Item> GHOST_BOOTS = ITEMS.register("ghost_boots",
            () -> new GhostBootsItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
                    .durability(ArmorItem.Type.BOOTS.getDurability(22))));

    private CBItems() {}
}
