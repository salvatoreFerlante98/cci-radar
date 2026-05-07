package com.cciradar.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class CciConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Resource tiers ────────────────────────────────────────────────────────

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

    // ── Safety toggles ────────────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue AUTO_SCAN_ENABLED =
            BUILDER.comment("Enable automatic background scanning around players. Requires scan_interval_ticks > 0. Default true.")
                   .define("auto_scan_enabled", true);

    public static final ModConfigSpec.BooleanValue SCAN_ON_CHUNK_LOAD_ENABLED =
            BUILDER.comment("Enqueue newly loaded chunks for COE vein scanning. Default true.")
                   .define("scan_on_chunk_load_enabled", true);

    public static final ModConfigSpec.BooleanValue SURFACE_HINTS_ENABLED =
            BUILDER.comment("Enable automatic surface hint (pebble) placement from queued jobs. Default false.")
                   .define("surface_hints_enabled", false);

    // ── Scan radius / interval ────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue MAX_RADIUS_CHUNKS =
            BUILDER.comment("Chunk radius for background scanning and debug_scan_real (no-arg). Default 8.")
                   .defineInRange("max_radius_chunks", 8, 1, 64);

    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS =
            BUILDER.comment("Tick interval for periodic background radius scan enqueueing (0 = disabled). Default 400 = every 20 s.")
                   .defineInRange("scan_interval_ticks", 400, 0, 72000);

    // ── Queue rate limits ─────────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue SCAN_CHUNKS_PER_TICK =
            BUILDER.comment("Max chunk scan jobs processed per server tick. Default 2.")
                   .defineInRange("scan_chunks_per_tick", 2, 1, 64);

    public static final ModConfigSpec.IntValue HINT_PLACEMENTS_PER_TICK =
            BUILDER.comment("Max surface hint placement operations per server tick (0 = hints disabled regardless of surface_hints_enabled). Default 0.")
                   .defineInRange("hint_placements_per_tick", 0, 0, 16);

    public static final ModConfigSpec.IntValue MAX_PENDING_SCAN_JOBS =
            BUILDER.comment("Max pending chunk scan jobs in queue before dropping. Default 2048.")
                   .defineInRange("max_pending_scan_jobs", 2048, 64, 65536);

    public static final ModConfigSpec.IntValue MAX_PENDING_HINT_JOBS =
            BUILDER.comment("Max pending surface hint jobs in queue before dropping. Default 512.")
                   .defineInRange("max_pending_hint_jobs", 512, 16, 8192);

    // ── Misc ──────────────────────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue LOADED_CHUNKS_ONLY =
            BUILDER.comment("Scanner only reads already-loaded chunks and never forces chunk generation. Keep true.")
                   .define("loaded_chunks_only", true);

    public static final ModConfigSpec.BooleanValue PERSIST_KNOWN_VEINS =
            BUILDER.comment("Discovered vein positions persist across server restarts in world save data. Keep true.")
                   .define("persist_known_veins", true);

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
