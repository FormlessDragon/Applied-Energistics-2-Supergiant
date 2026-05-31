/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.parts;

import ae2.core.AppEng;
import ae2.core.definitions.AEBlockEntities;
import ae2.core.definitions.AEBlocks;
import ae2.parts.PartPlacement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PartHelper {
    private PartHelper() {
    }

    /**
     * When implementing a custom part in an addon, you can use this method in
     * {@link net.minecraft.item.Item#onItemUse} of your parts item (if you're not using AE2s internal PartItem class)
     * to implement part placement.
     *
     * @return The result of placement suitable for returning from
     * {@link net.minecraft.item.Item#onItemUse(EntityPlayer, World, BlockPos, EnumHand, EnumFacing, float, float, float)}.
     */
    public static EnumActionResult usePartItem(ItemStack stack, @Nullable EntityPlayer player, World world,
                                               BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        return PartPlacement.place(stack, pos, side, player, hand, world, hitX, hitY, hitZ);
    }

    /**
     * Tries to retrieve a part placed from a given part item from the world, and returns it.
     *
     * @param side Null will retrieve the part at the center (the cable).
     */
    @Nullable
    public static <T extends IPart> T getPart(IPartItem<T> partItem, IBlockAccess level, BlockPos pos,
                                              @Nullable EnumFacing side) {
        var part = getPart(level, pos, side);
        if (part != null) {
            var partClass = partItem.getPartClass();
            if (partClass.isInstance(part)) {
                return partClass.cast(part);
            }
        }
        return null;
    }

    /**
     * Tries to retrieve a part from the world, and returns it.
     *
     * @param side Null will retrieve the part at the center (the cable).
     */
    @Nullable
    public static IPart getPart(IBlockAccess level, BlockPos pos, @Nullable EnumFacing side) {
        var tile = level.getTileEntity(pos);
        if (tile instanceof IPartHost partHost) {
            return partHost.getPart(side);
        }
        return null;
    }

    /**
     * Place or replace a part at the given position and side. Use `null` as the side to place a cable in the center of
     * the bus. An existing cable bus at the location will be reused, otherwise the existing block will be replaced with
     * a cable bus if its material is replaceable.
     *
     * @param player The player is only used to set the ownership of the created grid node.
     */
    @Nullable
    public static <T extends IPart> T setPart(World level, BlockPos pos, @Nullable EnumFacing side,
                                              @Nullable EntityPlayer player, IPartItem<T> partItem) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");

        var host = getOrPlacePartHost(level, pos, true, null);
        if (host == null) {
            return null;
        }

        var part = host.replacePart(partItem, side, player, null);
        if (host.isEmpty()) {
            host.cleanup();
        }

        return part;
    }

    /**
     * Gets or places a part host at the given position. The caller needs to handle empty part hosts. They should be
     * cleaned up if they contain no parts, otherwise they may impact gameplay.
     * <p/>
     * Use {@link IPartHost#isEmpty()} and {@link IPartHost#cleanup()}.
     *
     * @param force  If true, an existing non-cable-bus block will be unconditionally replaced.
     * @param player The player trying to place the cable bus. Will be used to check if the player can actually place it
     *               if force is not true.
     */
    @Nullable
    public static IPartHost getOrPlacePartHost(World level, BlockPos pos, boolean force, @Nullable EntityPlayer player) {
        var tile = level.getTileEntity(pos);
        if (tile instanceof IPartHost partHost) {
            return partHost;
        }

        if (!force && !canPlacePartHost(player, level, pos)) {
            return null;
        }

        level.setBlockState(pos, AEBlocks.CABLE_BUS.block()
                                                   .getStateForPlacement(level, pos, EnumFacing.UP, 0.5F, 0.5F, 0.5F, 0, player, EnumHand.MAIN_HAND), 3);
        return AEBlockEntities.CABLE_BUS.getTileEntity(level, pos);
    }

    /**
     * Tries placing a new part host at the given location as a player.
     *
     * @return null if placing a new bus fails (even if a bus already is at that location)
     */
    @Nullable
    public static IPartHost placePartHost(@Nullable EntityPlayer player, World level, BlockPos pos) {
        if (!canPlacePartHost(player, level, pos)) {
            return null;
        }

        level.setBlockState(pos, AEBlocks.CABLE_BUS.block()
                                                   .getStateForPlacement(level, pos, EnumFacing.UP, 0.5F, 0.5F, 0.5F, 0, player, EnumHand.MAIN_HAND), 3);
        return AEBlockEntities.CABLE_BUS.getTileEntity(level, pos);
    }

    public static boolean canPlacePartHost(@Nullable EntityPlayer player, World level, BlockPos pos) {
        if (player != null && !player.canPlayerEdit(pos, EnumFacing.UP, ItemStack.EMPTY)) {
            return false;
        }

        return level.getBlockState(pos).getBlock().isReplaceable(level, pos);
    }

    /**
     * Gets a part host at the given position.
     */
    @Nullable
    public static IPartHost getPartHost(IBlockAccess level, BlockPos pos) {
        var tile = level.getTileEntity(pos);
        if (tile instanceof IPartHost partHost) {
            return partHost;
        }

        return null;
    }

    /**
     * @return the render mode
     */
    public static CableRenderMode getCableRenderMode() {
        return AppEng.instance().getCableRenderMode();
    }

}

