package com.cciradar.server.coe;

import com.cciradar.config.CciConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Scans chunks already loaded around a position, using ServerChunkCache.getChunkNow()
 * which returns null for unloaded chunks — never forces chunk generation.
 */
public final class CoeVeinScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    public record ScanResult(
            ResourceLocation dimension,
            int centerCX,
            int centerCZ,
            int requestedRadius,
            int effectiveRadius,
            int candidateChunks,
            int loadedChunksChecked,
            int unloadedChunksSkipped,
            int veinsFound,
            int mappedVeins,
            int unmappedVeins,
            List<ResourceLocation> unmappedIds,
            List<DetectedVein> detectedVeins
    ) {}

    public static ScanResult scan(ServerLevel level, BlockPos center) {
        return scan(level, center, CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
    }

    public static ScanResult scan(ServerLevel level, BlockPos center, int requestedRadius) {
        int effectiveRadius = Math.min(requestedRadius, CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
        int centerCX = SectionPos.blockToSectionCoord(center.getX());
        int centerCZ = SectionPos.blockToSectionCoord(center.getZ());
        ServerChunkCache chunkSource = level.getChunkSource();

        int side = 2 * effectiveRadius + 1;
        int candidateChunks = side * side;
        int unloaded = 0;

        List<DetectedVein> detected = new ArrayList<>();
        Set<ResourceLocation> unmappedSet = new HashSet<>();

        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                // getChunkNow returns null if the chunk is not fully loaded — safe, no generation
                LevelChunk chunk = chunkSource.getChunkNow(centerCX + dx, centerCZ + dz);
                if (chunk == null) {
                    unloaded++;
                    continue;
                }

                Optional<DetectedVein> result = CoeVeinAdapter.detect(level, chunk);
                result.ifPresent(vein -> {
                    detected.add(vein);
                    if (vein.cciResourceKey() == null) {
                        unmappedSet.add(vein.coeRecipeId());
                    }
                });
            }
        }

        int checked = candidateChunks - unloaded;
        int mappedCount = (int) detected.stream().filter(v -> v.cciResourceKey() != null).count();
        int unmappedCount = detected.size() - mappedCount;

        LOGGER.info("[CCI Radar] Scan in {}: radius={}, {}/{} chunks loaded, {} veins ({} mapped, {} unmapped)",
                level.dimension().location(), effectiveRadius, checked, candidateChunks,
                detected.size(), mappedCount, unmappedCount);

        return new ScanResult(
                level.dimension().location(),
                centerCX, centerCZ,
                requestedRadius, effectiveRadius,
                candidateChunks, checked, unloaded,
                detected.size(), mappedCount, unmappedCount,
                List.copyOf(unmappedSet), List.copyOf(detected)
        );
    }
}
