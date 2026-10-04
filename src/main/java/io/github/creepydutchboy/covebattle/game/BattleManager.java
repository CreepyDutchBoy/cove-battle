package io.github.creepydutchboy.covebattle.game;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * Holds the single running game. One match per server, bound to the overworld, created when the
 * server has started and dropped when it stops.
 */
public final class BattleManager {

    @Nullable
    private static BattleGame game;

    private BattleManager() {}

    public static void onServerStarted(MinecraftServer server) {
        game = new BattleGame(server);
        Announcer.postLobbyPrompt(server);
    }

    public static void onServerStopping() {
        if (game != null) game.reset();
        game = null;
    }

    @Nullable
    public static BattleGame game() {
        return game;
    }

    public static boolean isRunning() {
        return game != null;
    }
}
