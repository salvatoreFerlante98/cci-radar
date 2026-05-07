package com.cciradar.server.surfacehint;

import com.cciradar.config.CciConfig;
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
 * Persistent record of surface-hint placement state per (dim, cx, cz, resourceKey).
 *
 * placedKeys        — at least one pebble was successfully placed; never retried.
 * failedAttempts    — count of attempts where chunk was loaded but 0 positions found.
 * lastAttemptTick   — server tick of the most recent failed attempt; drives retry cooldown.
 *
 * Retry limit and cooldown are read from CciConfig at runtime, so they can be tuned
 * without clearing world data.
 */
public class SurfaceHintWorldData extends SavedData {

    public static final String DATA_KEY = "cci_radar_surface_hints";

    private final Set<String>          placedKeys      = new HashSet<>();
    private final Map<String, Integer> failedAttempts  = new HashMap<>();
    private final Map<String, Integer> lastAttemptTick = new HashMap<>();

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

    /** Returns the server tick of the last failed attempt, or -1 if never failed. */
    public int getLastAttemptTick(ResourceLocation dim, int cx, int cz, String resourceKey) {
        return lastAttemptTick.getOrDefault(makeKey(dim, cx, cz, resourceKey), -1);
    }

    /**
     * Returns true if placement should be skipped permanently:
     * already placed, or failed attempts >= SURFACE_HINT_RETRY_LIMIT.
     * Does NOT check cooldown; cooldown is handled at enqueue time.
     */
    public boolean shouldSkip(ResourceLocation dim, int cx, int cz, String resourceKey) {
        String key = makeKey(dim, cx, cz, resourceKey);
        if (placedKeys.contains(key)) return true;
        int retryLimit = CciConfig.SURFACE_HINT_RETRY_LIMIT.getAsInt();
        return retryLimit > 0 && failedAttempts.getOrDefault(key, 0) >= retryLimit;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void markPlaced(ResourceLocation dim, int cx, int cz, String resourceKey) {
        placedKeys.add(makeKey(dim, cx, cz, resourceKey));
        setDirty();
    }

    /** Increments failed-attempt counter and records the server tick of this attempt. */
    public void markFailed(ResourceLocation dim, int cx, int cz, String resourceKey, int currentTick) {
        String key = makeKey(dim, cx, cz, resourceKey);
        failedAttempts.merge(key, 1, Integer::sum);
        lastAttemptTick.put(key, currentTick);
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

        CompoundTag ticks = tag.getCompound("lastAttemptTick");
        for (String key : ticks.getAllKeys()) {
            data.lastAttemptTick.put(key, ticks.getInt(key));
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

        CompoundTag ticks = new CompoundTag();
        for (Map.Entry<String, Integer> e : lastAttemptTick.entrySet()) {
            ticks.putInt(e.getKey(), e.getValue());
        }
        tag.put("lastAttemptTick", ticks);

        return tag;
    }
}
