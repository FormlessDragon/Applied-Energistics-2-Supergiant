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

package ae2.api.features;

import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.worlddata.AESavedData;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.MapStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Handles the matching between UUIDs and internal IDs for security systems. This whole system could be replaced by
 * storing directly the UUID, using a lot more traffic though
 *
 * @author thatsIch
 * @version rv3 - 30.05.2015
 * @since rv3 30.05.2015
 */
public final class PlayerRegistryInternal extends AESavedData implements IPlayerRegistry {

    private static final String NAME = AppEng.MOD_ID + "_players";
    private static final String TAG_PLAYER_IDS = "playerIds";
    private static final String TAG_PROFILE_IDS = "profileIds";

    private final BiMap<UUID, Integer> mapping = HashBiMap.create();

    @Nullable
    private MinecraftServer server;

    private int nextPlayerId = 0;

    public PlayerRegistryInternal() {
        this(NAME);
    }

    public PlayerRegistryInternal(String name) {
        super(name);
    }

    static PlayerRegistryInternal get(MinecraftServer server) {
        var overworld = server.getWorld(0);
        if (overworld == null) {
            throw new IllegalStateException("Cannot retrieve player data for a server that has no overworld.");
        }

        MapStorage storage = overworld.getMapStorage();
        PlayerRegistryInternal result = (PlayerRegistryInternal) storage.getOrLoadData(PlayerRegistryInternal.class,
            PlayerRegistryInternal.NAME);
        if (result == null) {
            result = new PlayerRegistryInternal(PlayerRegistryInternal.NAME);
            storage.setData(PlayerRegistryInternal.NAME, result);
        }
        result.bind(server);
        return result;
    }

    private static long unpackLong(int[] values, int offset) {
        return ((long) values[offset] << 32) | (values[offset + 1] & 0xffffffffL);
    }

    private static void packLong(int[] values, int offset, long value) {
        values[offset] = (int) (value >> 32);
        values[offset + 1] = (int) value;
    }

    private void bind(MinecraftServer server) {
        this.server = server;
    }

    @Nullable
    @Override
    public UUID getProfileId(int playerId) {
        return this.mapping.inverse().get(playerId);
    }

    @Override
    public int getPlayerId(UUID profileId) {
        Objects.requireNonNull(profileId, "profileId");

        Integer playerId = mapping.get(profileId);

        if (playerId == null) {
            playerId = this.nextPlayerId++;
            this.mapping.put(profileId, playerId);
            markDirty();

            var currentServer = Objects.requireNonNull(this.server, "server");
            var player = currentServer.getPlayerList().getPlayerByUUID(profileId);
            var name = player != null ? player.getGameProfile().getName() : "[UNKNOWN]";
            AELog.info("Assigning ME player id %s to Minecraft profile %s (%s)", playerId, profileId, name);
        }

        return playerId;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.mapping.clear();

        int[] playerIds = nbt.getIntArray(TAG_PLAYER_IDS);
        int[] profileIdParts = nbt.getIntArray(TAG_PROFILE_IDS);

        if (playerIds.length * 4 != profileIdParts.length) {
            throw new IllegalStateException("Player ID mapping is corrupted. " + playerIds.length + " player IDs vs. "
                + profileIdParts.length + " profile ID parts (latter must be 4 * the former)");
        }

        int highestPlayerId = -1;
        for (int i = 0; i < playerIds.length; i++) {
            int playerId = playerIds[i];
            UUID profileId = new UUID(unpackLong(profileIdParts, i * 4), unpackLong(profileIdParts, i * 4 + 2));
            highestPlayerId = Math.max(playerId, highestPlayerId);
            this.mapping.put(profileId, playerId);
            AELog.debug("AE player ID %s is assigned to profile ID %s", playerId, profileId);
        }
        this.nextPlayerId = highestPlayerId + 1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        int index = 0;
        int[] playerIds = new int[mapping.size()];
        int[] profileIdParts = new int[mapping.size() * 4];
        for (var entry : mapping.entrySet()) {
            packLong(profileIdParts, index * 4, entry.getKey().getMostSignificantBits());
            packLong(profileIdParts, index * 4 + 2, entry.getKey().getLeastSignificantBits());
            playerIds[index] = entry.getValue();
            index++;
        }

        compound.setIntArray(TAG_PLAYER_IDS, playerIds);
        compound.setIntArray(TAG_PROFILE_IDS, profileIdParts);

        return compound;
    }
}

