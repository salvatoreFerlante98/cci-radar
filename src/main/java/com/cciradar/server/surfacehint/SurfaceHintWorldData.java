package com.cciradar.server.surfacehint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent record of surface-hint placement state.
 *
 * Two tracking sets per (dim, cx, cz, resourceKey) key:
 *   placedKeys    — at least one pebble was successfully placed
 *   failedAttempts — count of attempts where chunk was loaded but 0 positions found
 *
 * MAX_FAILED_ATTEMPTS controls how many times we retry a chunk with no valid
 * surface positions before giving up permanently.
 */
public class SurfaceHintWorldData extends SavedData {

    public static final String DATA_KEY = "cci_radar_surface_hints";
    public static final int    MAX_FAILED_ATTEMPTS = 3;

    private final Set<String>         placedKeys     = new HashSet<>();
    private final Map<String, Integer> failedAttempts = new HashMap<>();

    private SurfaceHintWorldData() {}

    public static SurfaceHintWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SurfaceHintWorldData::new,
                        SurfaceHintWorldData::load,
                        DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES),
                DATA_KEY
        );
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean hasPlaced(ResourceLocation dim, int cx, int cz, String resourceKey) {
        return placedKeys.contains(makeKey(dim, cx, cz, resourceKey));
    }

    public int getFailedAttempts(ResourceLocation dim, int cx, int cz, String resourceKey) {
        return failedAttempts.getOrDefault(makeKey(dim, cx, cz, resourceKey), 0);
    }

    /**
     * Returns true if placement should be skipped entirely:
     * either already placed successfully, or exceeded the failed-attempt limit.
     */
    public boolean shouldSkip(ResourceLocation dim, int cx, int cz, String resourceKey) {
        String key = makeKey(dim, cx, cz, resourceKey);
        return placedKeys.contains(key) || failedAttempts.getOrDefault(key, 0) >= MAX_FAILED_ATTEMPTS;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void markPlaced(ResourceLocation dim, int cx, int cz, String resourceKey) {
        placedKeys.add(makeKey(dim, cx, cz, resourceKey));
        setDirty();
    }

    /** Increments the failed-attempt counter for this chunk/resource. */
    public void markFailed(ResourceLocation dim, int cx, int cz, String resourceKey) {
        String key = makeKey(dim, cx, cz, resourceKey);
        failedAttempts.merge(key, 1, Integer::sum);
        setDirty();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static String makeKey(ResourceLocation dim, int cx, int cz, String resourceKey) {
        return dim + ":" + cx + ":" + cz + ":" + resourceKey;
    }

    private static SurfaceHintWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurfaceHintWorldData data = new SurfaceHintWorldData();

        ListTag placed = tag.getList("placed", Tag.TAG_STRING);
        for (int i = 0; i < placed.size(); i++) {
            data.placedKeys.add(placed.getString(i));
        }

        CompoundTag failed = tag.getCompound("failed");
        for (String key : failed.getAllKeys()) {
            data.failedAttempts.put(key, failed.getInt(key));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag placed = new ListTag();
        for (String key : placedKeys) {
            placed.add(StringTag.valueOf(key));
        }
        tag.put("placed", placed);

        CompoundTag failed = new CompoundTag();
        for (Map.Entry<String, Integer> e : failedAttempts.entrySet()) {
            failed.putInt(e.getKey(), e.getValue());
        }
        tag.put("failed", failed);

        return tag;
    }
}
