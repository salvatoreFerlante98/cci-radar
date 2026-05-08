package com.cciradar.server.cci;

import com.cciradar.server.WorldVeinData;
import com.cciradar.server.VeinSyncManager;
import com.cciradar.server.coe.DetectedVein;
import com.ccicore.vein.CciVeinEvents;
import com.ccicore.vein.CciVeinProvider;
import com.ccicore.vein.CciVeinProviders;
import com.ccicore.vein.CciVeinSnapshot;
import com.ccicore.vein.CciVeinState;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge between cci_radar and cci_core's vein provider API.
 *
 * Responsibilities:
 *   1. Expose the authoritative {@link CciVeinProvider} (if any) to the scanner so it can
 *      switch to "provider-first" mode and skip the COE legacy reader entirely for that chunk.
 *   2. Listen for {@link CciVeinEvents#notifyUpdated} broadcasts and reconcile the radar's
 *      WorldVeinData cache (insert/update on VEIN, drop on NO_VEIN). This keeps the map free
 *      of stale markers when cci_world (or any other authoritative source) revises a chunk.
 *
 * Design notes:
 *   - No dependency on cci_world. We only depend on cci_core APIs.
 *   - No reflection, no mixin: cci_core is now a hard compile/runtime dep of the scanner.
 *   - The legacy COE path remains the fallback when no authoritative provider is registered,
 *     preserving standalone behavior.
 */
public final class CciCoreVeinBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Server reference captured at server-start so the event listener can resolve WorldVeinData. */
    private static final AtomicReference<MinecraftServer> SERVER_REF = new AtomicReference<>();

    private static volatile boolean listenerRegistered = false;
    private static CciVeinEvents.UpdatedListener listener;

    // ── Event/server lifecycle hooks ──────────────────────────────────────────

    public static void onServerStarting(MinecraftServer server) {
        SERVER_REF.set(server);
        registerListenerOnce();
    }

    public static void onServerStopping(MinecraftServer server) {
        SERVER_REF.compareAndSet(server, null);
        // listener stays registered: cci_core lifecycle owns it; CciVeinEvents.clear()
        // is also called by cci_core itself if it shuts down.
    }

    private static synchronized void registerListenerOnce() {
        if (listenerRegistered) return;
        listener = CciCoreVeinBridge::onSnapshotUpdated;
        CciVeinEvents.registerUpdatedListener(listener);
        listenerRegistered = true;
        LOGGER.info("[CCI Radar] Registered cci_core CciVeinEvents listener.");
    }

    // ── Provider-first scan API (called from CciScanQueue / debug commands) ──

    /** Returns the primary authoritative provider, if any. */
    public static Optional<CciVeinProvider> authoritativeProvider() {
        return CciVeinProviders.primary().filter(CciVeinProvider::isAuthoritative);
    }

    /** True when an authoritative provider is registered (provider-first mode active). */
    public static boolean isProviderModeActive() {
        return authoritativeProvider().isPresent();
    }

    /** Source id of the active authoritative provider, or "(none)" if none. */
    public static String activeSourceId() {
        return authoritativeProvider().map(CciVeinProvider::sourceId).orElse("(none)");
    }

    /**
     * Result of a provider lookup for a single chunk.
     */
    public enum ProviderOutcome {
        /** Provider returned VEIN: caller should use {@link ProviderScan#detected} as the real vein. */
        VEIN,
        /** Provider returned NO_VEIN: caller must drop any cached entry for this chunk. */
        NO_VEIN,
        /** Provider returned PENDING/UNKNOWN: caller must skip the chunk and NOT read COE. */
        SKIP,
        /** No authoritative provider registered: caller should fall back to legacy COE. */
        NO_PROVIDER
    }

    public record ProviderScan(ProviderOutcome outcome, DetectedVein detected, String sourceId, CciVeinState state) {}

    /**
     * Provider-first lookup for a single (dim, cx, cz) chunk.
     *
     * Contract:
     *   - If no authoritative provider is registered, returns {@link ProviderOutcome#NO_PROVIDER}
     *     and the caller must fall back to its legacy COE behavior.
     *   - If a provider exists, the legacy COE reader MUST NOT be consulted for this chunk.
     */
    public static ProviderScan lookup(ServerLevel level, int cx, int cz) {
        Optional<CciVeinProvider> opt = authoritativeProvider();
        if (opt.isEmpty()) {
            return new ProviderScan(ProviderOutcome.NO_PROVIDER, null, "(none)", CciVeinState.UNKNOWN);
        }
        CciVeinProvider provider = opt.get();
        CciVeinSnapshot snap;
        try {
            snap = provider.getVein(level, new net.minecraft.world.level.ChunkPos(cx, cz));
        } catch (Throwable t) {
            LOGGER.warn("[CCI Radar] cci_core provider '{}' threw on getVein({},{},{}): {}",
                    provider.sourceId(), level.dimension().location(), cx, cz, t.toString());
            return new ProviderScan(ProviderOutcome.SKIP, null, provider.sourceId(), CciVeinState.UNKNOWN);
        }
        if (snap == null) {
            return new ProviderScan(ProviderOutcome.SKIP, null, provider.sourceId(), CciVeinState.UNKNOWN);
        }
        return mapSnapshot(level.dimension().location(), cx, cz, snap, provider.sourceId());
    }

    private static ProviderScan mapSnapshot(ResourceLocation dim, int cx, int cz,
                                            CciVeinSnapshot snap, String fallbackSourceId) {
        String src = snap.sourceId() != null ? snap.sourceId() : fallbackSourceId;
        CciVeinState state = snap.state();
        if (state == null) state = CciVeinState.UNKNOWN;
        switch (state) {
            case VEIN -> {
                DetectedVein dv = toDetectedVein(dim, cx, cz, snap);
                if (dv == null) {
                    // VEIN but unmapped (no resource key) → behave like SKIP, don't fallback to COE
                    return new ProviderScan(ProviderOutcome.SKIP, null, src, state);
                }
                return new ProviderScan(ProviderOutcome.VEIN, dv, src, state);
            }
            case NO_VEIN -> {
                return new ProviderScan(ProviderOutcome.NO_VEIN, null, src, state);
            }
            case PENDING, UNKNOWN -> {
                return new ProviderScan(ProviderOutcome.SKIP, null, src, state);
            }
        }
        return new ProviderScan(ProviderOutcome.SKIP, null, src, state);
    }

    private static DetectedVein toDetectedVein(ResourceLocation dim, int cx, int cz, CciVeinSnapshot snap) {
        ResourceLocation recipe = snap.recipeId();
        // Legacy DetectedVein requires a non-null recipeId; synthesize a stable placeholder if needed.
        if (recipe == null) {
            recipe = ResourceLocation.fromNamespaceAndPath("cci_core", "provider_vein");
        }
        String key = (snap.resourceKey() != null) ? snap.resourceKey().id() : null;
        if (key == null) return null;
        return new DetectedVein(dim, cx, cz, recipe, key);
    }

    // ── Event listener ────────────────────────────────────────────────────────

    private static void onSnapshotUpdated(CciVeinSnapshot snap) {
        if (snap == null) return;
        MinecraftServer server = SERVER_REF.get();
        if (server == null) return;

        // Only act on snapshots produced by an authoritative source.
        // Non-authoritative sources are advisory and must not invalidate our cache.
        Optional<CciVeinProvider> provider = CciVeinProviders.get(snap.sourceId());
        boolean authoritative = provider.map(CciVeinProvider::isAuthoritative).orElse(false);
        if (!authoritative) return;

        final ResourceLocation dim = snap.dimension();
        final int cx = snap.chunk().x;
        final int cz = snap.chunk().z;
        final CciVeinState state = snap.state();

        // Hop to the server thread to mutate WorldVeinData safely.
        server.execute(() -> {
            WorldVeinData data = WorldVeinData.get(server);
            switch (state) {
                case VEIN -> {
                    DetectedVein dv = toDetectedVein(dim, cx, cz, snap);
                    if (dv == null) return;
                    // Replace any prior entry for this chunk with the authoritative one.
                    if (data.hasAt(dim, cx, cz)) {
                        data.removeAt(dim, cx, cz);
                    }
                    data.addIfAbsent(dv);
                    // Mark dirty for nearby players: re-sync online players so the new vein is visible.
                    VeinSyncManager.syncAllPlayers(server);
                }
                case NO_VEIN -> {
                    boolean removed = data.removeAt(dim, cx, cz);
                    if (removed) {
                        // No stale marker / polygon must remain.
                        VeinSyncManager.syncAllPlayers(server);
                    }
                }
                case PENDING, UNKNOWN -> {
                    // Nothing to do: ambiguous state must not mutate cache.
                }
            }
        });
    }

    private CciCoreVeinBridge() {}
}
