package com.cciradar.client.journeymap;

import com.cciradar.network.VeinEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups client-side {@link VeinEntry} objects into {@link ResourceCluster} instances
 * using 8-direction (Moore neighbourhood) adjacency within the same dimension and resource type.
 *
 * 8-direction was chosen over 4-direction for organic visual grouping.
 * Real COE veins are spaced 128 chunks apart, so diagonal merging from actual scan data
 * is virtually impossible — the 8-direction rule matters primarily for future custom cluster data.
 */
final class VeinClusterer {

    private VeinClusterer() {}

    /** Moore neighbourhood: all 8 directions including diagonals. */
    private static final int[][] DIRS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        { 0, -1},           { 0, 1},
        { 1, -1}, { 1, 0}, { 1, 1}
    };

    /** Groups veins into clusters and returns one {@link ResourceCluster} per connected component. */
    static List<ResourceCluster> cluster(List<VeinEntry> veins) {
        // Group by (dimension, resourceKey) before running adjacency BFS.
        Map<String, List<VeinEntry>> groups = new LinkedHashMap<>();
        for (VeinEntry v : veins) {
            groups.computeIfAbsent(v.dimension() + ":" + v.resourceKey(), k -> new ArrayList<>()).add(v);
        }

        List<ResourceCluster> result = new ArrayList<>();
        for (List<VeinEntry> group : groups.values()) {
            result.addAll(clusterGroup(group));
        }
        return result;
    }

    private static List<ResourceCluster> clusterGroup(List<VeinEntry> group) {
        Set<Long> posSet = new HashSet<>();
        for (VeinEntry v : group) {
            posSet.add(ChunkPos.asLong(v.chunkX(), v.chunkZ()));
        }

        Set<Long> visited = new HashSet<>();
        List<ResourceCluster> clusters = new ArrayList<>();

        for (VeinEntry seed : group) {
            long seedKey = ChunkPos.asLong(seed.chunkX(), seed.chunkZ());
            if (!visited.add(seedKey)) continue;

            // BFS to collect all chunks reachable via 8-direction adjacency.
            List<ChunkPos> component = new ArrayList<>();
            Deque<ChunkPos> queue = new ArrayDeque<>();
            queue.add(new ChunkPos(seed.chunkX(), seed.chunkZ()));

            while (!queue.isEmpty()) {
                ChunkPos cur = queue.poll();
                component.add(cur);
                for (int[] d : DIRS) {
                    int nx = cur.x + d[0], nz = cur.z + d[1];
                    long nKey = ChunkPos.asLong(nx, nz);
                    if (posSet.contains(nKey) && visited.add(nKey)) {
                        queue.add(new ChunkPos(nx, nz));
                    }
                }
            }

            // Stable sort so buildId is deterministic regardless of discovery order.
            component.sort(Comparator.comparingInt((ChunkPos c) -> c.x).thenComparingInt(c -> c.z));

            // Centroid in chunk coordinates → block center of that chunk.
            long sumX = 0, sumZ = 0;
            for (ChunkPos c : component) { sumX += c.x; sumZ += c.z; }
            int n = component.size();
            BlockPos center = new BlockPos((int)(sumX / n) * 16 + 8, 64, (int)(sumZ / n) * 16 + 8);

            String clusterId = ResourceCluster.buildId(seed.dimension(), seed.resourceKey(), component);

            clusters.add(new ResourceCluster(
                    clusterId,
                    seed.dimension(),
                    seed.resourceKey(),
                    seed.label(),
                    seed.color(),
                    seed.iconPath(),
                    List.copyOf(component),
                    center
            ));
        }
        return clusters;
    }
}
