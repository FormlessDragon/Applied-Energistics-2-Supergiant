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

package ae2.tile.misc;

import ae2.api.implementations.blockentities.ICrankable;
import ae2.block.misc.CrankBlock;
import ae2.tile.AEBaseTile;
import ae2.tile.ClientTickingTile;
import ae2.tile.ServerTickingTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import org.jetbrains.annotations.Nullable;

public class TileCrank extends AEBaseTile implements ServerTickingTile, ClientTickingTile {
    public static final int POWER_PER_CRANK_TURN = 160;

    private final int ticksPerRotation = 18;

    private float visibleRotation = 0;
    private int charge = 0;
    private int hits = 0;
    private int rotation = 0;

    @Nullable
    private ICrankable getCrankable() {
        if (this.world == null) {
            return null;
        }

        IBlockState blockState = this.getBlockState();
        if (blockState != null && blockState.getBlock() instanceof CrankBlock) {
            return ((CrankBlock) blockState.getBlock()).getCrankable(blockState, this.world, this.pos);
        }
        return null;
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        int oldRotation = this.rotation;
        this.rotation = data.readInt();
        return changed || oldRotation != this.rotation;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeInt(this.rotation);
    }

    public void power() {
        if (this.world == null || this.world.isRemote) {
            return;
        }

        if (this.rotation < 3) {
            ICrankable crankable = this.getCrankable();
            if (crankable != null) {
                if (crankable.canTurn()) {
                    this.hits = 0;
                    this.rotation += this.ticksPerRotation;
                    this.markForUpdate();
                    return;
                }

                this.hits++;
                if (this.hits > 10) {
                    this.world.destroyBlock(this.pos, false);
                }
            }
        }

    }

    public float getVisibleRotation() {
        return this.visibleRotation;
    }

    private void setVisibleRotation(float visibleRotation) {
        this.visibleRotation = visibleRotation;
    }

    @Override
    public void clientTick() {
        this.tickCrank();
    }

    @Override
    public void serverTick() {
        this.tickCrank();
    }

    private void tickCrank() {
        if (this.rotation > 0) {
            this.setVisibleRotation(this.getVisibleRotation() - 360.0F / this.ticksPerRotation);
            this.charge++;
            if (this.charge >= this.ticksPerRotation) {
                this.charge -= this.ticksPerRotation;
                ICrankable crankable = this.getCrankable();
                if (crankable != null) {
                    crankable.applyTurn();
                }
            }

            this.rotation--;
        }
    }
}
