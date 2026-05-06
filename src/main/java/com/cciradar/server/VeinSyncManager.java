package com.cciradar.server;

import com.cciradar.config.CciConfig;
import com.cciradar.data.ResourceDef;
import com.cciradar.data.ResourceRegistry;
import com.cciradar.network.VeinEntry;
import com.cciradar.network.VisibleVeinsPayload;
import com.cciradar.server.coe.DetectedVein;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class VeinSyncManager {

    /**
     * Sends real detected veins (from WorldVeinData) filtered by the player's unlocked tiers.
     * If no scan has been run yet, WorldVeinData is empty and the client receives an empty list.
     */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        Set<Integer> unlocked = PlayerProgressData.get(server).getUnlockedTiers(player.getUUID());
        List<VeinEntry> veins = WorldVeinData.get(server).getAll().stream()
                .filter(v -> v.cciResourceKey() != null)
                .filter(v -> isResourceUnlocked(v.cciResourceKey(), unlocked))
                .map(VeinSyncManager::toVeinEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        PacketDistributor.sendToPlayer(player, new VisibleVeinsPayload(veins));
    }

    /**
     * Syncs visible veins to every currently online player.
     */
    public static void syncAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(server, player);
        }
    }

    /**
     * Returns the number of veins visible to this player given their current unlocked tiers.
     */
    public static int countVisibleForPlayer(MinecraftServer server, ServerPlayer player) {
        Set<Integer> unlocked = PlayerProgressData.get(server).getUnlockedTiers(player.getUUID());
        return (int) WorldVeinData.get(server).getAll().stream()
                .filter(v -> v.cciResourceKey() != null)
                .filter(v -> isResourceUnlocked(v.cciResourceKey(), unlocked))
                .count();
    }

    /**
     * Sends ALL fake veins directly (debug command — bypasses tiers and WorldVeinData).
     */
    public static void syncAllFakeVeinsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new VisibleVeinsPayload(FakeVeinProvider.getAllVeins()));
    }

    public static boolean isResourceUnlocked(String resourceKey, Set<Integer> unlockedTiers) {
        for (int tier : unlockedTiers) {
            if (CciConfig.getResourcesForTier(tier).contains(resourceKey)) return true;
        }
        return false;
    }

    private static VeinEntry toVeinEntry(DetectedVein vein) {
        ResourceDef def = ResourceRegistry.get(vein.cciResourceKey());
        if (def == null) return null;
        return new VeinEntry(
                vein.dimension(),
                vein.chunkX(),
                vein.chunkZ(),
                def.resourceKey(),
                def.label(),
                def.color(),
                def.iconPath()
        );
    }
}
