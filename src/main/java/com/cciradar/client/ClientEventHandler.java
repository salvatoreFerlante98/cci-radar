package com.cciradar.client;

import com.cciradar.ColonialResourceRadar;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;

/**
 * Client-side game-bus event handler.
 * Clears the local vein store on world disconnect so stale overlays cannot
 * appear when joining a different world or server.
 */
@EventBusSubscriber(modid = ColonialResourceRadar.MODID, value = Dist.CLIENT)
public final class ClientEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.info("[CCI Radar] Client disconnecting — clearing vein store and JM overlays");
        ClientVeinStore.clear();
    }
}
