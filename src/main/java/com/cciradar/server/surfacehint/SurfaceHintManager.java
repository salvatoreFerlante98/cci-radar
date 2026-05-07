package com.cciradar.server.surfacehint;

import com.cciradar.block.CciBlocks;
import com.cciradar.config.CciConfig;
import com.cciradar.server.coe.DetectedVein;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.Random;
import java.util.Set;

/**
 * Server-side service that places pebble surface hints for real mapped COE veins.
 * Covers tier 0 (coal, iron, copper) and tier 1 (zinc, redstone, gold).
 *
 * Two entry points:
 *   placeHintsIfNeeded  — used by the tick worker; respects surface_hints_enabled.
 *   placeHintsForDebug  — used by OP debug commands; always runs (ignores surface_hints_enabled).
 *
 * Placement parameters are read from CciConfig at runtime so they can be tuned without restart.
 * Never called from chunk-load events.
 */
public final class SurfaceHintManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Spec-exact valid support blocks: natural terrain only, no manmade blocks.
    private static final Set<Block> VALID_SUPPORT = Set.of(
            Blocks.GRASS_BLOCK,   Blocks.DIRT,         Blocks.COARSE_DIRT,  Blocks.ROOTED_DIRT,
            Blocks.PODZOL,
            Blocks.MUD,           Blocks.PACKED_MUD,
            Blocks.GRAVEL,        Blocks.SAND,          Blocks.RED_SAND,
            Blocks.STONE,         Blocks.DEEPSLATE,     Blocks.TUFF,
            Blocks.ANDESITE,      Blocks.DIORITE,       Blocks.GRANITE,
            Blocks.CALCITE,       Blocks.DRIPSTONE_BLOCK
    );

    private SurfaceHintManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /** Respects surface_hints_enabled config. Called by the tick worker. */
    public static SurfaceHintPlacementResult placeHintsIfNeeded(ServerLevel level, DetectedVein vein) {
        if (!CciConfig.SURFACE_HINTS_ENABLED.get()) {
            return SurfaceHintPlacementResult.ofDisabled(getPebbleBlockId(vein.cciResourceKey()));
        }
        return doPlaceHints(level, vein);
    }

    /** Always runs regardless of surface_hints_enabled. Called by OP debug commands. */
    public static SurfaceHintPlacementResult placeHintsForDebug(ServerLevel level, DetectedVein vein) {
        return doPlaceHints(level, vein);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private static SurfaceHintPlacementResult doPlaceHints(ServerLevel level, DetectedVein vein) {
        String resKey   = vein.cciResourceKey();
        String pebbleId = getPebbleBlockId(resKey);

        if (resKey == null) return SurfaceHintPlacementResult.ofNoBlock("none");

        BlockState pebbleState = getPebbleState(resKey);
        if (pebbleState == null) return SurfaceHintPlacementResult.ofNoBlock(pebbleId);

        SurfaceHintWorldData data    = SurfaceHintWorldData.get(level.getServer());
        int prevFailed               = data.getFailedAttempts(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey);

        if (data.hasPlaced(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey)) {
            return SurfaceHintPlacementResult.ofAlreadyPlaced(pebbleId, prevFailed);
        }

        int retryLimit = CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt();
        if (retryLimit > 0 && prevFailed >= retryLimit) {
            return SurfaceHintPlacementResult.ofMaxRetries(pebbleId, prevFailed, retryLimit);
        }

        // Never force-generate chunks — only place in already-loaded chunks.
        ChunkAccess chunk = level.getChunkSource().getChunkNow(vein.chunkX(), vein.chunkZ());
        if (chunk == null) {
            LOGGER.debug("[CCI Radar] Surface hints deferred — chunk [{},{}] not loaded in {}",
                    vein.chunkX(), vein.chunkZ(), vein.dimension());
            return SurfaceHintPlacementResult.ofChunkNotLoaded(pebbleId, prevFailed);
        }

        int minHints    = CciConfig.SURFACE_HINTS_PER_CHUNK_MIN.getAsInt();
        int maxHints    = CciConfig.SURFACE_HINTS_PER_CHUNK_MAX.getAsInt();
        int maxAttempts = CciConfig.SURFACE_HINT_ATTEMPTS_PER_CHUNK.getAsInt();

        long seed   = deriveRngSeed(level.getSeed(), vein);
        Random rng  = new Random(seed);
        int target  = minHints + (maxHints > minHints ? rng.nextInt(maxHints - minHints + 1) : 0);
        int placed  = 0;
        int attempts = 0;

        while (attempts < maxAttempts && placed < target) {
            attempts++;
            int worldX = (vein.chunkX() << 4) + rng.nextInt(16);
            int worldZ = (vein.chunkZ() << 4) + rng.nextInt(16);

            int surfaceY      = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
            BlockPos placePos = new BlockPos(worldX, surfaceY, worldZ);
            BlockPos support  = placePos.below();

            if (!isValidPlacement(level, placePos, support)) continue;

            level.setBlock(placePos, pebbleState, Block.UPDATE_ALL);
            placed++;
        }

        int currentTick = level.getServer().getTickCount();

        if (placed > 0) {
            data.markPlaced(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey);
            LOGGER.debug("[CCI Radar] Placed {} {} pebble(s) at chunk [{},{}] in {} ({} attempts)",
                    placed, resKey, vein.chunkX(), vein.chunkZ(), vein.dimension(), attempts);
        } else {
            data.markFailed(vein.dimension(), vein.chunkX(), vein.chunkZ(), resKey, currentTick);
            LOGGER.debug("[CCI Radar] No valid positions for {} at chunk [{},{}] in {} after {} attempts (fail {}/{})",
                    resKey, vein.chunkX(), vein.chunkZ(), vein.dimension(),
                    attempts, prevFailed + 1, retryLimit > 0 ? retryLimit : "unlimited");
        }

        return SurfaceHintPlacementResult.ofSuccess(pebbleId, prevFailed, attempts, placed);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isValidPlacement(ServerLevel level, BlockPos placePos, BlockPos supportPos) {
        BlockState above   = level.getBlockState(placePos);
        BlockState support = level.getBlockState(supportPos);
        if (!isReplaceableAtSurface(above)) return false;
        if (!VALID_SUPPORT.contains(support.getBlock())) return false;
        if (level.isWaterAt(placePos) || level.isWaterAt(supportPos)) return false;
        return true;
    }

    private static boolean isReplaceableAtSurface(BlockState state) {
        if (state.isAir()) return true;
        if (!CciConfig.SURFACE_HINT_REPLACE_THIN_VEGETATION.get()) return false;
        Block b = state.getBlock();
        return b == Blocks.SHORT_GRASS
                || b == Blocks.FERN
                || b == Blocks.DEAD_BUSH;
    }

    private static BlockState getPebbleState(String resourceKey) {
        Block block = switch (resourceKey) {
            case "coal"     -> CciBlocks.COAL_PEBBLES.get();
            case "iron"     -> CciBlocks.IRON_PEBBLES.get();
            case "copper"   -> CciBlocks.COPPER_PEBBLES.get();
            case "zinc"     -> CciBlocks.ZINC_PEBBLES.get();
            case "redstone" -> CciBlocks.REDSTONE_PEBBLES.get();
            case "gold"     -> CciBlocks.GOLD_PEBBLES.get();
            default         -> null;
        };
        return block != null ? block.defaultBlockState() : null;
    }

    /** Returns the expected pebble block ID for a resource key, or "none" if unmapped. */
    public static String getPebbleBlockId(String resourceKey) {
        if (resourceKey == null) return "none";
        return switch (resourceKey) {
            case "coal", "iron", "copper", "zinc", "redstone", "gold" ->
                    "cci_radar:" + resourceKey + "_pebbles";
            default -> "none";
        };
    }

    private static long deriveRngSeed(long worldSeed, DetectedVein vein) {
        long seed = worldSeed;
        seed ^= (long) vein.chunkX() * 0x9E3779B97F4A7C15L;
        seed ^= (long) vein.chunkZ() * 0x6C62272E07BB0142L;
        String dimStr = vein.dimension().toString();
        for (int i = 0; i < dimStr.length(); i++) seed = seed * 31L + dimStr.charAt(i);
        String resKey = vein.cciResourceKey();
        for (int i = 0; i < resKey.length(); i++) seed = seed * 31L + resKey.charAt(i);
        return seed;
    }
}
