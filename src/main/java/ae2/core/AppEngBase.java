/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.core;

import ae2.api.implementations.items.AddWirelessTerminalEvent;
import ae2.capabilities.Capabilities;
import ae2.core.definitions.AEBlockEntities;
import ae2.core.definitions.AEEntities;
import ae2.core.gui.AEGuiHandler;
import ae2.core.network.InitNetwork;
import ae2.core.registries.AppEngRegistries;
import ae2.helpers.WirelessTerminalActions;
import ae2.hooks.CableBusLeftClickHook;
import ae2.hooks.SkyStoneBreakSpeed;
import ae2.hooks.WirelessTerminalEventHandler;
import ae2.hooks.WrenchHook;
import ae2.hooks.ticking.TickHandler;
import ae2.hotkeys.HotkeyActions;
import ae2.init.InitAdvancementTriggers;
import ae2.init.InitCapabilityProviders;
import ae2.init.InitDispenserBehavior;
import ae2.init.InitStats;
import ae2.init.InitVillager;
import ae2.init.internal.InitBlockEntityMoveStrategies;
import ae2.init.internal.InitGridServices;
import ae2.init.internal.InitP2PAttunements;
import ae2.init.internal.InitStorageCells;
import ae2.init.internal.InitUpgrades;
import ae2.init.worldgen.InitBiomes;
import ae2.init.worldgen.InitDimensionTypes;
import ae2.integration.Integrations;
import ae2.items.tools.NetworkAnalyserItem;
import ae2.me.ticker.RequestBox;
import ae2.me.tracker.PlayerTracker;
import ae2.recipes.AERecipeLoader;
import ae2.server.AECommand;
import ae2.server.services.ChunkLoadingService;
import ae2.server.services.compass.ServerCompassService;
import ae2.spatial.InitSpatialStorageDimension;
import ae2.worldgen.MeteoriteWorldGen;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Mod functionality that is common to both dedicated server and client.
 * <p>
 * Note that a client will still have zero or more embedded servers (although only one at a time).
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "after:jei@[4.30.3,);" +
        "before:bogosorter"
)
public final class AppEngBase implements AppEng {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);
    private static final String CLIENT_PROXY = "ae2.core.AppEngClient";
    private static final String COMMON_PROXY = "ae2.core.AppEngServer";
    @Mod.Instance(Tags.MOD_ID)
    public static AppEngBase INSTANCE;
    @SuppressWarnings("unused")
    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    private static AppEngServer runtime;
    private boolean commonBootstrapInitialized;
    private boolean commonSetupInitialized;
    private boolean postRegistrationInitialized;

    public static AppEngBase instance() {
        return INSTANCE;
    }

    public static AppEngServer runtime() {
        return Objects.requireNonNull(runtime, "AppEng runtime proxy has not been injected yet.");
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        AEConfig.init();
        LOGGER.info("{} preInit", Tags.MOD_NAME);
        initializeCommonBootstrap();
        runtime().preInit(event);
        Integrations.enqueueIMC();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("{} init", Tags.MOD_NAME);
        initializeCommonSetup();
        runtime().init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("{} postInit", Tags.MOD_NAME);
        postRegistrationInitialization();
        runtime().postInit(event);
        AERecipeLoader.loadRecipes();
        AEConfig.instance().save();
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        ChunkLoadingService.getInstance().onServerAboutToStart();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        RequestBox.clear();
        PlayerTracker.clear();
        NetworkAnalyserItem.clearCache();
        event.registerServerCommand(new AECommand(event.getServer()));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ChunkLoadingService.getInstance().onServerStopping();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        RequestBox.clear();
        PlayerTracker.clear();
        NetworkAnalyserItem.clearCache();
        ServerCompassService.clearCache();
        WirelessTerminalActions.clear();
        TickHandler.instance().shutdown();
    }

    public void postRegistrationInitialization() {
        if (this.postRegistrationInitialized) {
            return;
        }

        InitStorageCells.init();
        InitP2PAttunements.init();
        InitDispenserBehavior.init();
        InitUpgrades.init();
        this.postRegistrationInitialized = true;
    }

    private void initializeCommonBootstrap() {
        if (this.commonBootstrapInitialized) {
            return;
        }

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(AEConfig.instance());
        Capabilities.register();
        MinecraftForge.EVENT_BUS.register(new SkyStoneBreakSpeed());
        MinecraftForge.EVENT_BUS.register(new CableBusLeftClickHook());
        MinecraftForge.EVENT_BUS.register(new WrenchHook());
        MinecraftForge.EVENT_BUS.register(new WirelessTerminalEventHandler());
        MinecraftForge.EVENT_BUS.register(RequestBox.class);
        MinecraftForge.EVENT_BUS.register(PlayerTracker.class);
        TickHandler.instance().init();
        AppEngRegistries.init();
        InitGridServices.init();
        InitBlockEntityMoveStrategies.init();
        InitBiomes.init();
        InitDimensionTypes.init();
        InitSpatialStorageDimension.init();
        PlayerState.init();
        ForgeChunkManager.setForcedChunkLoadingCallback(this, ChunkLoadingService.getInstance());
        GameRegistry.registerWorldGenerator(new MeteoriteWorldGen(), 0);
        AEEntities.init();
        AEBlockEntities.init();
        InitNetwork.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new AEGuiHandler());
        InitCapabilityProviders.init();
        this.commonBootstrapInitialized = true;
    }

    private void initializeCommonSetup() {
        if (this.commonSetupInitialized) {
            return;
        }

        InitStats.init();
        InitAdvancementTriggers.init();
        InitVillager.init();
        Integrations.initOptionalIntegrations();
        AddWirelessTerminalEvent.run();
        HotkeyActions.init();
        this.commonSetupInitialized = true;
    }
}
