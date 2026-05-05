package com.cciradar.client.journeymap;

import com.cciradar.ColonialResourceRadar;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * JourneyMap plugin for Colonial Resource Radar.
 *
 * Discovered at runtime by JourneyMap via the {@link JourneyMapPlugin} annotation.
 * This class is never referenced from common/server code; it is only classloaded
 * when JourneyMap is present and scans for plugins.
 *
 * All JourneyMap API references are isolated to this package (com.cciradar.client.journeymap).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class CciJourneyMapPlugin implements IClientPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Smoke-test marker position: world origin at sea level.
    // Displayed in the Overworld only; Y is irrelevant for the 2-D map.
    private static final BlockPos MARKER_POS = new BlockPos(0, 64, 0);

    private IClientAPI jmApi;

    // Kept so we can remove/replace it on subsequent MAPPING_STARTED events.
    private MarkerOverlay testMarker;

    // --- IClientPlugin -------------------------------------------------------

    /**
     * Required by {@link journeymap.api.v2.common.IJourneyMapPlugin}.
     * Tells JourneyMap which mod owns this plugin.
     */
    @Override
    public String getModId() {
        return ColonialResourceRadar.MODID;
    }

    /**
     * Called once by JourneyMap during mod initialisation.
     * Store the API reference and subscribe to lifecycle events.
     */
    @Override
    public void initialize(final IClientAPI jmClientApi) {
        this.jmApi = jmClientApi;
        LOGGER.info("[CCI Radar] JourneyMap plugin initialized (API {})", IClientAPI.API_VERSION);

        // Subscribe with a permanent listener (never unsubscribed; class lives for
        // the duration of the game session together with JourneyMap).
        ClientEventRegistry.MAPPING_EVENT.subscribe(getModId(), this::onMappingEvent);
    }

    // --- Event handlers ------------------------------------------------------

    private void onMappingEvent(MappingEvent event) {
        switch (event.getStage()) {
            case MAPPING_STARTED -> showTestMarker();
            case MAPPING_STOPPED -> clearMarkers();
        }
    }

    // --- Marker management ---------------------------------------------------

    private void showTestMarker() {
        // Remove the previous instance if mapping restarted (e.g. world reload).
        if (testMarker != null) {
            jmApi.remove(testMarker);
            testMarker = null;
        }

        // Icon: first 8×8 sprite from vanilla's map_icons.png sprite sheet.
        // This texture is guaranteed to exist in every 1.21.1 installation.
        // centerAnchors() offsets the icon so its centre sits on MARKER_POS.
        MapImage icon = new MapImage(
                ResourceLocation.withDefaultNamespace("textures/map/map_icons.png"),
                0, 0, 8, 8,
                0xFFAA00,   // amber tint (white = no tint)
                1.0f
        ).centerAnchors();

        // MarkerOverlay auto-generates a UUID for its display ID.
        // getGuid() will be "cci_radar-Marker-<uuid>", namespaced by our modId.
        testMarker = new MarkerOverlay(ColonialResourceRadar.MODID, MARKER_POS, icon);

        // Overlay.setDimension() returns Overlay (not MarkerOverlay), so we call
        // each setter as a statement rather than chaining.
        testMarker.setDimension(Level.OVERWORLD);
        testMarker.setLabel("CCI Test");
        testMarker.setTitle("Colonial Resource Radar – smoke-test marker at origin");

        try {
            jmApi.show(testMarker);
            LOGGER.info("[CCI Radar] Test marker shown at {} in {}",
                    MARKER_POS, Level.OVERWORLD.location());
        } catch (Exception e) {
            LOGGER.error("[CCI Radar] Failed to show test marker", e);
        }
    }

    private void clearMarkers() {
        // Remove every displayable this mod has registered.
        jmApi.removeAll(ColonialResourceRadar.MODID);
        testMarker = null;
        LOGGER.info("[CCI Radar] JourneyMap overlays cleared (mapping stopped)");
    }
}
