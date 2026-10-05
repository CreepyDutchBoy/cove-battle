package io.github.creepydutchboy.covebattle.net;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.rules.MatchMode;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import io.github.creepydutchboy.covebattle.rules.RulesState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Mod networking for the rule set.
 *
 * <p>This is what the settings UI rides on: the client edits a local copy and sends one payload,
 * the server validates permission, applies it, and broadcasts the result back to everyone.
 */
public final class CBNetwork {

    private static final String VERSION = "1";

    private CBNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(SyncRulesPayload.TYPE, SyncRulesPayload.STREAM_CODEC, CBNetwork::onSyncRules);
        registrar.playToServer(SetRulesPayload.TYPE, SetRulesPayload.STREAM_CODEC, CBNetwork::onSetRules);
        registrar.playToClient(SyncLobbyPayload.TYPE, SyncLobbyPayload.STREAM_CODEC, CBNetwork::onSyncLobby);
        registrar.playToServer(LobbyActionPayload.TYPE, LobbyActionPayload.STREAM_CODEC, CBNetwork::onLobbyAction);
        registrar.playToClient(FlashPayload.TYPE, FlashPayload.STREAM_CODEC, CBNetwork::onFlash);
    }

    public static void sendRules(ServerPlayer player, MatchMode mode, Mutators mutators) {
        try {
            PacketDistributor.sendToPlayer(player, new SyncRulesPayload(mode, mutators));
        } catch (Exception e) {
            CoveBattle.LOGGER.warn("Could not send the rule set to {}: {}", player.getGameProfile().getName(), e.toString());
        }
    }

    public static void sendLobby(ServerPlayer player, io.github.creepydutchboy.covebattle.lobby.LobbySnapshot snapshot) {
        try {
            PacketDistributor.sendToPlayer(player, new SyncLobbyPayload(snapshot));
        } catch (Exception e) {
            CoveBattle.LOGGER.warn("Could not send the lobby to {}: {}",
                    player.getGameProfile().getName(), e.toString());
        }
    }

    /** Blinds one player's screen for a while. */
    public static void sendFlash(ServerPlayer player, int ticks) {
        try {
            PacketDistributor.sendToPlayer(player, new FlashPayload(ticks));
        } catch (Exception e) {
            CoveBattle.LOGGER.debug("Could not flash {}: {}", player.getGameProfile().getName(), e.toString());
        }
    }

    private static void onFlash(FlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                io.github.creepydutchboy.covebattle.client.FlashOverlay.start(payload.ticks()));
    }

    private static void onSyncLobby(SyncLobbyPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                io.github.creepydutchboy.covebattle.client.ClientLobby.accept(payload.snapshot()));
    }

    /** Server side: lobby buttons. Starting or stopping a match needs operator permission. */
    private static void onLobbyAction(LobbyActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var game = io.github.creepydutchboy.covebattle.game.BattleManager.game();
            boolean op = player.hasPermissions(2);

            switch (payload.action()) {
                case ENTER_LOBBY -> io.github.creepydutchboy.covebattle.lobby.LobbyState.enterLobby(player);
                case LEAVE_LOBBY -> io.github.creepydutchboy.covebattle.lobby.LobbyState.leaveLobby(player);
                case START_SOLO, START_TEAMS -> {
                    if (!op || game == null) {
                        player.sendSystemMessage(Component.literal("[Cove Battle] Only an operator can start a match."));
                        return;
                    }
                    io.github.creepydutchboy.covebattle.lobby.LobbyState.leaveLobby(player);
                    game.start(player::sendSystemMessage,
                            payload.action() == LobbyActionPayload.Action.START_TEAMS
                                    ? io.github.creepydutchboy.covebattle.game.BattleMode.TEAMS
                                    : io.github.creepydutchboy.covebattle.game.BattleMode.SOLO);
                    io.github.creepydutchboy.covebattle.lobby.LobbyState.sync(player.server);
                }
                case STOP_MATCH -> {
                    if (!op || game == null) return;
                    game.stop(true);
                    io.github.creepydutchboy.covebattle.lobby.LobbyState.sync(player.server);
                }
                case OPEN_TO_LAN -> openToLan(player, payload.argument());
            }
        });
    }

    /** Opens the host's integrated server on a port so other people can join the lobby. */
    private static void openToLan(ServerPlayer player, String argument) {
        var server = player.server;
        if (!server.isSingleplayerOwner(player.getGameProfile())) {
            player.sendSystemMessage(Component.literal("[Cove Battle] Only the host can open the server."));
            return;
        }
        if (server.isPublished()) {
            player.sendSystemMessage(Component.literal("[Cove Battle] Already open on port " + server.getPort() + "."));
            return;
        }
        int port;
        try {
            port = Integer.parseInt(argument.trim());
        } catch (NumberFormatException e) {
            port = net.minecraft.util.HttpUtil.getAvailablePort();
        }
        boolean opened = server.publishServer(net.minecraft.world.level.GameType.ADVENTURE, false, port);
        player.sendSystemMessage(Component.literal(opened
                ? "[Cove Battle] Server open on port " + server.getPort()
                : "[Cove Battle] Could not open port " + port));
        io.github.creepydutchboy.covebattle.lobby.LobbyState.sync(server);
    }

    /** Client side: remember what the server said so the screen shows live values. */
    private static void onSyncRules(SyncRulesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> io.github.creepydutchboy.covebattle.client.ClientRules
                .accept(payload.mode(), payload.mutators()));
    }

    /** Server side: only operators may change the rules. */
    private static void onSetRules(SetRulesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("[Cove Battle] You need operator permission to change the rules."));
                return;
            }
            RulesState.setCustom(player.server, payload.mutators());
            RulesState.setMode(player.server, payload.mode());
            CoveBattle.LOGGER.info("{} set the rules to {}", player.getGameProfile().getName(), payload.mode());
        });
    }
}
