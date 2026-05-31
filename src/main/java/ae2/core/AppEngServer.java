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
import ae2.client.EffectType;
import ae2.core.definitions.AEItems;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.InitNetwork;
import ae2.core.stats.AdvancementTriggers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Random;

public class AppEngServer implements AppEng {

    private final ThreadLocal<EntityPlayer> partInteractionPlayer = new ThreadLocal<>();
    private AdvancementTriggers advancementTriggers;

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    @Override
    public Collection<EntityPlayerMP> getPlayers() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return Collections.emptyList();
        }
        return server.getPlayerList().getPlayers();
    }

    @Override
    public void sendToAllNearExcept(@Nullable EntityPlayer excluded, double x, double y, double z, double distance,
                                    World world, ClientboundPacket packet) {
        InitNetwork.sendToAllNearExcept(excluded, x, y, z, distance, world, packet);
    }

    @Override
    public void setPartInteractionPlayer(@Nullable EntityPlayer player) {
        this.partInteractionPlayer.set(player);
    }

    @Override
    public CableRenderMode getCableRenderMode() {
        return this.getCableRenderModeForPlayer(this.partInteractionPlayer.get());
    }

    protected CableRenderMode getCableRenderModeForPlayer(@Nullable EntityPlayer player) {
        if (player != null) {
            if (AEItems.NETWORK_TOOL.is(player.getHeldItem(EnumHand.MAIN_HAND))
                || AEItems.NETWORK_TOOL.is(player.getHeldItem(EnumHand.OFF_HAND))) {
                return CableRenderMode.CABLE_VIEW;
            }
        }

        return CableRenderMode.STANDARD;
    }

    @Override
    public AdvancementTriggers getAdvancementTriggers() {
        if (this.advancementTriggers == null) {
            throw new IllegalStateException("Advancement triggers have not been initialized");
        }
        return this.advancementTriggers;
    }

    public void setAdvancementTriggers(AdvancementTriggers advancementTriggers) {
        this.advancementTriggers = advancementTriggers;
    }

    @Override
    public void spawnEffect(EffectType effect, World world, double posX, double posY, double posZ, Object data) {
    }

    @Nullable
    @Override
    public World getClientWorld() {
        return null;
    }

    @Nullable
    @Override
    public WorldServer getCurrentServerWorld() {
        return AppEng.super.getCurrentServerWorld();
    }

    @Override
    public void registerHotkey(String id) {
    }

    public boolean shouldAddParticles(Random rand) {
        return false;
    }

    public boolean shouldSpawnParticleEffects(World world) {
        return false;
    }
}
