package com.cciradar.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PlayerProgressData extends SavedData {
    public static final String DATA_KEY = "cci_radar_player_progress";

    private final Map<UUID, Set<Integer>> unlockedTiers = new HashMap<>();

    private PlayerProgressData() {}

    public static PlayerProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerProgressData::new, PlayerProgressData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES),
                DATA_KEY
        );
    }

    public Set<Integer> getUnlockedTiers(UUID playerId) {
        return Collections.unmodifiableSet(unlockedTiers.getOrDefault(playerId, Set.of()));
    }

    public void unlockTier(UUID playerId, int tier) {
        unlockedTiers.computeIfAbsent(playerId, k -> new HashSet<>()).add(tier);
        setDirty();
    }

    public void resetPlayer(UUID playerId) {
        unlockedTiers.remove(playerId);
        setDirty();
    }

    private static PlayerProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerProgressData data = new PlayerProgressData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            int[] tiers = entry.getIntArray("tiers");
            Set<Integer> tierSet = new HashSet<>();
            for (int t : tiers) tierSet.add(t);
            data.unlockedTiers.put(uuid, tierSet);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Set<Integer>> entry : unlockedTiers.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("uuid", entry.getKey());
            int[] tiers = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            playerTag.putIntArray("tiers", tiers);
            players.add(playerTag);
        }
        tag.put("players", players);
        return tag;
    }
}
