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

package ae2.parts;

import ae2.api.implementations.parts.ICablePart;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.PartHelper;
import ae2.api.util.ICustomName;
import ae2.core.AEConfig;
import ae2.core.PlayerState;
import ae2.parts.networking.CablePart;
import ae2.parts.reporting.AbstractReportingPart;
import ae2.util.CustomNameUtil;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.SoundType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;

import org.jetbrains.annotations.Nullable;

public final class PartPlacement {

    private PartPlacement() {
    }

    public static EnumActionResult place(ItemStack held, BlockPos pos, EnumFacing side, @Nullable EntityPlayer player,
                                         EnumHand hand, World world, float hitX, float hitY, float hitZ) {
        if (!(held.getItem() instanceof IPartItem<?>)) {
            return EnumActionResult.PASS;
        }

        Placement placement = getPartPlacement(player, world, held, pos, side,
            new Vec3d(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ));
        if (placement == null) {
            return EnumActionResult.FAIL;
        }

        IPart part = placePart(player, world, held, placement.pos(), placement.side());
        if (part == null) {
            if (player != null) {
                Platform.sendImmediateTileEntityUpdate(player, pos);
            }
            return EnumActionResult.FAIL;
        }

        if (!world.isRemote) {
            if (player != null && !player.capabilities.isCreativeMode) {
                held.shrink(1);
                if (held.getCount() == 0) {
                    player.setHeldItem(hand, ItemStack.EMPTY);
                    MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, held, hand));
                }
            }

            return EnumActionResult.SUCCESS;
        }

        if (player != null) {
            player.swingArm(hand);
        }
        return EnumActionResult.SUCCESS;
    }

    @Nullable
    public static IPart placePart(@Nullable EntityPlayer player, World world, ItemStack partStack, BlockPos pos,
                                  EnumFacing side) {
        if (!(partStack.getItem() instanceof IPartItem<?> partItem)) {
            return null;
        }

        IPartHost host = PartHelper.getOrPlacePartHost(world, pos, false, player);
        if (host == null) {
            return null;
        }

        if (!canPlacePartWithCableBlockingRule(player, partItem, host, side)) {
            return null;
        }

        IPart addedPart = host.addPart(partItem, side, player);
        if (addedPart == null) {
            if (host.isEmpty()) {
                host.cleanup();
            }
            return null;
        }

        if (hasEntityCollision(world, pos, host.getCollisionShape(null))) {
            host.removePart(addedPart);
            if (host.isEmpty()) {
                host.cleanup();
            }
            return null;
        }

        NBTTagCompound configData = partStack.getTagCompound();
        if (configData != null) {
            addedPart.importSettings(SettingsFrom.DISMANTLE_ITEM, configData.copy(), player);
        }
        String customName = CustomNameUtil.getDisplayName(partStack);
        if (customName != null && addedPart instanceof ICustomName customNamePart) {
            customNamePart.setCustomName(customName);
            host.markForSave();
            host.markForUpdate();
        }

        var state = world.getBlockState(pos);
        SoundType soundType = state.getBlock().getSoundType(state, world, pos, player);
        world.playSound(null, pos, soundType.getPlaceSound(), SoundCategory.BLOCKS,
            (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        return addedPart;
    }

    @Nullable
    public static Placement getPartPlacement(@Nullable EntityPlayer player, World world, ItemStack partStack,
                                             BlockPos pos, EnumFacing side, Vec3d clickLocation) {
        if (player != null && player.isSneaking()) {
            EnumFacing replaceCablePlacement = tryReplaceCableSegment(world, partStack, pos, clickLocation);
            if (replaceCablePlacement != null) {
                side = replaceCablePlacement;
            }
        }

        if (player != null && PlayerState.isHoldingCtrl(player)) {
            side = side.getOpposite();
        }

        if (canPlacePartOnBlock(player, world, partStack, pos, side)) {
            return new Placement(pos, side);
        }

        pos = pos.offset(side);
        side = side.getOpposite();
        if (canPlacePartOnBlock(player, world, partStack, pos, side)) {
            return new Placement(pos, side);
        }

        return null;
    }

    @Nullable
    private static EnumFacing tryReplaceCableSegment(World world, ItemStack partStack, BlockPos pos,
                                                     Vec3d clickLocation) {
        var host = PartHelper.getPartHost(world, pos);
        if (host == null) {
            return null;
        }

        var cable = host.getPart(null);
        if (!(cable instanceof CablePart cablePart)) {
            return null;
        }

        EnumFacing hitSide = null;

        var localClickLocation = clickLocation.subtract(pos.getX(), pos.getY(), pos.getZ());
        sideLoop:
        for (var side : EnumFacing.VALUES) {
            ObjectList<AxisAlignedBB> boxes = new ObjectArrayList<>();
            var bch = new BusCollisionHelper(boxes, null, true);
            cablePart.getBoxes(bch, boxSide -> boxSide == side);

            for (var box : boxes) {
                if (box.grow(0.02).contains(localClickLocation)) {
                    hitSide = side;
                    break sideLoop;
                }
            }
        }

        if (host.canAddPart(partStack, hitSide)) {
            return hitSide;
        } else {
            return null;
        }
    }

    public static boolean canPlacePartOnBlock(@Nullable EntityPlayer player, World world, ItemStack partStack,
                                              BlockPos pos, EnumFacing side) {
        if (!(partStack.getItem() instanceof IPartItem<?> partItem)) {
            return false;
        }

        IPartHost host = PartHelper.getPartHost(world, pos);
        if (host == null && !PartHelper.canPlacePartHost(player, world, pos)) {
            return false;
        }

        if (host == null || !host.canAddPart(partStack, side)) {
            return host == null;
        }

        return canPlacePartWithCableBlockingRule(player, partItem, host, side);
    }

    private static boolean canPlacePartWithCableBlockingRule(@Nullable EntityPlayer player, IPartItem<?> partItem,
                                                             IPartHost host, @Nullable EnumFacing side) {
        if (!AEConfig.instance().isRequireSneakForCableBlockingPanelPlacement()
            || (player != null && player.isSneaking())
            || side == null
            || !AbstractReportingPart.class.isAssignableFrom(partItem.getPartClass())) {
            return true;
        }

        return !(host.getPart(null) instanceof ICablePart cablePart) || !cablePart.isConnected(side);
    }

    private static boolean hasEntityCollision(World world, BlockPos pos, Object collisionShape) {
        if (!(collisionShape instanceof Iterable<?> boxes)) {
            return false;
        }

        for (Object boxObject : boxes) {
            if (boxObject instanceof AxisAlignedBB box
                && !world.checkNoEntityCollision(box.offset(pos))) {
                return true;
            }
        }

        return false;
    }

    public record Placement(BlockPos pos, EnumFacing side) {
    }
}
