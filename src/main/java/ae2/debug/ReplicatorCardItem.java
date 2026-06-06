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

package ae2.debug;

import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.spatial.ISpatialService;
import ae2.core.localization.PlayerMessages;
import ae2.items.AEBaseItem;
import ae2.util.InteractionUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class ReplicatorCardItem extends AEBaseItem {

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            ItemStack stack = player.getHeldItem(hand);
            NBTTagCompound tag = getOrCreateTag(stack);
            int replications = tag.hasKey("r") ? (tag.getInteger("r") + 1) % 4 : 0;
            tag.setInteger("r", replications);
            player.sendMessage(PlayerMessages.ReplicatorCardReplications.text(replications + 1));
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        if (player == null) {
            return EnumActionResult.PASS;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (InteractionUtil.isInAlternateUseMode(player)) {
            ae2.api.networking.IInWorldGridNodeHost gridHost = GridHelper.getNodeHost(world, pos);
            if (gridHost != null) {
                NBTTagCompound tag = getOrCreateTag(player.getHeldItem(hand));
                tag.setInteger("x", x);
                tag.setInteger("y", y);
                tag.setInteger("z", z);
                tag.setInteger("side", side.ordinal());
                tag.setInteger("dim", world.provider.getDimension());
                tag.setInteger("r", 0);
                outputMsg(player, PlayerMessages.ReplicatorCardSourceSet.text());
            } else {
                outputMsg(player, PlayerMessages.ReplicatorCardNoGridHost.text());
            }
        } else {
            NBTTagCompound tag = player.getHeldItem(hand).getTagCompound();
            if (tag != null && !tag.isEmpty()) {
                int srcX = tag.getInteger("x");
                int srcY = tag.getInteger("y");
                int srcZ = tag.getInteger("z");
                int srcSide = tag.getInteger("side");
                int dimension = tag.getInteger("dim");
                int replications = tag.getInteger("r") + 1;

                WorldServer srcWorld = DimensionManager.getWorld(dimension);
                if (srcWorld == null) {
                    outputMsg(player, PlayerMessages.ReplicatorCardSourceMissingGridBlock.text());
                    return EnumActionResult.SUCCESS;
                }

                ae2.api.networking.IInWorldGridNodeHost gridHost = GridHelper.getNodeHost(srcWorld,
                    new BlockPos(srcX, srcY, srcZ));
                if (gridHost != null) {
                    EnumFacing sourceSide = EnumFacing.VALUES[srcSide];
                    IGridNode node = gridHost.getGridNode(sourceSide);

                    if (node != null) {
                        IGrid grid = node.grid();
                        ISpatialService spatialService = grid.getSpatialService();

                        if (spatialService.isValidRegion()) {
                            BlockPos min = spatialService.getMin();
                            BlockPos max = spatialService.getMax();

                            int scSizeX = max.getX() - min.getX();
                            int scSizeY = max.getY() - min.getY();
                            int scSizeZ = max.getZ() - min.getZ();

                            int minX = min.getX();
                            int minY = min.getY();
                            int minZ = min.getZ();

                            int xRot = (int) -Math.signum(MathHelper.wrapDegrees(player.rotationYaw));
                            int zRot = (int) Math.signum(MathHelper.wrapDegrees(player.rotationYaw + 90));

                            for (int rx = 0; rx < replications; rx++) {
                                for (int ry = 0; ry < replications; ry++) {
                                    for (int rz = 0; rz < replications; rz++) {
                                        int relX = min.getX() - srcX + x + rx * scSizeX * xRot;
                                        int relY = min.getY() - srcY + y + ry * scSizeY;
                                        int relZ = min.getZ() - srcZ + z + rz * scSizeZ * zRot;

                                        for (int i = 1; i < scSizeX; i++) {
                                            for (int j = 1; j < scSizeY; j++) {
                                                for (int k = 1; k < scSizeZ; k++) {
                                                    BlockPos sourcePos = new BlockPos(minX + i, minY + j, minZ + k);
                                                    BlockPos destPos = new BlockPos(i + relX, j + relY, k + relZ);

                                                    IBlockState state = srcWorld.getBlockState(sourcePos);
                                                    IBlockState previous = world.getBlockState(destPos);

                                                    world.setBlockState(destPos, state, 3);
                                                    if (state.getBlock().hasTileEntity(state)) {
                                                        TileEntity sourceTe = srcWorld.getTileEntity(sourcePos);
                                                        if (sourceTe != null) {
                                                            NBTTagCompound data = sourceTe.writeToNBT(new NBTTagCompound());
                                                            data.setInteger("x", destPos.getX());
                                                            data.setInteger("y", destPos.getY());
                                                            data.setInteger("z", destPos.getZ());
                                                            TileEntity newTe = TileEntity.create(world, data);
                                                            if (newTe != null) {
                                                                world.setTileEntity(destPos, newTe);
                                                            }
                                                        }
                                                    }
                                                    world.notifyBlockUpdate(destPos, previous, state, 3);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            outputMsg(player, PlayerMessages.ReplicatorCardRequiresSpatialPylons.text());
                        }
                    } else {
                        outputMsg(player, PlayerMessages.ReplicatorCardNoGridNode.text());
                    }
                } else {
                    outputMsg(player, PlayerMessages.ReplicatorCardSourceMissingGridBlock.text());
                }
            } else {
                outputMsg(player, PlayerMessages.ReplicatorCardNoSourceDefined.text());
            }
        }

        return EnumActionResult.SUCCESS;
    }

    private NBTTagCompound getOrCreateTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    private void outputMsg(Entity player, ITextComponent message) {
        player.sendMessage(message);
    }
}
