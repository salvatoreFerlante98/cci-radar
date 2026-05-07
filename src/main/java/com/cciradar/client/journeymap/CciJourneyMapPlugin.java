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
import journeymap.api.v2.client.model.MapPolygonWithHoles;
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
 * All JM API references are isolated to this package.
 *
 * Visual design (Factorio-style):
 *   - Material dot cluster: many small colored circles scattered across cluster chunks
 *   - One larger center marker icon at the cluster centroid
 *   - Optional very-low-opacity polygon border (near-invisible by default)
 *
 * Overlay lifecycle:
 *   MAPPING_STARTED  → clear stable maps, full refresh from store
 *   vein store changed → diff refresh (add new clusters, remove gone clusters)
 *   MAPPING_STOPPED  → clear stable maps (JM already removed its overlays)
 *   client logout    → ClientVeinStore.clear() → diff removes all clusters
 *
 * Stable maps keyed by clusterId (encodes dim + resourceKey + sorted chunk positions):
 *   clusterMarkerMap — one center MarkerOverlay per cluster
 *   clusterDotMap    — N dot MarkerOverlays per cluster (deterministic positions)
 *   clusterPolyMap   — optional polygon(s) per cluster (very low opacity, subtle guide)
 *
 * Because clusterId encodes cluster composition, growing or merging clusters produce
 * a new key — the diff naturally removes the old overlay and adds the updated one.
 * No duplicate overlays can accumulate on resync.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class CciJourneyMapPlugin implements IClientPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Overlay style constants ──────────────────────────────────────────────
    // Center marker: large colored circle, no text label
    private static final double CENTER_DISPLAY_SIZE = 20.0; // display pixels

    // Dot markers: small colored circles scattered through cluster
    private static final double DOT_DISPLAY_SIZE    = 7.0;  // display pixels
    private static final float  DOT_OPACITY         = 0.90f;

    // Polygon: optional very-low-opacity border — barely visible, just guides the eye
    static final boolean ENABLE_CLUSTER_POLYGON  = true;
    static final float   POLYGON_FILL_OPACITY    = 0.03f;
    static final float   POLYGON_STROKE_OPACITY  = 0.22f;
    private static final float  POLYGON_STROKE_WIDTH   = 1.5f;

    // Dot rendering
    static final boolean ENABLE_MATERIAL_DOTS    = true;
    static final int     MAX_DOTS_PER_CLUSTER    = 90;
    static final int     MAX_TOTAL_DOTS_VISIBLE  = 600;

    // ── State ────────────────────────────────────────────────────────────────
    private IClientAPI jmApi;
    private boolean mappingActive = false;

    /** clusterId → center MarkerOverlay */
    private final Map<String, MarkerOverlay> clusterMarkerMap = new HashMap<>();
    /** clusterId → list of dot MarkerOverlays */
    private final Map<String, List<MarkerOverlay>> clusterDotMap = new HashMap<>();
    /** clusterId → list of PolygonOverlays (one per MapPolygonWithHoles from union) */
    private final Map<String, List<PolygonOverlay>> clusterPolyMap = new HashMap<>();

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
                clusterMarkerMap.clear();
                clusterDotMap.clear();
                clusterPolyMap.clear();
                refreshClusters();
            }
            case MAPPING_STOPPED -> {
                mappingActive = false;
                int cleared = clusterMarkerMap.size()
                        + clusterDotMap.values().stream().mapToInt(List::size).sum()
                        + clusterPolyMap.values().stream().mapToInt(List::size).sum();
                clusterMarkerMap.clear();
                clusterDotMap.clear();
                clusterPolyMap.clear();
                LOGGER.info("[CCI Radar] MAPPING_STOPPED — cleared {} overlay reference(s)", cleared);
            }
        }
    }

    private void onVeinStoreChanged() {
        if (mappingActive) refreshClusters();
    }

    // ── refresh ────────────────────────────────────────────────────────────────

    private void refreshClusters() {
        List<VeinEntry> veins = ClientVeinStore.getVeins();
        List<ResourceCluster> clusters = VeinClusterer.cluster(veins);

        Map<String, ResourceCluster> incoming = new HashMap<>();
        for (ResourceCluster c : clusters) incoming.put(c.clusterId(), c);

        int removed = removeStale(incoming);
        int added   = addNew(incoming);

        int dots     = clusterDotMap.values().stream().mapToInt(List::size).sum();
        int polys    = clusterPolyMap.values().stream().mapToInt(List::size).sum();
        LOGGER.debug("[CCI Radar] JM refresh — clusters: {}, dots: {}, polys: {} | added: {}, removed: {} | veins: {}",
                clusterMarkerMap.size(), dots, polys, added, removed, veins.size());
    }

    private int removeStale(Map<String, ResourceCluster> incoming) {
        int count = 0;

        List<String> stale = new ArrayList<>();
        for (String id : clusterMarkerMap.keySet()) {
            if (!incoming.containsKey(id)) stale.add(id);
        }
        for (String id : stale) {
            jmApi.remove(clusterMarkerMap.remove(id));
            count++;
        }

        stale.clear();
        for (String id : clusterDotMap.keySet()) {
            if (!incoming.containsKey(id)) stale.add(id);
        }
        for (String id : stale) {
            for (MarkerOverlay dot : clusterDotMap.remove(id)) {
                jmApi.remove(dot);
                count++;
            }
        }

        stale.clear();
        for (String id : clusterPolyMap.keySet()) {
            if (!incoming.containsKey(id)) stale.add(id);
        }
        for (String id : stale) {
            for (PolygonOverlay poly : clusterPolyMap.remove(id)) {
                jmApi.remove(poly);
                count++;
            }
        }

        return count;
    }

    private int addNew(Map<String, ResourceCluster> incoming) {
        int count = 0;
        int totalDots = clusterDotMap.values().stream().mapToInt(List::size).sum();

        for (Map.Entry<String, ResourceCluster> entry : incoming.entrySet()) {
            String id = entry.getKey();
            ResourceCluster cluster = entry.getValue();
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, cluster.dimension());

            // Center marker
            if (!clusterMarkerMap.containsKey(id)) {
                MarkerOverlay marker = buildCenterMarker(cluster, dimKey);
                clusterMarkerMap.put(id, marker);
                try {
                    jmApi.show(marker);
                    count++;
                } catch (Exception e) {
                    LOGGER.error("[CCI Radar] Failed to show center marker {} ({}): {}", cluster.resourceKey(), id, e.getMessage());
                    clusterMarkerMap.remove(id);
                }
            }

            // Material dots
            if (ENABLE_MATERIAL_DOTS && !clusterDotMap.containsKey(id)) {
                if (totalDots < MAX_TOTAL_DOTS_VISIBLE) {
                    int remaining    = MAX_TOTAL_DOTS_VISIBLE - totalDots;
                    int capForCluster = Math.min(MAX_DOTS_PER_CLUSTER, remaining);
                    List<BlockPos> positions = ClusterDotPlacer.computeDots(cluster, capForCluster);
                    List<MarkerOverlay> dots = new ArrayList<>(positions.size());
                    for (BlockPos pos : positions) {
                        MarkerOverlay dot = buildDotMarker(cluster, dimKey, pos);
                        dots.add(dot);
                        try {
                            jmApi.show(dot);
                            count++;
                        } catch (Exception e) {
                            LOGGER.error("[CCI Radar] Failed to show dot for {} at {}: {}", cluster.resourceKey(), pos, e.getMessage());
                        }
                    }
                    clusterDotMap.put(id, dots);
                    totalDots += dots.size();
                }
            }

            // Optional polygon border (very low opacity)
            if (ENABLE_CLUSTER_POLYGON && !clusterPolyMap.containsKey(id)) {
                List<PolygonOverlay> polys = buildClusterPolygons(cluster, dimKey);
                if (!polys.isEmpty()) {
                    clusterPolyMap.put(id, polys);
                    for (PolygonOverlay poly : polys) {
                        try {
                            jmApi.show(poly);
                            count++;
                        } catch (Exception e) {
                            LOGGER.error("[CCI Radar] Failed to show polygon for {} ({}): {}", cluster.resourceKey(), id, e.getMessage());
                        }
                    }
                }
            }
        }
        return count;
    }

    // ── overlay builders ──────────────────────────────────────────────────────

    /** Large icon at the cluster centroid. No label on minimap; title for fullscreen tooltip. */
    private MarkerOverlay buildCenterMarker(ResourceCluster cluster, ResourceKey<Level> dimKey) {
        ResourceLocation texLoc = ResourceLocation.parse(cluster.iconPath());
        MapImage icon = new MapImage(texLoc, 0, 0, 16, 16, cluster.color(), 1.0f)
                .setDisplayWidth(CENTER_DISPLAY_SIZE)
                .setDisplayHeight(CENTER_DISPLAY_SIZE)
                .centerAnchors()
                .setBlur(false);

        String chunks = cluster.chunks().size() == 1
                ? "1 chunk" : cluster.chunks().size() + " chunks";

        MarkerOverlay marker = new MarkerOverlay(ColonialResourceRadar.MODID, cluster.center(), icon);
        marker.setDimension(dimKey);
        marker.setLabel("");
        marker.setTitle(cluster.label() + " vein — " + chunks);
        return marker;
    }

    /** Small dot placed at a random block inside the cluster. No label. */
    private MarkerOverlay buildDotMarker(ResourceCluster cluster, ResourceKey<Level> dimKey, BlockPos pos) {
        // Use the generic circle dot for small markers — at 7 px the material shape is invisible,
        // only color distinguishes resource type at dot scale.
        ResourceLocation texLoc = ResourceLocation.parse("cci_radar:textures/map/resource/dot.png");
        MapImage icon = new MapImage(texLoc, 0, 0, 16, 16, cluster.color(), DOT_OPACITY)
                .setDisplayWidth(DOT_DISPLAY_SIZE)
                .setDisplayHeight(DOT_DISPLAY_SIZE)
                .centerAnchors()
                .setBlur(false);

        MarkerOverlay dot = new MarkerOverlay(ColonialResourceRadar.MODID, pos, icon);
        dot.setDimension(dimKey);
        dot.setLabel("");
        dot.setTitle("");
        return dot;
    }

    /** Very-low-opacity polygon showing the cluster boundary. Visual guide only. */
    private List<PolygonOverlay> buildClusterPolygons(ResourceCluster cluster, ResourceKey<Level> dimKey) {
        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(cluster.color())
                .setFillColor(cluster.color())
                .setStrokeOpacity(POLYGON_STROKE_OPACITY)
                .setFillOpacity(POLYGON_FILL_OPACITY)
                .setStrokeWidth(POLYGON_STROKE_WIDTH);

        List<MapPolygonWithHoles> unionPolys = PolygonHelper.createChunksPolygon(cluster.chunks(), 64);
        List<PolygonOverlay> overlays = new ArrayList<>(unionPolys.size());
        for (MapPolygonWithHoles poly : unionPolys) {
            PolygonOverlay overlay = new PolygonOverlay(ColonialResourceRadar.MODID, dimKey, shape, poly);
            overlay.setLabel("");
            overlay.setTitle("");
            overlays.add(overlay);
        }
        return overlays;
    }
}
