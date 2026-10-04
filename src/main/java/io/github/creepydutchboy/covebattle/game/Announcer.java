package io.github.creepydutchboy.covebattle.game;

import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat output, including the clickable prompt that starts a match the intended way.
 *
 * <p>The prompt is posted when the server finishes starting (so it appears after every
 * {@code /reload} and every launch), when a player joins, and whenever a match returns to the
 * lobby — so the way in is always one click away rather than something to be remembered.
 */
public final class Announcer {

    private Announcer() {}

    public static MutableComponent prefix() {
        return Component.literal("[Cove Battle] ").withStyle(ChatFormatting.GOLD);
    }

    public static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public static void info(MinecraftServer server, String text) {
        broadcast(server, prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
    }

    /** The start prompt. Buttons run plain commands, so a vanilla client needs nothing extra. */
    public static void postLobbyPrompt(MinecraftServer server) {
        broadcast(server, Component.empty());
        broadcast(server, Component.literal("  Cove Battle ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("— last one standing wins the round.")
                        .withStyle(ChatFormatting.GRAY)));
        broadcast(server, Component.literal("  ")
                .append(button("[ START MATCH ]", ChatFormatting.GREEN, "/covebattle start",
                        "Begin a best-of-three match with everyone who is playing"))
                .append(Component.literal("  "))
                .append(button("[ HOW IT WORKS ]", ChatFormatting.AQUA, "/covebattle help",
                        "Rules, timings and the arena"))
                .append(Component.literal("  "))
                .append(button("[ STATUS ]", ChatFormatting.YELLOW, "/covebattle status",
                        "Phase, arena and update state")));
        broadcast(server, Component.empty());
    }

    public static void postLobbyPrompt(ServerPlayer player) {
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.literal("  Cove Battle ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("— click to begin.").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal("  ")
                .append(button("[ START MATCH ]", ChatFormatting.GREEN, "/covebattle start",
                        "Begin a best-of-three match"))
                .append(Component.literal("  "))
                .append(button("[ HOW IT WORKS ]", ChatFormatting.AQUA, "/covebattle help", "Rules and timings")));
        player.sendSystemMessage(Component.empty());
    }

    private static MutableComponent button(String label, ChatFormatting colour, String command, String tooltip) {
        return Component.literal(label).withStyle(style -> style
                .withColor(colour)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(tooltip).withStyle(ChatFormatting.GRAY))));
    }

    public static void help(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Cove Battle").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        line(player, "Rounds", "Best of three. A round ends when one player is left standing.");
        line(player, "Start", "Everyone is frozen and invulnerable on the platform for the grace period, then released.");
        line(player, "Loot", "The centre platform holds the best gear. Outer chests and barrels are weaker but plentiful.");
        line(player, "Restock", "Looted containers refill every 30 seconds, skipping ones a player is standing over.");
        line(player, "Showdown", "When the main timer runs out the border closes in steps and both players glow.");
        line(player, "Tie", "If the border reaches its smallest size and nobody has died, the round is a tie and scores nothing.");
    }

    private static void line(ServerPlayer player, String heading, String text) {
        player.sendSystemMessage(Component.literal("  " + heading + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
    }

    public static void log(String message) {
        CoveBattle.LOGGER.info(message);
    }
}
