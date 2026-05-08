package com.cciradar.server;

import com.cciradar.server.coe.DetectedVein;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side SavedData storing all COE veins detected by the scanner.
 * Persists to world data as cci_radar_world_veins.dat.
 */
public class WorldVeinData extends SavedData {

    public static final String DATA_KEY = "cci_radar_world_veins";

    private final List<DetectedVein> veins = new ArrayList<>();
    private final Set<String> knownChunkKeys = new HashSet<>();

    private WorldVeinData() {}

    public static WorldVeinData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldVeinData::new, WorldVeinData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES),
                DATA_KEY
        );
    }

    public List<DetectedVein> getAll() {
        return Collections.unmodifiableList(veins);
    }

    /**
     * Adds a vein only if no vein is already recorded for this dimension+chunk position.
     * Returns true if the vein was newly added.
     */
    public boolean addIfAbsent(DetectedVein vein) {
        String key = chunkKey(vein.dimension(), vein.chunkX(), vein.chunkZ());
        if (knownChunkKeys.contains(key)) return false;
        knownChunkKeys.add(key);
        veins.add(vein);
        setDirty();
        return true;
    }

    /**
     * Adds all veins not already present. Returns the list of newly added veins.
     */
    public List<DetectedVein> addAllIfAbsent(List<DetectedVein> newVeins) {
        List<DetectedVein> added = new ArrayList<>();
        for (DetectedVein vein : newVeins) {
            if (addIfAbsent(vein)) added.add(vein);
        }
        return added;
    }

    /**
     * Removes any cached vein recorded for the given dimension+chunk position.
     * Returns true if a vein was actually removed.
     *
     * Used by the cci_core provider integration to handle authoritative NO_VEIN
     * snapshots: if cci_world declares a chunk has no vein, any stale legacy
     * cache entry must be dropped so it does not appear on the map.
     */
    public boolean removeAt(ResourceLocation dim, int cx, int cz) {
        String key = chunkKey(dim, cx, cz);
        if (!knownChunkKeys.remove(key)) return false;
        boolean removed = veins.removeIf(v ->
                v.dimension().equals(dim) && v.chunkX() == cx && v.chunkZ() == cz);
        if (removed) setDirty();
        return removed;
    }

    /** True if a vein is cached for this dimension+chunk. */
    public boolean hasAt(ResourceLocation dim, int cx, int cz) {
        return knownChunkKeys.contains(chunkKey(dim, cx, cz));
    }

    public void replaceAll(List<DetectedVein> newVeins) {
        veins.clear();
        knownChunkKeys.clear();
        for (DetectedVein vein : newVeins) {
            veins.add(vein);
            knownChunkKeys.add(chunkKey(vein.dimension(), vein.chunkX(), vein.chunkZ()));
        }
        setDirty();
    }

    private static String chunkKey(ResourceLocation dim, int cx, int cz) {
        return dim + ":" + cx + ":" + cz;
    }

    private static WorldVeinData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldVeinData data = new WorldVeinData();
        ListTag list = tag.getList("veins", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.parse(e.getString("dim"));
            int cx = e.getInt("cx");
            int cz = e.getInt("cz");
            ResourceLocation recipe = ResourceLocation.parse(e.getString("recipe"));
            String key = e.contains("key") ? e.getString("key") : null;
            DetectedVein vein = new DetectedVein(dim, cx, cz, recipe, key);
            data.veins.add(vein);
            data.knownChunkKeys.add(chunkKey(dim, cx, cz));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (DetectedVein v : veins) {
            CompoundTag e = new CompoundTag();
            e.putString("dim", v.dimension().toString());
            e.putInt("cx", v.chunkX());
            e.putInt("cz", v.chunkZ());
            e.putString("recipe", v.coeRecipeId().toString());
            if (v.cciResourceKey() != null) e.putString("key", v.cciResourceKey());
            list.add(e);
        }
        tag.put("veins", list);
        return tag;
    }
}
