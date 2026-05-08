package com.cciradar.server;

import com.cciradar.config.CciConfig;
import com.cciradar.server.cci.CciCoreVeinBridge;
import com.cciradar.server.coe.CoeVeinAdapter;
import com.cciradar.server.coe.DetectedVein;
import com.cciradar.server.surfacehint.SurfaceHintManager;
import com.cciradar.server.surfacehint.SurfaceHintPlacementResult;
import com.cciradar.server.surfacehint.SurfaceHintWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deferred scan and hint placement queues.
 *
 * enqueueScan() is thread-safe (may be called from world-gen threads via ChunkEvent.Load).
 * All other public methods and processTick() are server-thread only.
 *
 * Scan jobs: deduplicated by (dim, cx, cz).
 * Hint jobs: deduplicated by (dim, cx, cz, resourceKey).
 *
 * Tier gating: hints are only enqueued or placed when at least one online player has the
 * resource's tier unlocked. This prevents pebbles from appearing for locked resources.
 * The backfill on player login and the periodic backfill handle re-enqueueing once a
 * player unlocks the relevant tier.
 */
public final class CciScanQueue {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SYNC_DEBOUNCE_TICKS = 20;

    // ── Scan queue — thread-safe ──────────────────────────────────────────────

    private static final Queue<ScanJob>  SCAN_QUEUE         = new ConcurrentLinkedQueue<>();
    private static final Set<String>     SCAN_ENQUEUED_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger   SCAN_QUEUE_SIZE    = new AtomicInteger();

    // ── Hint queue — server-thread only ──────────────────────────────────────

    private static final Deque<DetectedVein> HINT_QUEUE         = new ArrayDeque<>();
    private static final Set<String>         HINT_ENQUEUED_KEYS = new HashSet<>();

    // ── Sync throttle — server-thread only ───────────────────────────────────

    private static boolean hasPendingSync    = false;
    private static int     syncThrottleTicks = 0;

    // ── Per-tick stats — server-thread only ──────────────────────────────────

    private static int lastProcessedScans = 0;
    private static int lastProcessedHints = 0;

    // ── Scan lifetime counters ────────────────────────────────────────────────

    private static final AtomicLong totalEnqueued         = new AtomicLong();
    private static final AtomicLong totalDroppedScans     = new AtomicLong();
    private static       long       totalProcessed        = 0;
    private static       long       totalSkippedUnloaded  = 0;
    private static       long       totalRawCoeVeinsFound = 0;
    private static       long       totalMappedVeinsAdded = 0;
    private static       long       totalUnmappedVeins    = 0;

    // ── Hint lifetime counters — server-thread only ───────────────────────────

    private static long totalHintsEnqueued            = 0;
    private static long totalHintsProcessed           = 0;
    private static long totalHintsPlacedBlocks        = 0;
    private static long totalHintsSkippedUnloaded     = 0;
    private static long totalHintsFailedNoPosition    = 0;
    private static long totalHintsSkippedTierLocked   = 0;
    private static long totalDroppedHints             = 0;

    // ── Hint backfill lifetime counters — server-thread only ─────────────────

    private static long totalHintBackfillChecks             = 0;
    private static long totalHintBackfillEnqueued           = 0;
    private static long totalHintBackfillSkippedPlaced      = 0;
    private static long totalHintBackfillSkippedUnloaded    = 0;
    private static long totalHintBackfillSkippedRetry       = 0;
    private static long totalHintBackfillSkippedNoBlock     = 0;
    private static long totalHintBackfillSkippedTierLocked  = 0;

    private CciScanQueue() {}

    // ── Enqueue scan ──────────────────────────────────────────────────────────

    /**
     * Thread-safe scan enqueue. Deduplicated; drops if queue full.
     */
    public static void enqueueScan(ResourceLocation dim, int cx, int cz) {
        String key = scanKey(dim, cx, cz);
        if (!SCAN_ENQUEUED_KEYS.add(key)) return;

        int max = CciConfig.MAX_PENDING_SCAN_JOBS.getAsInt();
        if (SCAN_QUEUE_SIZE.get() >= max) {
            SCAN_ENQUEUED_KEYS.remove(key);
            long dropped = totalDroppedScans.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                LOGGER.warn("[CCI Radar] Scan queue full ({} pending), dropped {} total — increase max_pending_scan_jobs",
                        SCAN_QUEUE_SIZE.get(), dropped);
            }
            return;
        }

        SCAN_QUEUE.add(new ScanJob(dim, cx, cz));
        SCAN_QUEUE_SIZE.incrementAndGet();
        totalEnqueued.incrementAndGet();
    }

    // ── Enqueue hint ──────────────────────────────────────────────────────────

    /**
     * Server-thread only. Deduplicated by (dim, cx, cz, resourceKey).
     * Returns true if the job was actually added; false if deduped or dropped.
     * Does NOT check tier — callers are responsible for gating on tier unlock.
     */
    public static boolean enqueueHint(DetectedVein vein) {
        if (vein.cciResourceKey() == null) return false;
        String key = hintKey(vein);
        if (HINT_ENQUEUED_KEYS.contains(key)) return false;

        int max = CciConfig.MAX_PENDING_HINT_JOBS.getAsInt();
        if (HINT_QUEUE.size() >= max) {
            totalDroppedHints++;
            if (totalDroppedHints == 1 || totalDroppedHints % 200 == 0) {
                LOGGER.warn("[CCI Radar] Hint queue full ({} pending), dropped {} total",
                        HINT_QUEUE.size(), totalDroppedHints);
            }
            return false;
        }

        HINT_QUEUE.addLast(vein);
        HINT_ENQUEUED_KEYS.add(key);
        totalHintsEnqueued++;
        return true;
    }

    // ── Tick processing ───────────────────────────────────────────────────────

    /** Called once per server tick from ChunkScanEventHandler. Server thread only. */
    public static void processTick(MinecraftServer server) {
        lastProcessedScans = processScans(server);
        lastProcessedHints = processHints(server);
        driveSync(server);
    }

    private static int processScans(MinecraftServer server) {
        int limit = CciConfig.SCAN_CHUNKS_PER_TICK.getAsInt();
        int processed = 0;

        while (processed < limit) {
            ScanJob job = SCAN_QUEUE.poll();
            if (job == null) break;
            SCAN_ENQUEUED_KEYS.remove(scanKey(job.dim(), job.cx(), job.cz()));
            SCAN_QUEUE_SIZE.decrementAndGet();
            processed++;
            totalProcessed++;

            ServerLevel level = levelFor(server, job.dim());
            if (level == null) continue;

            LevelChunk chunk = level.getChunkSource().getChunkNow(job.cx(), job.cz());
            if (chunk == null) {
                totalSkippedUnloaded++;
                continue;
            }

            // ── Provider-first scan (cci_core) ───────────────────────────
            // If an authoritative cci_core provider is registered (e.g. cci_world),
            // we trust it exclusively for this chunk and never read COE for it.
            DetectedVein vein;
            if (CciCoreVeinBridge.isProviderModeActive()) {
                CciCoreVeinBridge.ProviderScan ps = CciCoreVeinBridge.lookup(level, job.cx(), job.cz());
                switch (ps.outcome()) {
                    case VEIN -> {
                        vein = ps.detected();
                        totalRawCoeVeinsFound++; // counted as a "found vein" in stats
                    }
                    case NO_VEIN -> {
                        // Authoritative says: no vein here → drop any stale legacy cache entry.
                        WorldVeinData.get(server).removeAt(job.dim(), job.cx(), job.cz());
                        hasPendingSync = true;
                        continue;
                    }
                    case SKIP -> {
                        // PENDING / UNKNOWN / unmapped VEIN → skip, do NOT read COE legacy.
                        continue;
                    }
                    case NO_PROVIDER -> {
                        // Provider disappeared between check and lookup; fall through to COE.
                        Optional<DetectedVein> result = CoeVeinAdapter.detect(level, chunk);
                        if (result.isEmpty()) continue;
                        vein = result.get();
                        totalRawCoeVeinsFound++;
                    }
                    default -> { continue; }
                }
            } else {
                Optional<DetectedVein> result = CoeVeinAdapter.detect(level, chunk);
                if (result.isEmpty()) continue;
                vein = result.get();
                totalRawCoeVeinsFound++;
            }

            if (vein.cciResourceKey() == null) {
                totalUnmappedVeins++;
                continue;
            }

            boolean added = WorldVeinData.get(server).addIfAbsent(vein);
            if (!added) {
                // Vein already cached (e.g., from a previous session when hints were disabled).
                // Try hint backfill now that the chunk is confirmed loaded.
                if (CciConfig.SURFACE_HINTS_ENABLED.get() && CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get()) {
                    tryBackfillHint(server, vein);
                }
                continue;
            }

            totalMappedVeinsAdded++;
            LOGGER.debug("[CCI Radar] Tick scan: new vein {} ({}) at [{},{}] in {}",
                    vein.coeRecipeId(), vein.cciResourceKey(), job.cx(), job.cz(), job.dim());

            if (CciConfig.SURFACE_HINTS_ENABLED.get()
                    && isResourceTierUnlockedByAnyOnlinePlayer(server, vein.cciResourceKey())) {
                enqueueHint(vein);
            }
            hasPendingSync = true;
        }

        return processed;
    }

    /**
     * Tries to enqueue a hint for an already-cached vein.
     * Assumes the chunk is loaded (caller verified via getChunkNow).
     */
    private static void tryBackfillHint(MinecraftServer server, DetectedVein vein) {
        String resKey = vein.cciResourceKey();
        if (SurfaceHintManager.getPebbleBlockId(resKey).equals("none")) return;

        SurfaceHintWorldData hintData = SurfaceHintWorldData.get(server);
        if (hintData.hasPlaced(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey)) return;

        int retryLimit = CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt();
        if (retryLimit > 0 && hintData.getFailedAttempts(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey) >= retryLimit) return;

        int lastAttempt = hintData.getLastAttemptTick(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey);
        int currentTick = server.getTickCount();
        int cooldown    = CciConfig.SURFACE_HINT_RETRY_COOLDOWN_TICKS.getAsInt();
        if (lastAttempt >= 0 && cooldown > 0 && (currentTick - lastAttempt) < cooldown) return;

        if (!isResourceTierUnlockedByAnyOnlinePlayer(server, resKey)) return;

        enqueueHint(vein);
    }

    private static int processHints(MinecraftServer server) {
        if (!CciConfig.SURFACE_HINTS_ENABLED.get()) {
            if (!HINT_QUEUE.isEmpty()) {
                HINT_QUEUE.clear();
                HINT_ENQUEUED_KEYS.clear();
            }
            return 0;
        }
        int limit = CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt();
        if (limit == 0) return 0;

        int processed = 0;

        while (processed < limit && !HINT_QUEUE.isEmpty()) {
            DetectedVein vein = HINT_QUEUE.pollFirst();
            HINT_ENQUEUED_KEYS.remove(hintKey(vein));
            totalHintsProcessed++;
            processed++;

            // Guard: if no online player has this tier unlocked, skip without failure.
            // Periodic backfill / login backfill will re-enqueue when tier is unlocked.
            if (!isResourceTierUnlockedByAnyOnlinePlayer(server, vein.cciResourceKey())) {
                totalHintsSkippedTierLocked++;
                continue;
            }

            ServerLevel level = levelFor(server, vein.dimension());
            if (level == null) {
                totalHintsSkippedUnloaded++;
                continue;
            }

            SurfaceHintPlacementResult result = SurfaceHintManager.placeHintsIfNeeded(level, vein);
            String reason = result.reason();

            if (result.placed() > 0) {
                totalHintsPlacedBlocks += result.placed();
            } else if ("chunk not loaded".equals(reason)) {
                totalHintsSkippedUnloaded++;
                enqueueHint(vein); // re-enqueue to back; dedup prevents spam
            } else if ("no valid positions found".equals(reason)) {
                totalHintsFailedNoPosition++;
            }
            // "already placed", "max retries exceeded …", "surface_hints_enabled=false" → terminal, drop.
        }

        return processed;
    }

    private static void driveSync(MinecraftServer server) {
        if (!hasPendingSync) {
            syncThrottleTicks = 0;
            return;
        }
        syncThrottleTicks++;
        if (syncThrottleTicks >= SYNC_DEBOUNCE_TICKS) {
            VeinSyncManager.syncAllPlayers(server);
            hasPendingSync    = false;
            syncThrottleTicks = 0;
        }
    }

    // ── Hint backfill ─────────────────────────────────────────────────────────

    /**
     * Scans all cached veins in WorldVeinData and enqueues hint jobs for those that are
     * eligible: pebble block registered, not yet placed, not beyond retry limit, not in
     * cooldown, resource tier unlocked by at least one online player, chunk currently loaded.
     *
     * maxChecks caps the number of veins inspected to avoid server stalls.
     * Returns per-pass stats and accumulates into lifetime counters.
     * Server-thread only.
     */
    public static BackfillStats backfillHints(MinecraftServer server, int maxChecks) {
        if (!CciConfig.SURFACE_HINTS_ENABLED.get()) return BackfillStats.EMPTY;

        WorldVeinData        worldData  = WorldVeinData.get(server);
        SurfaceHintWorldData hintData   = SurfaceHintWorldData.get(server);
        int currentTick  = server.getTickCount();
        int cooldown     = CciConfig.SURFACE_HINT_RETRY_COOLDOWN_TICKS.getAsInt();
        int retryLimit   = CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt();

        int checked = 0, enqueued = 0;
        int skippedPlaced = 0, skippedUnloaded = 0, skippedRetry = 0, skippedNoBlock = 0, skippedTierLocked = 0;

        for (DetectedVein vein : worldData.getAll()) {
            if (checked >= maxChecks) break;
            checked++;
            totalHintBackfillChecks++;

            String resKey = vein.cciResourceKey();
            if (resKey == null || SurfaceHintManager.getPebbleBlockId(resKey).equals("none")) {
                skippedNoBlock++;
                totalHintBackfillSkippedNoBlock++;
                continue;
            }

            if (hintData.hasPlaced(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey)) {
                skippedPlaced++;
                totalHintBackfillSkippedPlaced++;
                continue;
            }

            if (retryLimit > 0 && hintData.getFailedAttempts(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey) >= retryLimit) {
                skippedRetry++;
                totalHintBackfillSkippedRetry++;
                continue;
            }

            int lastAttempt = hintData.getLastAttemptTick(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey);
            if (lastAttempt >= 0 && cooldown > 0 && (currentTick - lastAttempt) < cooldown) {
                skippedRetry++;
                totalHintBackfillSkippedRetry++;
                continue;
            }

            if (!isResourceTierUnlockedByAnyOnlinePlayer(server, resKey)) {
                skippedTierLocked++;
                totalHintBackfillSkippedTierLocked++;
                continue;
            }

            ServerLevel level = levelFor(server, vein.dimension());
            if (level == null || level.getChunkSource().getChunkNow(vein.chunkX(), vein.chunkZ()) == null) {
                skippedUnloaded++;
                totalHintBackfillSkippedUnloaded++;
                continue;
            }

            if (enqueueHint(vein)) {
                enqueued++;
                totalHintBackfillEnqueued++;
            }
        }

        return new BackfillStats(checked, enqueued, skippedPlaced, skippedUnloaded, skippedRetry, skippedNoBlock, skippedTierLocked);
    }

    /** Per-pass result from backfillHints. */
    public record BackfillStats(int checked, int enqueued, int skippedPlaced,
                                 int skippedUnloaded, int skippedRetry, int skippedNoBlock,
                                 int skippedTierLocked) {
        public static final BackfillStats EMPTY =
                new BackfillStats(0, 0, 0, 0, 0, 0, 0);
    }

    // ── Tier helpers — server-thread only ─────────────────────────────────────

    private static int tierForResource(String resKey) {
        for (int t = 0; t <= 1; t++) {
            if (CciConfig.getResourcesForTier(t).contains(resKey)) return t;
        }
        return -1;
    }

    /**
     * Returns true if at least one currently online player has the tier for resKey unlocked.
     * Returns false if no players are online, resKey is null, or resKey maps to no tier.
     */
    private static boolean isResourceTierUnlockedByAnyOnlinePlayer(MinecraftServer server, String resKey) {
        if (resKey == null) return false;
        int tier = tierForResource(resKey);
        if (tier < 0) return false;
        PlayerProgressData progress = PlayerProgressData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (progress.getUnlockedTiers(player.getUUID()).contains(tier)) return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerLevel levelFor(MinecraftServer server, ResourceLocation dim) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dim)) return level;
        }
        return null;
    }

    private static String scanKey(ResourceLocation dim, int cx, int cz) {
        return dim + ":" + cx + "," + cz;
    }

    private static String hintKey(DetectedVein vein) {
        return vein.dimension() + ":" + vein.chunkX() + "," + vein.chunkZ() + ":" + vein.cciResourceKey();
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    public static int  pendingScanJobs()                         { return SCAN_QUEUE_SIZE.get(); }
    public static int  pendingHintJobs()                         { return HINT_QUEUE.size(); }
    public static int  lastProcessedScans()                      { return lastProcessedScans; }
    public static int  lastProcessedHints()                      { return lastProcessedHints; }

    public static long totalEnqueuedCount()                      { return totalEnqueued.get(); }
    public static long totalProcessedCount()                     { return totalProcessed; }
    public static long totalSkippedUnloaded()                    { return totalSkippedUnloaded; }
    public static long totalRawCoeVeinsFound()                   { return totalRawCoeVeinsFound; }
    public static long totalMappedVeinsAdded()                   { return totalMappedVeinsAdded; }
    public static long totalUnmappedVeins()                      { return totalUnmappedVeins; }
    public static long totalDroppedScans()                       { return totalDroppedScans.get(); }

    public static long totalHintsEnqueuedCount()                 { return totalHintsEnqueued; }
    public static long totalHintsProcessedCount()                { return totalHintsProcessed; }
    public static long totalHintsPlacedBlocksCount()             { return totalHintsPlacedBlocks; }
    public static long totalHintsSkippedUnloadedCount()          { return totalHintsSkippedUnloaded; }
    public static long totalHintsFailedNoPositionCount()         { return totalHintsFailedNoPosition; }
    public static long totalHintsSkippedTierLockedCount()        { return totalHintsSkippedTierLocked; }

    public static long totalHintBackfillChecksCount()            { return totalHintBackfillChecks; }
    public static long totalHintBackfillEnqueuedCount()          { return totalHintBackfillEnqueued; }
    public static long totalHintBackfillSkippedPlacedCount()     { return totalHintBackfillSkippedPlaced; }
    public static long totalHintBackfillSkippedUnloadedCount()   { return totalHintBackfillSkippedUnloaded; }
    public static long totalHintBackfillSkippedRetryCount()      { return totalHintBackfillSkippedRetry; }
    public static long totalHintBackfillSkippedNoBlockCount()    { return totalHintBackfillSkippedNoBlock; }
    public static long totalHintBackfillSkippedTierLockedCount() { return totalHintBackfillSkippedTierLocked; }

    /** Clears all queues and resets all counters. Call on server start/stop. */
    public static void reset() {
        SCAN_QUEUE.clear();
        SCAN_ENQUEUED_KEYS.clear();
        SCAN_QUEUE_SIZE.set(0);

        HINT_QUEUE.clear();
        HINT_ENQUEUED_KEYS.clear();

        hasPendingSync    = false;
        syncThrottleTicks = 0;
        lastProcessedScans = 0;
        lastProcessedHints = 0;

        totalEnqueued.set(0);
        totalDroppedScans.set(0);
        totalProcessed        = 0;
        totalSkippedUnloaded  = 0;
        totalRawCoeVeinsFound = 0;
        totalMappedVeinsAdded = 0;
        totalUnmappedVeins    = 0;

        totalHintsEnqueued          = 0;
        totalHintsProcessed         = 0;
        totalHintsPlacedBlocks      = 0;
        totalHintsSkippedUnloaded   = 0;
        totalHintsFailedNoPosition  = 0;
        totalHintsSkippedTierLocked = 0;
        totalDroppedHints           = 0;

        totalHintBackfillChecks            = 0;
        totalHintBackfillEnqueued          = 0;
        totalHintBackfillSkippedPlaced     = 0;
        totalHintBackfillSkippedUnloaded   = 0;
        totalHintBackfillSkippedRetry      = 0;
        totalHintBackfillSkippedNoBlock    = 0;
        totalHintBackfillSkippedTierLocked = 0;
    }

    private record ScanJob(ResourceLocation dim, int cx, int cz) {}
}
