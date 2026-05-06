package com.cciradar.client.journeymap;

import com.cciradar.ColonialResourceRadar;
import com.cciradar.client.ClientVeinStore;
import com.cciradar.network.VeinEntry;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;

/**
 * JourneyMap plugin for Colonial Resource Radar.
 *
 * Discovered at runtime by JourneyMap via the {@link JourneyMapPlugin} annotation.
 * All JourneyMap API references are isolated to this package.
 *
 * Renders both a point marker (icon) and a chunk-boundary polygon for each visible vein.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class CciJourneyMapPlugin implements IClientPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private IClientAPI jmApi;
    private boolean mappingActive = false;

    @Override
    public String getModId() {
        return ColonialResourceRadar.MODID;
    }

    @Override
    public void initialize(final IClientAPI jmClientApi) {
        this.jmApi = jmClientApi;
        LOGGER.info("[CCI Radar] JourneyMap plugin initialized (API {})", IClientAPI.API_VERSION);

        ClientEventRegistry.MAPPING_EVENT.subscribe(getModId(), this::onMappingEvent);
        ClientVeinStore.addChangeListener(this::onVeinStoreChanged);
    }

    private void onMappingEvent(MappingEvent event) {
        switch (event.getStage()) {
            case MAPPING_STARTED -> {
                mappingActive = true;
                refreshMarkers();
            }
            case MAPPING_STOPPED -> {
                mappingActive = false;
                clearMarkers();
            }
        }
    }

    private void onVeinStoreChanged() {
        if (mappingActive) {
            refreshMarkers();
        }
    }

    private void refreshMarkers() {
        jmApi.removeAll(ColonialResourceRadar.MODID);

        List<VeinEntry> veins = ClientVeinStore.getVeins();
        for (VeinEntry vein : veins) {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, vein.dimension());
            showMarker(vein, dimKey);
            showPolygon(vein, dimKey);
        }

        LOGGER.debug("[CCI Radar] Refreshed {} JourneyMap overlay(s) ({} marker + polygon pairs)",
                veins.size() * 2, veins.size());
    }

    private void showMarker(VeinEntry vein, ResourceKey<Level> dimKey) {
        try {
            BlockPos pos = new BlockPos(vein.chunkX() * 16 + 8, 64, vein.chunkZ() * 16 + 8);

            MapImage icon = new MapImage(
                    ResourceLocation.parse(vein.iconPath()),
                    0, 0, 8, 8,
                    vein.color(),
                    1.0f
            ).centerAnchors();

            MarkerOverlay marker = new MarkerOverlay(ColonialResourceRadar.MODID, pos, icon);
            marker.setDimension(dimKey);
            marker.setLabel(vein.label());
            marker.setTitle(vein.label() + " vein @ chunk [" + vein.chunkX() + ", " + vein.chunkZ() + "]");

            jmApi.show(marker);
        } catch (Exception e) {
            LOGGER.error("[CCI Radar] Failed to show marker for {}", vein.resourceKey(), e);
        }
    }

    private void showPolygon(VeinEntry vein, ResourceKey<Level> dimKey) {
        try {
            // Chunk boundary polygon: outlines the 16x16 block area of the vein's chunk.
            // PolygonHelper.createChunkPolygon(chunkX, y, chunkZ)
            MapPolygon chunkPoly = PolygonHelper.createChunkPolygon(vein.chunkX(), 64, vein.chunkZ());

            ShapeProperties shape = new ShapeProperties()
                    .setStrokeColor(vein.color())
                    .setFillColor(vein.color())
                    .setStrokeOpacity(0.85f)
                    .setFillOpacity(0.12f)
                    .setStrokeWidth(2.0f);

            PolygonOverlay polygon = new PolygonOverlay(
                    ColonialResourceRadar.MODID, dimKey, shape, chunkPoly);
            polygon.setLabel(vein.label());
            polygon.setTitle(vein.label() + " vein @ chunk [" + vein.chunkX() + ", " + vein.chunkZ() + "]");

            jmApi.show(polygon);
        } catch (Exception e) {
            LOGGER.error("[CCI Radar] Failed to show polygon for {}", vein.resourceKey(), e);
        }
    }

    private void clearMarkers() {
        jmApi.removeAll(ColonialResourceRadar.MODID);
        LOGGER.info("[CCI Radar] JourneyMap overlays cleared (mapping stopped)");
    }
}
