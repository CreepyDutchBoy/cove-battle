package io.github.creepydutchboy.covebattle.loot;

import io.github.creepydutchboy.covebattle.registry.CBItems;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loot, defined in code rather than as data-pack tables so the tiers can be tuned in one place
 * and nothing depends on resource loading order.
 *
 * <p>Tiers follow console Battle and Survival Games rather than the old datapack: the centre
 * platform holds the <em>best</em> gear, so rushing it is high risk and high reward, while the
 * outer ring is plentiful but modest. Nothing here can be placed as a block, because matches
 * run in adventure mode and unplaceable loot would be dead weight.
 */
public final class BattleLoot {

    /** One possible stack. {@code enchants} is applied verbatim, so loot is predictable to balance. */
    public record Entry(Item item, int min, int max, int weight, Map<ResourceKey<Enchantment>, Integer> enchants) {
        public Entry(Item item, int min, int max, int weight) {
            this(item, min, max, weight, Map.of());
        }
    }

    private static List<Entry> centrePool;
    private static List<Entry> outerPool;

    /** Centre platform: the strong stuff. */
    private static List<Entry> buildCentre() {
        return List.of(
            new Entry(Items.NETHERITE_SWORD, 1, 1, 2, Map.of(Enchantments.SHARPNESS, 2)),
            new Entry(Items.DIAMOND_SWORD, 1, 1, 7, Map.of(Enchantments.SHARPNESS, 2)),
            new Entry(Items.DIAMOND_AXE, 1, 1, 6, Map.of(Enchantments.SHARPNESS, 1)),
            new Entry(Items.DIAMOND_CHESTPLATE, 1, 1, 5, Map.of(Enchantments.PROTECTION, 2)),
            new Entry(Items.DIAMOND_HELMET, 1, 1, 5, Map.of(Enchantments.PROTECTION, 2)),
            new Entry(Items.DIAMOND_LEGGINGS, 1, 1, 4, Map.of(Enchantments.PROTECTION, 1)),
            new Entry(Items.DIAMOND_BOOTS, 1, 1, 4, Map.of(Enchantments.FEATHER_FALLING, 2)),
            new Entry(Items.TURTLE_HELMET, 1, 1, 4),
            new Entry(Items.BOW, 1, 1, 6, Map.of(Enchantments.POWER, 2)),
            new Entry(Items.CROSSBOW, 1, 1, 4),
            new Entry(Items.ARROW, 8, 16, 8),
            new Entry(Items.SPECTRAL_ARROW, 4, 8, 4),
            new Entry(Items.SHIELD, 1, 1, 5),
            new Entry(Items.GOLDEN_APPLE, 1, 3, 8),
            new Entry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 1),
            new Entry(Items.ENDER_PEARL, 2, 4, 5),
            new Entry(Items.TOTEM_OF_UNDYING, 1, 1, 1),
            new Entry(Items.COOKED_BEEF, 3, 6, 7),
            new Entry(Items.GOLDEN_CARROT, 3, 6, 5),
            // Custom gear is a centre-only prize, so the middle stays worth contesting.
            new Entry(CBItems.COVESLAYER.get(), 1, 1, 2),
            new Entry(CBItems.STORM_EGG.get(), 1, 2, 3),
            new Entry(CBItems.WAR_HORN.get(), 1, 1, 2),
            new Entry(CBItems.SIPHON_FLASK.get(), 1, 2, 3)
        );
    }

    /** Outer ring: usable, but you give something up by staying away from the middle. */
    private static List<Entry> buildOuter() {
        return List.of(
            new Entry(Items.IRON_SWORD, 1, 1, 7),
            new Entry(Items.STONE_SWORD, 1, 1, 8),
            new Entry(Items.IRON_AXE, 1, 1, 6),
            new Entry(Items.STONE_AXE, 1, 1, 6),
            new Entry(Items.IRON_PICKAXE, 1, 1, 5),
            new Entry(Items.IRON_SHOVEL, 1, 1, 4),
            new Entry(Items.IRON_HELMET, 1, 1, 6),
            new Entry(Items.IRON_CHESTPLATE, 1, 1, 5),
            new Entry(Items.IRON_LEGGINGS, 1, 1, 5),
            new Entry(Items.IRON_BOOTS, 1, 1, 5),
            new Entry(Items.CHAINMAIL_CHESTPLATE, 1, 1, 4),
            new Entry(Items.LEATHER_HELMET, 1, 1, 5),
            new Entry(Items.TURTLE_HELMET, 1, 1, 2),
            new Entry(Items.BOW, 1, 1, 5),
            new Entry(Items.ARROW, 4, 10, 8),
            new Entry(Items.SHIELD, 1, 1, 4),
            new Entry(Items.BREAD, 2, 5, 9),
            new Entry(Items.COOKED_CHICKEN, 2, 4, 7),
            new Entry(Items.APPLE, 2, 4, 7),
            new Entry(Items.GOLDEN_APPLE, 1, 1, 2),
            new Entry(Items.SNOWBALL, 4, 8, 4),
            new Entry(Items.EGG, 2, 5, 3),
            new Entry(CBItems.SMOKE_BOMB.get(), 1, 2, 4),
            new Entry(CBItems.GHOST_BOOTS.get(), 1, 1, 2)
        );
    }

    private static List<Entry> centre() {
        if (centrePool == null) centrePool = buildCentre();
        return centrePool;
    }

    private static List<Entry> outer() {
        if (outerPool == null) outerPool = buildOuter();
        return outerPool;
    }

    private static final int CENTRE_ROLLS_MIN = 4;
    private static final int CENTRE_ROLLS_MAX = 6;
    private static final int OUTER_ROLLS_MIN = 2;
    private static final int OUTER_ROLLS_MAX = 3;

    private BattleLoot() {}

    /**
     * Replaces a container's contents with a fresh roll for its tier.
     *
     * @param centre whether this container sits inside the centre tier radius
     */
    public static void fill(ServerLevel level, Container container, boolean centre, RandomSource random, Mutators rules) {
        container.clearContent();

        List<Entry> pool = centre ? centre() : outer();
        if (!rules.customItems()) {
            // Classic: strip anything of ours, leaving the vanilla gear the console game had.
            pool = pool.stream().filter(entry -> !isCustom(entry)).toList();
        }
        int rolls = centre
                ? CENTRE_ROLLS_MIN + random.nextInt(CENTRE_ROLLS_MAX - CENTRE_ROLLS_MIN + 1)
                : OUTER_ROLLS_MIN + random.nextInt(OUTER_ROLLS_MAX - OUTER_ROLLS_MIN + 1);
        rolls = Math.max(1, Math.round(rolls * rules.lootAmount()));

        Registry<Enchantment> enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        List<Integer> freeSlots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) freeSlots.add(i);

        for (int i = 0; i < rolls && !freeSlots.isEmpty(); i++) {
            ItemStack stack = build(pick(pool, random), enchantments, random);
            int slot = freeSlots.remove(random.nextInt(freeSlots.size()));
            container.setItem(slot, stack);
        }
        container.setChanged();
    }

    private static boolean isCustom(Entry entry) {
        return entry.item() == CBItems.COVESLAYER.get() || entry.item() == CBItems.STORM_EGG.get()
                || entry.item() == CBItems.WAR_HORN.get() || entry.item() == CBItems.SIPHON_FLASK.get()
                || entry.item() == CBItems.SMOKE_BOMB.get() || entry.item() == CBItems.GHOST_BOOTS.get();
    }

    private static Entry pick(List<Entry> pool, RandomSource random) {
        int total = 0;
        for (Entry e : pool) total += e.weight();
        int roll = random.nextInt(total);
        for (Entry e : pool) {
            roll -= e.weight();
            if (roll < 0) return e;
        }
        return pool.get(pool.size() - 1);
    }

    private static ItemStack build(Entry entry, Registry<Enchantment> enchantments, RandomSource random) {
        int count = entry.min() + (entry.max() > entry.min() ? random.nextInt(entry.max() - entry.min() + 1) : 0);
        ItemStack stack = new ItemStack(entry.item(), Math.max(1, count));
        entry.enchants().forEach((key, lvl) -> {
            Holder<Enchantment> holder = enchantments.getHolderOrThrow(key);
            stack.enchant(holder, lvl);
        });
        return stack;
    }

    public static int centrePoolSize() {
        return centre().size();
    }

    public static int outerPoolSize() {
        return outer().size();
    }
}
