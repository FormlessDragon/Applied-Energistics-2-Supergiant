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

package ae2.block.misc;

import ae2.block.AEBaseBlock;
import ae2.entity.TinyTNTPrimedEntity;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class TinyTNTBlock extends AEBaseBlock {

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 0.5D, 0.75D);

    public TinyTNTBlock(Material material) {
        super(material);
        this.setDefaultState(this.blockState.getBaseState());
        this.setLightOpacity(2);
        this.setFullSize();
        this.setOpaque();
        this.setSoundType(SoundType.GROUND);
        this.setHardness(0F);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && (heldItem.getItem() == Items.FLINT_AND_STEEL
            || heldItem.getItem() == Items.FIRE_CHARGE)) {
            this.startFuse(world, pos, player);
            world.setBlockToAir(pos);
            Item item = heldItem.getItem();
            if (!player.capabilities.isCreativeMode) {
                if (item == Items.FLINT_AND_STEEL) {
                    heldItem.damageItem(1, player);
                } else {
                    heldItem.shrink(1);
                }
            }
            player.addStat(StatList.getObjectUseStats(item));
            return true;
        }

        return super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ);
    }

    public void startFuse(World world, BlockPos pos, @Nullable EntityLivingBase igniter) {
        if (!world.isRemote) {
            TinyTNTPrimedEntity primedTinyTNTEntity = new TinyTNTPrimedEntity(world, pos.getX() + 0.5F, pos.getY(),
                pos.getZ() + 0.5F, igniter);
            world.spawnEntity(primedTinyTNTEntity);
            world.playSound(null, primedTinyTNTEntity.posX, primedTinyTNTEntity.posY, primedTinyTNTEntity.posZ,
                SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (world.getRedstonePowerFromNeighbors(pos) > 0) {
            this.startFuse(world, pos, null);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);

        if (world.getRedstonePowerFromNeighbors(pos) > 0) {
            this.startFuse(world, pos, null);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        if (!world.isRemote && entity instanceof EntityArrow arrow) {
            if (arrow.isBurning()) {
                EntityLivingBase igniter = arrow.shootingEntity instanceof EntityLivingBase
                    ? (EntityLivingBase) arrow.shootingEntity
                    : null;
                this.startFuse(world, pos, igniter);
                world.setBlockToAir(pos);
            }
        }
    }

    @Override
    public boolean canDropFromExplosion(Explosion exp) {
        return false;
    }

    @Override
    public void onBlockExploded(World world, BlockPos pos, Explosion exp) {
        super.onBlockExploded(world, pos, exp);
        if (!world.isRemote) {
            TinyTNTPrimedEntity primedTinyTNTEntity = new TinyTNTPrimedEntity(world, pos.getX() + 0.5F, pos.getY(),
                pos.getZ() + 0.5F, exp.getExplosivePlacedBy());
            primedTinyTNTEntity
                .setFuse(world.rand.nextInt(primedTinyTNTEntity.getFuse() / 4) + primedTinyTNTEntity.getFuse() / 8);
            world.spawnEntity(primedTinyTNTEntity);
        }
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, collidingBoxes, SHAPE);
    }
}
