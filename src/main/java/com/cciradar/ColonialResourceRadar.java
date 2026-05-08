package com.cciradar;

import com.cciradar.block.CciBlocks;
import com.cciradar.block.CciItems;
import com.cciradar.command.CciCommands;
import com.cciradar.config.CciConfig;
import com.cciradar.server.CciScanQueue;
import com.cciradar.server.cci.CciCoreVeinBridge;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(ColonialResourceRadar.MODID)
public class ColonialResourceRadar {

    public static final String MODID = "cci_radar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColonialResourceRadar(IEventBus modEventBus, ModContainer modContainer) {
        CciBlocks.BLOCKS.register(modEventBus);
        CciItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, CciConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[CCI Radar] Common setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CciScanQueue.reset();
        // Initialize cci_core bridge: capture server reference and register the
        // CciVeinEvents listener (idempotent). Provider-first mode activates as soon as
        // any authoritative provider (e.g. cci_world) registers itself with cci_core.
        CciCoreVeinBridge.onServerStarting(event.getServer());
        LOGGER.info("[CCI Radar] cci_core bridge initialized. Provider mode active = {} (sourceId = {})",
                CciCoreVeinBridge.isProviderModeActive(), CciCoreVeinBridge.activeSourceId());
        LOGGER.info("[CCI Radar] Server starting — effective safety config:");
        LOGGER.info("[CCI Radar]   auto_scan_enabled         = {}", CciConfig.AUTO_SCAN_ENABLED.get());
        LOGGER.info("[CCI Radar]   scan_on_chunk_load_enabled = {}", CciConfig.SCAN_ON_CHUNK_LOAD_ENABLED.get());
        LOGGER.info("[CCI Radar]   surface_hints_enabled      = {}", CciConfig.SURFACE_HINTS_ENABLED.get());
        LOGGER.info("[CCI Radar]   max_radius_chunks          = {}", CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
        LOGGER.info("[CCI Radar]   scan_interval_ticks        = {}", CciConfig.SCAN_INTERVAL_TICKS.getAsInt());
        LOGGER.info("[CCI Radar]   scan_chunks_per_tick       = {}", CciConfig.SCAN_CHUNKS_PER_TICK.getAsInt());
        LOGGER.info("[CCI Radar]   hint_placements_per_tick   = {}", CciConfig.HINT_PLACEMENTS_PER_TICK.getAsInt());
        LOGGER.info("[CCI Radar]   loaded_chunks_only         = {}", CciConfig.LOADED_CHUNKS_ONLY.get());
        LOGGER.info("[CCI Radar]   persist_known_veins        = {}", CciConfig.PERSIST_KNOWN_VEINS.get());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CciCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CciCoreVeinBridge.onServerStopping(event.getServer());
    }
}
