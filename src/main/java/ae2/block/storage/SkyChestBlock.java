/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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
package ae2.block.storage;

import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.helpers.ICustomCollision;
import ae2.tile.storage.TileSkyChest;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.vecmath.Vector4f;
import java.util.List;

public class SkyChestBlock extends AEBaseTileBlock<TileSkyChest> implements ICustomCollision {
    private static final double MODEL_MIN_X = 1.0 / 16.0;
    private static final double MODEL_MIN_Y = 0.0;
    private static final double MODEL_MIN_Z = 0.0;
    private static final double MODEL_MAX_X = 15.0 / 16.0;
    private static final double MODEL_MAX_Y = 15.0 / 16.0;
    private static final double MODEL_MAX_Z = 15.0 / 16.0;
    private static final AxisAlignedBB[] ORIENTED_BOUNDS = createOrientedBounds();
    private static final List<AxisAlignedBB>[] ORIENTED_BOUND_LISTS = createOrientedBoundLists();
    public final SkyChestType type;

    public SkyChestBlock(SkyChestType type) {
        super(Material.ROCK);
        this.type = type;
        setOpaque();
        setFullSize();
        setHardness(50);
        setResistance(150.0f);
        setTileEntity(TileSkyChest.class);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @SuppressWarnings("deprecation")
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileSkyChest tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.SKY_CHEST, tile);
            }
            return true;
        }
        return false;
    }

    @Override
    public Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(World world, BlockPos pos, Entity entity,
                                                                    boolean hitFluids) {
        return ORIENTED_BOUND_LISTS[getOrientation(world, pos).ordinal()];
    }

    @Override
    public void addCollidingBlockToList(World world, BlockPos pos, AxisAlignedBB bb, List<AxisAlignedBB> out,
                                        Entity entity) {
        AxisAlignedBB blockBounds = ORIENTED_BOUNDS[getOrientation(world, pos).ordinal()];
        if (bb == null || intersectsWorld(bb, blockBounds, pos)) {
            out.add(blockBounds);
        }
    }

    private BlockOrientation getOrientation(World world, BlockPos pos) {
        TileSkyChest chest = this.getTileEntity(world, pos);
        return chest != null ? chest.getOrientation() : BlockOrientation.NORTH_UP;
    }

    private static boolean intersectsWorld(AxisAlignedBB worldBox, AxisAlignedBB localBox, BlockPos pos) {
        double minX = localBox.minX + pos.getX();
        double minY = localBox.minY + pos.getY();
        double minZ = localBox.minZ + pos.getZ();
        double maxX = localBox.maxX + pos.getX();
        double maxY = localBox.maxY + pos.getY();
        double maxZ = localBox.maxZ + pos.getZ();

        return worldBox.maxX > minX && worldBox.minX < maxX
            && worldBox.maxY > minY && worldBox.minY < maxY
            && worldBox.maxZ > minZ && worldBox.minZ < maxZ;
    }

    private static AxisAlignedBB[] createOrientedBounds() {
        AxisAlignedBB[] result = new AxisAlignedBB[BlockOrientation.values().length];
        for (BlockOrientation orientation : BlockOrientation.values()) {
            result[orientation.ordinal()] = createOrientedBound(orientation);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<AxisAlignedBB>[] createOrientedBoundLists() {
        List<AxisAlignedBB>[] result = new List[ORIENTED_BOUNDS.length];
        for (int i = 0; i < ORIENTED_BOUNDS.length; i++) {
            result[i] = List.of(ORIENTED_BOUNDS[i]);
        }
        return result;
    }

    private static AxisAlignedBB createOrientedBound(BlockOrientation orientation) {
        TRSRTransformation transformation = orientation.getTransformation();

        double minX = 1.0;
        double minY = 1.0;
        double minZ = 1.0;
        double maxX = 0.0;
        double maxY = 0.0;
        double maxZ = 0.0;
        Vector4f corner = new Vector4f();

        for (int xIndex = 0; xIndex < 2; xIndex++) {
            double x = xIndex == 0 ? MODEL_MIN_X : MODEL_MAX_X;
            for (int yIndex = 0; yIndex < 2; yIndex++) {
                double y = yIndex == 0 ? MODEL_MIN_Y : MODEL_MAX_Y;
                for (int zIndex = 0; zIndex < 2; zIndex++) {
                    double z = zIndex == 0 ? MODEL_MIN_Z : MODEL_MAX_Z;
                    corner.set((float) (x - 0.5), (float) (y - 0.5), (float) (z - 0.5), 1.0F);
                    transformation.transformPosition(corner);
                    double rotatedX = corner.x + 0.5;
                    double rotatedY = corner.y + 0.5;
                    double rotatedZ = corner.z + 0.5;

                    minX = Math.min(minX, rotatedX);
                    minY = Math.min(minY, rotatedY);
                    minZ = Math.min(minZ, rotatedZ);
                    maxX = Math.max(maxX, rotatedX);
                    maxY = Math.max(maxY, rotatedY);
                    maxZ = Math.max(maxZ, rotatedZ);
                }
            }
        }

        return new AxisAlignedBB(clamp(minX), clamp(minY), clamp(minZ), clamp(maxX), clamp(maxY), clamp(maxZ));
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    public enum SkyChestType {
        STONE, BLOCK
    }
}
