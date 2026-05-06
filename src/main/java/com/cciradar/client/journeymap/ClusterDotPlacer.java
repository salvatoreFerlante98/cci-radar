package com.cciradar.client.journeymap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates deterministic, organic-looking dot positions for a {@link ResourceCluster}.
 *
 * Each dot is placed at a random block inside one of the cluster's chunks.
 * The seed is derived from the cluster ID string, so positions are stable across
 * map opens, resyncs, and chunk reloads.
 *
 * Dot count scales with cluster size:
 *   1 chunk  →  8 dots
 *   n chunks → min(8 + (n-1) * 5, maxDots)
 */
final class ClusterDotPlacer {

    private ClusterDotPlacer() {}

    /** Number of dots for a cluster of {@code chunkCount} chunks, capped at {@code maxDots}. */
    static int dotCount(int chunkCount, int maxDots) {
        return Math.min(8 + (chunkCount - 1) * 5, maxDots);
    }

    /**
     * Computes dot block positions for {@code cluster}.
     *
     * @param cluster  the cluster to place dots for
     * @param maxDots  maximum dots this cluster may receive (global cap already factored in by caller)
     * @return list of BlockPos, all inside the cluster's chunk area, y = 64
     */
    static List<BlockPos> computeDots(ResourceCluster cluster, int maxDots) {
        int count = dotCount(cluster.chunks().size(), maxDots);

        // Derive seed from cluster ID: stable, no hash-collision risk for same-cluster refreshes.
        long seed = 0L;
        for (int i = 0; i < cluster.clusterId().length(); i++) {
            seed = seed * 31L + cluster.clusterId().charAt(i);
        }

        Random rng = new Random(seed);
        List<ChunkPos> chunks = cluster.chunks();
        List<BlockPos> dots = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ChunkPos chunk = chunks.get(rng.nextInt(chunks.size()));
            int x = chunk.getMinBlockX() + rng.nextInt(16);
            int z = chunk.getMinBlockZ() + rng.nextInt(16);
            dots.add(new BlockPos(x, 64, z));
        }
        return dots;
    }
}
