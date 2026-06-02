/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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
import ae2.core.network.ClientboundPacket;
import ae2.core.stats.AdvancementTriggers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface AppEng {

    String MOD_ID = Tags.MOD_ID;
    String MOD_NAME = Tags.MOD_NAME;

    static AppEng instance() {
        return AppEngBase.runtime();
    }

    static ResourceLocation makeId(String path) {
        return makeId(MOD_ID, path);
    }

    static ResourceLocation makeId(String name, String path) {
        return new ResourceLocation(name, path);
    }

    /**
     * @return A stream of all players in the game. On the client it'll be empty if no level is loaded.
     */
    default Collection<EntityPlayerMP> getPlayers() {
        throw new IllegalStateException("Missing AppEng runtime bridge for getPlayers");
    }

    /**
     * Sends a packet to all players around a position except the optionally excluded player.
     */
    default void sendToAllNearExcept(@Nullable EntityPlayer excluded, double x, double y, double z, double distance,
                                     World world, ClientboundPacket packet) {
        throw new IllegalStateException("Missing AppEng runtime bridge for sendToAllNearExcept");
    }

    /**
     * Sets the player that is currently interacting with a cable or part attached to a cable. This will return that
     * player's cable render mode from calls to {@link #getCableRenderMode()}, until another player or null is set.
     *
     * @param player Null to revert to the default cable render mode.
     */
    default void setPartInteractionPlayer(@Nullable EntityPlayer player) {
        throw new IllegalStateException("Missing AppEng runtime bridge for setPartInteractionPlayer");
    }

    default CableRenderMode getCableRenderMode() {
        throw new IllegalStateException("Missing AppEng runtime bridge for getCableRenderMode");
    }

    default AdvancementTriggers getAdvancementTriggers() {
        throw new IllegalStateException("Missing AppEng runtime bridge for getAdvancementTriggers");
    }

    default void spawnEffect(EffectType effect, World world, double posX, double posY, double posZ, Object data) {
        throw new IllegalStateException("Missing AppEng runtime bridge for spawnEffect");
    }

    @Nullable
    default World getClientWorld() {
        throw new IllegalStateException("Missing AppEng runtime bridge for getClientWorld");
    }

    /**
     * @return The current server world, if one exists.
     */
    @Nullable
    default WorldServer getCurrentServerWorld() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.worlds.length == 0) {
            return null;
        }
        return server.worlds[0];
    }

    /**
     * registers Hotkeys for {@link ae2.hotkeys.HotkeyActions}
     */
    default void registerHotkey(String id) {
        throw new IllegalStateException("Missing AppEng runtime bridge for registerHotkey");
    }
}
