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
            BUILDER.comment("Enable automatic surface hint (pebble) placement from queued jobs. Default true.")
                   .define("surface_hints_enabled", true);

    // ── Scan radius / interval ────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue MAX_RADIUS_CHUNKS =
            BUILDER.comment("Chunk radius for background scanning and debug_scan_real (no-arg). Default 8.")
                   .defineInRange("max_radius_chunks", 8, 1, 64);

    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS =
            BUILDER.comment("Tick interval for periodic background radius scan enqueueing (0 = disabled). Default 200 = every 10 s.")
                   .defineInRange("scan_interval_ticks", 200, 0, 72000);

    // ── Queue rate limits ─────────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue SCAN_CHUNKS_PER_TICK =
            BUILDER.comment("Max chunk scan jobs processed per server tick. Default 6.")
                   .defineInRange("scan_chunks_per_tick", 6, 1, 64);

    public static final ModConfigSpec.IntValue HINT_PLACEMENTS_PER_TICK =
            BUILDER.comment("Max surface hint placement operations per server tick (0 = hints disabled regardless of surface_hints_enabled). Default 1.")
                   .defineInRange("hint_placements_per_tick", 1, 0, 16);

    public static final ModConfigSpec.IntValue MAX_PENDING_SCAN_JOBS =
            BUILDER.comment("Max pending chunk scan jobs in queue before dropping. Default 8192.")
                   .defineInRange("max_pending_scan_jobs", 8192, 64, 65536);

    public static final ModConfigSpec.IntValue MAX_PENDING_HINT_JOBS =
            BUILDER.comment("Max pending surface hint jobs in queue before dropping. Default 512.")
                   .defineInRange("max_pending_hint_jobs", 512, 16, 8192);

    // ── Surface hint placement ─────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue SURFACE_HINTS_PER_CHUNK_MIN =
            BUILDER.comment("Minimum pebble blocks to place per chunk hint job. Default 2.")
                   .defineInRange("surface_hints_per_chunk_min", 2, 1, 16);

    public static final ModConfigSpec.IntValue SURFACE_HINTS_PER_CHUNK_MAX =
            BUILDER.comment("Maximum pebble blocks to place per chunk hint job. Default 4.")
                   .defineInRange("surface_hints_per_chunk_max", 4, 1, 16);

    public static final ModConfigSpec.IntValue SURFACE_HINT_ATTEMPTS_PER_CHUNK =
            BUILDER.comment("Max block positions tried per hint job when searching for valid surface spots. Default 48.")
                   .defineInRange("surface_hint_attempts_per_chunk", 48, 8, 256);

    public static final ModConfigSpec.IntValue SURFACE_HINT_RETRY_LIMIT =
            BUILDER.comment("Max failed placement attempts before permanently skipping a chunk/resource (0 = unlimited). Default 3.")
                   .defineInRange("surface_hint_retry_limit", 3, 0, 10);

    public static final ModConfigSpec.IntValue SURFACE_HINT_RETRY_COOLDOWN_TICKS =
            BUILDER.comment("Ticks to wait between retry attempts after 0 valid positions found. Default 600 (30 s).")
                   .defineInRange("surface_hint_retry_cooldown_ticks", 600, 0, 72000);

    public static final ModConfigSpec.BooleanValue SURFACE_HINT_REPLACE_THIN_VEGETATION =
            BUILDER.comment("Allow pebbles to replace short_grass, fern, and dead_bush on valid support. Default true.")
                   .define("surface_hint_replace_thin_vegetation", true);

    // ── Surface hint backfill ─────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue SURFACE_HINT_BACKFILL_ENABLED =
            BUILDER.comment("Enable periodic backfill pass: enqueues hint jobs for cached veins not yet hinted. Default true.")
                   .define("surface_hint_backfill_enabled", true);

    public static final ModConfigSpec.IntValue SURFACE_HINT_BACKFILL_INTERVAL_TICKS =
            BUILDER.comment("Tick interval between hint backfill passes (0 = disabled, independent of scan_interval_ticks). Default 600 = every 30 s.")
                   .defineInRange("surface_hint_backfill_interval_ticks", 600, 0, 72000);

    public static final ModConfigSpec.IntValue SURFACE_HINT_BACKFILL_MAX_CHECKS =
            BUILDER.comment("Max cached vein entries checked per backfill pass (prevents stall on large vein lists). Default 128.")
                   .defineInRange("surface_hint_backfill_max_checks", 128, 8, 4096);

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
