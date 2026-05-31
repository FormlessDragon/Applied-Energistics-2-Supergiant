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
package ae2.tile.storage;

import ae2.api.inventories.InternalInventory;
import ae2.tile.AEBaseInvTile;
import ae2.tile.ClientTickingTile;
import ae2.util.inv.AppEngInternalInventory;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;

public class TileSkyChest extends AEBaseInvTile implements ClientTickingTile {
    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 9 * 4);

    private int numPlayersUsing;
    private long lastEvent;
    private float lidAngle;
    private float prevLidAngle;

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.numPlayersUsing > 0);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        int wasOpen = this.numPlayersUsing;
        this.numPlayersUsing = data.readBoolean() ? 1 : 0;
        if (wasOpen != this.numPlayersUsing) {
            this.lastEvent = System.currentTimeMillis();
            changed = true;
        }
        return changed;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    public void openInventory(EntityPlayer player) {
        if (!player.isSpectator()) {
            this.numPlayersUsing++;
            this.world.addBlockEvent(this.pos, this.getBlockType(), 1, this.numPlayersUsing);
            if (this.numPlayersUsing == 1) {
                this.world.playSound(player, this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D,
                    SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F,
                    this.world.rand.nextFloat() * 0.1F + 0.9F);
                markForUpdate();
            }
        }
    }

    public void closeInventory(EntityPlayer player) {
        if (!player.isSpectator()) {
            this.numPlayersUsing--;
            this.world.addBlockEvent(this.pos, this.getBlockType(), 1, this.numPlayersUsing);

            if (this.numPlayersUsing < 0) {
                this.numPlayersUsing = 0;
            }

            if (this.numPlayersUsing == 0) {
                this.world.playSound(player, this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D,
                    SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F,
                    this.world.rand.nextFloat() * 0.1F + 0.9F);
                markForUpdate();
            }
        }
    }

    @Override
    public void clientTick() {
        this.prevLidAngle = this.lidAngle;
        if (this.numPlayersUsing == 0 && this.lidAngle > 0.0F || this.numPlayersUsing > 0 && this.lidAngle < 1.0F) {
            if (this.numPlayersUsing > 0) {
                this.lidAngle += 0.1F;
            } else {
                this.lidAngle -= 0.1F;
            }

            if (this.lidAngle > 1.0F) {
                this.lidAngle = 1.0F;
            }
            if (this.lidAngle < 0.0F) {
                this.lidAngle = 0.0F;
            }
        }
    }

    @Override
    public boolean receiveClientEvent(int id, int type) {
        if (id == 1) {
            this.numPlayersUsing = type;
            return true;
        }
        return super.receiveClientEvent(id, type);
    }

    public float getLidAngle() {
        return this.lidAngle;
    }

    public float getPrevLidAngle() {
        return this.prevLidAngle;
    }

    public long getLastEvent() {
        return this.lastEvent;
    }
}
