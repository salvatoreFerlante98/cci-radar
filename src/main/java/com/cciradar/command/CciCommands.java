package com.cciradar.command;

import com.cciradar.config.CciConfig;
import com.cciradar.data.ResourceDef;
import com.cciradar.data.ResourceRegistry;
import com.cciradar.server.PlayerProgressData;
import com.cciradar.server.VeinSyncManager;
import com.cciradar.server.WorldVeinData;
import com.cciradar.server.coe.CoeVeinAdapter;
import com.cciradar.server.coe.CoeVeinScanner;
import com.cciradar.server.coe.DetectedVein;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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

        CoeVeinScanner.ScanResult result = CoeVeinScanner.scan(level, player.blockPosition(), requestedRadius);

        WorldVeinData worldData = WorldVeinData.get(server);
        int added = worldData.addAllIfAbsent(result.detectedVeins());
        int cachedTotal = worldData.getAll().size();

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

        if (markerVisible) {
            VeinSyncManager.syncToPlayer(server, player);
        }

        String mappedKey = vein.cciResourceKey() != null ? vein.cciResourceKey() : "(unmapped)";
        String tierStr = tier >= 0 ? String.valueOf(tier) : "unknown";
        String addedStr = added ? "yes (newly added)" : "already present";

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

        List<DetectedVein> visible = allVeins.stream()
                .filter(v -> v.cciResourceKey() != null)
                .filter(v -> VeinSyncManager.isResourceUnlocked(v.cciResourceKey(), unlocked))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("[CCI Radar] debug_visible_veins:");
        sb.append("\n  dim: ").append(player.serverLevel().dimension().location());
        sb.append("\n  player: ").append(player.getName().getString());
        sb.append("\n  unlocked_tiers: ").append(unlocked.isEmpty() ? "none" : unlocked.toString());
        sb.append("\n  cached_real_veins_total: ").append(allVeins.size());
        sb.append("\n  visible_veins: ").append(visible.size());

        if (!visible.isEmpty()) {
            sb.append("\n  --- visible veins ---");
            for (DetectedVein v : visible) {
                int tier = getTierForResourceKey(v.cciResourceKey());
                ResourceDef def = ResourceRegistry.get(v.cciResourceKey());
                String label = def != null ? def.label() : v.cciResourceKey();
                sb.append("\n    [").append(v.chunkX()).append(", ").append(v.chunkZ()).append("] ")
                  .append(label).append(" (").append(v.cciResourceKey())
                  .append(") tier ").append(tier >= 0 ? tier : "?")
                  .append(" in ").append(v.dimension());
            }
        }

        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return visible.size();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the tier for a CCI resource key by checking config lists, or -1 if unknown.
     */
    static int getTierForResourceKey(String cciKey) {
        if (cciKey == null) return -1;
        for (int tier = 0; tier <= 1; tier++) {
            if (CciConfig.getResourcesForTier(tier).contains(cciKey)) return tier;
        }
        return -1;
    }
}
