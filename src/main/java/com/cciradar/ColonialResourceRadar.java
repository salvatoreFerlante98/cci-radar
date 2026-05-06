package com.cciradar;

import com.cciradar.command.CciCommands;
import com.cciradar.config.CciConfig;
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

@Mod(ColonialResourceRadar.MODID)
public class ColonialResourceRadar {

    public static final String MODID = "cci_radar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColonialResourceRadar(IEventBus modEventBus, ModContainer modContainer) {
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
        LOGGER.info("[CCI Radar] Server starting — effective config:");
        LOGGER.info("[CCI Radar]   max_radius_chunks    = {}", CciConfig.MAX_RADIUS_CHUNKS.getAsInt());
        LOGGER.info("[CCI Radar]   scan_interval_ticks  = {}", CciConfig.SCAN_INTERVAL_TICKS.getAsInt());
        LOGGER.info("[CCI Radar]   loaded_chunks_only   = {}", CciConfig.LOADED_CHUNKS_ONLY.get());
        LOGGER.info("[CCI Radar]   persist_known_veins  = {}", CciConfig.PERSIST_KNOWN_VEINS.get());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CciCommands.register(event.getDispatcher());
    }
}
