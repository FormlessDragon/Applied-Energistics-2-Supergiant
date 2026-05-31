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

package ae2.recipes.entropy;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("deprecation")
public record EntropyRecipe(EntropyMode mode, Input input, Output output) {

    @Nullable
    public IBlockState getOutputBlockState(IBlockState originalBlockState) {
        return this.output.block.map(blockOutput -> blockOutput.apply(originalBlockState)).orElse(null);
    }

    @Nullable
    public IBlockState getOutputFluidState(IBlockState originalBlockState) {
        return this.output.fluid.map(fluidOutput -> fluidOutput.apply(originalBlockState)).orElse(null);
    }

    @Nullable
    public FluidStack getOutputFluidStack(FluidStack originalFluidStack) {
        return this.output.fluid.map(fluidOutput -> fluidOutput.apply(originalFluidStack)).orElse(null);
    }

    public List<ItemStack> getDrops() {
        return this.output.drops;
    }

    public boolean matches(EntropyMode mode, IBlockState blockState, FluidStack fluidStack) {
        if (this.mode() != mode) {
            return false;
        }

        return this.input.matches(blockState, fluidStack);
    }

    public record Input(Optional<BlockInput> block, Optional<FluidInput> fluid) {

        public boolean matches(IBlockState blockState, FluidStack fluidStack) {
            if (this.block.isPresent()) {
                BlockInput blockInput = this.block.get();
                if (blockState == null || blockState.getBlock() != blockInput.block) {
                    return false;
                }
                if (!PropertyUtils.doPropertiesMatch(blockInput.block, blockState, blockInput.properties)) {
                    return false;
                }
            }

            if (this.fluid.isPresent()) {
                FluidInput fluidInput = this.fluid.get();
                if (fluidStack == null || fluidStack.getFluid() != fluidInput.fluid) {
                    return false;
                }
                Block fluidBlock = fluidInput.fluid.getBlock();
                if (!fluidInput.properties.isEmpty()) {
                    if (fluidBlock == null || blockState == null) {
                        return false;
                    }
                    return PropertyUtils.doPropertiesMatch(fluidBlock, blockState, fluidInput.properties);
                }
            }

            return true;
        }
    }

    public record BlockInput(Block block, Map<String, PropertyValueMatcher> properties) {
        public BlockInput(Block block, Map<String, PropertyValueMatcher> properties) {
            this.block = block;
            this.properties = properties;
            PropertyUtils.validatePropertyMatchers(block, properties);
        }
    }

    public record FluidInput(Fluid fluid, Map<String, PropertyValueMatcher> properties) {
    }

    public record Output(Optional<BlockOutput> block, Optional<FluidOutput> fluid, List<ItemStack> drops) {
        public Output(Optional<BlockOutput> block, Optional<FluidOutput> fluid, List<ItemStack> drops) {
            this.block = block;
            this.fluid = fluid;
            this.drops = drops == null ? Collections.emptyList() : drops;
        }
    }

    public static final class BlockOutput {
        private final Block block;
        private final int metadata;
        private final boolean keepProperties;
        private final Map<String, String> properties;

        public BlockOutput(Block block, int metadata, boolean keepProperties, Map<String, String> properties) {
            this.block = block;
            this.metadata = metadata;
            this.keepProperties = keepProperties;
            this.properties = properties;
        }

        public IBlockState apply(IBlockState originalBlockState) {
            IBlockState state = this.metadata >= 0 ? this.block.getStateFromMeta(this.metadata) : this.block.getDefaultState();

            if (this.keepProperties) {
                state = PropertyUtils.copyProperties(originalBlockState, state);
            }

            return PropertyUtils.applyProperties(state, this.properties);
        }
    }

    public static final class FluidOutput {
        private final Fluid fluid;
        private final boolean keepProperties;
        private final Map<String, String> properties;

        public FluidOutput(Fluid fluid, boolean keepProperties, Map<String, String> properties) {
            this.fluid = fluid;
            this.keepProperties = keepProperties;
            this.properties = properties;
        }

        public @org.jspecify.annotations.Nullable IBlockState apply(IBlockState originalBlockState) {
            Block fluidBlock = this.fluid.getBlock();
            if (fluidBlock == null) {
                return null;
            }

            IBlockState state = fluidBlock.getDefaultState();
            if (this.keepProperties) {
                state = PropertyUtils.copyProperties(originalBlockState, state);
            }
            return PropertyUtils.applyProperties(state, this.properties);
        }

        public FluidStack apply(FluidStack originalFluidStack) {
            int amount = originalFluidStack == null ? Fluid.BUCKET_VOLUME : originalFluidStack.amount;
            return new FluidStack(this.fluid, amount);
        }
    }
}
