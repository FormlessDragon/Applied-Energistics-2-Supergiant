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

import ae2.api.parts.CableRenderMode;
import ae2.client.ClientTickHandler;
import ae2.client.EffectType;
import ae2.client.Hotkeys;
import ae2.client.commands.ClientCommands;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.StackTooltipRenderer;
import ae2.client.gui.me.common.PendingCraftingJobs;
import ae2.client.gui.me.common.PinnedKeys;
import ae2.client.render.NetworkRender;
import ae2.client.render.ProfileRender;
import ae2.client.render.bloom.BeamFormerBloom;
import ae2.client.render.crafting.CraftingMonitorTESR;
import ae2.client.render.effects.EnergyParticleData;
import ae2.client.render.effects.LightningArcParticleData;
import ae2.client.render.effects.ParticleTypes;
import ae2.client.render.model.UVLModelLoader;
import ae2.client.render.overlay.AdvancedMemoryCardHighlightHandler;
import ae2.client.render.overlay.CraftingSupplierHighlightHandler;
import ae2.client.render.overlay.MeteoriteCompassBeaconRenderer;
import ae2.client.render.overlay.OverlayManager;
import ae2.client.render.tesr.ChargerTESR;
import ae2.client.render.tesr.CrankRenderer;
import ae2.client.render.tesr.CrystalFixerTESR;
import ae2.client.render.tesr.DenseBeamFormerTESR;
import ae2.client.render.tesr.DriveLedTESR;
import ae2.client.render.tesr.InscriberTESR;
import ae2.client.render.tesr.MEChestTESR;
import ae2.client.render.tesr.MolecularAssemblerTESR;
import ae2.client.render.tesr.SkyChestTESR;
import ae2.client.render.tesr.SkyStoneTankBlockEntityRenderer;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.ExportedGridContent;
import ae2.core.network.serverbound.MouseWheelPacket;
import ae2.core.network.serverbound.UpdateHoldingCtrlPacket;
import ae2.entity.TinyTNTPrimedEntity;
import ae2.entity.TinyTNTPrimedRenderer;
import ae2.helpers.IMouseWheelItem;
import ae2.hooks.CompassManager;
import ae2.hooks.RenderBlockOutlineHook;
import ae2.hooks.WirelessTerminalPickBlockHook;
import ae2.hooks.WirelessUniversalTerminalClientHandler;
import ae2.init.client.InitBlockColors;
import ae2.init.client.InitBuiltInModels;
import ae2.init.client.InitGuis;
import ae2.init.client.InitItemColors;
import ae2.init.client.InitItemModelsProperties;
import ae2.init.client.InitParticleTypes;
import ae2.init.client.InitStackRenderHandlers;
import ae2.integration.Integrations;
import ae2.tile.crafting.TileCraftingMonitor;
import ae2.tile.crafting.TileMolecularAssembler;
import ae2.tile.misc.TileCharger;
import ae2.tile.misc.TileCrank;
import ae2.tile.misc.TileCrystalFixer;
import ae2.tile.misc.TileInscriber;
import ae2.tile.networking.CableBusTESR;
import ae2.tile.networking.TileCableBus;
import ae2.tile.networking.TileDenseBeamFormer;
import ae2.tile.storage.TileDrive;
import ae2.tile.storage.TileMEChest;
import ae2.tile.storage.TileSkyChest;
import ae2.tile.storage.TileSkyStoneTank;
import ae2.util.MouseHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.util.Random;

@SideOnly(Side.CLIENT)
public final class AppEngClient extends AppEngServer {

    private static final KeyBinding MOUSE_WHEEL_ITEM_MODIFIER = new KeyBinding(
        "key.ae2.mouse_wheel_item_modifier", Keyboard.KEY_LSHIFT, "key.ae2.category");
    private static final KeyBinding PART_PLACEMENT_OPPOSITE = new KeyBinding(
        "key.ae2.part_placement_opposite", Keyboard.KEY_LCONTROL, "key.ae2.category");

    private CableRenderMode prevCableRenderMode = CableRenderMode.STANDARD;
    private boolean prevPartPlacementOppositeDown;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        if (!FMLClientHandler.instance().hasOptifine() && ForgeModContainer.forgeLightPipelineEnabled) {
            ModelLoaderRegistry.registerLoader(UVLModelLoader.INSTANCE);
        }
        InitBuiltInModels.init();
        InitItemModelsProperties.init();
        InitParticleTypes.init();
        InitStackRenderHandlers.init();
        RenderingRegistry.registerEntityRenderingHandler(TinyTNTPrimedEntity.class, TinyTNTPrimedRenderer::new);
        ClientRegistry.bindTileEntitySpecialRenderer(TileCableBus.class, new CableBusTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileDenseBeamFormer.class, new DenseBeamFormerTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileCraftingMonitor.class, new CraftingMonitorTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileDrive.class, new DriveLedTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileMEChest.class, new MEChestTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileSkyChest.class, new SkyChestTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileCharger.class, new ChargerTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileCrystalFixer.class, new CrystalFixerTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileInscriber.class, new InscriberTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileCrank.class, new CrankRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileMolecularAssembler.class, new MolecularAssemblerTESR());
        ClientRegistry.bindTileEntitySpecialRenderer(TileSkyStoneTank.class, new SkyStoneTankBlockEntityRenderer());
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        InitGuis.init();
        InitBlockColors.init();
        InitItemColors.init();
        ClientRegistry.registerKeyBinding(MOUSE_WHEEL_ITEM_MODIFIER);
        ClientRegistry.registerKeyBinding(PART_PLACEMENT_OPPOSITE);
        ClientCommands.register();
        MinecraftForge.EVENT_BUS.register(new RenderBlockOutlineHook());
        MinecraftForge.EVENT_BUS.register(new WirelessUniversalTerminalClientHandler());
        MinecraftForge.EVENT_BUS.register(new MeteoriteCompassBeaconRenderer());
        MinecraftForge.EVENT_BUS.register(OverlayManager.getInstance());
        MinecraftForge.EVENT_BUS.register(CraftingSupplierHighlightHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AdvancedMemoryCardHighlightHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(StackTooltipRenderer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NetworkRender.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ProfileRender.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BeamFormerBloom.INSTANCE);
        MinecraftForge.EVENT_BUS.register(MouseHelper.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent(priority = EventPriority.HIGH)
            public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
                if (event.getGui() instanceof AEBaseGui<?> a) {
                    if (inGui(a, MouseHelper.getMouseX(), MouseHelper.getMouseY()) && a.handleAeMouseWheelInput()) {
                        event.setCanceled(true);
                        event.setResult(Event.Result.ALLOW);
                    }
                }
            }

            private boolean inGui(AEBaseGui<?> gui, int x, int y) {
                if (gui.getBounds(true).contains(x, y)) {
                    return true;
                }
                for (Rectangle rectangle : gui.getArrayExclusionZones()) {
                    if (rectangle.contains(x, y)) {
                        return true;
                    }
                }
                return false;
            }
        });
        Integrations.hei().registerClientFeatures();
    }

    @Override
    public boolean shouldAddParticles(Random rand) {
        int setting = Minecraft.getMinecraft().gameSettings.particleSetting;
        return switch (setting) {
            case 0 -> true;
            case 1 -> rand.nextBoolean();
            default -> false;
        };
    }

    @Override
    public boolean shouldSpawnParticleEffects(World world) {
        return this.shouldAddParticles(world.rand);
    }

    @Override
    public void spawnEffect(EffectType effect, World world, double posX, double posY, double posZ, Object data) {
        if (world == null || !world.isRemote) {
            return;
        }

        switch (effect) {
            case Energy -> this.spawnEnergy(world, posX, posY, posZ, data);
            case Lightning -> this.spawnLightning(world, posX, posY, posZ, data);
            case Vibrant -> this.spawnVibrant(world, posX, posY, posZ);
            default -> {
            }
        }
    }

    @Nullable
    @Override
    public World getClientWorld() {
        return Minecraft.getMinecraft().world;
    }

    @Override
    public void registerHotkey(String id) {
        Hotkeys.registerHotkey(id);
    }

    @Override
    public CableRenderMode getCableRenderMode() {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            return super.getCableRenderMode();
        }

        var player = Minecraft.getMinecraft().player;
        if (player == null) {
            return CableRenderMode.STANDARD;
        }

        return this.getCableRenderModeForPlayer(player);
    }

    @SubscribeEvent
    public void clientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clearClientSessionState();
    }

    @SubscribeEvent
    public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearClientSessionState();
    }

    @SubscribeEvent
    public void clientWorldUnload(WorldEvent.Unload event) {
        World level = event.getWorld();
        if (level != null && level.isRemote) {
            CompassManager.INSTANCE.clear();
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            WirelessTerminalPickBlockHook.tick();
            updateCableRenderMode();
            syncPartPlacementOppositeState();
            return;
        }

        Hotkeys.checkHotkeys();
        if (Minecraft.getMinecraft().currentScreen == null) {
            PinnedKeys.prune();
        }
    }

    @SubscribeEvent
    public void mouseInput(MouseEvent event) {
        handleMouseWheelEvent(event);
    }

    private void handleMouseWheelEvent(MouseEvent event) {
        if (event.getDwheel() == 0) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || !MOUSE_WHEEL_ITEM_MODIFIER.isKeyDown()) {
            return;
        }

        boolean mainHandItem = mc.player.getHeldItemMainhand().getItem() instanceof IMouseWheelItem;
        boolean offHandItem = mc.player.getHeldItemOffhand().getItem() instanceof IMouseWheelItem;
        if (!mainHandItem && !offHandItem) {
            return;
        }

        InitNetwork.sendToServer(new MouseWheelPacket(event.getDwheel() > 0));
        event.setCanceled(true);
    }


    private void updateCableRenderMode() {
        CableRenderMode currentMode = getCableRenderMode();
        if (currentMode == this.prevCableRenderMode) {
            return;
        }

        this.prevCableRenderMode = currentMode;
        refreshCableBusRenderState();
    }

    private void syncPartPlacementOppositeState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            this.prevPartPlacementOppositeDown = false;
            return;
        }

        boolean isDown = mc.currentScreen == null && PART_PLACEMENT_OPPOSITE.isKeyDown();
        if (isDown == this.prevPartPlacementOppositeDown) {
            return;
        }

        this.prevPartPlacementOppositeDown = isDown;
        PlayerState.setHoldingCtrl(mc.player, isDown);
        InitNetwork.sendToServer(new UpdateHoldingCtrlPacket(isDown));
    }

    private void refreshCableBusRenderState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }

        int viewDistance = (Math.max(2, mc.gameSettings.renderDistanceChunks) + 1) * 16;
        double viewDistanceSq = (double) viewDistance * viewDistance;

        for (var loadedTileEntity : mc.world.loadedTileEntityList) {
            if (!(loadedTileEntity instanceof TileCableBus tileEntity)) {
                continue;
            }

            if (tileEntity.isInvalid()) {
                continue;
            }

            BlockPos pos = tileEntity.getPos();
            if (!mc.world.isBlockLoaded(pos) || mc.player.getDistanceSqToCenter(pos) > viewDistanceSq) {
                continue;
            }

            mc.world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    @SubscribeEvent
    public void registerTextures(TextureStitchEvent.Pre event) {
        event.getMap().registerSprite(AppEng.makeId("block/molecular_assembler_lights"));
        InscriberTESR.registerTexture(event);
        InitParticleTypes.registerTextures(event.getMap());
    }

    private void spawnEnergy(World world, double posX, double posY, double posZ, Object data) {
        if (!shouldAddParticles(world.rand)) {
            return;
        }

        Random random = world.rand;
        final double x = (Math.abs(random.nextInt()) % 100 * 0.01 - 0.5) * 0.7;
        final double y = (Math.abs(random.nextInt()) % 100 * 0.01 - 0.5) * 0.7;
        final double z = (Math.abs(random.nextInt()) % 100 * 0.01 - 0.5) * 0.7;
        EnergyParticleData particleData = data instanceof EnergyParticleData ? (EnergyParticleData) data : EnergyParticleData.FOR_BLOCK;
        ParticleTypes.ENERGY.spawn(world, posX + x, posY + y, posZ + z, -x * 0.1, -y * 0.1, -z * 0.1,
            particleData);
    }

    private void spawnLightning(World world, double posX, double posY, double posZ, Object data) {
        if (!AEConfig.instance().isEnableEffects()) {
            return;
        }

        if (data instanceof LightningArcParticleData lightningArcParticleData) {
            ParticleTypes.LIGHTNING_ARC.spawn(world, posX, posY, posZ, 1.0, 1.0, 1.0, lightningArcParticleData);
            return;
        }
        ParticleTypes.LIGHTNING.spawn(world, posX, posY + 0.3f, posZ, 1.0, 1.0, 1.0, null);
    }

    private void spawnVibrant(World world, double posX, double posY, double posZ) {
        if (!shouldAddParticles(world.rand)) {
            return;
        }

        final double d0 = (world.rand.nextFloat() - 0.5F) * 0.26D;
        final double d1 = (world.rand.nextFloat() - 0.5F) * 0.26D;
        final double d2 = (world.rand.nextFloat() - 0.5F) * 0.26D;

        ParticleTypes.VIBRANT.spawn(world, posX + d0, posY + d1, posZ + d2, 0.0D, 0.0D, 0.0D, null);
    }

    private void clearClientSessionState() {
        this.prevCableRenderMode = CableRenderMode.STANDARD;
        this.prevPartPlacementOppositeDown = false;
        var player = Minecraft.getMinecraft().player;
        if (player != null) {
            PlayerState.setHoldingCtrl(player, false);
        }
        ExportedGridContent.clearActiveExports();
        CompassManager.INSTANCE.clear();
        AdvancedMemoryCardHighlightHandler.INSTANCE.clear();
        PendingCraftingJobs.clearPendingJobs();
        PinnedKeys.clearPinnedKeys();
    }

}
