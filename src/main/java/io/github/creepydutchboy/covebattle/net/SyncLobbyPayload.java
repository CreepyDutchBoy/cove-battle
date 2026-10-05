package io.github.creepydutchboy.covebattle.net;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.lobby.LobbySnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client: everything the lobby screen draws. */
public record SyncLobbyPayload(LobbySnapshot snapshot) implements CustomPacketPayload {

    public static final Type<SyncLobbyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "sync_lobby"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLobbyPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                LobbySnapshot s = payload.snapshot();
                buf.writeUtf(s.phase());
                buf.writeUtf(s.modeLabel());
                buf.writeUtf(s.matchLabel());
                buf.writeVarInt(s.round());
                buf.writeVarInt(s.bestOf());
                buf.writeBoolean(s.hosting());
                buf.writeVarInt(s.port());
                buf.writeVarInt(s.entries().size());
                for (LobbySnapshot.Entry entry : s.entries()) {
                    buf.writeUtf(entry.name());
                    buf.writeVarInt(entry.where().ordinal());
                    buf.writeUtf(entry.side());
                }
            }, buf -> {
                String phase = buf.readUtf();
                String mode = buf.readUtf();
                String match = buf.readUtf();
                int round = buf.readVarInt();
                int bestOf = buf.readVarInt();
                boolean hosting = buf.readBoolean();
                int port = buf.readVarInt();
                int count = buf.readVarInt();
                List<LobbySnapshot.Entry> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String name = buf.readUtf();
                    int whereOrdinal = buf.readVarInt();
                    String side = buf.readUtf();
                    LobbySnapshot.Where where = LobbySnapshot.Where.values()[
                            Math.floorMod(whereOrdinal, LobbySnapshot.Where.values().length)];
                    entries.add(new LobbySnapshot.Entry(name, where, side));
                }
                return new SyncLobbyPayload(new LobbySnapshot(phase, mode, match, round, bestOf, hosting, port, entries));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
