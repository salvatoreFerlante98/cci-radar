package com.cciradar.server;

import com.cciradar.network.VisibleVeinsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

public final class VeinSyncManager {

    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        Set<Integer> unlocked = PlayerProgressData.get(server).getUnlockedTiers(player.getUUID());
        var veins = FakeVeinProvider.getVisibleVeins(unlocked);
        PacketDistributor.sendToPlayer(player, new VisibleVeinsPayload(veins));
    }

    public static void syncAllFakeVeinsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new VisibleVeinsPayload(FakeVeinProvider.getAllVeins()));
    }
}
