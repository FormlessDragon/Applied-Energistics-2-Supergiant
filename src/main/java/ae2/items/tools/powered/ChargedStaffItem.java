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
import ae2.core.AEConfig;
import ae2.core.AppEng;
import ae2.core.network.clientbound.LightningPacket;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;

public class ChargedStaffItem extends AEBasePoweredItem {

    public ChargedStaffItem() {
        super(getBatteryCapacity());
    }

    private static double getBatteryCapacity() {
        try {
            return AEConfig.instance().getChargedStaffBattery();
        } catch (IllegalStateException ignored) {
            return 8000;
        }
    }

    @Override
    public boolean hitEntity(ItemStack item, EntityLivingBase target, EntityLivingBase hitter) {
        if (this.getAECurrentPower(item) > 300) {
            this.extractAEPower(item, 300, Actionable.MODULATE);
            if (!target.world.isRemote) {
                for (int x = 0; x < 2; x++) {
                    final AxisAlignedBB entityBoundingBox = target.getEntityBoundingBox();
                    final float dx = (float) (target.world.rand.nextFloat() * target.width + entityBoundingBox.minX);
                    final float dy = (float) (target.world.rand.nextFloat() * target.height + entityBoundingBox.minY);
                    final float dz = (float) (target.world.rand.nextFloat() * target.width + entityBoundingBox.minZ);
                    AppEng.instance().sendToAllNearExcept(null, dx, dy, dz, 32.0, target.world,
                        new LightningPacket(dx, dy, dz));
                }
            }
            target.attackEntityFrom(DamageSource.MAGIC, 6);
            return true;
        }

        return false;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 32d;
    }
}
