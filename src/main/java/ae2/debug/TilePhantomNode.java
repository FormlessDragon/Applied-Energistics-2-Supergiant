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

package ae2.debug;

import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.me.helpers.TileNodeListener;
import ae2.tile.grid.AENetworkedTile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.EnumSet;

public class TilePhantomNode extends AENetworkedTile {

    private IManagedGridNode proxy;
    private boolean crashMode;

    @Override
    public void onReady() {
        super.onReady();
        this.proxy = GridHelper.createManagedNode(this, TileNodeListener.INSTANCE)
                               .setInWorldNode(true)
                               .setVisualRepresentation(getItemFromTile());
        this.proxy.create(getWorld(), getPos());
        this.crashMode = true;
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.crashMode = data.getBoolean("crashMode");
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setBoolean("crashMode", this.crashMode);
    }

    @Override
    public IGridNode getGridNode(EnumFacing dir) {
        if (!this.crashMode) {
            return super.getGridNode(dir);
        }

        return this.proxy == null ? null : this.proxy.getNode();
    }

    void triggerCrashMode() {
        if (this.proxy != null) {
            this.crashMode = true;
            this.proxy.setExposedOnSides(EnumSet.allOf(EnumFacing.class));
            this.markForUpdate();
            this.saveChanges();
        }
    }
}
