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

package ae2.tile.networking;

import ae2.api.networking.energy.IPassiveEnergyGenerator;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.tile.grid.AENetworkedTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.EnumSet;
import java.util.Set;

public class TileCrystalResonanceGenerator extends AENetworkedTile {

    private boolean suppressed;

    public TileCrystalResonanceGenerator() {
        super();
        getMainNode().setIdlePowerUsage(0);
        getMainNode().addService(IPassiveEnergyGenerator.class, new IPassiveEnergyGenerator() {
            @Override
            public double getRate() {
                return AEConfig.instance().getCrystalResonanceGeneratorRate();
            }

            @Override
            public boolean isSuppressed() {
                return TileCrystalResonanceGenerator.this.suppressed;
            }

            @Override
            public void setSuppressed(boolean suppressed) {
                if (suppressed != TileCrystalResonanceGenerator.this.suppressed) {
                    TileCrystalResonanceGenerator.this.suppressed = suppressed;
                    markForUpdate();
                }
            }
        });
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CRYSTAL_RESONANCE_GENERATOR.stack();
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        super.readFromStream(data);
        this.suppressed = data.readBoolean();
        return false;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.suppressed);
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);
        data.setBoolean("suppressed", this.suppressed);
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);
        this.suppressed = data.getBoolean("suppressed");
    }

    @Override
    public Set<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }
}
