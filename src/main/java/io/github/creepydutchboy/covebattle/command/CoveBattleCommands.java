package io.github.creepydutchboy.covebattle.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import io.github.creepydutchboy.covebattle.UpdateBridge;
import io.github.creepydutchboy.covebattle.update.UpdateOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** {@code /covebattle version | status | update check | update now} */
public final class CoveBattleCommands {

    private CoveBattleCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("covebattle")
                .executes(ctx -> version(ctx.getSource()))
                .then(Commands.literal("version")
                        .executes(ctx -> version(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("update")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("check")
                                .executes(ctx -> update(ctx.getSource(), false)))
                        .then(Commands.literal("now")
                                .executes(ctx -> update(ctx.getSource(), true)))));
    }

    private static int version(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CoveBattle.MOD_NAME + " ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(UpdateBridge.modVersion()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" for Minecraft " + UpdateBridge.mcVersion()).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        UpdateOutcome outcome = UpdateBridge.last();
        source.sendSuccess(() -> Component.literal("Update channel: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(CoveBattleConfig.allowPrerelease ? "stable + prerelease" : "stable")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   auto-install: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(CoveBattleConfig.autoUpdate ? "on" : "off").withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Managed jar: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(UpdateBridge.settings().modsDir().resolve(UpdateBridge.JAR_NAME).toString())
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> describe(outcome), false);
        if (UpdateBridge.isRunning()) {
            source.sendSuccess(() -> Component.literal("A check is running right now.").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

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
