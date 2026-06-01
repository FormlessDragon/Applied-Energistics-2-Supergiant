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

package ae2.items.tools.powered;

import ae2.api.config.Actionable;
import ae2.api.util.DimensionalBlockPos;
import ae2.block.misc.TinyTNTBlock;
import ae2.core.AEConfig;
import ae2.hooks.IBlockTool;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import ae2.recipes.AERecipeTypes;
import ae2.recipes.entropy.EntropyMode;
import ae2.recipes.entropy.EntropyRecipe;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class EntropyManipulatorItem extends AEBasePoweredItem implements IBlockTool {
    public static final int ENERGY_PER_USE = 1600;

    public EntropyManipulatorItem() {
        super(getBatteryCapacity());
    }

    private static void applyRecipe(EntropyRecipe recipe, World world, BlockPos pos, IBlockState blockState) {
        IBlockState outputBlockState = recipe.getOutputBlockState(blockState);
        if (outputBlockState != null) {
            if (outputBlockState.getBlock() == Blocks.AIR) {
                world.setBlockToAir(pos);
            } else {
                world.setBlockState(pos, outputBlockState, 3);
            }
        } else {
            IBlockState outputFluidState = recipe.getOutputFluidState(blockState);
            if (outputFluidState != null) {
                world.setBlockState(pos, outputFluidState, 3);
            } else {
                world.setBlockToAir(pos);
            }
        }

        for (ItemStack drop : recipe.getDrops()) {
            Platform.spawnDrops(world, pos, Collections.singletonList(drop.copy()));
        }

        if (recipe.mode() == EntropyMode.HEAT && world instanceof WorldServer) {
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F,
                2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, 8, 0.5D, 0.5D, 0.5D, 0.0D);
        }
    }

    @Nullable
    private static FluidStack getFluidStack(IBlockState blockState) {
        Fluid fluid = FluidRegistry.lookupFluidForBlock(blockState.getBlock());
        return fluid == null ? null : new FluidStack(fluid, Fluid.BUCKET_VOLUME);
    }

    private static boolean isLiquidStateTargetable(IBlockState state) {
        return state.getMaterial().isLiquid();
    }

    private static double getBatteryCapacity() {
        try {
            return AEConfig.instance().getEntropyManipulatorBattery();
        } catch (IllegalStateException ignored) {
            return 100000;
        }
    }

    @Nullable
    private static RayTraceResult findLiquidTargetAlongRay(World world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double distance = delta.length();
        if (distance <= 1.0E-6D) {
            return null;
        }

        Vec3d step = delta.scale(0.25D / distance);
        int steps = MathHelper.ceil(distance / 0.25D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= steps; i++) {
            Vec3d sample = from.add(step.scale(i));
            pos.setPos(MathHelper.floor(sample.x), MathHelper.floor(sample.y), MathHelper.floor(sample.z));
            IBlockState state = world.getBlockState(pos);
            if (isLiquidStateTargetable(state)) {
                return new RayTraceResult(sample, EnumFacing.UP, pos.toImmutable());
            }
        }

        return null;
    }

    @Nullable
    private static RayTraceResult selectNearestTarget(Vec3d from, @Nullable RayTraceResult blockHit,
                                                      @Nullable RayTraceResult liquidHit) {
        if (blockHit == null) {
            return liquidHit;
        }
        if (liquidHit == null) {
            return blockHit;
        }
        return from.squareDistanceTo(liquidHit.hitVec) < from.squareDistanceTo(blockHit.hitVec) ? liquidHit : blockHit;
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d;
    }

    @Override
    public boolean hitEntity(ItemStack item, EntityLivingBase target, EntityLivingBase hitter) {
        if (this.getAECurrentPower(item) > ENERGY_PER_USE) {
            this.extractAEPower(item, ENERGY_PER_USE, Actionable.MODULATE);
            target.setFire(8);
        }
        return false;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        RayTraceResult target = this.rayTraceTarget(world, player);
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        BlockPos pos = target.getBlockPos();
        if (!isLiquidStateTargetable(world.getBlockState(pos))) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        if (!world.isRemote && this.getAECurrentPower(stack) > ENERGY_PER_USE
            && Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
            tryApplyEffect(world, stack, pos, target.sideHit, player);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side,
                                      float hitX, float hitY, float hitZ) {
        return this.onItemUse(player.getHeldItem(hand), player, world, pos, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public EnumActionResult onItemUse(ItemStack item, EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        RayTraceResult target = this.rayTraceTarget(world, player);
        if (target != null && target.typeOfHit == RayTraceResult.Type.BLOCK) {
            pos = target.getBlockPos();
        }

        if (this.getAECurrentPower(item) <= ENERGY_PER_USE) {
            return EnumActionResult.PASS;
        }

        if (!player.canPlayerEdit(pos, side, item)) {
            return EnumActionResult.FAIL;
        }

        if (!world.isRemote) {
            if (!Platform.hasPermissions(new DimensionalBlockPos(world, pos), player)) {
                return EnumActionResult.FAIL;
            }
            if (!tryApplyEffect(world, item, pos, side, player)) {
                return EnumActionResult.FAIL;
            }
        }

        return EnumActionResult.SUCCESS;
    }

    private boolean tryApplyEffect(World world, ItemStack item, BlockPos pos, EnumFacing side, EntityPlayer player) {
        IBlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();
        FluidStack fluidStack = getFluidStack(blockState);

        if (InteractionUtil.isInAlternateUseMode(player)) {
            EntropyRecipe coolRecipe = findRecipe(EntropyMode.COOL, blockState, fluidStack);
            if (coolRecipe != null) {
                this.extractAEPower(item, ENERGY_PER_USE, Actionable.MODULATE);
                applyRecipe(coolRecipe, world, pos, blockState);
                return true;
            }
        } else {
            if (block instanceof BlockTNT) {
                world.setBlockToAir(pos);
                ((BlockTNT) block).explode(world, pos, blockState, player);
                return true;
            }

            if (block instanceof TinyTNTBlock) {
                world.setBlockToAir(pos);
                ((TinyTNTBlock) block).startFuse(world, pos, player);
                return true;
            }

            EntropyRecipe heatRecipe = findRecipe(EntropyMode.HEAT, blockState, fluidStack);
            if (heatRecipe != null) {
                this.extractAEPower(item, ENERGY_PER_USE, Actionable.MODULATE);
                applyRecipe(heatRecipe, world, pos, blockState);
                return true;
            }

            if (performInWorldSmelting(item, world, player, pos, block)) {
                return true;
            }

            return applyFlintAndSteelEffect(world, item, pos, side, player);
        }

        return false;
    }

    private boolean applyFlintAndSteelEffect(World world, ItemStack item, BlockPos pos, EnumFacing side,
                                             EntityPlayer player) {
        BlockPos offsetPos = pos.offset(side);
        if (!player.canPlayerEdit(offsetPos, side, item)) {
            return false;
        }

        if (world.isAirBlock(offsetPos) && Blocks.FIRE.canPlaceBlockAt(world, offsetPos)) {
            this.extractAEPower(item, ENERGY_PER_USE, Actionable.MODULATE);
            world.playSound(null, offsetPos.getX() + 0.5D, offsetPos.getY() + 0.5D, offsetPos.getZ() + 0.5D,
                SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1.0F,
                world.rand.nextFloat() * 0.4F + 0.8F);
            world.setBlockState(offsetPos, Blocks.FIRE.getDefaultState(), 11);
            return true;
        }
        return false;
    }

    private boolean performInWorldSmelting(ItemStack item, World world, EntityPlayer player, BlockPos pos, Block block) {
        IBlockState state = world.getBlockState(pos);
        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, state, 0);

        IBlockState smeltedBlockState = null;
        List<ItemStack> smeltedDrops = new ObjectArrayList<>();

        for (ItemStack drop : drops) {
            ItemStack result = FurnaceRecipes.instance().getSmeltingResult(drop);
            if (result.isEmpty()) {
                return false;
            }

            ItemStack resultCopy = result.copy();
            if (resultCopy.getItem() instanceof ItemBlock) {
                Block smeltedBlock = ((ItemBlock) resultCopy.getItem()).getBlock();
                if (smeltedBlock == block) {
                    return false;
                }
                if (smeltedBlockState == null && smeltedBlock != Blocks.AIR) {
                    smeltedBlockState = smeltedBlock.getDefaultState();
                    continue;
                }
            }

            smeltedDrops.add(resultCopy);
        }

        if (smeltedBlockState == null && smeltedDrops.isEmpty()) {
            return false;
        }

        this.extractAEPower(item, ENERGY_PER_USE, Actionable.MODULATE);
        world.playSound(player, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
            SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1.0F,
            world.rand.nextFloat() * 0.4F + 0.8F);

        if (smeltedBlockState == null) {
            world.setBlockToAir(pos);
        } else {
            world.setBlockState(pos, smeltedBlockState, 3);
        }

        Platform.spawnDrops(world, pos, smeltedDrops);
        return true;
    }

    @Nullable
    private EntropyRecipe findRecipe(EntropyMode mode, IBlockState blockState, FluidStack fluidStack) {
        for (EntropyRecipe recipe : AERecipeTypes.ENTROPY.getRecipes()) {
            if (recipe.matches(mode, blockState, fluidStack)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private RayTraceResult rayTraceTarget(World world, EntityPlayer player) {
        Vec3d from = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3d look = player.getLookVec();
        Vec3d to = from.add(look.x * 5.0D, look.y * 5.0D, look.z * 5.0D);
        RayTraceResult hit = world.rayTraceBlocks(from, to, true);
        RayTraceResult liquidHit = findLiquidTargetAlongRay(world, from, to);
        return selectNearestTarget(from, hit, liquidHit);
    }
}
