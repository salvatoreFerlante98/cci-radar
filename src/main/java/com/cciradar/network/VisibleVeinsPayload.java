package com.cciradar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record VisibleVeinsPayload(List<VeinEntry> veins) implements CustomPacketPayload {

    public static final Type<VisibleVeinsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("cci_radar", "visible_veins"));

    public static final StreamCodec<FriendlyByteBuf, VisibleVeinsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VisibleVeinsPayload decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<VeinEntry> veins = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                veins.add(VeinEntry.STREAM_CODEC.decode(buf));
            }
            return new VisibleVeinsPayload(veins);
        }

        @Override
        public void encode(FriendlyByteBuf buf, VisibleVeinsPayload payload) {
            buf.writeVarInt(payload.veins().size());
            for (VeinEntry vein : payload.veins()) {
                VeinEntry.STREAM_CODEC.encode(buf, vein);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
