package io.github.creepydutchboy.covebattle.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Every tunable number in one immutable value.
 *
 * <p>Presets are just instances of this: {@link #classic()} and {@link #remastered()} are fixed,
 * and the Mutators mode is whatever the sliders have been left on. Keeping it a record with a codec
 * means the same object persists to disk, travels to clients, and drives the UI, instead of being
 * scattered across config lookups.
 */
public record Mutators(
        int graceSeconds,
        int matchSeconds,
        int borderStepSeconds,
        int minRadius,
        int restockSeconds,
        int restockCount,
        int roundsToWin,
        float lootAmount,
        float maxHealthHearts,
        float damageDealt,
        float moveSpeed,
        float abilityCooldown,
        boolean customItems,
        boolean fallDamage,
        boolean hunger,
        boolean naturalRegen,
        boolean friendlyFire,
        boolean showdownGlow,
        boolean closingBorder,
        boolean screenEffects
) {

    // RecordCodecBuilder.group() tops out at 16 fields and this has 20, so persistence uses Gson
    // directly. The stream codec below is hand-written anyway, which is what the network path needs.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Mutators fromJson(String json) {
        Mutators parsed = GSON.fromJson(json, Mutators.class);
        return parsed == null ? remastered() : parsed.sanitised();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Mutators> STREAM_CODEC =
            StreamCodec.of(Mutators::write, Mutators::read);

    private static void write(RegistryFriendlyByteBuf buf, Mutators m) {
        buf.writeVarInt(m.graceSeconds);
        buf.writeVarInt(m.matchSeconds);
        buf.writeVarInt(m.borderStepSeconds);
        buf.writeVarInt(m.minRadius);
        buf.writeVarInt(m.restockSeconds);
        buf.writeVarInt(m.restockCount);
        buf.writeVarInt(m.roundsToWin);
        buf.writeFloat(m.lootAmount);
        buf.writeFloat(m.maxHealthHearts);
        buf.writeFloat(m.damageDealt);
        buf.writeFloat(m.moveSpeed);
        buf.writeFloat(m.abilityCooldown);
        int flags = (m.customItems ? 1 : 0) | (m.fallDamage ? 2 : 0) | (m.hunger ? 4 : 0)
                | (m.naturalRegen ? 8 : 0) | (m.friendlyFire ? 16 : 0) | (m.showdownGlow ? 32 : 0)
                | (m.closingBorder ? 64 : 0) | (m.screenEffects ? 128 : 0);
        buf.writeVarInt(flags);
    }

    private static Mutators read(RegistryFriendlyByteBuf buf) {
        int grace = buf.readVarInt();
        int match = buf.readVarInt();
        int step = buf.readVarInt();
        int minRadius = buf.readVarInt();
        int restockSeconds = buf.readVarInt();
        int restockCount = buf.readVarInt();
        int rounds = buf.readVarInt();
        float loot = buf.readFloat();
        float health = buf.readFloat();
        float damage = buf.readFloat();
        float speed = buf.readFloat();
        float cooldown = buf.readFloat();
        int flags = buf.readVarInt();
        return new Mutators(grace, match, step, minRadius, restockSeconds, restockCount, rounds,
                loot, health, damage, speed, cooldown,
                (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0,
                (flags & 16) != 0, (flags & 32) != 0, (flags & 64) != 0, (flags & 128) != 0);
    }

    /**
     * Console Battle: fifteen second grace, four chests restocked every thirty seconds, vanilla
     * gear only, no closing border — the arenas are enclosed, and Showdown is glowing name tags.
     */
    public static Mutators classic() {
        return new Mutators(15, 180, 15, 24, 30, 4, 2,
                1.0f, 10.0f, 1.0f, 1.0f, 1.0f,
                false, true, true, true, false, true, false, false);
    }

    /** The default: everything Classic has, plus the custom gear and the modern finish. */
    public static Mutators remastered() {
        return new Mutators(15, 90, 12, 15, 30, 4, 2,
                1.0f, 10.0f, 1.0f, 1.0f, 1.0f,
                true, true, true, true, false, true, true, true);
    }

    public static Mutators forMode(MatchMode mode) {
        return mode == MatchMode.CLASSIC ? classic() : remastered();
    }

    public int graceTicks() {
        return graceSeconds * 20;
    }

    public int matchTicks() {
        return matchSeconds * 20;
    }

    public int bestOf() {
        return roundsToWin * 2 - 1;
    }

    /** Clamps everything into the ranges the sliders offer, so a hand-edited file cannot break a match. */
    public Mutators sanitised() {
        return new Mutators(
                clamp(graceSeconds, 0, 60),
                clamp(matchSeconds, 20, 900),
                clamp(borderStepSeconds, 2, 60),
                clamp(minRadius, 4, 400),
                clamp(restockSeconds, 5, 300),
                clamp(restockCount, 0, 32),
                clamp(roundsToWin, 1, 5),
                clamp(lootAmount, 0.25f, 3.0f),
                clamp(maxHealthHearts, 2.0f, 30.0f),
                clamp(damageDealt, 0.25f, 3.0f),
                clamp(moveSpeed, 0.5f, 2.5f),
                clamp(abilityCooldown, 0.1f, 3.0f),
                customItems, fallDamage, hunger, naturalRegen, friendlyFire,
                showdownGlow, closingBorder, screenEffects);
    }

    /** Reads one tunable by its key, for the command line and the sliders. */
    public float value(String key) {
        return switch (key) {
            case "grace_seconds" -> graceSeconds;
            case "match_seconds" -> matchSeconds;
            case "border_step_seconds" -> borderStepSeconds;
            case "min_radius" -> minRadius;
            case "restock_seconds" -> restockSeconds;
            case "restock_count" -> restockCount;
            case "rounds_to_win" -> roundsToWin;
            case "loot_amount" -> lootAmount;
            case "max_health_hearts" -> maxHealthHearts;
            case "damage_dealt" -> damageDealt;
            case "move_speed" -> moveSpeed;
            case "ability_cooldown" -> abilityCooldown;
            case "custom_items" -> customItems ? 1 : 0;
            case "fall_damage" -> fallDamage ? 1 : 0;
            case "hunger" -> hunger ? 1 : 0;
            case "natural_regen" -> naturalRegen ? 1 : 0;
            case "friendly_fire" -> friendlyFire ? 1 : 0;
            case "showdown_glow" -> showdownGlow ? 1 : 0;
            case "closing_border" -> closingBorder ? 1 : 0;
            case "screen_effects" -> screenEffects ? 1 : 0;
            default -> 0;
        };
    }

    /** Returns a copy with one tunable changed. */
    public Mutators with(String key, float v) {
        int i = Math.round(v);
        boolean b = v >= 0.5f;
        return switch (key) {
            case "grace_seconds" -> new Mutators(i, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "match_seconds" -> new Mutators(graceSeconds, i, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "border_step_seconds" -> new Mutators(graceSeconds, matchSeconds, i, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "min_radius" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, i, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "restock_seconds" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, i, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "restock_count" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, i, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "rounds_to_win" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, i, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "loot_amount" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, v, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "max_health_hearts" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, v, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "damage_dealt" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, v, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "move_speed" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, v, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "ability_cooldown" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, v, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects).sanitised();
            case "custom_items" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, b, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects);
            case "fall_damage" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, b, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects);
            case "hunger" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, b, naturalRegen, friendlyFire, showdownGlow, closingBorder, screenEffects);
            case "natural_regen" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, b, friendlyFire, showdownGlow, closingBorder, screenEffects);
            case "friendly_fire" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, b, showdownGlow, closingBorder, screenEffects);
            case "showdown_glow" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, b, closingBorder, screenEffects);
            case "closing_border" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, b, screenEffects);
            case "screen_effects" -> new Mutators(graceSeconds, matchSeconds, borderStepSeconds, minRadius, restockSeconds, restockCount, roundsToWin, lootAmount, maxHealthHearts, damageDealt, moveSpeed, abilityCooldown, customItems, fallDamage, hunger, naturalRegen, friendlyFire, showdownGlow, closingBorder, b);
            default -> this;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
