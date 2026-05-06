package com.cciradar.server;

import com.cciradar.ColonialResourceRadar;
import com.cciradar.config.CciConfig;
import com.cciradar.server.coe.CoeVeinAdapter;
import com.cciradar.server.coe.CoeVeinScanner;
import com.cciradar.server.coe.DetectedVein;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Automatic chunk scanning — fires on chunk load, player login, and periodic server tick.
 * Never forces chunk generation (uses getChunkNow/LevelChunk guard).
 */
@EventBusSubscriber(modid = ColonialResourceRadar.MODID)
public final class ChunkScanEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Scans each chunk as it becomes fully loaded. Only adds to WorldVeinData if a vein is found
     * and the chunk was not already known. Syncs all online players if a new vein is discovered.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;

        Optional<DetectedVein> result = CoeVeinAdapter.detect(level, chunk);
        result.ifPresent(vein -> {
            boolean added = WorldVeinData.get(server).addIfAbsent(vein);
            if (added) {
                LOGGER.debug("[CCI Radar] New vein on chunk load: {} at [{},{}] in {}",
                        vein.coeRecipeId(), vein.chunkX(), vein.chunkZ(), vein.dimension());
                VeinSyncManager.syncAllPlayers(server);
            }
        });
    }

    /**
     * On player login, scans loaded chunks in the configured radius around their spawn position.
     * Syncs all online players if any new veins are found, otherwise syncs just the logging-in player.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();

        CoeVeinScanner.ScanResult result = CoeVeinScanner.scan(level, player.blockPosition());
        int added = WorldVeinData.get(server).addAllIfAbsent(result.detectedVeins());

        if (added > 0) {
            LOGGER.info("[CCI Radar] Login scan for {}: {} new vein(s) added to cache (total: {})",
                    player.getName().getString(), added, WorldVeinData.get(server).getAll().size());
            VeinSyncManager.syncAllPlayers(server);
        } else {
            VeinSyncManager.syncToPlayer(server, player);
        }
    }

    /**
     * Periodic background scan around each online player. Only active when
     * scan_interval_ticks > 0 in server config. Scans a small radius each interval
     * to catch newly loaded chunks without per-chunk-load overhead on busy servers.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int interval = CciConfig.SCAN_INTERVAL_TICKS.getAsInt();
        if (interval <= 0) return;

        MinecraftServer server = event.getServer();
        if (server.getTickCount() % interval != 0) return;

        boolean anyNew = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            CoeVeinScanner.ScanResult result = CoeVeinScanner.scan(level, player.blockPosition(), 4);
            int added = WorldVeinData.get(server).addAllIfAbsent(result.detectedVeins());
            if (added > 0) {
                LOGGER.debug("[CCI Radar] Tick scan near {}: {} new vein(s)", player.getName().getString(), added);
                anyNew = true;
            }
        }

        if (anyNew) {
            VeinSyncManager.syncAllPlayers(server);
        }
    }
}
