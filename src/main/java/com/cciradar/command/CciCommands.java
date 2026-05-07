package com.cciradar.command;

import com.cciradar.config.CciConfig;
import com.cciradar.data.ResourceDef;
import com.cciradar.data.ResourceRegistry;
import com.cciradar.server.CciScanQueue;
import com.cciradar.server.PlayerProgressData;
import com.cciradar.server.VeinSyncManager;
import com.cciradar.server.WorldVeinData;
import com.cciradar.server.coe.CoeVeinAdapter;
import com.cciradar.server.coe.CoeVeinScanner;
import com.cciradar.server.coe.DetectedVein;
import com.cciradar.server.surfacehint.SurfaceHintManager;
import com.cciradar.server.surfacehint.SurfaceHintPlacementResult;
import com.cciradar.server.surfacehint.SurfaceHintWorldData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CciCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cci_radar")
                        .then(Commands.literal("unlock_tier")
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                        .executes(ctx -> unlockTierSelf(ctx, IntegerArgumentType.getInteger(ctx, "tier")))
                                )
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                                .executes(ctx -> unlockTierTargets(
                                                        ctx,
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        IntegerArgumentType.getInteger(ctx, "tier")))
                                        )
                                )
                        )
                        .then(Commands.literal("reset")
                                .executes(CciCommands::resetSelf)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> resetTargets(ctx, EntityArgument.getPlayers(ctx, "targets")))
                                )
                        )
                        .then(Commands.literal("resync")
                                .executes(CciCommands::resync)
                        )
                        .then(Commands.literal("debug_fake_veins")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugFakeVeins)
                        )
                        .then(Commands.literal("debug_scan_real")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> debugScanReal(ctx, CciConfig.MAX_RADIUS_CHUNKS.getAsInt()))
                                .then(Commands.argument("radius_chunks", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> debugScanReal(ctx, IntegerArgumentType.getInteger(ctx, "radius_chunks")))
                                )
                        )
                        .then(Commands.literal("debug_coe_chunk")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugCoeChunk)
                        )
                        .then(Commands.literal("debug_visible_veins")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugVisibleVeins)
                        )
                        .then(Commands.literal("debug_place_hints")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugPlaceHints)
                        )
                        .then(Commands.literal("debug_queues")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugQueues)
                        )
                        .then(Commands.literal("debug_enqueue_nearby")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> debugEnqueueNearby(ctx, CciConfig.MAX_RADIUS_CHUNKS.getAsInt()))
                                .then(Commands.argument("radius_chunks", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> debugEnqueueNearby(ctx, IntegerArgumentType.getInteger(ctx, "radius_chunks")))
                                )
                        )
        );
    }

    // ── unlock_tier ──────────────────────────────────────────────────────────

    private static int unlockTierSelf(CommandContext<CommandSourceStack> ctx, int tier)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgressData.get(player.server).unlockTier(player.getUUID(), tier);
        VeinSyncManager.syncToPlayer(player.server, player);
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Tier " + tier + " unlocked."), false);
        return 1;
    }

    private static int unlockTierTargets(CommandContext<CommandSourceStack> ctx,
                                          Collection<ServerPlayer> targets, int tier) {
        PlayerProgressData data = PlayerProgressData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            data.unlockTier(player.getUUID(), tier);
            VeinSyncManager.syncToPlayer(ctx.getSource().getServer(), player);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("[CCI Radar] Tier " + tier + " unlocked for " + targets.size() + " player(s)."),
                true);
        return targets.size();
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    private static int resetSelf(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgressData.get(player.server).resetPlayer(player.getUUID());
        VeinSyncManager.syncToPlayer(player.server, player);
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Progress reset."), false);
        return 1;
    }

    private static int resetTargets(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        PlayerProgressData data = PlayerProgressData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            data.resetPlayer(player.getUUID());
            VeinSyncManager.syncToPlayer(ctx.getSource().getServer(), player);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("[CCI Radar] Progress reset for " + targets.size() + " player(s)."),
                true);
        return targets.size();
    }

    // ── resync ────────────────────────────────────────────────────────────────

    private static int resync(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VeinSyncManager.syncToPlayer(player.server, player);
        int visible = VeinSyncManager.countVisibleForPlayer(player.server, player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[CCI Radar] Resynced " + visible + " visible vein(s) to client."),
                false);
        return visible;
    }

    // ── debug_fake_veins ─────────────────────────────────────────────────────

    private static int debugFakeVeins(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VeinSyncManager.syncAllFakeVeinsToPlayer(player);
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Sent all fake veins (debug)."), false);
        return 1;
    }

    // ── debug_scan_real ──────────────────────────────────────────────────────

    private static int debugScanReal(CommandContext<CommandSourceStack> ctx, int requestedRadius)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        MinecraftServer server = player.server;

        int side = 2 * Math.min(requestedRadius, 64) + 1;
        int candidateCount = side * side;

        if (requestedRadius > 16) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[CCI Radar] WARNING: radius=" + requestedRadius
                            + " scans up to " + candidateCount + " candidate chunks."
                            + " Large radii may cause a brief server hitch."
            ), false);
        }

        CoeVeinScanner.ScanResult result = CoeVeinScanner.scan(level, player.blockPosition(), requestedRadius);

        WorldVeinData worldData = WorldVeinData.get(server);
        List<DetectedVein> newVeins = worldData.addAllIfAbsent(result.detectedVeins());
        int added = newVeins.size();
        int cachedTotal = worldData.getAll().size();

        // debug_scan_real is an explicit OP command — place hints for newly found veins
        // regardless of surface_hints_enabled (debug intent)
        for (DetectedVein vein : newVeins) {
            if (vein.cciResourceKey() != null) {
                SurfaceHintManager.placeHintsForDebug(level, vein);
            }
        }

        VeinSyncManager.syncToPlayer(server, player);
        int visible = VeinSyncManager.countVisibleForPlayer(server, player);

        String unmappedStr = result.unmappedIds().isEmpty()
                ? "none"
                : result.unmappedIds().stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_scan_real:"
                        + "\n  dim: " + result.dimension()
                        + "\n  player_chunk: [" + result.centerCX() + ", " + result.centerCZ() + "]"
                        + "\n  requested_radius_chunks: " + result.requestedRadius()
                        + "\n  effective_radius_chunks: " + result.effectiveRadius()
                        + "\n  candidate_chunks: " + result.candidateChunks()
                        + "\n  loaded_chunks_checked: " + result.loadedChunksChecked()
                        + "\n  unloaded_chunks_skipped: " + result.unloadedChunksSkipped()
                        + "\n  raw_coe_veins_found: " + result.veinsFound()
                        + "\n  mapped_veins: " + result.mappedVeins()
                        + "\n  unmapped_veins: " + result.unmappedVeins()
                        + "\n  unmapped_ids: " + unmappedStr
                        + "\n  added_to_world_cache: " + added
                        + "\n  cached_real_veins_total: " + cachedTotal
                        + "\n  visible_veins_for_player: " + visible
                        + "\n  sync_sent: yes"
        ), false);

        return result.veinsFound();
    }

    // ── debug_coe_chunk ──────────────────────────────────────────────────────

    private static int debugCoeChunk(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        MinecraftServer server = player.server;
        LevelChunk chunk = level.getChunkAt(player.blockPosition());

        Optional<DetectedVein> result = CoeVeinAdapter.detect(level, chunk);

        if (result.isEmpty()) {
            int cx = chunk.getPos().x;
            int cz = chunk.getPos().z;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[CCI Radar] debug_coe_chunk:"
                            + "\n  dim: " + level.dimension().location()
                            + "\n  chunk: [" + cx + ", " + cz + "]"
                            + "\n  raw COE data: no"
                            + "\n  (no vein in this chunk)"
            ), false);
            return 0;
        }

        DetectedVein vein = result.get();
        WorldVeinData worldData = WorldVeinData.get(server);
        boolean added = worldData.addIfAbsent(vein);
        int tier = getTierForResourceKey(vein.cciResourceKey());
        boolean tierUnlocked = tier >= 0 &&
                PlayerProgressData.get(server).getUnlockedTiers(player.getUUID()).contains(tier);
        boolean markerVisible = vein.cciResourceKey() != null && tierUnlocked;

        // debug_coe_chunk is an explicit OP command — always try to place hints
        if (added && vein.cciResourceKey() != null) {
            SurfaceHintManager.placeHintsForDebug(level, vein);
        }

        if (markerVisible) {
            VeinSyncManager.syncToPlayer(server, player);
        }

        String mappedKey = vein.cciResourceKey() != null ? vein.cciResourceKey() : "(unmapped)";
        String tierStr = tier >= 0 ? String.valueOf(tier) : "unknown";
        String addedStr = added ? "yes (newly added)" : "already present";
        String markerId = stableMarkerId(vein.dimension(), vein.chunkX(), vein.chunkZ());
        boolean hintsPlaced = vein.cciResourceKey() != null
                && SurfaceHintWorldData.get(server).hasPlaced(vein.dimension(), vein.chunkX(), vein.chunkZ(), vein.cciResourceKey());

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_coe_chunk:"
                        + "\n  dim: " + vein.dimension()
                        + "\n  chunk: [" + vein.chunkX() + ", " + vein.chunkZ() + "]"
                        + "\n  raw COE data: yes"
                        + "\n  raw recipe id: " + vein.coeRecipeId()
                        + "\n  CCI resource key: " + mappedKey
                        + "\n  tier: " + tierStr
                        + "\n  player tier unlocked: " + (tierUnlocked ? "yes" : "no")
                        + "\n  added to WorldVeinCache: " + addedStr
                        + "\n  sync sent: " + (markerVisible ? "yes" : "no (tier locked)")
                        + "\n  marker visible: " + (markerVisible ? "yes" : "no")
                        + "\n  marker id: " + markerId
                        + "\n  surface_hints_placed: " + (hintsPlaced ? "yes" : "no")
        ), false);

        return 1;
    }

    // ── debug_visible_veins ───────────────────────────────────────────────────

    private static int debugVisibleVeins(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;

        Set<Integer> unlocked = PlayerProgressData.get(server).getUnlockedTiers(player.getUUID());
        List<DetectedVein> allVeins = WorldVeinData.get(server).getAll();
        boolean fakeActive = VeinSyncManager.isFakeVeinsActive(player.getUUID());

        List<DetectedVein> visible = allVeins.stream()
                .filter(v -> v.cciResourceKey() != null)
                .filter(v -> VeinSyncManager.isResourceUnlocked(v.cciResourceKey(), unlocked))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("[CCI Radar] debug_visible_veins:");
        sb.append("\n  current_dim: ").append(player.serverLevel().dimension().location());
        sb.append("\n  player: ").append(player.getName().getString());
        sb.append("\n  unlocked_tiers: ").append(unlocked.isEmpty() ? "none" : unlocked.toString());
        sb.append("\n  cached_real_veins_total: ").append(allVeins.size());
        sb.append("\n  visible_real_veins_total: ").append(visible.size());
        sb.append("\n  fake_debug_veins_active: ").append(fakeActive ? "yes" : "no");

        if (!visible.isEmpty()) {
            int limit = Math.min(visible.size(), 20);
            sb.append("\n  --- first ").append(limit).append(" visible veins ---");
            for (int i = 0; i < limit; i++) {
                DetectedVein v = visible.get(i);
                int tier = getTierForResourceKey(v.cciResourceKey());
                ResourceDef def = ResourceRegistry.get(v.cciResourceKey());
                String label = def != null ? def.label() : v.cciResourceKey();
                String markerId = stableMarkerId(v.dimension(), v.chunkX(), v.chunkZ());
                sb.append("\n  [").append(i + 1).append("] ")
                  .append(v.cciResourceKey())
                  .append(" | ").append(label)
                  .append(" | tier ").append(tier >= 0 ? tier : "?")
                  .append(" | ").append(v.dimension())
                  .append(" | chunk [").append(v.chunkX()).append(", ").append(v.chunkZ()).append("]")
                  .append(" | id: ").append(markerId);
            }
            if (visible.size() > 20) {
                sb.append("\n  ... and ").append(visible.size() - 20).append(" more");
            }
        }

        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return visible.size();
    }

    // ── debug_place_hints ────────────────────────────────────────────────────

    private static int debugPlaceHints(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level   = player.serverLevel();
        MinecraftServer server = player.server;

        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        ResourceLocation dim = level.dimension().location();

        Optional<DetectedVein> veinOpt = WorldVeinData.get(server).getAll().stream()
                .filter(v -> v.chunkX() == cx && v.chunkZ() == cz && v.dimension().equals(dim))
                .findFirst();

        if (veinOpt.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[CCI Radar] debug_place_hints:"
                            + "\n  dim: " + dim
                            + "\n  chunk: [" + cx + ", " + cz + "]"
                            + "\n  cached vein: none"
                            + "\n  Tip: run /cci_radar debug_coe_chunk to add this chunk to cache"
            ), false);
            return 0;
        }

        DetectedVein vein = veinOpt.get();
        String resKey = vein.cciResourceKey() != null ? vein.cciResourceKey() : "(unmapped)";
        // Always runs — debug command bypasses surface_hints_enabled config
        SurfaceHintPlacementResult result = SurfaceHintManager.placeHintsForDebug(level, vein);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_place_hints:"
                        + "\n  dim: " + dim
                        + "\n  chunk: [" + cx + ", " + cz + "]"
                        + "\n  resource: " + resKey
                        + "\n  pebble_block: " + result.pebbleBlockId()
                        + "\n  already_placed: " + (result.alreadyPlaced() ? "yes" : "no")
                        + "\n  previous_failed_attempts: " + result.previousFailedAttempts() + "/" + SurfaceHintWorldData.MAX_FAILED_ATTEMPTS
                        + "\n  attempted_positions: " + result.attempted()
                        + "\n  placed: " + result.placed()
                        + "\n  reason: " + result.reason()
        ), false);

        return result.placed();
    }

    // ── debug_queues ──────────────────────────────────────────────────────────

    private static int debugQueues(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_queues:"
                        + "\n  auto_scan_enabled:              " + CciConfig.AUTO_SCAN_ENABLED.get()
                        + "\n  scan_on_chunk_load_enabled:     " + CciConfig.SCAN_ON_CHUNK_LOAD_ENABLED.get()
                        + "\n  surface_hints_enabled:          " + CciConfig.SURFACE_HINTS_ENABLED.get()
                        + "\n  pending_scan_jobs:              " + CciScanQueue.pendingScanJobs()
                        + "\n  pending_hint_jobs:              " + CciScanQueue.pendingHintJobs()
                        + "\n  scan_chunks_per_tick:           " + CciConfig.SCAN_CHUNKS_PER_TICK.getAsInt()
                        + "\n  hint_placements_per_tick:       " + CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt()
                        + "\n  last_tick_scan_jobs_processed:  " + CciScanQueue.lastProcessedScans()
                        + "\n  total_scan_jobs_enqueued:       " + CciScanQueue.totalEnqueuedCount()
                        + "\n  total_scan_jobs_processed:      " + CciScanQueue.totalProcessedCount()
                        + "\n  total_scan_jobs_skipped_unloaded: " + CciScanQueue.totalSkippedUnloaded()
                        + "\n  total_raw_coe_veins_found:      " + CciScanQueue.totalRawCoeVeinsFound()
                        + "\n  total_mapped_veins_added:       " + CciScanQueue.totalMappedVeinsAdded()
                        + "\n  total_unmapped_veins:           " + CciScanQueue.totalUnmappedVeins()
                        + "\n  total_scan_jobs_dropped:        " + CciScanQueue.totalDroppedScans()
        ), false);
        return 1;
    }

    // ── debug_enqueue_nearby ──────────────────────────────────────────────────

    private static int debugEnqueueNearby(CommandContext<CommandSourceStack> ctx, int radius)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        int centerCX = SectionPos.blockToSectionCoord(player.blockPosition().getX());
        int centerCZ = SectionPos.blockToSectionCoord(player.blockPosition().getZ());

        int found = 0;
        int added = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Only enqueue chunks that are currently loaded
                LevelChunk chunk = level.getChunkSource().getChunkNow(centerCX + dx, centerCZ + dz);
                if (chunk == null) continue;
                found++;
                long before = CciScanQueue.totalEnqueuedCount();
                CciScanQueue.enqueueScan(level.dimension().location(), centerCX + dx, centerCZ + dz);
                if (CciScanQueue.totalEnqueuedCount() > before) added++;
            }
        }

        final int skipped = found - added;
        final int queueAfter = CciScanQueue.pendingScanJobs();
        final int finalFound = found;
        final int finalAdded = added;

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_enqueue_nearby:"
                        + "\n  radius: " + radius
                        + "\n  loaded_chunks_found: " + finalFound
                        + "\n  jobs_added_to_queue: " + finalAdded
                        + "\n  jobs_skipped_already_pending: " + skipped
                        + "\n  queue_pending_after: " + queueAfter
                        + "\n  Tip: run /cci_radar debug_queues after a few seconds to see processing progress"
        ), false);
        return added;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    static int getTierForResourceKey(String cciKey) {
        if (cciKey == null) return -1;
        for (int tier = 0; tier <= 1; tier++) {
            if (CciConfig.getResourcesForTier(tier).contains(cciKey)) return tier;
        }
        return -1;
    }

    static String stableMarkerId(net.minecraft.resources.ResourceLocation dim, int cx, int cz) {
        return "cci_vein:" + dim + ":" + cx + "," + cz;
    }
}
