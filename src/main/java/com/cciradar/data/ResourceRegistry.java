package com.cciradar.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceRegistry {
    private static final Map<String, ResourceDef> REGISTRY = new LinkedHashMap<>();

    public static final ResourceDef COAL = register(new ResourceDef(
            "coal", "Coal", 0x444444,
            "minecraft:textures/map/map_icons.png",
            List.of("createoreexcavation:coal_vein")
    ));
    public static final ResourceDef IRON = register(new ResourceDef(
            "iron", "Fe", 0xBBBBBB,
            "minecraft:textures/map/map_icons.png",
            List.of("createoreexcavation:iron_vein")
    ));
    public static final ResourceDef COPPER = register(new ResourceDef(
            "copper", "Cu", 0xB87333,
            "minecraft:textures/map/map_icons.png",
            List.of("createoreexcavation:copper_vein")
    ));
    public static final ResourceDef ZINC = register(new ResourceDef(
            "zinc", "Zn", 0xD0D0C0,
            "minecraft:textures/map/map_icons.png",
            List.of("createoreexcavation:zinc_vein")
    ));
    public static final ResourceDef REDSTONE = register(new ResourceDef(
            "redstone", "Red", 0xFF2200,
            "minecraft:textures/map/map_icons.png",
            List.of("createoreexcavation:redstone_vein")
    ));
    public static final ResourceDef GOLD = register(new ResourceDef(
            "gold", "Au", 0xFFD700,
            "minecraft:textures/map/map_icons.png",
            List.of("createoreexcavation:gold_vein")
    ));

    private static ResourceDef register(ResourceDef def) {
        REGISTRY.put(def.resourceKey(), def);
        return def;
    }

    public static ResourceDef get(String key) {
        return REGISTRY.get(key);
    }

    public static Collection<ResourceDef> all() {
        return REGISTRY.values();
    }
}
