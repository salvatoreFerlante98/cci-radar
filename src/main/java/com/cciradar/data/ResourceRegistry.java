package com.cciradar.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceRegistry {
    private static final Map<String, ResourceDef> REGISTRY = new LinkedHashMap<>();

    private static final String DOT_ICON = "cci_radar:textures/map/resource/dot.png";

    // COE recipe IDs are the data-pack path under data/createoreexcavation/recipe/ore_vein_type/
    public static final ResourceDef COAL = register(new ResourceDef(
            "coal", "Coal", 0x555555,
            DOT_ICON,
            List.of("createoreexcavation:ore_vein_type/coal")
    ));
    public static final ResourceDef IRON = register(new ResourceDef(
            "iron", "Iron", 0xC8C8C8,
            DOT_ICON,
            List.of("createoreexcavation:ore_vein_type/iron")
    ));
    public static final ResourceDef COPPER = register(new ResourceDef(
            "copper", "Copper", 0xC87941,
            DOT_ICON,
            List.of("createoreexcavation:ore_vein_type/copper")
    ));
    public static final ResourceDef ZINC = register(new ResourceDef(
            "zinc", "Zinc", 0xADB89E,
            DOT_ICON,
            List.of("createoreexcavation:ore_vein_type/zinc")
    ));
    public static final ResourceDef REDSTONE = register(new ResourceDef(
            "redstone", "Redstone", 0xFF3300,
            DOT_ICON,
            List.of("createoreexcavation:ore_vein_type/redstone")
    ));
    public static final ResourceDef GOLD = register(new ResourceDef(
            "gold", "Gold", 0xFFCC00,
            DOT_ICON,
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
