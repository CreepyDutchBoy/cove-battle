package io.github.creepydutchboy.covebattle.net;

import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: what the player pressed in the lobby. */
public record LobbyActionPayload(Action action, String argument) implements CustomPacketPayload {

    public enum Action {
        /** Leave any running match and sit in the lobby. */
        ENTER_LOBBY,
        /** Mark ready, so the next match includes you. */
        LEAVE_LOBBY,
        /** Start a match now (operators only). */
        START_SOLO,
        START_TEAMS,
        /** Stop the running match (operators only). */
        STOP_MATCH,
        /** Ask the integrated server to open on a port (host only). */
        OPEN_TO_LAN
    }

    public static final Type<LobbyActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "lobby_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.action().ordinal());
                buf.writeUtf(payload.argument() == null ? "" : payload.argument());
            }, buf -> {
                int ordinal = buf.readVarInt();
                Action action = Action.values()[Math.floorMod(ordinal, Action.values().length)];
                return new LobbyActionPayload(action, buf.readUtf());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
