// ─────────────────────────────────────────────────────────────────────────────
// CCI Radar — Remove Create Ore Excavation manual discovery tools
//
// Context:
//   Colonial Resource Radar (CCI Radar) is the official resource discovery
//   layer for this pack. Players discover ore veins via:
//     - JourneyMap tier-gated overlays (colored cluster icons + material dots)
//     - Automatic surface pebble hints placed by the mod
//   Tier progression gates what resources are visible on the map.
//
//   Create Ore Excavation remains the extraction backend. Its Drilling Machine,
//   Extractor, and drill items are intentionally kept so players can build
//   automated ore extraction once they have discovered a vein through CCI Radar.
//
// Recipes removed (discovery tools replaced by CCI Radar):
//   createoreexcavation:vein_finder  — handheld scanner, shows veins in a
//                                      3-chunk radius. Bypasses CCI Radar's
//                                      tier-gated discovery progression.
//   createoreexcavation:vein_atlas   — vein notebook that records and filters
//                                      discovered veins. Replaced by CCI Radar's
//                                      persistent vein store and JourneyMap layer.
//
// Recipes intentionally kept (extraction backend — do not remove):
//   createoreexcavation:sample_drill       — Create mechanical crafting precursor
//                                            that gates the drilling_machine.
//   createoreexcavation:drill              — Iron drill (shaped, iron blocks + ingots)
//   createoreexcavation:diamond_drill      — Diamond drill upgrade
//   minecraft:netherite_drill_smithing     — Netherite drill (smithing transform)
//   createoreexcavation:drilling_machine   — Main extraction multiblock (Create MC)
//   createoreexcavation:extractor          — Fluid/ore extractor multiblock (Create MC)
//
// Verified from: create-ore-excavation-1.21.1-1.6.8.jar
//   data/createoreexcavation/recipe/vein_finder.json
//   data/createoreexcavation/recipe/vein_atlas.json
// ─────────────────────────────────────────────────────────────────────────────

ServerEvents.recipes(event => {

    // Remove Ore Vein Finder
    // Crafting: ender_eye + amethyst gem + redstone ore tag + wooden rod (shaped)
    // Removed because: handheld scanner that shows veins in a 3-chunk radius,
    // directly bypassing CCI Radar's tier-unlock and JourneyMap overlay system.
    event.remove({ id: 'createoreexcavation:vein_finder' })

    // Remove Ore Vein Atlas
    // Crafting: map + writable_book + amethyst gem + chest tag (shaped)
    // Removed because: vein recording journal that pairs with the vein_finder.
    // CCI Radar's persistent WorldVeinData and JourneyMap layer replace this.
    event.remove({ id: 'createoreexcavation:vein_atlas' })

    console.info('[CCI Radar] Removed COE discovery tool recipes: vein_finder, vein_atlas')

})
