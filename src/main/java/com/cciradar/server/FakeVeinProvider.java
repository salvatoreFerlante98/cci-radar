package com.cciradar.server;

import com.cciradar.config.CciConfig;
import com.cciradar.data.ResourceDef;
import com.cciradar.data.ResourceRegistry;
import com.cciradar.network.VeinEntry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FakeVeinProvider {

    private record FakeVein(String resourceKey, ResourceKey<Level> dimension, int chunkX, int chunkZ) {}

    private static final List<FakeVein> FAKE_VEINS = List.of(
            new FakeVein("iron",     Level.OVERWORLD,  0,  0),
            new FakeVein("copper",   Level.OVERWORLD,  4,  0),
            new FakeVein("coal",     Level.OVERWORLD, -4,  0),
            new FakeVein("zinc",     Level.OVERWORLD,  8,  0),
            new FakeVein("redstone", Level.OVERWORLD,  8,  4),
            new FakeVein("gold",     Level.OVERWORLD,  8, -4)
    );

    public static List<VeinEntry> getVisibleVeins(Set<Integer> unlockedTiers) {
        List<VeinEntry> result = new ArrayList<>();
        for (FakeVein fv : FAKE_VEINS) {
            if (!isUnlocked(fv.resourceKey(), unlockedTiers)) continue;
            addVeinEntry(result, fv);
        }
        return result;
    }

    public static List<VeinEntry> getAllVeins() {
        List<VeinEntry> result = new ArrayList<>();
        for (FakeVein fv : FAKE_VEINS) {
            addVeinEntry(result, fv);
        }
        return result;
    }

    private static void addVeinEntry(List<VeinEntry> list, FakeVein fv) {
        ResourceDef def = ResourceRegistry.get(fv.resourceKey());
        if (def == null) return;
        list.add(new VeinEntry(
                fv.dimension().location(),
                fv.chunkX(),
                fv.chunkZ(),
                def.resourceKey(),
                def.label(),
                def.color(),
                def.iconPath()
        ));
    }

    private static boolean isUnlocked(String resourceKey, Set<Integer> unlockedTiers) {
        for (int tier : unlockedTiers) {
            if (CciConfig.getResourcesForTier(tier).contains(resourceKey)) return true;
        }
        return false;
    }
}
