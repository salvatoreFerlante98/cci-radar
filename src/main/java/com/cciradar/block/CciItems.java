package com.cciradar.block;

import com.cciradar.ColonialResourceRadar;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CciItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ColonialResourceRadar.MODID);

    // ── Tier 0 ───────────────────────────────────────────────────────────────
    public static final DeferredItem<BlockItem> COAL_PEBBLES =
            ITEMS.registerSimpleBlockItem(CciBlocks.COAL_PEBBLES, new Item.Properties());

    public static final DeferredItem<BlockItem> IRON_PEBBLES =
            ITEMS.registerSimpleBlockItem(CciBlocks.IRON_PEBBLES, new Item.Properties());

    public static final DeferredItem<BlockItem> COPPER_PEBBLES =
            ITEMS.registerSimpleBlockItem(CciBlocks.COPPER_PEBBLES, new Item.Properties());

    // ── Tier 1 ───────────────────────────────────────────────────────────────
    public static final DeferredItem<BlockItem> ZINC_PEBBLES =
            ITEMS.registerSimpleBlockItem(CciBlocks.ZINC_PEBBLES, new Item.Properties());

    public static final DeferredItem<BlockItem> REDSTONE_PEBBLES =
            ITEMS.registerSimpleBlockItem(CciBlocks.REDSTONE_PEBBLES, new Item.Properties());

    public static final DeferredItem<BlockItem> GOLD_PEBBLES =
            ITEMS.registerSimpleBlockItem(CciBlocks.GOLD_PEBBLES, new Item.Properties());
}
