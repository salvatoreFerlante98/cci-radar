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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
                        .then(Commands.literal("debug_hint_queues")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugHintQueues)
                        )
                        .then(Commands.literal("debug_enqueue_hints")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugEnqueueHints)
                        )
                        .then(Commands.literal("debug_enqueue_nearby")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> debugEnqueueNearby(ctx, CciConfig.MAX_RADIUS_CHUNKS.getAsInt()))
                                .then(Commands.argument("radius_chunks", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> debugEnqueueNearby(ctx, IntegerArgumentType.getInteger(ctx, "radius_chunks")))
                                )
                        )
                        .then(Commands.literal("debug_resource_distribution")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugResourceDistribution)
                        )
                        .then(Commands.literal("debug_config")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugConfig)
                        )
        );
    }

    // ── unlock_tier ──────────────────────────────────────────────────────────

    private static int unlockTierSelf(CommandContext<CommandSourceStack> ctx, int tier)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;
        PlayerProgressData.get(server).unlockTier(player.getUUID(), tier);
        VeinSyncManager.syncToPlayer(server, player);
        if (CciConfig.SURFACE_HINTS_ENABLED.get() && CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get()) {
            CciScanQueue.backfillHints(server, CciConfig.SURFACE_HINT_BACKFILL_MAX_CHECKS.getAsInt());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Tier " + tier + " unlocked."), false);
        return 1;
    }

    private static int unlockTierTargets(CommandContext<CommandSourceStack> ctx,
                                          Collection<ServerPlayer> targets, int tier) {
        MinecraftServer server = ctx.getSource().getServer();
        PlayerProgressData data = PlayerProgressData.get(server);
        for (ServerPlayer player : targets) {
            data.unlockTier(player.getUUID(), tier);
            VeinSyncManager.syncToPlayer(server, player);
        }
        if (CciConfig.SURFACE_HINTS_ENABLED.get() && CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get()) {
            CciScanQueue.backfillHints(server, CciConfig.SURFACE_HINT_BACKFILL_MAX_CHECKS.getAsInt());
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

        // Per-recipe and per-resource breakdowns from this scan session
        Set<Integer> unlocked = PlayerProgressData.get(server).getUnlockedTiers(player.getUUID());
        Map<String, Long> byRecipeId = new TreeMap<>();
        Map<String, Long> byResourceKey = new TreeMap<>();
        Map<String, Long> visibleByResourceKey = new TreeMap<>();

        for (DetectedVein vein : result.detectedVeins()) {
            String recipeStr = vein.coeRecipeId() != null ? vein.coeRecipeId().toString() : "(null)";
            byRecipeId.merge(recipeStr, 1L, Long::sum);
            if (vein.cciResourceKey() != null) {
                byResourceKey.merge(vein.cciResourceKey(), 1L, Long::sum);
                if (VeinSyncManager.isResourceUnlocked(vein.cciResourceKey(), unlocked)) {
                    visibleByResourceKey.merge(vein.cciResourceKey(), 1L, Long::sum);
                }
            }
        }

        String unmappedStr = result.unmappedIds().isEmpty()
                ? "none"
                : result.unmappedIds().stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("[CCI Radar] debug_scan_real:");
        sb.append("\n  dim: ").append(result.dimension());
        sb.append("\n  player_chunk: [").append(result.centerCX()).append(", ").append(result.centerCZ()).append("]");
        sb.append("\n  requested_radius_chunks: ").append(result.requestedRadius());
        sb.append("\n  effective_radius_chunks: ").append(result.effectiveRadius());
        sb.append("\n  candidate_chunks: ").append(result.candidateChunks());
        sb.append("\n  loaded_chunks_checked: ").append(result.loadedChunksChecked());
        sb.append("\n  unloaded_chunks_skipped: ").append(result.unloadedChunksSkipped());
        sb.append("\n  raw_coe_veins_found: ").append(result.veinsFound());
        sb.append("\n  mapped_veins: ").append(result.mappedVeins());
        sb.append("\n  unmapped_veins: ").append(result.unmappedVeins());
        sb.append("\n  unmapped_ids: ").append(unmappedStr);
        sb.append("\n  added_to_world_cache: ").append(added);
        sb.append("\n  cached_real_veins_total: ").append(cachedTotal);
        sb.append("\n  visible_veins_for_player: ").append(visible);
        sb.append("\n  sync_sent: yes");
        sb.append("\n  --- raw_coe_veins_found_by_recipe_id ---");
        if (byRecipeId.isEmpty()) {
            sb.append("\n  (none)");
        } else {
            for (Map.Entry<String, Long> e : byRecipeId.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        sb.append("\n  --- mapped_veins_by_resource_key ---");
        if (byResourceKey.isEmpty()) {
            sb.append("\n  (none)");
        } else {
            for (Map.Entry<String, Long> e : byResourceKey.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        sb.append("\n  --- visible_veins_by_resource_key ---");
        if (visibleByResourceKey.isEmpty()) {
            sb.append("\n  (none — check unlocked tiers)");
        } else {
            for (Map.Entry<String, Long> e : visibleByResourceKey.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }

        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);

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
                        + "\n  surface_hints_enabled: " + CciConfig.SURFACE_HINTS_ENABLED.get()
                        + "\n  chunk_loaded: " + (!"chunk not loaded".equals(result.reason()) ? "yes" : "no")
                        + "\n  already_placed: " + (result.alreadyPlaced() ? "yes" : "no")
                        + "\n  failed_attempts: " + result.previousFailedAttempts() + "/" + CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt()
                        + "\n  attempted_positions: " + result.attempted()
                        + "\n  valid_positions: " + result.placed()
                        + "\n  placed_count: " + result.placed()
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

    // ── debug_hint_queues ─────────────────────────────────────────────────────

    private static int debugHintQueues(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_hint_queues:"
                        + "\n  surface_hints_enabled:                   " + CciConfig.SURFACE_HINTS_ENABLED.get()
                        + "\n  surface_hint_backfill_enabled:           " + CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get()
                        + "\n  pending_hint_jobs:                       " + CciScanQueue.pendingHintJobs()
                        + "\n  hint_placements_per_tick:                " + CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt()
                        + "\n  max_pending_hint_jobs:                   " + CciConfig.MAX_PENDING_HINT_JOBS.getAsInt()
                        + "\n  total_hint_jobs_enqueued:                " + CciScanQueue.totalHintsEnqueuedCount()
                        + "\n  total_hint_jobs_processed:               " + CciScanQueue.totalHintsProcessedCount()
                        + "\n  total_placed_blocks:                     " + CciScanQueue.totalHintsPlacedBlocksCount()
                        + "\n  total_skipped_unloaded:                  " + CciScanQueue.totalHintsSkippedUnloadedCount()
                        + "\n  total_failed_no_valid_position:          " + CciScanQueue.totalHintsFailedNoPositionCount()
                        + "\n  total_hints_skipped_tier_locked:         " + CciScanQueue.totalHintsSkippedTierLockedCount()
                        + "\n  --- backfill stats ---"
                        + "\n  total_hint_backfill_checks:              " + CciScanQueue.totalHintBackfillChecksCount()
                        + "\n  total_hint_backfill_enqueued:            " + CciScanQueue.totalHintBackfillEnqueuedCount()
                        + "\n  total_hint_backfill_skipped_placed:      " + CciScanQueue.totalHintBackfillSkippedPlacedCount()
                        + "\n  total_hint_backfill_skipped_unloaded:    " + CciScanQueue.totalHintBackfillSkippedUnloadedCount()
                        + "\n  total_hint_backfill_skipped_retry:       " + CciScanQueue.totalHintBackfillSkippedRetryCount()
                        + "\n  total_hint_backfill_skipped_no_block:    " + CciScanQueue.totalHintBackfillSkippedNoBlockCount()
                        + "\n  total_hint_backfill_skipped_tier_locked: " + CciScanQueue.totalHintBackfillSkippedTierLockedCount()
        ), false);
        return 1;
    }

    // ── debug_enqueue_hints ───────────────────────────────────────────────────

    private static int debugEnqueueHints(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;

        // Check all cached veins (no per-player filter — backfill is global).
        // Use a large cap so this command is a genuine full scan.
        CciScanQueue.BackfillStats stats = CciScanQueue.backfillHints(server, Integer.MAX_VALUE);
        int pendingAfter = CciScanQueue.pendingHintJobs();

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[CCI Radar] debug_enqueue_hints:"
                        + "\n  surface_hints_enabled:        " + CciConfig.SURFACE_HINTS_ENABLED.get()
                        + "\n  cached_veins_checked:         " + stats.checked()
                        + "\n  jobs_enqueued:                " + stats.enqueued()
                        + "\n  already_placed_skipped:       " + stats.skippedPlaced()
                        + "\n  retry_cooldown_skipped:       " + stats.skippedRetry()
                        + "\n  chunk_unloaded_skipped:       " + stats.skippedUnloaded()
                        + "\n  no_pebble_block_skipped:      " + stats.skippedNoBlock()
                        + "\n  tier_locked_skipped:          " + stats.skippedTierLocked()
                        + "\n  pending_hint_jobs_after:      " + pendingAfter
                        + "\n  Tip: run /cci_radar debug_hint_queues in ~30 s to see processing progress"
        ), false);
        return stats.enqueued();
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

    // ── debug_resource_distribution ──────────────────────────────────────────

    private static int debugResourceDistribution(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;

        List<DetectedVein> allVeins = WorldVeinData.get(server).getAll();
        Set<Integer> unlocked = PlayerProgressData.get(server).getUnlockedTiers(player.getUUID());

        Map<String, Long> byResourceKey = new TreeMap<>();
        Map<String, Long> byRecipeId = new TreeMap<>();
        Map<String, Long> visibleByResourceKey = new TreeMap<>();
        Map<String, Long> unmappedIds = new TreeMap<>();

        for (DetectedVein vein : allVeins) {
            String recipeStr = vein.coeRecipeId() != null ? vein.coeRecipeId().toString() : "(null)";
            byRecipeId.merge(recipeStr, 1L, Long::sum);
            if (vein.cciResourceKey() != null) {
                byResourceKey.merge(vein.cciResourceKey(), 1L, Long::sum);
                if (VeinSyncManager.isResourceUnlocked(vein.cciResourceKey(), unlocked)) {
                    visibleByResourceKey.merge(vein.cciResourceKey(), 1L, Long::sum);
                }
            } else {
                unmappedIds.merge(recipeStr, 1L, Long::sum);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[CCI Radar] debug_resource_distribution:");
        sb.append("\n  current_dim: ").append(player.serverLevel().dimension().location());
        sb.append("\n  player: ").append(player.getName().getString());
        sb.append("\n  unlocked_tiers: ").append(unlocked.isEmpty() ? "none" : unlocked.toString());
        sb.append("\n  total_cached_veins: ").append(allVeins.size());

        sb.append("\n  --- resources by tier ---");
        for (int t = 0; t <= 1; t++) {
            List<String> tierResources = CciConfig.getResourcesForTier(t);
            boolean tierUnlocked = unlocked.contains(t);
            sb.append("\n  tier ").append(t).append(" (").append(tierUnlocked ? "UNLOCKED" : "locked").append("): ")
              .append(tierResources.isEmpty() ? "(empty)" : String.join(", ", tierResources));
        }

        sb.append("\n  --- cached veins by resource key ---");
        if (byResourceKey.isEmpty()) {
            sb.append("\n  (none)");
        } else {
            for (Map.Entry<String, Long> e : byResourceKey.entrySet()) {
                int tier = getTierForResourceKey(e.getKey());
                boolean vis = tier >= 0 && unlocked.contains(tier);
                sb.append("\n  ").append(e.getKey())
                  .append(": ").append(e.getValue())
                  .append(" (tier ").append(tier >= 0 ? tier : "?").append(", ")
                  .append(vis ? "visible" : "locked").append(")");
            }
        }

        sb.append("\n  --- visible veins for ").append(player.getName().getString()).append(" ---");
        if (visibleByResourceKey.isEmpty()) {
            sb.append("\n  (none — unlock a tier to see veins)");
        } else {
            for (Map.Entry<String, Long> e : visibleByResourceKey.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }

        sb.append("\n  --- cached veins by raw COE recipe id ---");
        if (byRecipeId.isEmpty()) {
            sb.append("\n  (none)");
        } else {
            for (Map.Entry<String, Long> e : byRecipeId.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }

        sb.append("\n  --- unmapped recipe ids ---");
        if (unmappedIds.isEmpty()) {
            sb.append("\n  none");
        } else {
            for (Map.Entry<String, Long> e : unmappedIds.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }

        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return allVeins.size();
    }

    // ── debug_config ─────────────────────────────────────────────────────────

    static int debugConfig(CommandContext<CommandSourceStack> ctx) {
        int scanChunksPerTick  = CciConfig.SCAN_CHUNKS_PER_TICK.getAsInt();
        boolean hintsEnabled   = CciConfig.SURFACE_HINTS_ENABLED.get();
        int hintsPertick       = CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt();

        StringBuilder sb = new StringBuilder();
        sb.append("[CCI Radar] debug_config (effective runtime values):");
        sb.append("\n  auto_scan_enabled:                    ").append(CciConfig.AUTO_SCAN_ENABLED.get());
        sb.append("\n  scan_on_chunk_load_enabled:           ").append(CciConfig.SCAN_ON_CHUNK_LOAD_ENABLED.get());
        sb.append("\n  surface_hints_enabled:                ").append(hintsEnabled);
        sb.append("\n  surface_hint_backfill_enabled:        ").append(CciConfig.SURFACE_HINT_BACKFILL_ENABLED.get());
        sb.append("\n  scan_chunks_per_tick:                 ").append(scanChunksPerTick);
        sb.append("\n  max_pending_scan_jobs:                ").append(CciConfig.MAX_PENDING_SCAN_JOBS.getAsInt());
        sb.append("\n  scan_interval_ticks:                  ").append(CciConfig.SCAN_INTERVAL_TICKS.getAsInt());
        sb.append("\n  max_radius_chunks:                    ").append(CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
        sb.append("\n  loaded_chunks_only:                   ").append(CciConfig.LOADED_CHUNKS_ONLY.get());
        sb.append("\n  persist_known_veins:                  ").append(CciConfig.PERSIST_KNOWN_VEINS.get());
        sb.append("\n  hint_placements_per_tick:             ").append(hintsPertick);
        sb.append("\n  max_pending_hint_jobs:                ").append(CciConfig.MAX_PENDING_HINT_JOBS.getAsInt());
        sb.append("\n  surface_hints_per_chunk_min:          ").append(CciConfig.SURFACE_HINTS_PER_CHUNK_MIN.getAsInt());
        sb.append("\n  surface_hints_per_chunk_max:          ").append(CciConfig.SURFACE_HINTS_PER_CHUNK_MAX.getAsInt());
        sb.append("\n  surface_hint_attempts_per_chunk:      ").append(CciConfig.SURFACE_HINT_ATTEMPTS_PER_CHUNK.getAsInt());
        sb.append("\n  surface_hint_retry_limit:             ").append(CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt());
        sb.append("\n  surface_hint_retry_cooldown_ticks:    ").append(CciConfig.SURFACE_HINT_RETRY_COOLDOWN_TICKS.getAsInt());
        sb.append("\n  surface_hint_replace_thin_vegetation: ").append(CciConfig.SURFACE_HINT_REPLACE_THIN_VEGETATION.get());

        String configHint = "\n  -> Edit the active world server config:"
                + " run/saves/<world>/serverconfig/cci_radar-server.toml."
                + " Existing worlds do not automatically inherit defaultconfigs.";

        if (scanChunksPerTick <= 2) {
            sb.append("\n  [WARNING] scan_chunks_per_tick=").append(scanChunksPerTick)
              .append(": scanning may be very slow.").append(configHint);
        }
        if (!hintsEnabled) {
            sb.append("\n  [WARNING] surface_hints_enabled=false: automatic pebble placement is disabled.")
              .append(configHint);
        }
        if (hintsEnabled && hintsPertick == 0) {
            sb.append("\n  [WARNING] surface_hints_enabled=true but hint_placements_per_tick=0:")
              .append(" pebbles will never be placed. Set hint_placements_per_tick >= 1.")
              .append(configHint);
        }

        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
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
