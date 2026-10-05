package io.github.creepydutchboy.covebattle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import io.github.creepydutchboy.covebattle.UpdateBridge;
import io.github.creepydutchboy.covebattle.game.Announcer;
import io.github.creepydutchboy.covebattle.game.ArenaLayout;
import io.github.creepydutchboy.covebattle.game.BattleBots;
import io.github.creepydutchboy.covebattle.game.BattleMode;
import io.github.creepydutchboy.covebattle.rules.MatchMode;
import io.github.creepydutchboy.covebattle.rules.MutatorSpec;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import io.github.creepydutchboy.covebattle.rules.RulesState;
import io.github.creepydutchboy.covebattle.game.BattleGame;
import io.github.creepydutchboy.covebattle.game.BattleManager;
import io.github.creepydutchboy.covebattle.loot.BattleLoot;
import io.github.creepydutchboy.covebattle.loot.ContainerRegistry;
import io.github.creepydutchboy.covebattle.update.UpdateOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /covebattle} — match control, arena tools, update control and debug helpers.
 *
 * <p>Match control sits at permission level 2 so a single-player host can run it, while the
 * read-only views are open to everyone.
 */
public final class CoveBattleCommands {

    private CoveBattleCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("covebattle")
                .executes(ctx -> status(ctx.getSource()))

                .then(Commands.literal("help")
                        .executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("version")
                        .executes(ctx -> version(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))

                .then(Commands.literal("start")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> start(ctx.getSource(), BattleMode.byName(CoveBattleConfig.defaultMode)))
                        .then(Commands.literal("solo")
                                .executes(ctx -> start(ctx.getSource(), BattleMode.SOLO)))
                        .then(Commands.literal("teams")
                                .executes(ctx -> start(ctx.getSource(), BattleMode.TEAMS))))
                .then(Commands.literal("stop")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> reset(ctx.getSource())))

                .then(Commands.literal("arena")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info")
                                .executes(ctx -> arenaInfo(ctx.getSource())))
                        .then(Commands.literal("rescan")
                                .executes(ctx -> arenaRescan(ctx.getSource()))))

                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("skip")
                                .executes(ctx -> debugSkip(ctx.getSource())))
                        .then(Commands.literal("fill")
                                .executes(ctx -> debugFill(ctx.getSource())))
                        .then(Commands.literal("border")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(4, 2048))
                                        .executes(ctx -> debugBorder(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "radius"))))))

                .then(Commands.literal("mode")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> showMode(ctx.getSource()))
                        .then(Commands.literal("classic").executes(ctx -> setMode(ctx.getSource(), MatchMode.CLASSIC)))
                        .then(Commands.literal("remastered").executes(ctx -> setMode(ctx.getSource(), MatchMode.REMASTERED)))
                        .then(Commands.literal("mutators").executes(ctx -> setMode(ctx.getSource(), MatchMode.MUTATORS))))

                .then(Commands.literal("mutator")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> listMutators(ctx.getSource()))
                        .then(Commands.literal("list").executes(ctx -> listMutators(ctx.getSource())))
                        .then(Commands.literal("preset")
                                .then(Commands.literal("classic").executes(ctx -> preset(ctx.getSource(), MatchMode.CLASSIC)))
                                .then(Commands.literal("remastered").executes(ctx -> preset(ctx.getSource(), MatchMode.REMASTERED))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(MUTATOR_KEYS)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> setMutator(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "key"),
                                                        (float) DoubleArgumentType.getDouble(ctx, "value")))))))

                .then(Commands.literal("bot")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .executes(ctx -> addBots(ctx.getSource(), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> addBots(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("kill")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> killBot(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list").executes(ctx -> listBots(ctx.getSource())))
                        .then(Commands.literal("clear").executes(ctx -> clearBots(ctx.getSource()))))

                .then(Commands.literal("update")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("check")
                                .executes(ctx -> update(ctx.getSource(), false)))
                        .then(Commands.literal("now")
                                .executes(ctx -> update(ctx.getSource(), true)))));
    }

    private static final SuggestionProvider<CommandSourceStack> MUTATOR_KEYS = (ctx, builder) -> {
        for (String key : MutatorSpec.keys()) builder.suggest(key);
        return builder.buildFuture();
    };

    // ---- modes and mutators ----

    private static int showMode(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Mode: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(RulesState.mode().label()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  " + RulesState.mode().blurb()).withStyle(ChatFormatting.DARK_GRAY)), false);
        return 1;
    }

    private static int setMode(CommandSourceStack source, MatchMode mode) {
        RulesState.setMode(source.getServer(), mode);
        source.sendSuccess(() -> Component.literal("Mode set to " + mode.label() + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int preset(CommandSourceStack source, MatchMode from) {
        RulesState.copyPresetIntoCustom(source.getServer(), from);
        source.sendSuccess(() -> Component.literal("Mutators reset to the " + from.label() + " preset.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listMutators(CommandSourceStack source) {
        Mutators active = RulesState.active();
        Mutators custom = RulesState.custom();
        source.sendSuccess(() -> Component.literal("Mode " + RulesState.mode().label()
                + "  (editable set shown in brackets)").withStyle(ChatFormatting.GOLD), false);
        for (MutatorSpec spec : MutatorSpec.ALL) {
            String live = spec.format(active.value(spec.key()));
            String edit = spec.format(custom.value(spec.key()));
            source.sendSuccess(() -> Component.literal("  " + spec.key()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" = " + live).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(live.equals(edit) ? "" : "  [" + edit + "]").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("   " + spec.label()).withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int setMutator(CommandSourceStack source, String key, float value) {
        MutatorSpec spec = MutatorSpec.byKey(key);
        if (spec == null) {
            source.sendFailure(Component.literal("Unknown mutator \"" + key + "\". Try /covebattle mutator list."));
            return 0;
        }
        Mutators updated = RulesState.custom().with(spec.key(), value);
        RulesState.setCustom(source.getServer(), updated);
        String shown = spec.format(updated.value(spec.key()));
        source.sendSuccess(() -> Component.literal(spec.label() + " set to " + shown).withStyle(ChatFormatting.GREEN)
                .append(Component.literal(RulesState.mode() == MatchMode.MUTATORS ? "" : "  (switch to Mutators mode to use it)")
                        .withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    // ---- test bots ----

    private static int addBots(CommandSourceStack source, int count) {
        ServerLevel level = source.getServer().overworld();
        int before = BattleBots.count();
        for (int i = 0; i < count; i++) {
            BattleBots.spawn(level, "CoveBot" + (before + i + 1));
        }
        source.sendSuccess(() -> Component.literal("Spawned " + count + " test bot(s); " + BattleBots.count() + " total.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listBots(CommandSourceStack source) {
        if (BattleBots.count() == 0) {
            source.sendSuccess(() -> Component.literal("No test bots.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        for (ServerPlayer bot : BattleBots.all()) {
            source.sendSuccess(() -> Component.literal("  " + bot.getGameProfile().getName()
                    + "  health " + Math.round(bot.getHealth())
                    + "  at " + bot.blockPosition().toShortString()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /**
     * Eliminates a bot. NeoForge's FakePlayer ignores damage by design, so this calls the same
     * elimination the death event would, which is the part worth testing: it marks the bot out and
     * lets the game resolve the round.
     */
    private static int killBot(CommandSourceStack source, String name) {
        BattleGame game = BattleManager.game();
        for (ServerPlayer bot : BattleBots.all()) {
            if (!bot.getGameProfile().getName().equalsIgnoreCase(name)) continue;
            if (game == null || !game.onDeath(bot, null)) {
                source.sendFailure(Component.literal(name + " is not in a live round."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Eliminated " + name + ".").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        source.sendFailure(Component.literal("No bot called " + name));
        return 0;
    }

    private static int clearBots(CommandSourceStack source) {
        int n = BattleBots.count();
        BattleBots.clear();
        source.sendSuccess(() -> Component.literal("Removed " + n + " bot(s).").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ---- read-only ----

    private static int version(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CoveBattle.MOD_NAME + " ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(UpdateBridge.modVersion()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" for Minecraft " + UpdateBridge.mcVersion()).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            Announcer.help(player);
        } else {
            source.sendSuccess(() -> Component.literal("Best of " + (CoveBattleConfig.roundsToWin * 2 - 1)
                    + ", " + CoveBattleConfig.graceSeconds + "s grace, " + CoveBattleConfig.matchSeconds
                    + "s before the border closes from " + CoveBattleConfig.playRadius
                    + " to " + CoveBattleConfig.minRadius + " blocks."), false);
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        if (game == null) {
            source.sendFailure(Component.literal("The game is not initialised yet."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Phase: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(game.phase().name() + " / " + game.mode().label()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(game.phase().isLive()
                        ? "   round " + game.round() + "   alive " + game.aliveCount() + "/" + game.participantCount()
                        : "").withStyle(ChatFormatting.GRAY)), false);

        ContainerRegistry registry = game.registry();
        source.sendSuccess(() -> Component.literal("Containers: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(registry == null
                                ? "not scanned yet"
                                : registry.size() + " (" + registry.centreCount() + " centre, "
                                  + (registry.size() - registry.centreCount()) + " outer), "
                                  + registry.lootedCount() + " looted")
                        .withStyle(ChatFormatting.WHITE)), false);

        source.sendSuccess(() -> Component.literal("Update channel: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(CoveBattleConfig.allowPrerelease ? "stable + prerelease" : "stable")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   auto-install: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CoveBattleConfig.autoUpdate ? "on" : "off").withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Managed jar: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(UpdateBridge.settings().modsDir().resolve(UpdateBridge.JAR_NAME).toString())
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> describe(UpdateBridge.last()), false);
        return 1;
    }

    // ---- match control ----

    private static int start(CommandSourceStack source, BattleMode mode) {
        BattleGame game = BattleManager.game();
        if (game == null) {
            source.sendFailure(Component.literal("The game is not initialised yet."));
            return 0;
        }
        boolean started = game.start(source::sendFailure, mode);
        return started ? 1 : 0;
    }

    private static int stop(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        if (game == null) return 0;
        game.stop(true);
        source.sendSuccess(() -> Component.literal("Match stopped; back in the lobby.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        if (game == null) return 0;
        game.reset();
        source.sendSuccess(() -> Component.literal("Reset: lobby, border restored, loot cleared, scores dropped.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ---- arena ----

    private static int arenaInfo(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        BlockPos centre = game == null
                ? new BlockPos(CoveBattleConfig.centreX, CoveBattleConfig.centreY, CoveBattleConfig.centreZ)
                : game.centre();
        source.sendSuccess(() -> Component.literal("Arena centre ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(centre.getX() + " " + centre.getY() + " " + centre.getZ())
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   play radius " + CoveBattleConfig.playRadius
                                + ", centre tier " + CoveBattleConfig.centreTierRadius
                                + ", border floor " + CoveBattleConfig.minRadius)
                        .withStyle(ChatFormatting.GRAY)), false);
        ArenaLayout layout = game == null ? null : game.layout();
        source.sendSuccess(() -> Component.literal("Layout: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(layout == null ? "not scanned yet — run /covebattle arena rescan" : layout.summary())
                        .withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Loot pools: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(BattleLoot.centrePoolSize() + " centre entries, "
                        + BattleLoot.outerPoolSize() + " outer entries").withStyle(ChatFormatting.WHITE)), false);
        return 1;
    }

    private static int arenaRescan(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        if (game == null) return 0;
        int found = game.debugRescan();
        source.sendSuccess(() -> Component.literal("Scanned the arena: " + found + " containers.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ---- debug ----

    private static int debugSkip(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        if (game == null) return 0;
        game.debugSkipPhase();
        source.sendSuccess(() -> Component.literal("Ending phase " + game.phase().name() + " on the next tick.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int debugFill(CommandSourceStack source) {
        BattleGame game = BattleManager.game();
        if (game == null) return 0;
        int filled = game.debugFill();
        source.sendSuccess(() -> Component.literal("Stocked " + filled + " containers.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int debugBorder(CommandSourceStack source, double radius) {
        BattleGame game = BattleManager.game();
        if (game == null) return 0;
        game.debugBorder(radius);
        source.sendSuccess(() -> Component.literal("Border set to radius " + radius + " around the arena centre.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    // ---- updates ----

    private static int update(CommandSourceStack source, boolean install) {
        if (UpdateBridge.isRunning()) {
            source.sendFailure(Component.literal("An update check is already running."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(install ? "Checking for updates and installing..." : "Checking for updates...")
                .withStyle(ChatFormatting.GRAY), false);

        MinecraftServer server = source.getServer();
        UpdateBridge.runAsync(install, outcome ->
                server.execute(() -> source.sendSystemMessage(describe(outcome))));
        return 1;
    }

    private static Component describe(UpdateOutcome outcome) {
        ChatFormatting colour = switch (outcome.status()) {
            case UPDATE_INSTALLED -> ChatFormatting.GREEN;
            case UPDATE_AVAILABLE -> ChatFormatting.YELLOW;
            case FAILED -> ChatFormatting.RED;
            case INCOMPATIBLE -> ChatFormatting.GOLD;
            case DISABLED -> ChatFormatting.DARK_GRAY;
            case UP_TO_DATE -> ChatFormatting.GRAY;
        };
        return Component.literal("[" + CoveBattle.MOD_NAME + "] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(outcome.message()).withStyle(colour));
    }
}
