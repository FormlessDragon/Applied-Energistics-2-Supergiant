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

package ae2.worldgen.meteorite;

import ae2.server.services.compass.ServerCompassService;
import ae2.worldgen.meteorite.fallout.FalloutMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import java.util.Random;

public class MeteoriteStructurePiece {
    private final PlacedMeteoriteSettings settings;
    private final StructureBoundingBox bounds;

    public MeteoriteStructurePiece(BlockPos center, float meteoriteRadius, CraterType craterType, FalloutMode fallout,
                                   boolean pureCrater, boolean craterLake) {
        this(new PlacedMeteoriteSettings(center, meteoriteRadius, craterType, fallout, pureCrater, craterLake));
    }

    public MeteoriteStructurePiece(NBTTagCompound tag) {
        this(new PlacedMeteoriteSettings(
            BlockPos.fromLong(tag.getLong(Constants.TAG_POS)),
            tag.getFloat(Constants.TAG_RADIUS),
            CraterType.fromOrdinal(tag.getByte(Constants.TAG_CRATER)),
            FalloutMode.fromOrdinal(tag.getByte(Constants.TAG_FALLOUT)),
            tag.getBoolean(Constants.TAG_PURE),
            tag.getBoolean(Constants.TAG_LAKE)));
    }

    public MeteoriteStructurePiece(PlacedMeteoriteSettings settings) {
        this.settings = settings;
        this.bounds = createBoundingBox(settings.pos());
    }

    private static StructureBoundingBox createBoundingBox(BlockPos origin) {
        int range = 4 * 16;
        ChunkPos chunkPos = new ChunkPos(origin);
        return new StructureBoundingBox((chunkPos.x << 4) - range, origin.getY(),
            (chunkPos.z << 4) - range, (chunkPos.x << 4) + 15 + range, origin.getY(),
            (chunkPos.z << 4) + 15 + range);
    }

    public boolean isFinalized() {
        return settings.craterType() != null;
    }

    public PlacedMeteoriteSettings getSettings() {
        return settings;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setFloat(Constants.TAG_RADIUS, settings.meteoriteRadius());
        tag.setLong(Constants.TAG_POS, settings.pos().toLong());
        tag.setByte(Constants.TAG_CRATER, (byte) settings.craterType().ordinal());
        tag.setByte(Constants.TAG_FALLOUT, (byte) settings.fallout().ordinal());
        tag.setBoolean(Constants.TAG_PURE, settings.pureCrater());
        tag.setBoolean(Constants.TAG_LAKE, settings.craterLake());
        return tag;
    }

    public boolean intersectsChunk(int chunkX, int chunkZ) {
        StructureBoundingBox chunkBounds = new StructureBoundingBox(chunkX << 4, 0, chunkZ << 4, (chunkX << 4) + 15,
            255, (chunkZ << 4) + 15);
        return bounds.intersectsWith(chunkBounds);
    }

    public void postProcess(World world, Random rand, StructureBoundingBox chunkBounds) {
        MeteoritePlacer.place(world, settings, chunkBounds, rand);
        updateCompass(world, chunkBounds);
    }

    private void updateCompass(World world, StructureBoundingBox chunkBounds) {
        if (!(world instanceof WorldServer worldServer)) {
            return;
        }
        Chunk chunk = worldServer.getChunk(chunkBounds.minX >> 4, chunkBounds.minZ >> 4);
        ServerCompassService.updateArea(worldServer, chunk);
    }
}
