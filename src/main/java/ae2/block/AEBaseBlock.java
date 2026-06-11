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

package ae2.block;

import ae2.api.orientation.IOrientableBlock;
import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.core.AELog;
import ae2.helpers.ICustomCollision;
import ae2.util.InteractionUtil;
import ae2.util.LookDirection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public abstract class AEBaseBlock extends Block implements IOrientableBlock {

    protected AxisAlignedBB boundingBox = FULL_BLOCK_AABB;
    private boolean isOpaque = true;
    private boolean isFullSize = true;
    private boolean isInventory = false;

    protected AEBaseBlock(Material material) {
        super(material);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public final void addInformation(final ItemStack stack, final World world, final List<String> lines,
                                     final ITooltipFlag advancedTooltips) {
        this.addCheckedInformation(stack, world, lines, advancedTooltips);
    }

    @SideOnly(Side.CLIENT)
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addInformation(stack, world, lines, advancedTooltips);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.none();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(new IProperty<?>[0]);
    }

    protected final BlockStateContainer createBlockState(IProperty<?>... additionalProperties) {
        ObjectSet<IProperty<?>> properties = new ObjectLinkedOpenHashSet<>(
            getOrientationStrategy().getProperties());
        Collections.addAll(properties, additionalProperties);
        return new BlockStateContainer(this, properties.toArray(new IProperty<?>[0]));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        IBlockState state = this.getStateFromMeta(meta);
        return getOrientationStrategy().getStateForPlacement(state, world, pos, facing, hitX, hitY, hitZ, placer);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return getOrientationStrategy().getFacing(state).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IOrientationStrategy strategy = this.getOrientationStrategy();
        EnumFacing facing = EnumFacing.byIndex(meta & 7);
        if (facing == null) {
            facing = EnumFacing.NORTH;
        }
        return strategy.setOrientation(this.getDefaultState(), facing, 0);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return this.isOpaque() && this.isFullSize();
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return this.isFullSize();
    }

    public boolean isOpaque() {
        return this.isOpaque;
    }

    protected void setOpaque() {
        this.isOpaque = false;
    }

    public boolean isFullSize() {
        return this.isFullSize;
    }

    protected void setFullSize() {
        this.isFullSize = false;
    }

    public boolean isInventory() {
        return this.isInventory;
    }

    protected void setInventory(boolean isInventory) {
        this.isInventory = isInventory;
    }

    @Override
    public void getSubBlocks(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        itemStacks.add(new ItemStack(Item.getItemFromBlock(this)));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return this.boundingBox;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB bb,
                                      List<AxisAlignedBB> out, Entity entity, boolean isActualState) {
        ICustomCollision collisionHandler = getCustomCollision(world, pos);
        if (collisionHandler == null || bb == null) {
            super.addCollisionBoxToList(state, world, pos, bb, out, entity, isActualState);
            return;
        }

        List<AxisAlignedBB> boxes = new ObjectArrayList<>();
        collisionHandler.addCollidingBlockToList(world, pos, bb, boxes, entity);
        for (AxisAlignedBB box : boxes) {
            AxisAlignedBB offset = box.offset(pos);
            if (bb.intersects(offset)) {
                out.add(offset);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World world, BlockPos pos) {
        ICustomCollision collisionHandler = getCustomCollision(world, pos);
        if (collisionHandler == null) {
            return super.getSelectedBoundingBox(state, world, pos);
        }

        if (world.isRemote) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                LookDirection ray = InteractionUtil.getPlayerRay(player, 5.0D);
                AxisAlignedBB selected = null;
                double bestDistance = 0;

                for (AxisAlignedBB box : collisionHandler.getSelectedBoundingBoxesFromPool(world, pos, player, true)) {
                    this.boundingBox = box;
                    RayTraceResult hit = super.collisionRayTrace(state, world, pos, ray.a(), ray.b());
                    this.boundingBox = FULL_BLOCK_AABB;

                    if (hit == null) {
                        continue;
                    }

                    double distance = ray.a().squareDistanceTo(hit.hitVec);
                    if (selected == null || distance < bestDistance) {
                        bestDistance = distance;
                        selected = box;
                    }
                }

                if (selected != null) {
                    return selected.offset(pos);
                }
            }
        }

        AxisAlignedBB result = null;
        for (AxisAlignedBB box : collisionHandler.getSelectedBoundingBoxesFromPool(world, pos, null, false)) {
            result = result == null ? box : result.union(box);
        }

        return result == null ? FULL_BLOCK_AABB.offset(pos) : result.offset(pos);
    }

    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        ICustomCollision collisionHandler = getCustomCollision(world, pos);
        if (collisionHandler != null) {
            RayTraceResult bestHit = null;
            double bestDistance = 0;

            for (AxisAlignedBB box : collisionHandler.getSelectedBoundingBoxesFromPool(world, pos, null, true)) {
                this.boundingBox = box;
                RayTraceResult hit = super.collisionRayTrace(state, world, pos, start, end);
                this.boundingBox = FULL_BLOCK_AABB;

                if (hit == null) {
                    continue;
                }

                double distance = start.squareDistanceTo(hit.hitVec);
                if (bestHit == null || distance < bestDistance) {
                    bestDistance = distance;
                    bestHit = hit;
                }
            }

            this.boundingBox = FULL_BLOCK_AABB;
            return bestHit;
        }

        AxisAlignedBB boundingBox = state.getBoundingBox(world, pos);
        if (boundingBox == null) {
            AELog.error("Null bounding box for %s at %s with state %s", this, pos, state);
            return null;
        }
        this.boundingBox = FULL_BLOCK_AABB;
        return super.collisionRayTrace(state, world, pos, start, end);
    }

    protected @Nullable ICustomCollision getCustomCollision(World world, BlockPos pos) {
        if (world != null && pos != null && this instanceof ICustomCollision collision) {
            return collision;
        }
        return null;
    }

    @Override
    public String toString() {
        ResourceLocation id = this.getRegistryName();
        String regName = id != null ? id.getPath() : "unregistered";
        return this.getClass().getSimpleName() + "[" + regName + "]";
    }
}
