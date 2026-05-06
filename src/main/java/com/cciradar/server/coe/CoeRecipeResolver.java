package com.cciradar.server.coe;

import com.cciradar.data.ResourceDef;
import com.cciradar.data.ResourceRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and caches the reverse mapping: COE vein recipe id -> CCI resource key.
 * Populated from ResourceRegistry coeVeinRecipeIds at class-load time.
 */
public final class CoeRecipeResolver {

    private static final Map<ResourceLocation, String> RECIPE_TO_KEY;

    static {
        Map<ResourceLocation, String> map = new HashMap<>();
        for (ResourceDef def : ResourceRegistry.all()) {
            for (String id : def.coeVeinRecipeIds()) {
                map.put(ResourceLocation.parse(id), def.resourceKey());
            }
        }
        RECIPE_TO_KEY = Map.copyOf(map);
    }

    public static Optional<String> resolve(ResourceLocation coeRecipeId) {
        return Optional.ofNullable(RECIPE_TO_KEY.get(coeRecipeId));
    }
}
