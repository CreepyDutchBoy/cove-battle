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
        try {
            if (game != null) game.reset();
        } catch (Exception e) {
            // A server that failed to initialise has no level to tidy up; never make shutdown worse.
            io.github.creepydutchboy.covebattle.CoveBattle.LOGGER.debug("Nothing to reset on shutdown: {}", e.toString());
        }
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
