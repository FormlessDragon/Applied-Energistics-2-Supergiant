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

package appeng.core;

import appeng.api.implementations.items.AddWirelessTerminalEvent;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEEntities;
import appeng.core.gui.AEGuiHandler;
import appeng.core.network.InitNetwork;
import appeng.core.registries.AppEngRegistries;
import appeng.hooks.CableBusLeftClickHook;
import appeng.hooks.SkyStoneBreakSpeed;
import appeng.hooks.WirelessTerminalEventHandler;
import appeng.hooks.WrenchHook;
import appeng.hooks.ticking.TickHandler;
import appeng.hotkeys.HotkeyActions;
import appeng.init.InitAdvancementTriggers;
import appeng.init.InitCapabilityProviders;
import appeng.init.InitDispenserBehavior;
import appeng.init.InitStats;
import appeng.init.InitVillager;
import appeng.init.internal.InitBlockEntityMoveStrategies;
import appeng.init.internal.InitGridServices;
import appeng.init.internal.InitP2PAttunements;
import appeng.init.internal.InitStorageCells;
import appeng.init.internal.InitUpgrades;
import appeng.init.worldgen.InitBiomes;
import appeng.init.worldgen.InitDimensionTypes;
import appeng.integration.Integrations;
import appeng.recipes.AERecipeLoader;
import appeng.server.AECommand;
import appeng.server.services.ChunkLoadingService;
import appeng.spatial.InitSpatialStorageDimension;
import appeng.worldgen.MeteoriteWorldGen;
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

import java.io.File;
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
    private static final String CLIENT_PROXY = "appeng.core.AppEngClient";
    private static final String COMMON_PROXY = "appeng.core.AppEngServer";
    @Mod.Instance(Tags.MOD_ID)
    public static AppEngBase INSTANCE;
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
        AEConfig.init(new File(event.getModConfigurationDirectory(), "ae2.cfg"));
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
        event.registerServerCommand(new AECommand(event.getServer()));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ChunkLoadingService.getInstance().onServerStopping();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
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
        Capabilities.register();
        MinecraftForge.EVENT_BUS.register(new SkyStoneBreakSpeed());
        MinecraftForge.EVENT_BUS.register(new CableBusLeftClickHook());
        MinecraftForge.EVENT_BUS.register(new WrenchHook());
        MinecraftForge.EVENT_BUS.register(new WirelessTerminalEventHandler());
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
