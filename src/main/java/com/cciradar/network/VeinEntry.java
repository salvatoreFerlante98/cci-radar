package com.cciradar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record VeinEntry(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        String resourceKey,
        String label,
        int color,
        String iconPath
) {
    public static final StreamCodec<FriendlyByteBuf, VeinEntry> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VeinEntry decode(FriendlyByteBuf buf) {
            return new VeinEntry(
                    buf.readResourceLocation(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readUtf()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, VeinEntry v) {
            buf.writeResourceLocation(v.dimension());
            buf.writeVarInt(v.chunkX());
            buf.writeVarInt(v.chunkZ());
            buf.writeUtf(v.resourceKey());
            buf.writeUtf(v.label());
            buf.writeInt(v.color());
            buf.writeUtf(v.iconPath());
        }
    };
}
