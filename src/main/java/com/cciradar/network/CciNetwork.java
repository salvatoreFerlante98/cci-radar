package com.cciradar.network;

import com.cciradar.ColonialResourceRadar;
import com.cciradar.client.ClientPayloadHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ColonialResourceRadar.MODID)
public final class CciNetwork {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                VisibleVeinsPayload.TYPE,
                VisibleVeinsPayload.STREAM_CODEC,
                ClientPayloadHandler::handle
        );
    }
}
