package io.github.creepydutchboy.covebattle.net;

import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: you have been flashed, white out for this many ticks. */
public record FlashPayload(int ticks) implements CustomPacketPayload {

    public static final Type<FlashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "flash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.ticks()),
                    buf -> new FlashPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
