package io.github.creepydutchboy.covebattle.lobby;

import io.github.creepydutchboy.covebattle.game.BattleGame;
import io.github.creepydutchboy.covebattle.game.BattleManager;
import io.github.creepydutchboy.covebattle.net.CBNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who is sitting in the lobby menu and who is out playing.
 *
 * <p>The two are independent: a match keeps running for everyone still in it while somebody else
 * sits in the lobby, and going to the lobby mid-match simply takes you out of that match. The
 * lobby is a screen, not a world, so this is a flag per player rather than a place to teleport to.
 */
public final class LobbyState {

    private static final Set<UUID> IN_LOBBY = new HashSet<>();

    private LobbyState() {}

    public static boolean isInLobby(UUID id) {
        return IN_LOBBY.contains(id);
    }

    public static int lobbyCount() {
        return IN_LOBBY.size();
    }

    /** Players arrive in the lobby rather than dropped into whatever is happening. */
    public static void onPlayerJoin(ServerPlayer player) {
        IN_LOBBY.add(player.getUUID());
        sync(player.server);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        IN_LOBBY.remove(player.getUUID());
        BattleGame game = BattleManager.game();
        if (game != null) game.onParticipantLeft(player);
        sync(player.server);
    }

    /** Leaves any running match and shows the lobby. */
    public static void enterLobby(ServerPlayer player) {
        if (!IN_LOBBY.add(player.getUUID())) return;
        BattleGame game = BattleManager.game();
        if (game != null) game.onParticipantLeft(player);
        sync(player.server);
    }

    /** Marks the player ready to be pulled into the next match. */
    public static void leaveLobby(ServerPlayer player) {
        if (!IN_LOBBY.remove(player.getUUID())) return;
        sync(player.server);
    }

    public static void sync(MinecraftServer server) {
        if (server == null) return;
        LobbySnapshot snapshot = LobbySnapshot.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CBNetwork.sendLobby(player, snapshot);
        }
    }

    public static void syncTo(ServerPlayer player) {
        CBNetwork.sendLobby(player, LobbySnapshot.of(player.server));
    }
}
