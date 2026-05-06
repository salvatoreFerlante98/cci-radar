package com.cciradar.client.journeymap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A group of adjacent vein chunks belonging to the same dimension and resource type.
 * Produced by {@link VeinClusterer} from the client-side vein store.
 *
 * Surface hints (not yet implemented) can iterate {@link #chunks()} and scale
 * density inversely to {@code chunks().size()}: common clusters get fewer hints,
 * rare large clusters get a wider visible footprint.
 */
record ResourceCluster(
        String clusterId,
        ResourceLocation dimension,
        String resourceKey,
        String label,
        int color,
        String iconPath,
        List<ChunkPos> chunks,
        BlockPos center
) {
    /**
     * Builds a stable cluster ID that encodes the exact chunk composition.
     * Changes whenever any chunk is added or removed, triggering a diff-update.
     * Chunks must be pre-sorted (x asc, z asc).
     */
    static String buildId(ResourceLocation dim, String resourceKey, List<ChunkPos> sortedChunks) {
        String chunkStr = sortedChunks.stream()
                .map(c -> c.x + "," + c.z)
                .collect(Collectors.joining(";"));
        return dim + ":" + resourceKey + ":" + chunkStr;
    }
}
