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
package appeng.block.misc;

import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AEAxisAlignedBB;
import appeng.block.AEBaseTileBlock;
import appeng.client.render.effects.LightningArcParticleData;
import appeng.client.render.effects.ParticleTypes;
import appeng.core.AEConfig;
import appeng.core.AppEngBase;
import appeng.helpers.ICustomCollision;
import appeng.tile.misc.ChargerRecipes;
import appeng.tile.misc.TileCharger;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Random;

@SuppressWarnings("deprecation")
public class ChargerBlock extends AEBaseTileBlock<TileCharger> implements ICustomCollision {
    public ChargerBlock() {
        super(Material.IRON);
        setOpaque();
        setFullSize();
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TileCharger.class);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        if (!AEConfig.instance().isEnableEffects()) {
            return;
        }

        TileCharger tile = this.getTileEntity(world, pos);
        if (tile == null || !tile.isWorking()) {
            return;
        }

        if (random.nextFloat() < 0.5f) {
            return;
        }

        var up = tile.getOrientation().getSide(RelativeSide.TOP);
        var forward = tile.getOrientation().getSide(RelativeSide.FRONT);
        Vec3i side = forward.getDirectionVec().crossProduct(up.getDirectionVec());

        for (int bolts = 0; bolts < 3; bolts++) {
            float xOff = (random.nextFloat() * 0.3f) - 0.15f;
            float zOff = (random.nextFloat() * 0.3f) - 0.15f;

            double centerX = pos.getX() + 0.5D;
            double centerY = pos.getY() + 0.5D;
            double centerZ = pos.getZ() + 0.5D;

            double originX = centerX + side.getX() * xOff + up.getXOffset() * -0.3D + forward.getXOffset() * zOff;
            double originY = centerY + side.getY() * xOff + up.getYOffset() * -0.3D + forward.getYOffset() * zOff;
            double originZ = centerZ + side.getZ() * xOff + up.getZOffset() * -0.3D + forward.getZOffset() * zOff;

            double targetX = centerX + side.getX() * xOff + up.getXOffset() * 0.3D + forward.getXOffset() * zOff;
            double targetY = centerY + side.getY() * xOff + up.getYOffset() * 0.3D + forward.getYOffset() * zOff;
            double targetZ = centerZ + side.getZ() * xOff + up.getZOffset() * 0.3D + forward.getZOffset() * zOff;

            if (random.nextBoolean()) {
                double swapX = targetX;
                double swapY = targetY;
                double swapZ = targetZ;
                targetX = originX;
                targetY = originY;
                targetZ = originZ;
                originX = swapX;
                originY = swapY;
                originZ = swapZ;
            }

            if (AppEngBase.runtime().shouldAddParticles(random)) {
                ParticleTypes.LIGHTNING_ARC.spawn(world, originX, originY, originZ, 1.0D, 1.0D, 1.0D,
                    new LightningArcParticleData(targetX, targetY, targetZ));
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        if (player.isSneaking()) {
            return false;
        }

        TileCharger tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return false;
        }

        ItemStack held = player.getHeldItem(hand);
        ItemStack chargingItem = tile.getInternalInventory().getStackInSlot(0);

        if (chargingItem.isEmpty()) {
            if (!held.isEmpty() && (held.getItem() instanceof IAEItemPowerStorage || ChargerRecipes.allowInsert(held))) {
                if (!world.isRemote) {
                    tile.getInternalInventory().setItemDirect(0, held.splitStack(1));
                }
                return true;
            }
        } else if (held.isEmpty()) {
            if (!world.isRemote) {
                List<ItemStack> drops = new ObjectArrayList<>();
                drops.add(chargingItem.copy());
                tile.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
                Platform.spawnDrops(world, pos.offset(tile.getOrientation().getSide(RelativeSide.FRONT)), drops);
            }
            return true;
        }

        return false;
    }

    @Override
    public Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(World world, BlockPos pos, Entity entity,
                                                                    boolean hitFluids) {
        TileCharger tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return Collections.singletonList(FULL_BLOCK_AABB);
        }

        final double twoPixels = 2.0 / 16.0;
        final EnumFacing up = tile.getOrientation().getSide(RelativeSide.TOP);
        final EnumFacing forward = tile.getOrientation().getSide(RelativeSide.FRONT);
        final AEAxisAlignedBB bb = new AEAxisAlignedBB(twoPixels, twoPixels, twoPixels, 1.0 - twoPixels,
            1.0 - twoPixels, 1.0 - twoPixels);

        if (up.getXOffset() != 0) {
            bb.minX = 0;
            bb.maxX = 1;
        }
        if (up.getYOffset() != 0) {
            bb.minY = 0;
            bb.maxY = 1;
        }
        if (up.getZOffset() != 0) {
            bb.minZ = 0;
            bb.maxZ = 1;
        }

        switch (forward) {
            case DOWN -> bb.maxY = 1;
            case UP -> bb.minY = 0;
            case NORTH -> bb.maxZ = 1;
            case SOUTH -> bb.minZ = 0;
            case EAST -> bb.minX = 0;
            case WEST -> bb.maxX = 1;
            default -> {
            }
        }

        return Collections.singletonList(bb.getBoundingBox());
    }

    @Override
    public void addCollidingBlockToList(World world, BlockPos pos, AxisAlignedBB bb, List<AxisAlignedBB> out,
                                        Entity entity) {
        out.add(FULL_BLOCK_AABB);
    }
}
