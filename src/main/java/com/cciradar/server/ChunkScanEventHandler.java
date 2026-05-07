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
 * Lightweight event handler — does NO heavy work during chunk load or player login.
 *
 * Chunk load:   only enqueues a (dim, cx, cz) key to CciScanQueue (if enabled).
 * Player login: syncs cached veins; optionally enqueues nearby chunks (if auto_scan enabled).
 * Server tick:  drives CciScanQueue.processTick() and periodic background scan enqueueing.
 *
 * All COE reading, block placement, and player syncing happens in the tick worker,
 * rate-limited by scan_chunks_per_tick and hint_placements_per_tick.
 */
@EventBusSubscriber(modid = ColonialResourceRadar.MODID)
public final class ChunkScanEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Chunk load: enqueue the chunk key only. Must not read COE data, place blocks, or sync players.
     */
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
     * Player login: sync current cached veins immediately. Optionally enqueue a radius scan.
     * Does NOT run CoeVeinScanner.scan() synchronously.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;

        VeinSyncManager.syncToPlayer(server, player);

        if (CciConfig.AUTO_SCAN_ENABLED.get()) {
            enqueueRadius(player.serverLevel(), player, CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
        }
    }

    /**
     * Server tick: drive queue processing and periodic background radius scan enqueueing.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        CciScanQueue.processTick(server);

        int interval = CciConfig.SCAN_INTERVAL_TICKS.getAsInt();
        if (CciConfig.AUTO_SCAN_ENABLED.get() && interval > 0 && server.getTickCount() % interval == 0) {
            int radius = CciConfig.MAX_RADIUS_CHUNKS.getAsInt();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                enqueueRadius(player.serverLevel(), player, radius);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CciScanQueue.reset();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CciScanQueue.reset();
    }

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
