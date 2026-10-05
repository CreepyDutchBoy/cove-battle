package io.github.creepydutchboy.covebattle.rules;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.net.CBNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The live rule set: which mode is selected and, for {@link MatchMode#MUTATORS}, the numbers the
 * sliders were left on.
 *
 * <p>Server-authoritative. Clients receive a copy so the mutator screen can show real values, and
 * every change is pushed back out, so two people with the screen open never disagree.
 */
public final class RulesState {

    private static final String FILE = "covebattle-rules.json";

    private static MatchMode mode = MatchMode.REMASTERED;
    private static Mutators custom = Mutators.remastered();
    private static Path file;

    private RulesState() {}

    /** The numbers a match should actually run with right now. */
    public static Mutators active() {
        return mode == MatchMode.MUTATORS ? custom : Mutators.forMode(mode);
    }

    public static MatchMode mode() {
        return mode;
    }

    /** The editable set, which is what the sliders bind to. */
    public static Mutators custom() {
        return custom;
    }

    public static void setMode(MinecraftServer server, MatchMode newMode) {
        mode = newMode;
        save();
        sync(server);
    }

    public static void setCustom(MinecraftServer server, Mutators newCustom) {
        custom = newCustom.sanitised();
        save();
        sync(server);
    }

    /** Applies a preset into the editable set, so Mutators can start from Classic or Remastered. */
    public static void copyPresetIntoCustom(MinecraftServer server, MatchMode preset) {
        custom = Mutators.forMode(preset);
        save();
        sync(server);
    }

    public static void load(Path configDir) {
        file = configDir.resolve(FILE);
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file);
                RulesFile parsed = new com.google.gson.Gson().fromJson(json, RulesFile.class);
                if (parsed != null) {
                    if (parsed.mode != null) mode = MatchMode.byName(parsed.mode);
                    if (parsed.mutators != null) custom = parsed.mutators.sanitised();
                }
                CoveBattle.LOGGER.info("Loaded rules: mode {}", mode);
            } else {
                save();
            }
        } catch (Exception e) {
            CoveBattle.LOGGER.warn("Could not read {}, falling back to defaults: {}", FILE, e.toString());
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            RulesFile out = new RulesFile();
            out.mode = mode.name();
            out.mutators = custom;
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (IOException e) {
            CoveBattle.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
        }
    }

    public static void sync(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CBNetwork.sendRules(player, mode, custom);
        }
    }

    public static void syncTo(ServerPlayer player) {
        CBNetwork.sendRules(player, mode, custom);
    }

    private static final class RulesFile {
        String mode;
        Mutators mutators;
    }
}
