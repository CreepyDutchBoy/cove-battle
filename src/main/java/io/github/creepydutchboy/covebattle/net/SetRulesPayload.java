package io.github.creepydutchboy.covebattle.net;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.rules.MatchMode;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: apply this mode and these mutators. Rejected unless the sender is an operator. */
public record SetRulesPayload(MatchMode mode, Mutators mutators) implements CustomPacketPayload {

    public static final Type<SetRulesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "set_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRulesPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.mode.ordinal());
                Mutators.STREAM_CODEC.encode(buf, payload.mutators);
            }, buf -> {
                int ordinal = buf.readVarInt();
                MatchMode mode = MatchMode.values()[Math.floorMod(ordinal, MatchMode.values().length)];
                return new SetRulesPayload(mode, Mutators.STREAM_CODEC.decode(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
