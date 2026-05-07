package com.cciradar.server;

import com.cciradar.ColonialResourceRadar;
import com.cciradar.config.CciConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Lightweight event handler — does NO heavy work in chunk-load or player-login callbacks.
 *
 * Chunk load:      only enqueues a (dim, cx, cz) key into the scan queue.
 * Player login:    syncs cached veins; optionally enqueues nearby scan + hint backfill.
 * Server tick:     drives CciScanQueue.processTick() and two separate periodic passes:
 *                    1. COE scan radius re-enqueue  (every scan_interval_ticks)
 *                    2. Surface hint backfill pass  (every surface_hint_backfill_interval_ticks)
 *
 * All COE reading, block placement, and player syncing happens in the tick worker,
 * rate-limited by scan_chunks_per_tick and hint_placements_per_tick.
 */
@EventBusSubscriber(modid = ColonialResourceRadar.MODID)
public final class ChunkScanEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Chunk load: enqueue scan key only. Never reads COE, places blocks, or syncs. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!CciConfig.SCAN_ON_CHUNK_LOAD_ENABLED.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk)) return;
        if (level.getServer() == null) return;

        CciScanQueue.enqueueScan(
                level.dimension().location(),
                event.getChunk().getPos().x,
                event.getChunk().getPos().z);
    }

    /**
     * Player login: sync cached veins, enqueue nearby scan radius, and run a hint backfill
     * pass so that veins already cached near this player get hints enqueued immediately
     * rather than waiting for the next periodic backfill interval.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;

        VeinSyncManager.syncToPlayer(server, player);

        if (CciConfig.AUTO_SCAN_ENABLED.get()) {
            enqueueRadius(player.serverLevel(), player, CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
        }

        // Backfill hints for any cached veins in loaded chunks so the player sees pebbles
        // on join rather than waiting up to backfill_interval_ticks.
        if (CciConfig.SURFACE_HINTS_ENABLED.get() && CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get()) {
            CciScanQueue.backfillHints(server, CciConfig.SURFACE_HINT_BACKFILL_MAX_CHECKS.getAsInt());
        }
    }

    /** Server tick: drive queue processing, periodic scan radius, and periodic hint backfill. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();

        CciScanQueue.processTick(server);

        // ── Periodic COE scan radius re-enqueue ───────────────────────────────
        int scanInterval = CciConfig.SCAN_INTERVAL_TICKS.getAsInt();
        if (CciConfig.AUTO_SCAN_ENABLED.get() && scanInterval > 0 && tick % scanInterval == 0) {
            int radius = CciConfig.MAX_RADIUS_CHUNKS.getAsInt();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                enqueueRadius(player.serverLevel(), player, radius);
            }
        }

        // ── Periodic hint backfill pass (independent interval) ────────────────
        int backfillInterval = CciConfig.SURFACE_HINT_BACKFILL_INTERVAL_TICKS.getAsInt();
        if (CciConfig.SURFACE_HINTS_ENABLED.get()
                && CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get()
                && backfillInterval > 0
                && tick % backfillInterval == 0) {
            CciScanQueue.backfillHints(server, CciConfig.SURFACE_HINT_BACKFILL_MAX_CHECKS.getAsInt());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CciScanQueue.reset();
        logConfig();
    }

    private static void logConfig() {
        boolean hintsEnabled  = CciConfig.SURFACE_HINTS_ENABLED.get();
        int scanChunksPerTick = CciConfig.SCAN_CHUNKS_PER_TICK.getAsInt();
        int hintsPerTick      = CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt();

        LOGGER.info("[CCI Radar] Effective server config:"
                + "\n  auto_scan_enabled:                    {}"
                + "\n  scan_on_chunk_load_enabled:           {}"
                + "\n  surface_hints_enabled:                {}"
                + "\n  surface_hint_backfill_enabled:        {}"
                + "\n  scan_chunks_per_tick:                 {}"
                + "\n  max_pending_scan_jobs:                {}"
                + "\n  scan_interval_ticks:                  {}"
                + "\n  max_radius_chunks:                    {}"
                + "\n  loaded_chunks_only:                   {}"
                + "\n  persist_known_veins:                  {}"
                + "\n  hint_placements_per_tick:             {}"
                + "\n  max_pending_hint_jobs:                {}"
                + "\n  surface_hints_per_chunk_min:          {}"
                + "\n  surface_hints_per_chunk_max:          {}"
                + "\n  surface_hint_attempts_per_chunk:      {}"
                + "\n  surface_hint_retry_limit:             {}"
                + "\n  surface_hint_retry_cooldown_ticks:    {}"
                + "\n  surface_hint_replace_thin_vegetation: {}",
                CciConfig.AUTO_SCAN_ENABLED.get(),
                CciConfig.SCAN_ON_CHUNK_LOAD_ENABLED.get(),
                hintsEnabled,
                CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get(),
                scanChunksPerTick,
                CciConfig.MAX_PENDING_SCAN_JOBS.getAsInt(),
                CciConfig.SCAN_INTERVAL_TICKS.getAsInt(),
                CciConfig.MAX_RADIUS_CHUNKS.getAsInt(),
                CciConfig.LOADED_CHUNKS_ONLY.get(),
                CciConfig.PERSIST_KNOWN_VEINS.get(),
                hintsPerTick,
                CciConfig.MAX_PENDING_HINT_JOBS.getAsInt(),
                CciConfig.SURFACE_HINTS_PER_CHUNK_MIN.getAsInt(),
                CciConfig.SURFACE_HINTS_PER_CHUNK_MAX.getAsInt(),
                CciConfig.SURFACE_HINT_ATTEMPTS_PER_CHUNK.getAsInt(),
                CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt(),
                CciConfig.SURFACE_HINT_RETRY_COOLDOWN_TICKS.getAsInt(),
                CciConfig.SURFACE_HINT_REPLACE_THIN_VEGETATION.get());

        if (scanChunksPerTick <= 2) {
            LOGGER.warn("[CCI Radar] scan_chunks_per_tick={} — scanning may be very slow."
                    + " Edit the active world server config:"
                    + " run/saves/<world>/serverconfig/cci_radar-server.toml."
                    + " Existing worlds do not automatically inherit defaultconfigs.", scanChunksPerTick);
        }
        if (!hintsEnabled) {
            LOGGER.warn("[CCI Radar] surface_hints_enabled=false — automatic pebble placement is disabled."
                    + " Edit the active world server config:"
                    + " run/saves/<world>/serverconfig/cci_radar-server.toml."
                    + " Existing worlds do not automatically inherit defaultconfigs.");
        }
        if (hintsEnabled && hintsPerTick == 0) {
            LOGGER.warn("[CCI Radar] surface_hints_enabled=true but hint_placements_per_tick=0"
                    + " — pebbles will never be placed. Set hint_placements_per_tick >= 1."
                    + " Edit the active world server config:"
                    + " run/saves/<world>/serverconfig/cci_radar-server.toml."
                    + " Existing worlds do not automatically inherit defaultconfigs.");
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CciScanQueue.reset();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void enqueueRadius(ServerLevel level, ServerPlayer player, int radius) {
        int centerCX = SectionPos.blockToSectionCoord(player.blockPosition().getX());
        int centerCZ = SectionPos.blockToSectionCoord(player.blockPosition().getZ());
        int candidates = (2 * radius + 1) * (2 * radius + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                CciScanQueue.enqueueScan(level.dimension().location(), centerCX + dx, centerCZ + dz);
            }
        }
        LOGGER.debug("[CCI Radar] Enqueued up to {} candidates for {} (queue: {})",
                candidates, player.getName().getString(), CciScanQueue.pendingScanJobs());
    }
}
