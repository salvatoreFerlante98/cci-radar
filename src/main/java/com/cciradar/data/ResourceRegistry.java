package com.cciradar.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceRegistry {
    private static final Map<String, ResourceDef> REGISTRY = new LinkedHashMap<>();

    private static final String ICON = "cci_radar:textures/map/resource/";

    // COE recipe IDs are the data-pack path under data/createoreexcavation/recipe/ore_vein_type/
    // Icons are white-silhouette PNGs tinted at render time via MapImage.setColor(resourceColor).
    // Shape distinguishes resource type; color provides secondary identity.
    public static final ResourceDef COAL = register(new ResourceDef(
            "coal", "Coal", 0x555555,
            ICON + "coal.png",
            List.of("createoreexcavation:ore_vein_type/coal")
    ));
    public static final ResourceDef IRON = register(new ResourceDef(
            "iron", "Iron", 0xC8C8C8,
            ICON + "iron.png",
            List.of("createoreexcavation:ore_vein_type/iron")
    ));
    public static final ResourceDef COPPER = register(new ResourceDef(
            "copper", "Copper", 0xC87941,
            ICON + "copper.png",
            List.of("createoreexcavation:ore_vein_type/copper")
    ));
    public static final ResourceDef ZINC = register(new ResourceDef(
            "zinc", "Zinc", 0xADB89E,
            ICON + "zinc.png",
            List.of("createoreexcavation:ore_vein_type/zinc")
    ));
    public static final ResourceDef REDSTONE = register(new ResourceDef(
            "redstone", "Redstone", 0xFF3300,
            ICON + "redstone.png",
            List.of("createoreexcavation:ore_vein_type/redstone")
    ));
    public static final ResourceDef GOLD = register(new ResourceDef(
            "gold", "Gold", 0xFFCC00,
            ICON + "gold.png",
            List.of("createoreexcavation:ore_vein_type/gold")
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
