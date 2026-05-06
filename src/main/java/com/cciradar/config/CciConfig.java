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

    public static final ModConfigSpec.IntValue MAX_RADIUS_CHUNKS =
            BUILDER.comment("Chunk radius to scan around each player in debug_scan_real (and future auto-scan)")
                   .defineInRange("max_radius_chunks", 8, 1, 64);

    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS =
            BUILDER.comment("Server tick interval for automatic background scanning (0 = disabled, not yet active)")
                   .defineInRange("scan_interval_ticks", 0, 0, 72000);

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
