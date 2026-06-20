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

package ae2.block.crafting;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.api.util.AEColor;
import ae2.tile.crafting.TileCraftingMonitor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public class CraftingMonitorBlock extends AbstractCraftingUnitBlock<TileCraftingMonitor> {
    public static final IUnlistedProperty<AEColor> COLOR = new IUnlistedProperty<>() {
        @Override
        public String getName() {
            return "color";
        }

        @Override
        public boolean isValid(AEColor value) {
            return true;
        }

        @Override
        public Class<AEColor> getType() {
            return AEColor.class;
        }

        @Override
        public String valueToString(AEColor value) {
            return String.valueOf(value);
        }
    };

    public CraftingMonitorBlock(ICraftingUnitType type) {
        super(type, TileCraftingMonitor.class);
    }

    public CraftingMonitorBlock(ICraftingUnitDefinition definition) {
        super(definition, TileCraftingMonitor.class);
    }

    @Override
    protected IUnlistedProperty<?>[] getUnlistedProperties() {
        return new IUnlistedProperty<?>[]{FORWARD, UP, STATE, COLOR};
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        state = super.getExtendedState(state, world, pos);
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TileCraftingMonitor tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return state;
        }

        return ((IExtendedBlockState) state).withProperty(COLOR, tile.getColor());
    }
}
