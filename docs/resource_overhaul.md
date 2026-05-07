# Resource Overhaul v0.1 — Create Colonial Industry

## Overview

Normal Overworld ore generation for Tier 0/1 resources is disabled.
Create Ore Excavation (COE) veins become the primary source of those materials.
A sparse starter layer is preserved to prevent immediate softlock.

---

## Files Changed

### COE vein recipe overrides
Location: `kubejs/data/createoreexcavation/recipe/ore_vein_type/`
These files override the COE defaults by placing higher-priority datapack files at the
same namespace:path. Recipe IDs are preserved so CCI Radar's mapping remains intact.

| File | spacing (was→now) | separation | amount min (was→now) | amount max (was→now) |
|------|-------------------|------------|----------------------|----------------------|
| `coal.json` | 128→32 | 8 | 15.0→22.0 | 40.0→55.0 |
| `iron.json` | 128→32 | 8 | 10.0→15.0 | 30.0→42.0 |
| `copper.json` | 128→32 | 8 | 10.0→15.0 | 30.0→42.0 |
| `zinc.json` | 128→48 | 8→12 | 8.0→12.0 | 24.0→34.0 |
| `redstone.json` | 128→64 | 16 | 10.0→14.0 | 30.0→42.0 |
| `gold.json` | 128→64 | 32→24 | 2.0→4.0 | 4.0→10.0 |

spacing = max chunks between vein grid cells (1 cell = 1 attempt).
spacing=32 → roughly 1 vein attempt per 512×512 block area.

### Biome modifier removals
Location: `kubejs/data/cci_radar_pack/neoforge/biome_modifier/`

| File | Placed features removed | Namespace |
|------|------------------------|-----------|
| `remove_vanilla_coal_ores.json` | ore_coal_lower, ore_coal_upper | minecraft |
| `remove_vanilla_iron_ores.json` | ore_iron_upper, ore_iron_middle, ore_iron_small | minecraft |
| `remove_vanilla_copper_ores.json` | ore_copper, ore_copper_large | minecraft |
| `remove_vanilla_redstone_ores.json` | ore_redstone, ore_redstone_lower | minecraft |
| `remove_vanilla_gold_ores.json` | ore_gold, ore_gold_lower, ore_gold_extra | minecraft |
| `remove_create_zinc_ore.json` | zinc_ore | create |

### Starter safety features
Location: `kubejs/data/cci_radar_pack/worldgen/placed_feature/` (placed_features)
and `kubejs/data/cci_radar_pack/neoforge/biome_modifier/add_starter_*.json` (biome hooks)

| Placed feature | Count/chunk | Depth range | Configured feature used |
|----------------|-------------|-------------|------------------------|
| `ore_coal_starter` | 1 | y 48–128 | minecraft:ore_coal (size 17) |
| `ore_iron_starter` | 1 | y 16–80 | minecraft:ore_iron (size 9) |
| `ore_copper_starter` | 1 | y 0–80 | minecraft:ore_copper_small (size 10) |

This gives ~9–17 ore blocks per 16×16 column in the relevant depth band —
sparse enough to require COE long-term, dense enough to build a first furnace.

---

## IDs Overridden

### COE vein recipes (same ID, different parameters)
- `createoreexcavation:ore_vein_type/coal`
- `createoreexcavation:ore_vein_type/iron`
- `createoreexcavation:ore_vein_type/copper`
- `createoreexcavation:ore_vein_type/zinc`
- `createoreexcavation:ore_vein_type/redstone`
- `createoreexcavation:ore_vein_type/gold`

### Placed features removed from all overworld biomes
- `minecraft:ore_coal_lower`
- `minecraft:ore_coal_upper`
- `minecraft:ore_iron_upper`
- `minecraft:ore_iron_middle`
- `minecraft:ore_iron_small`
- `minecraft:ore_copper`
- `minecraft:ore_copper_large`
- `minecraft:ore_redstone`
- `minecraft:ore_redstone_lower`
- `minecraft:ore_gold`
- `minecraft:ore_gold_lower`
- `minecraft:ore_gold_extra`
- `create:zinc_ore`

### New placed features added
- `cci_radar_pack:ore_coal_starter`
- `cci_radar_pack:ore_iron_starter`
- `cci_radar_pack:ore_copper_starter`

---

## What Remains as Starter Resources

| Resource | Generation | Notes |
|----------|-----------|-------|
| Coal | ~1/chunk y48–128, size 17 | Enough for a furnace and first tools |
| Iron | ~1/chunk y16–80, size 9 | Enough to craft the COE drill and drilling machine |
| Copper | ~1/chunk y0–80, size 10 | Enough for first brass and Create components |
| Diamond | Vanilla unchanged | Early tools + smithing table; no COE vein equivalent |
| Emerald | Vanilla unchanged | Not a CCI Radar resource |
| Lapis | Vanilla unchanged | Not a CCI Radar resource |
| Redstone | None until Tier 1 unlock | Gate is intentional — redstone is Tier 1 |
| Gold | None until Tier 1 unlock | Gate is intentional — gold is Tier 1 |
| Zinc | None until Tier 1 unlock | Scatter-mined zinc no longer exists |

---

## IE Ores — Not Implemented

Immersive Engineering ores (lead, nickel, aluminum/bauxite, silver, uranium) could not be
audited — the IE jar is not in this project's Gradle cache.

To disable IE ore generation:
1. Extract the IE jar and read `data/immersiveengineering/neoforge/biome_modifier/` to
   find the placed feature IDs for each ore.
2. Add a `neoforge:remove_features` biome modifier to `kubejs/data/cci_radar_pack/neoforge/biome_modifier/`
   using the same pattern as the vanilla removals above.
3. Optionally add those ores as CCI Radar resources (new ResourceDef entries + tier config).

---

## Validation Checklist

Run this in a fresh world after loading the pack:

- [ ] Create new world, do not use /tp or spectator
- [ ] Mine at y=60–80 for 5 minutes — iron and coal should be very rare (1–2 veins visible)
- [ ] Mine at y=20 for 5 minutes — confirm no redstone or gold
- [ ] Run `/cci_radar debug_config` — verify scan_chunks_per_tick=6, surface_hints_enabled=true
- [ ] Walk around for 60 seconds — watch for pebble surface hints appearing
- [ ] Run `/cci_radar debug_resource_distribution` — confirm coal/iron/copper veins are cached
- [ ] Run `/cci_radar unlock_tier 0` — confirm coal/iron/copper appear on JourneyMap
- [ ] Build a COE drilling machine — verify it extracts the full vein once located
- [ ] Run `/cci_radar unlock_tier 1` — confirm zinc/redstone/gold appear on JourneyMap
- [ ] Confirm COE drill running on a zinc/redstone/gold vein produces ore
- [ ] Monitor server console for worldgen errors during chunk loading
- [ ] Fly 500 blocks in creative — confirm no worldgen freeze (chunk generation completes)

---

## Known Limitations

1. **`steps` field in remove_features**: NeoForge 21.1 requires `steps` as a JSON array.
   If worldgen errors mention unknown biome modifier fields, try removing the `steps` key
   from the removal files and re-test (this makes them apply to all decoration steps, which
   is safe since these IDs only appear in `underground_ores`).

2. **Existing worlds**: Biome modifiers and placed features only affect newly generated chunks.
   Chunks already loaded in an existing world retain their original ore distribution.
   Wipe the world or test in a fresh world for accurate validation.

3. **Create zinc config**: Create's `create:config_filter` placement modifier reads
   `CWorldGen` settings from `create-server.toml`. The field name for zinc generation
   cannot be confirmed without decompiling. The biome modifier removal is used instead
   and is independent of Create's config.

4. **IE ore generation**: Not audited; not disabled. If IE is in the pack, IE ores
   (lead, nickel, aluminum, silver, uranium) will still generate normally until
   the above steps are followed.

5. **COE vein recipe override priority**: These overrides rely on KubeJS data being
   loaded at higher priority than mod data. If COE veins still use vanilla spacing
   in-game, verify with `/cci_radar debug_resource_distribution` and check the
   KubeJS log for recipe load errors.
