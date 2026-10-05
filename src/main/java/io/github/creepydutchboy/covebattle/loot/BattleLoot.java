package io.github.creepydutchboy.covebattle.loot;

import io.github.creepydutchboy.covebattle.registry.CBItems;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * Loot, defined in code so the two tiers can be tuned in one place.
 *
 * <p>The centre platform is the <em>poor</em> tier: you land on it with nothing and it gives you
 * just enough to fight with, which pushes people off it and out into the map. The outer ring is
 * where the real gear is — better, but deliberately short of overwhelming, so arriving there first
 * is an advantage rather than a win. Custom gear is a rare surprise in the middle and a normal find
 * outside.
 *
 * <p>Nothing here can be placed as a block: matches run in adventure mode and unplaceable loot
 * would be dead weight.
 */
public final class BattleLoot {

    /** One possible stack. {@code style} decides which enchantments it can roll, if any. */
    public record Entry(Item item, int min, int max, int weight, EnchantStyle style) {
        public Entry(Item item, int min, int max, int weight) {
            this(item, min, max, weight, EnchantStyle.NONE);
        }
    }

    private static List<Entry> centrePool;
    private static List<Entry> outerPool;

    /** Centre platform: scraps to get you moving. */
    private static List<Entry> buildCentre() {
        return List.of(
                new Entry(Items.STONE_SWORD, 1, 1, 9),
                new Entry(Items.IRON_SWORD, 1, 1, 6, EnchantStyle.SWORD),
                new Entry(Items.STONE_AXE, 1, 1, 7),
                new Entry(Items.IRON_AXE, 1, 1, 5, EnchantStyle.AXE),
                new Entry(Items.BOW, 1, 1, 6, EnchantStyle.BOW),
                new Entry(Items.ARROW, 4, 10, 9),
                new Entry(Items.SHIELD, 1, 1, 5),
                new Entry(Items.LEATHER_CHESTPLATE, 1, 1, 6, EnchantStyle.ARMOUR),
                new Entry(Items.LEATHER_HELMET, 1, 1, 5, EnchantStyle.HELMET),
                new Entry(Items.IRON_HELMET, 1, 1, 4, EnchantStyle.HELMET),
                new Entry(Items.IRON_CHESTPLATE, 1, 1, 3, EnchantStyle.ARMOUR),
                new Entry(Items.IRON_BOOTS, 1, 1, 4, EnchantStyle.BOOTS),
                new Entry(Items.CHAINMAIL_LEGGINGS, 1, 1, 4, EnchantStyle.ARMOUR),
                new Entry(Items.BREAD, 2, 5, 9),
                new Entry(Items.COOKED_CHICKEN, 2, 4, 7),
                new Entry(Items.APPLE, 2, 4, 7),
                new Entry(Items.GOLDEN_APPLE, 1, 1, 2),
                new Entry(CBItems.TIDE_ROD.get(), 1, 1, 4, EnchantStyle.ROD),
                // Custom gear is a rare surprise in the middle, not a reason to stay there.
                new Entry(CBItems.STORM_EGG.get(), 1, 1, 1),
                new Entry(CBItems.SMOKE_BOMB.get(), 1, 1, 1),
                new Entry(CBItems.MIRAGE_TOTEM.get(), 1, 1, 1),
                new Entry(CBItems.SIPHON_FLASK.get(), 1, 1, 1)
        );
    }

    /** Outer ring: the good stuff, stopping short of overwhelming. */
    private static List<Entry> buildOuter() {
        return List.of(
                new Entry(Items.DIAMOND_SWORD, 1, 1, 6, EnchantStyle.SWORD),
                new Entry(Items.IRON_SWORD, 1, 1, 7, EnchantStyle.SWORD),
                new Entry(Items.DIAMOND_AXE, 1, 1, 5, EnchantStyle.AXE),
                new Entry(Items.TRIDENT, 1, 1, 2, EnchantStyle.TRIDENT),
                new Entry(Items.BOW, 1, 1, 7, EnchantStyle.BOW),
                new Entry(Items.CROSSBOW, 1, 1, 5, EnchantStyle.CROSSBOW),
                new Entry(Items.ARROW, 8, 16, 8),
                new Entry(Items.SPECTRAL_ARROW, 4, 8, 4),
                new Entry(Items.DIAMOND_CHESTPLATE, 1, 1, 4, EnchantStyle.ARMOUR),
                new Entry(Items.DIAMOND_LEGGINGS, 1, 1, 4, EnchantStyle.ARMOUR),
                new Entry(Items.DIAMOND_BOOTS, 1, 1, 4, EnchantStyle.BOOTS),
                new Entry(Items.DIAMOND_HELMET, 1, 1, 4, EnchantStyle.HELMET),
                new Entry(Items.TURTLE_HELMET, 1, 1, 4, EnchantStyle.HELMET),
                new Entry(Items.IRON_CHESTPLATE, 1, 1, 6, EnchantStyle.ARMOUR),
                new Entry(Items.SHIELD, 1, 1, 5),
                new Entry(Items.GOLDEN_APPLE, 1, 3, 7),
                new Entry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 1),
                new Entry(Items.ENDER_PEARL, 2, 4, 5),
                new Entry(Items.TOTEM_OF_UNDYING, 1, 1, 1),
                new Entry(Items.COOKED_BEEF, 3, 6, 7),
                new Entry(Items.GOLDEN_CARROT, 3, 6, 5),
                new Entry(CBItems.TIDE_ROD.get(), 1, 1, 5, EnchantStyle.ROD),
                // Out here the custom gear is a normal find.
                new Entry(CBItems.STORM_EGG.get(), 1, 2, 6),
                new Entry(CBItems.SMOKE_BOMB.get(), 1, 2, 6),
                new Entry(CBItems.MIRAGE_TOTEM.get(), 1, 1, 5),
                new Entry(CBItems.SIPHON_FLASK.get(), 1, 2, 6),
                new Entry(CBItems.GHOST_BOOTS.get(), 1, 1, 4)
        );
    }

    private static final int CENTRE_ROLLS_MIN = 2;
    private static final int CENTRE_ROLLS_MAX = 3;
    private static final int OUTER_ROLLS_MIN = 3;
    private static final int OUTER_ROLLS_MAX = 5;

    /** Nudges how many and how strong the enchantments roll, per tier. */
    private static final float CENTRE_QUALITY = 0.3f;
    private static final float OUTER_QUALITY = 0.75f;

    private BattleLoot() {}

    private static List<Entry> centre() {
        if (centrePool == null) centrePool = buildCentre();
        return centrePool;
    }

    private static List<Entry> outer() {
        if (outerPool == null) outerPool = buildOuter();
        return outerPool;
    }

    public static void fill(ServerLevel level, Container container, boolean centre, RandomSource random, Mutators rules) {
        fill(level, container, centre, random, rules, 1.0f);
    }

    /**
     * Replaces a container's contents with a fresh roll for its tier.
     *
     * @param centre      whether this container sits inside the centre tier radius
     * @param rollScale   extra multiplier, used to give a double chest its three times the loot
     */
    public static void fill(ServerLevel level, Container container, boolean centre, RandomSource random,
                            Mutators rules, float rollScale) {
        container.clearContent();

        List<Entry> pool = centre ? centre() : outer();
        if (!rules.customItems()) {
            pool = pool.stream().filter(entry -> !isCustom(entry)).toList();
        }

        int rolls = centre
                ? CENTRE_ROLLS_MIN + random.nextInt(CENTRE_ROLLS_MAX - CENTRE_ROLLS_MIN + 1)
                : OUTER_ROLLS_MIN + random.nextInt(OUTER_ROLLS_MAX - OUTER_ROLLS_MIN + 1);
        rolls = Math.max(1, Math.round(rolls * rules.lootAmount() * rollScale));
        rolls = Math.min(rolls, container.getContainerSize());

        Registry<Enchantment> enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        float quality = centre ? CENTRE_QUALITY : OUTER_QUALITY;

        List<Integer> freeSlots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) freeSlots.add(i);

        for (int i = 0; i < rolls && !freeSlots.isEmpty(); i++) {
            ItemStack stack = build(pick(pool, random), enchantments, random, quality);
            int slot = freeSlots.remove(random.nextInt(freeSlots.size()));
            container.setItem(slot, stack);
        }
        container.setChanged();
    }

    private static boolean isCustom(Entry entry) {
        return entry.item() == CBItems.STORM_EGG.get()
                || entry.item() == CBItems.SIPHON_FLASK.get()
                || entry.item() == CBItems.SMOKE_BOMB.get()
                || entry.item() == CBItems.MIRAGE_TOTEM.get()
                || entry.item() == CBItems.GHOST_BOOTS.get()
                || entry.item() == CBItems.TIDE_ROD.get();
    }

    private static Entry pick(List<Entry> pool, RandomSource random) {
        int total = 0;
        for (Entry e : pool) total += e.weight();
        int roll = random.nextInt(Math.max(1, total));
        for (Entry e : pool) {
            roll -= e.weight();
            if (roll < 0) return e;
        }
        return pool.get(pool.size() - 1);
    }

    private static ItemStack build(Entry entry, Registry<Enchantment> enchantments, RandomSource random, float quality) {
        int count = entry.min() + (entry.max() > entry.min() ? random.nextInt(entry.max() - entry.min() + 1) : 0);
        ItemStack stack = new ItemStack(entry.item(), Math.max(1, count));
        // Not everything rolls enchanted, so plain gear still turns up.
        if (entry.style() != EnchantStyle.NONE && random.nextFloat() < 0.35f + 0.5f * quality) {
            entry.style().apply(stack, enchantments, random, quality);
        }
        return stack;
    }

    public static int centrePoolSize() {
        return centre().size();
    }

    public static int outerPoolSize() {
        return outer().size();
    }
}
