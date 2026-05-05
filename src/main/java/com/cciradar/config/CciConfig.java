package com.cciradar.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class CciConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIER_0_RESOURCES =
            BUILDER.comment("Resource keys available in Tier 0 (basic resources)")
                   .defineListAllowEmpty("tier0_resources",
                           List.of("coal", "iron", "copper"),
                           () -> "",
                           obj -> obj instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIER_1_RESOURCES =
            BUILDER.comment("Resource keys available in Tier 1 (advanced resources)")
                   .defineListAllowEmpty("tier1_resources",
                           List.of("zinc", "redstone", "gold"),
                           () -> "",
                           obj -> obj instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    @SuppressWarnings("unchecked")
    public static List<String> getResourcesForTier(int tier) {
        return switch (tier) {
            case 0 -> (List<String>) (List<?>) TIER_0_RESOURCES.get();
            case 1 -> (List<String>) (List<?>) TIER_1_RESOURCES.get();
            default -> List.of();
        };
    }
}
