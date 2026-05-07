# Resource Overhaul — Create Colonial Industry

## Version history

| Version | Scope |
|---------|-------|
| v0.1 | Nerf/remove vanilla and Create underground ore generation. COE unchanged. |
| v0.2 | (planned) Tune COE vein spacing, separation, and amount multipliers. |

---

## v0.1 — Vanilla ore nerf

### What v0.1 does

Removes or heavily nerfs normal Overworld underground ore generation for all
Tier 0/1 resources. COE vein recipes are **not modified** — they use exactly the
defaults shipped by Create Ore Excavation. CCI Radar is untouched.

### What v0.1 does NOT do

- Does **not** change any COE vein recipe fields (spacing, separation, amount multipliers).
- Does **not** touch CCI Radar Java code or configuration.
- Does **not** modify any extraction machines.

COE vein spacing/amount balancing is deferred to v0.2 once the base nerf is validated.

---

## Files

### Biome modifier removals
Location: `kubejs/data/cci_radar_pack/neoforge/biome_modifier/`

These remove specific placed features from all overworld biomes via `neoforge:remove_features`.

| File | Placed features removed | Source mod |
|------|------------------------|------------|
| `remove_vanilla_coal_ores.json` | ore_coal_lower, ore_coal_upper | Minecraft |
| `remove_vanilla_iron_ores.json` | ore_iron_upper, ore_iron_middle, ore_iron_small | Minecraft |
| `remove_vanilla_copper_ores.json` | ore_copper, ore_copper_large | Minecraft |
| `remove_vanilla_redstone_ores.json` | ore_redstone, ore_redstone_lower | Minecraft |
| `remove_vanilla_gold_ores.json` | ore_gold, ore_gold_lower, ore_gold_extra | Minecraft |
| `remove_create_zinc_ore.json` | zinc_ore | Create |

### Starter safety features
Location: `kubejs/data/cci_radar_pack/worldgen/placed_feature/` and `add_starter_*.json` biome modifiers.

Sparse generation is kept so players can craft basic tools and the first COE machines
before a COE vein is located. Without any starter iron/coal the COE chain itself
cannot be bootstrapped (drill requires iron; drilling machine requires iron and a
working furnace).

| Placed feature | Count/chunk | Depth range | Configured feature | Vanilla count (removed) |
|----------------|-------------|-------------|-------------------|------------------------|
| `ore_coal_starter` | 1 | y 48–128 | minecraft:ore_coal (size 17) | 30+20 = 50/chunk |
| `ore_iron_starter` | 1 | y 16–80 | minecraft:ore_iron (size 9) | 90+10+10 = 110/chunk |
| `ore_copper_starter` | 1 | y 0–80 | minecraft:ore_copper_small (size 10) | 16+16 = 32/chunk |

count=1 means roughly one ore pocket per chunk column in the depth range — findable
by mining but cannot sustain a base long-term.

### COE vein recipes — unchanged
No files under `kubejs/data/createoreexcavation/` exist. COE vein recipes load
from the COE jar unchanged:

| Recipe ID | spacing | separation | amount |
|-----------|---------|------------|--------|
| `createoreexcavation:ore_vein_type/coal` | 128 | 8 | 15–40× |
| `createoreexcavation:ore_vein_type/iron` | 128 | 8 | 10–30× |
| `createoreexcavation:ore_vein_type/copper` | 128 | 8 | 10–30× |
| `createoreexcavation:ore_vein_type/zinc` | 128 | 8 | 8–24× |
| `createoreexcavation:ore_vein_type/redstone` | 128 | 16 | 10–30× |
| `createoreexcavation:ore_vein_type/gold` | 128 | 32 | 2–4× |

---

## IE Ores — Not Implemented

Immersive Engineering ores (lead, nickel, aluminum/bauxite, silver, uranium) could not
be audited — the IE jar is not in this project's Gradle cache.

To add IE ore removal:
1. Extract the IE jar, read `data/immersiveengineering/neoforge/biome_modifier/` for
   the placed feature IDs of each IE ore.
2. Add a `neoforge:remove_features` file to `kubejs/data/cci_radar_pack/neoforge/biome_modifier/`
   matching the pattern used by the vanilla removal files above.

---

## Validation Checklist

Run this in a **fresh world** after loading the pack. Existing chunks are not affected.

- [ ] Server log shows no biome modifier load errors
- [ ] Run `/cci_radar debug_config` — scan_chunks_per_tick=6, surface_hints_enabled=true
- [ ] Mine y=60–80 for 3 minutes — coal/iron extremely rare (1 pocket per ~5+ minutes typical)
- [ ] Mine y=10 for 3 minutes — confirm no redstone, no gold
- [ ] No zinc ore blocks anywhere underground (vanilla Create zinc disabled)
- [ ] Walk around surface 120 seconds — pebble surface hints appear near COE veins
- [ ] Run `/cci_radar debug_resource_distribution` — veins cached for coal/iron/copper
- [ ] Unlock tier 0: `/cci_radar unlock_tier 0` — JourneyMap shows coal/iron/copper clusters
- [ ] Build COE drilling machine on a located vein — extraction works normally
- [ ] Unlock tier 1: `/cci_radar unlock_tier 1` — zinc/redstone/gold appear on map
- [ ] Fly 500 blocks creative — no worldgen freeze (chunk gen completes promptly)

---

## Known Limitations

1. **`steps` in `neoforge:remove_features`**: encoded as a JSON array `["underground_ores"]`.
   If NeoForge 21.1 rejects this format and logs a biome modifier codec error, remove the
   `steps` key entirely — omitting it removes from all decoration steps, which is safe
   since these IDs only appear in `underground_ores`.

2. **Existing worlds**: biome modifiers only affect newly generated chunks. Test in a
   fresh world for accurate results.

3. **COE vein density**: with COE defaults (spacing=128) and vanilla scatter removed,
   veins are sparse — expect to scan 500–1000 blocks before the first vein appears.
   This is intentional in v0.1. Tuning is planned for v0.2.

4. **IE ores**: not addressed; generate normally until explicitly disabled.
