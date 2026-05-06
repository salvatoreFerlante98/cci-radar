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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JourneyMap plugin for Colonial Resource Radar.
 *
 * Discovered at runtime by JourneyMap via {@link JourneyMapPlugin}. All JM API references
 * are isolated to this package.
 *
 * Overlay lifecycle:
 * - MAPPING_STARTED  → clear stable maps, full refresh from store
 * - vein store changed → diff refresh (add new, remove gone, keep existing)
 * - MAPPING_STOPPED  → clear stable maps (JM already removed its overlays)
 * - client logout    → ClientVeinStore.clear() → triggers diff that removes all overlays
 *
 * Stable map keys use the format: "{dim}:{cx},{cz}"
 * Two overlays per vein: a point marker (icon) and a chunk-boundary polygon.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class CciJourneyMapPlugin implements IClientPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private IClientAPI jmApi;
    private boolean mappingActive = false;

    /** Stable map: vein key → MarkerOverlay instance currently registered with JM. */
    private final Map<String, MarkerOverlay> markerMap = new HashMap<>();
    /** Stable map: vein key → PolygonOverlay instance currently registered with JM. */
    private final Map<String, PolygonOverlay> polygonMap = new HashMap<>();

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

    // ── JM events ─────────────────────────────────────────────────────────────

    private void onMappingEvent(MappingEvent event) {
        switch (event.getStage()) {
            case MAPPING_STARTED -> {
                mappingActive = true;
                // JM session is fresh; clear stale overlay references and re-register all veins.
                markerMap.clear();
                polygonMap.clear();
                refreshMarkers();
            }
            case MAPPING_STOPPED -> {
                mappingActive = false;
                // JM already removed all our overlays internally; just clear our tracking maps.
                int cleared = markerMap.size() + polygonMap.size();
                markerMap.clear();
                polygonMap.clear();
                LOGGER.info("[CCI Radar] MAPPING_STOPPED — cleared {} overlay reference(s)", cleared);
            }
        }
    }

    private void onVeinStoreChanged() {
        if (mappingActive) {
            refreshMarkers();
        }
    }

    // ── refresh ────────────────────────────────────────────────────────────────

    /**
     * Diff-based refresh: adds overlays for new veins, removes overlays for gone veins,
     * leaves existing overlay instances unchanged (no flicker, no duplicates).
     */
    private void refreshMarkers() {
        List<VeinEntry> veins = ClientVeinStore.getVeins();

        // Build incoming map: stableKey → VeinEntry
        Map<String, VeinEntry> incoming = new HashMap<>();
        for (VeinEntry v : veins) {
            incoming.put(stableKey(v.dimension(), v.chunkX(), v.chunkZ()), v);
        }

        // Remove overlays for veins no longer in the store
        int removed = removeStaleOverlays(incoming);

        // Add overlays for new veins
        int added = addNewOverlays(incoming);

        LOGGER.debug("[CCI Radar] JM refresh — markers: {}, polygons: {} | added: {}, removed: {} | store: {} vein(s)",
                markerMap.size(), polygonMap.size(), added, removed, veins.size());
    }

    private int removeStaleOverlays(Map<String, VeinEntry> incoming) {
        int count = 0;

        List<String> staleMarkers = new ArrayList<>();
        for (String key : markerMap.keySet()) {
            if (!incoming.containsKey(key)) staleMarkers.add(key);
        }
        for (String key : staleMarkers) {
            jmApi.remove(markerMap.remove(key));
            count++;
        }

        List<String> stalePolygons = new ArrayList<>();
        for (String key : polygonMap.keySet()) {
            if (!incoming.containsKey(key)) stalePolygons.add(key);
        }
        for (String key : stalePolygons) {
            jmApi.remove(polygonMap.remove(key));
            count++;
        }

        return count;
    }

    private int addNewOverlays(Map<String, VeinEntry> incoming) {
        int count = 0;
        for (Map.Entry<String, VeinEntry> entry : incoming.entrySet()) {
            String key = entry.getKey();
            VeinEntry vein = entry.getValue();
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, vein.dimension());

            if (!markerMap.containsKey(key)) {
                MarkerOverlay marker = buildMarker(vein, dimKey);
                markerMap.put(key, marker);
                try {
                    jmApi.show(marker);
                    count++;
                } catch (Exception e) {
                    LOGGER.error("[CCI Radar] Failed to show marker for {} at [{},{}]: {}",
                            vein.resourceKey(), vein.chunkX(), vein.chunkZ(), e.getMessage());
                    markerMap.remove(key);
                }
            }

            if (!polygonMap.containsKey(key)) {
                PolygonOverlay poly = buildPolygon(vein, dimKey);
                polygonMap.put(key, poly);
                try {
                    jmApi.show(poly);
                } catch (Exception e) {
                    LOGGER.error("[CCI Radar] Failed to show polygon for {} at [{},{}]: {}",
                            vein.resourceKey(), vein.chunkX(), vein.chunkZ(), e.getMessage());
                    polygonMap.remove(key);
                }
            }
        }
        return count;
    }

    // ── overlay builders ──────────────────────────────────────────────────────

    private MarkerOverlay buildMarker(VeinEntry vein, ResourceKey<Level> dimKey) {
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
        marker.setTitle(vein.label() + " @ chunk [" + vein.chunkX() + ", " + vein.chunkZ() + "]");
        return marker;
    }

    private PolygonOverlay buildPolygon(VeinEntry vein, ResourceKey<Level> dimKey) {
        // PolygonHelper.createChunkPolygon(chunkX, y, chunkZ): outlines the 16×16 block chunk.
        MapPolygon chunkPoly = PolygonHelper.createChunkPolygon(vein.chunkX(), 64, vein.chunkZ());

        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(vein.color())
                .setFillColor(vein.color())
                .setStrokeOpacity(0.85f)
                .setFillOpacity(0.12f)
                .setStrokeWidth(2.0f);

        PolygonOverlay poly = new PolygonOverlay(ColonialResourceRadar.MODID, dimKey, shape, chunkPoly);
        poly.setLabel(vein.label());
        poly.setTitle(vein.label() + " @ chunk [" + vein.chunkX() + ", " + vein.chunkZ() + "]");
        return poly;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Internal stable key for overlay maps.
     * Format: "{dim}:{cx},{cz}" — matches CciCommands.stableMarkerId minus the "cci_vein:" prefix.
     */
    static String stableKey(ResourceLocation dim, int cx, int cz) {
        return dim + ":" + cx + "," + cz;
    }
}
