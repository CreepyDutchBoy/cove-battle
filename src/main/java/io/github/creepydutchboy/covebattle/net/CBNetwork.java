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
    }

    public static void sendRules(ServerPlayer player, MatchMode mode, Mutators mutators) {
        try {
            PacketDistributor.sendToPlayer(player, new SyncRulesPayload(mode, mutators));
        } catch (Exception e) {
            CoveBattle.LOGGER.warn("Could not send the rule set to {}: {}", player.getGameProfile().getName(), e.toString());
        }
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
