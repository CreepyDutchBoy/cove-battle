package io.github.creepydutchboy.covebattle.loot;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What kind of enchantments a piece of loot can roll.
 *
 * <p>Loot used to carry one fixed enchantment per entry, so every diamond sword in every match was
 * Sharpness II. Each entry now names a style and the roll picks one to three enchantments from that
 * style's table at random levels, so two swords out of the same chest can read very differently.
 */
public enum EnchantStyle {
    NONE,
    SWORD,
    AXE,
    BOW,
    CROSSBOW,
    ARMOUR,
    BOOTS,
    HELMET,
    ROD,
    TRIDENT;

    /** enchantment -> highest level this style will roll */
    private Map<ResourceKey<Enchantment>, Integer> table() {
        Map<ResourceKey<Enchantment>, Integer> table = new LinkedHashMap<>();
        switch (this) {
            case SWORD -> {
                table.put(Enchantments.SHARPNESS, 4);
                table.put(Enchantments.FIRE_ASPECT, 2);
                table.put(Enchantments.KNOCKBACK, 2);
                table.put(Enchantments.SWEEPING_EDGE, 3);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case AXE -> {
                table.put(Enchantments.SHARPNESS, 4);
                table.put(Enchantments.EFFICIENCY, 3);
                table.put(Enchantments.UNBREAKING, 3);
                table.put(Enchantments.FIRE_ASPECT, 1);
            }
            case BOW -> {
                table.put(Enchantments.POWER, 4);
                table.put(Enchantments.PUNCH, 2);
                table.put(Enchantments.FLAME, 1);
                table.put(Enchantments.INFINITY, 1);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case CROSSBOW -> {
                table.put(Enchantments.QUICK_CHARGE, 3);
                table.put(Enchantments.PIERCING, 3);
                table.put(Enchantments.MULTISHOT, 1);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case ARMOUR -> {
                table.put(Enchantments.PROTECTION, 4);
                table.put(Enchantments.PROJECTILE_PROTECTION, 3);
                table.put(Enchantments.BLAST_PROTECTION, 3);
                table.put(Enchantments.FIRE_PROTECTION, 3);
                table.put(Enchantments.THORNS, 2);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case BOOTS -> {
                table.put(Enchantments.PROTECTION, 4);
                table.put(Enchantments.FEATHER_FALLING, 4);
                table.put(Enchantments.DEPTH_STRIDER, 3);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case HELMET -> {
                table.put(Enchantments.PROTECTION, 4);
                table.put(Enchantments.RESPIRATION, 3);
                table.put(Enchantments.AQUA_AFFINITY, 1);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case ROD -> {
                table.put(Enchantments.LURE, 3);
                table.put(Enchantments.LUCK_OF_THE_SEA, 3);
                table.put(Enchantments.UNBREAKING, 2);
            }
            case TRIDENT -> {
                table.put(Enchantments.IMPALING, 4);
                table.put(Enchantments.LOYALTY, 3);
                table.put(Enchantments.RIPTIDE, 2);
                table.put(Enchantments.UNBREAKING, 3);
            }
            case NONE -> {
            }
        }
        return table;
    }

    /**
     * Rolls enchantments onto a stack.
     *
     * @param quality 0..1, nudging both how many enchantments land and how high they go
     */
    public void apply(ItemStack stack, Registry<Enchantment> registry, RandomSource random, float quality) {
        Map<ResourceKey<Enchantment>, Integer> table = table();
        if (table.isEmpty()) return;

        List<ResourceKey<Enchantment>> keys = new ArrayList<>(table.keySet());
        java.util.Collections.shuffle(keys, new java.util.Random(random.nextLong()));

        int count = 1 + random.nextInt(quality > 0.6f ? 3 : 2);
        count = Math.min(count, keys.size());

        for (int i = 0; i < count; i++) {
            ResourceKey<Enchantment> key = keys.get(i);
            int max = table.get(key);
            int ceiling = Math.max(1, Math.round(max * (0.45f + 0.55f * quality)));
            int level = 1 + random.nextInt(Math.max(1, ceiling));
            Holder<Enchantment> holder = registry.getHolderOrThrow(key);
            stack.enchant(holder, level);
        }
    }
}
