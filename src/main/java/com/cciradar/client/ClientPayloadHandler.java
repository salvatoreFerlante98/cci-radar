package com.cciradar.client;

import com.cciradar.network.VisibleVeinsPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {

    public static void handle(VisibleVeinsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientVeinStore.update(payload.veins()));
    }
}
