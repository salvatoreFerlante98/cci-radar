package com.cciradar.server;

import com.cciradar.config.CciConfig;
import com.cciradar.server.coe.CoeVeinAdapter;
import com.cciradar.server.coe.DetectedVein;
import com.cciradar.server.surfacehint.SurfaceHintManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * enqueueScan() is thread-safe: ChunkEvent.Load may fire from NeoForge world-gen threads.
 * processTick() runs only on the server thread (called from ServerTickEvent.Post).
 *
 * Scan jobs: (dim, cx, cz) — deduplicated so the same chunk is never queued twice.
 * Hint jobs: DetectedVein — processed in order, at most hint_placements_per_tick per tick.
 *
 * Player syncing is debounced: at most one syncAllPlayers() call per SYNC_DEBOUNCE_TICKS ticks
 * even when many veins are found in rapid succession.
 */
public final class CciScanQueue {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SYNC_DEBOUNCE_TICKS = 20; // sync at most once per second

    // ── Scan queue — thread-safe (enqueueScan may run off server thread) ───────

    private static final Queue<ScanJob>  SCAN_QUEUE         = new ConcurrentLinkedQueue<>();
    private static final Set<String>     SCAN_ENQUEUED_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger   SCAN_QUEUE_SIZE    = new AtomicInteger();

    // ── Hint queue — server-thread only ──────────────────────────────────────

    private static final Deque<DetectedVein> HINT_QUEUE = new ArrayDeque<>();

    // ── Sync throttle — server-thread only ───────────────────────────────────

    private static boolean hasPendingSync    = false;
    private static int     syncThrottleTicks = 0;

    // ── Per-tick stats — server-thread only ──────────────────────────────────

    private static int lastProcessedScans = 0;
    private static int lastProcessedHints = 0;

    // ── Lifetime counters — totalEnqueued/totalDroppedScans written off-thread ─

    private static final AtomicLong totalEnqueued         = new AtomicLong();
    private static final AtomicLong totalDroppedScans     = new AtomicLong();
    private static       long       totalProcessed        = 0;
    private static       long       totalSkippedUnloaded  = 0;
    private static       long       totalRawCoeVeinsFound = 0;
    private static       long       totalMappedVeinsAdded = 0;
    private static       long       totalUnmappedVeins    = 0;
    private static       long       totalDroppedHints     = 0;

    private CciScanQueue() {}

    // ── Enqueue ───────────────────────────────────────────────────────────────

    /**
     * Enqueue a chunk for COE scanning.
     * Thread-safe — may be called from world-gen threads via ChunkEvent.Load.
     * Deduplicated: same (dim, cx, cz) is never in the queue more than once.
     */
    public static void enqueueScan(ResourceLocation dim, int cx, int cz) {
        String key = scanKey(dim, cx, cz);
        // ConcurrentHashMap.add() is atomic — only one caller wins per key
        if (!SCAN_ENQUEUED_KEYS.add(key)) return;

        int max = CciConfig.MAX_PENDING_SCAN_JOBS.getAsInt();
        if (SCAN_QUEUE_SIZE.get() >= max) {
            SCAN_ENQUEUED_KEYS.remove(key); // undo — queue full
            long dropped = totalDroppedScans.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                LOGGER.warn("[CCI Radar] Scan queue full ({} pending), dropped {} total — increase max_pending_scan_jobs or reduce scan radius",
                        SCAN_QUEUE_SIZE.get(), dropped);
            }
            return;
        }

        SCAN_QUEUE.add(new ScanJob(dim, cx, cz));
        SCAN_QUEUE_SIZE.incrementAndGet();
        totalEnqueued.incrementAndGet();
    }

    /**
     * Enqueue a detected vein for surface hint placement.
     * Call only from server thread.
     */
    public static void enqueueHint(DetectedVein vein) {
        int max = CciConfig.MAX_PENDING_HINT_JOBS.getAsInt();
        if (HINT_QUEUE.size() >= max) {
            totalDroppedHints++;
            if (totalDroppedHints == 1 || totalDroppedHints % 200 == 0) {
                LOGGER.warn("[CCI Radar] Hint queue full ({} pending), dropped {} total",
                        HINT_QUEUE.size(), totalDroppedHints);
            }
            return;
        }
        HINT_QUEUE.addLast(vein);
    }

    // ── Tick processing ───────────────────────────────────────────────────────

    /**
     * Called once per server tick from ChunkScanEventHandler.onServerTick.
     * Server thread only.
     */
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

            // getChunkNow returns null if not fully loaded — never forces generation
            LevelChunk chunk = level.getChunkSource().getChunkNow(job.cx(), job.cz());
            if (chunk == null) {
                totalSkippedUnloaded++;
                continue;
            }

            Optional<DetectedVein> result = CoeVeinAdapter.detect(level, chunk);
            if (result.isEmpty()) continue;

            DetectedVein vein = result.get();
            totalRawCoeVeinsFound++;

            if (vein.cciResourceKey() == null) {
                totalUnmappedVeins++;
                continue;
            }

            boolean added = WorldVeinData.get(server).addIfAbsent(vein);
            if (!added) continue;

            totalMappedVeinsAdded++;
            LOGGER.debug("[CCI Radar] Tick scan: new vein {} ({}) at [{},{}] in {}",
                    vein.coeRecipeId(), vein.cciResourceKey(), job.cx(), job.cz(), job.dim());

            if (CciConfig.SURFACE_HINTS_ENABLED.get()) {
                enqueueHint(vein);
            }
            hasPendingSync = true;
        }

        return processed;
    }

    private static int processHints(MinecraftServer server) {
        if (!CciConfig.SURFACE_HINTS_ENABLED.get()) {
            if (!HINT_QUEUE.isEmpty()) HINT_QUEUE.clear();
            return 0;
        }
        int limit = CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt();
        if (limit == 0) return 0;
        int processed = 0;
        while (processed < limit && !HINT_QUEUE.isEmpty()) {
            DetectedVein vein = HINT_QUEUE.pollFirst();
            processed++;
            ServerLevel level = levelFor(server, vein.dimension());
            if (level == null) continue;
            SurfaceHintManager.placeHintsIfNeeded(level, vein);
        }
        return processed;
    }

    /** Debounced: syncs all players at most once per SYNC_DEBOUNCE_TICKS ticks. */
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

    // ── Diagnostics ───────────────────────────────────────────────────────────

    public static int  pendingScanJobs()         { return SCAN_QUEUE_SIZE.get(); }
    public static int  pendingHintJobs()         { return HINT_QUEUE.size(); }
    public static int  lastProcessedScans()      { return lastProcessedScans; }
    public static int  lastProcessedHints()      { return lastProcessedHints; }
    public static long totalEnqueuedCount()      { return totalEnqueued.get(); }
    public static long totalProcessedCount()     { return totalProcessed; }
    public static long totalSkippedUnloaded()    { return totalSkippedUnloaded; }
    public static long totalRawCoeVeinsFound()   { return totalRawCoeVeinsFound; }
    public static long totalMappedVeinsAdded()   { return totalMappedVeinsAdded; }
    public static long totalUnmappedVeins()      { return totalUnmappedVeins; }
    public static long totalDroppedScans()       { return totalDroppedScans.get(); }
    public static long totalDroppedHints()       { return totalDroppedHints; }

    /** Clears all queues and resets all counters. Call on server start/stop. */
    public static void reset() {
        SCAN_QUEUE.clear();
        SCAN_ENQUEUED_KEYS.clear();
        SCAN_QUEUE_SIZE.set(0);
        HINT_QUEUE.clear();
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
        totalDroppedHints     = 0;
    }

    private record ScanJob(ResourceLocation dim, int cx, int cz) {}
}
