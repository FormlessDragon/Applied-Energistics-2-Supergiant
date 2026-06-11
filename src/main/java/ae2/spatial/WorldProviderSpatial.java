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

package ae2.spatial;

import ae2.client.render.SpatialSkyRender;
import ae2.init.worldgen.InitBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class WorldProviderSpatial extends WorldProvider {

    private static final IRenderHandler NO_CLOUD_RENDERER = new IRenderHandler() {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
        }
    };

    private final Biome biome;

    public WorldProviderSpatial() {
        this.hasSkyLight = false;
        this.biome = InitBiomes.getSpatialBiome();
        this.biomeProvider = new BiomeProviderSingle(this.biome);
    }

    @Override
    protected void init() {
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new SpatialStorageChunkGenerator(this.world);
    }

    @Override
    public DimensionType getDimensionType() {
        return SpatialStorageDimensionIds.getDimensionType();
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return false;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        return 0.5F;
    }

    @Override
    public boolean doesXZShowFog(int x, int z) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float @Nullable [] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
        return null;
    }

    @Override
    public Vec3d getFogColor(float p1, float p2) {
        return new Vec3d(0, 0, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isSkyColored() {
        return true;
    }

    @Override
    public boolean isDaytime() {
        return false;
    }

    @Override
    public Vec3d getSkyColor(Entity cameraEntity, float partialTicks) {
        return new Vec3d(0, 0, 0);
    }

    @Override
    public float getStarBrightness(float par1) {
        return 0;
    }

    @Override
    public boolean canSnowAt(BlockPos pos, boolean checkLight) {
        return false;
    }

    @Override
    public BlockPos getSpawnCoordinate() {
        return new BlockPos(0, 0, 0);
    }

    @Override
    public boolean isBlockHighHumidity(BlockPos pos) {
        return false;
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }

    @Override
    public Biome getBiomeForCoords(BlockPos pos) {
        return this.biome;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getSkyRenderer() {
        return SpatialSkyRender.getInstance();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getCloudRenderer() {
        return NO_CLOUD_RENDERER;
    }

    @Override
    protected void generateLightBrightnessTable() {
        Arrays.fill(this.lightBrightnessTable, 1.0F);
    }
}
