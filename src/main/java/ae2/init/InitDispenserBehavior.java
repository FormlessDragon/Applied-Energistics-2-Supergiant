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

package ae2.init;

import ae2.api.util.AEPartLocation;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.hooks.IBlockTool;
import ae2.hooks.TinyTNTDispenseItemBehavior;
import ae2.items.tools.powered.MatterCannonItem;
import ae2.util.LookDirection;
import ae2.util.Platform;
import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class InitDispenserBehavior {

    private static boolean initialized;

    private InitDispenserBehavior() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(AEBlocks.TINY_TNT.item(), new TinyTNTDispenseItemBehavior());
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(AEItems.ENTROPY_MANIPULATOR.asItem(), new ToolDispenseBehavior());
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(AEItems.MATTER_CANNON.asItem(), new MatterCannonDispenseBehavior());
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(AEItems.COLOR_APPLICATOR.asItem(), new ToolDispenseBehavior());
        initialized = true;
    }

    private static final class ToolDispenseBehavior extends BehaviorDefaultDispenseItem {
        @Override
        protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
            EnumFacing facing = source.getBlockState().getValue(BlockDispenser.FACING);
            BlockPos targetPos = source.getBlockPos().offset(facing);
            World world = source.getWorld();
            if (!world.isRemote && stack.getItem() instanceof IBlockTool tool) {
                var player = Platform.getFakeEntityPlayer(world, null);
                Platform.configurePlayer(player, AEPartLocation.fromFacing(facing), source.getBlockTileEntity());
                EnumActionResult result = tool.onItemUse(stack, player, world, targetPos, EnumHand.MAIN_HAND,
                    facing.getOpposite(), 0.5F, 0.5F, 0.5F);
                if (result == EnumActionResult.SUCCESS) {
                    return stack;
                }
            }
            return super.dispenseStack(source, stack);
        }
    }

    private static final class MatterCannonDispenseBehavior extends BehaviorDefaultDispenseItem {
        @Override
        protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
            World world = source.getWorld();
            if (!world.isRemote && stack.getItem() instanceof MatterCannonItem cannon) {
                EnumFacing facing = source.getBlockState().getValue(BlockDispenser.FACING);
                var player = Platform.getFakeEntityPlayer(world, null);
                Platform.configurePlayer(player, AEPartLocation.fromFacing(facing), source.getBlockTileEntity());

                Vec3d from = new Vec3d(source.getX(), source.getY(), source.getZ());
                Vec3d to = new Vec3d(from.x + facing.getXOffset() * 32.0D,
                    from.y + facing.getYOffset() * 32.0D,
                    from.z + facing.getZOffset() * 32.0D);
                cannon.fireCannon(world, stack, player, EnumHand.MAIN_HAND, new LookDirection(from, to));
                return stack;
            }
            return super.dispenseStack(source, stack);
        }
    }
}
