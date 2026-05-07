package com.cciradar.block;

import com.cciradar.ColonialResourceRadar;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CciBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ColonialResourceRadar.MODID);

    // ── Tier 0 ───────────────────────────────────────────────────────────────
    public static final DeferredBlock<PebbleBlock> COAL_PEBBLES = BLOCKS.register(
            "coal_pebbles", () -> new PebbleBlock(pebbleProps(MapColor.COLOR_GRAY)));

    public static final DeferredBlock<PebbleBlock> IRON_PEBBLES = BLOCKS.register(
            "iron_pebbles", () -> new PebbleBlock(pebbleProps(MapColor.METAL)));

    public static final DeferredBlock<PebbleBlock> COPPER_PEBBLES = BLOCKS.register(
            "copper_pebbles", () -> new PebbleBlock(pebbleProps(MapColor.COLOR_ORANGE)));

    // ── Tier 1 ───────────────────────────────────────────────────────────────
    public static final DeferredBlock<PebbleBlock> ZINC_PEBBLES = BLOCKS.register(
            "zinc_pebbles", () -> new PebbleBlock(pebbleProps(MapColor.ICE)));

    public static final DeferredBlock<PebbleBlock> REDSTONE_PEBBLES = BLOCKS.register(
            "redstone_pebbles", () -> new PebbleBlock(pebbleProps(MapColor.COLOR_RED)));

    public static final DeferredBlock<PebbleBlock> GOLD_PEBBLES = BLOCKS.register(
            "gold_pebbles", () -> new PebbleBlock(pebbleProps(MapColor.GOLD)));

    // ── Shared props ─────────────────────────────────────────────────────────
    private static BlockBehaviour.Properties pebbleProps(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .instabreak()
                .sound(SoundType.GRAVEL)
                .noOcclusion()
                .noCollission()
                .pushReaction(PushReaction.DESTROY);
    }
}
