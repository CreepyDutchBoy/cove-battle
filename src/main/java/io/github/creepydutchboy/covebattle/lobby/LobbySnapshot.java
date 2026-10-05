package io.github.creepydutchboy.covebattle.lobby;

import io.github.creepydutchboy.covebattle.game.BattleGame;
import io.github.creepydutchboy.covebattle.game.BattleManager;
import io.github.creepydutchboy.covebattle.rules.RulesState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** What the lobby screen needs to draw itself, assembled server side and sent as one payload. */
public record LobbySnapshot(String phase, String modeLabel, String matchLabel, int round, int bestOf,
                            boolean hosting, int port, List<Entry> entries) {

    /** One row in the player list. */
    public record Entry(String name, Where where, String side) {}

    public enum Where { LOBBY, PLAYING, OUT }

    public static LobbySnapshot of(MinecraftServer server) {
        BattleGame game = BattleManager.game();
        List<Entry> entries = new ArrayList<>();

        List<ServerPlayer> everyone = new ArrayList<>(server.getPlayerList().getPlayers());
        everyone.addAll(io.github.creepydutchboy.covebattle.game.BattleBots.all());

        for (ServerPlayer player : everyone) {
            Where where;
            if (LobbyState.isInLobby(player.getUUID())) {
                where = Where.LOBBY;
            } else if (game != null && game.isEliminated(player.getUUID())) {
                where = Where.OUT;
            } else if (game != null && game.isParticipant(player.getUUID())) {
                where = Where.PLAYING;
            } else {
                where = Where.LOBBY;
            }
            String side = game == null ? "" : game.sideLabelOf(player.getUUID());
            entries.add(new Entry(player.getGameProfile().getName(), where, side == null ? "" : side));
        }

        boolean hosting = server.isPublished();
        int port = hosting ? server.getPort() : 0;

        return new LobbySnapshot(
                game == null ? "LOBBY" : game.phase().name(),
                RulesState.mode().label(),
                game == null ? "" : game.mode().label(),
                game == null ? 0 : game.round(),
                RulesState.active().bestOf(),
                hosting, port, entries);
    }
}
