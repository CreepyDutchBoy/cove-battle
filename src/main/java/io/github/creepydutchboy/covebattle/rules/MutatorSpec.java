package io.github.creepydutchboy.covebattle.rules;

import java.util.List;

/**
 * One tunable, described once.
 *
 * <p>The command line and the slider screen are both generated from this table, so a new mutator
 * means adding a row here and a case in {@link Mutators#with}, not touching a UI layout.
 */
public record MutatorSpec(String key, String label, String tooltip, float min, float max, float step, Kind kind) {

    public enum Kind { INT, FLOAT, BOOL }

    public static final List<MutatorSpec> ALL = List.of(
            new MutatorSpec("grace_seconds", "Grace period", "Frozen, invulnerable seconds before the fight", 0, 60, 1, Kind.INT),
            new MutatorSpec("match_seconds", "Fight length", "Open fighting before the showdown", 20, 900, 5, Kind.INT),
            new MutatorSpec("border_step_seconds", "Border step", "Seconds between border shrinks", 2, 60, 1, Kind.INT),
            new MutatorSpec("min_radius", "Border floor", "Smallest border radius", 4, 400, 1, Kind.INT),
            new MutatorSpec("restock_seconds", "Restock every", "Seconds between container restocks", 5, 300, 5, Kind.INT),
            new MutatorSpec("restock_count", "Restock count", "Containers refilled each time", 0, 32, 1, Kind.INT),
            new MutatorSpec("rounds_to_win", "Rounds to win", "2 is best of three", 1, 5, 1, Kind.INT),
            new MutatorSpec("loot_amount", "Loot amount", "Multiplies how much each container holds", 0.25f, 3.0f, 0.25f, Kind.FLOAT),
            new MutatorSpec("max_health_hearts", "Max health", "Hearts each player has", 2, 30, 1, Kind.FLOAT),
            new MutatorSpec("damage_dealt", "Damage dealt", "Multiplies player damage", 0.25f, 3.0f, 0.25f, Kind.FLOAT),
            new MutatorSpec("move_speed", "Move speed", "Multiplies walking speed", 0.5f, 2.5f, 0.1f, Kind.FLOAT),
            new MutatorSpec("ability_cooldown", "Ability cooldown", "Multiplies custom item cooldowns", 0.1f, 3.0f, 0.1f, Kind.FLOAT),
            new MutatorSpec("custom_items", "Custom items", "Whether Cove Battle gear appears in loot", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("fall_damage", "Fall damage", "Take damage from falling", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("hunger", "Hunger", "Food bar drains", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("natural_regen", "Natural regen", "Heal passively when fed", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("friendly_fire", "Friendly fire", "Teammates can hurt each other", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("showdown_glow", "Showdown glow", "Mark survivors when the showdown starts", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("closing_border", "Closing border", "Shrink the border during the showdown", 0, 1, 1, Kind.BOOL),
            new MutatorSpec("screen_effects", "Screen effects", "Spectator and showdown shaders", 0, 1, 1, Kind.BOOL)
    );

    public static MutatorSpec byKey(String key) {
        for (MutatorSpec spec : ALL) {
            if (spec.key.equalsIgnoreCase(key)) return spec;
        }
        return null;
    }

    public static List<String> keys() {
        return ALL.stream().map(MutatorSpec::key).toList();
    }

    public String format(float value) {
        return switch (kind) {
            case BOOL -> value >= 0.5f ? "on" : "off";
            case INT -> String.valueOf(Math.round(value));
            case FLOAT -> String.format("%.2f", value);
        };
    }

    /** Slider position 0..1 for a value. */
    public double toSlider(float value) {
        return (value - min) / (max - min);
    }

    /** Value for a slider position, snapped to the step. */
    public float fromSlider(double slider) {
        float raw = (float) (min + slider * (max - min));
        float snapped = Math.round(raw / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }
}
