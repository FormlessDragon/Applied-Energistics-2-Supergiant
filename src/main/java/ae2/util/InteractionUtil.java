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

package ae2.util;

import ae2.api.ids.AEItemIds;
import ae2.items.tools.NetworkToolItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Utility functions revolving around using or placing items.
 */
public final class InteractionUtil {

    private InteractionUtil() {
    }

    public static boolean canWrenchDisassemble(ItemStack tool) {
        return isAeWrench(tool);
    }

    public static boolean canWrenchRotate(ItemStack tool) {
        if (tool.isEmpty()) {
            return false;
        }

        if (tool.getItem() instanceof NetworkToolItem) {
            return false;
        }

        return isAeWrench(tool);
    }

    public static boolean isInAlternateUseMode(EntityPlayer player) {
        return player.isSneaking();
    }

    public static LookDirection getPlayerRay(EntityPlayer player, double reachDistance) {
        var x = player.prevPosX + (player.posX - player.prevPosX);
        var y = player.prevPosY + (player.posY - player.prevPosY) + player.getEyeHeight();
        var z = player.prevPosZ + (player.posZ - player.prevPosZ);

        var playerPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch);
        var playerYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw);

        var yawRayX = MathHelper.sin(-playerYaw * 0.017453292f - (float) Math.PI);
        var yawRayZ = MathHelper.cos(-playerYaw * 0.017453292f - (float) Math.PI);

        var pitchMultiplier = -MathHelper.cos(-playerPitch * 0.017453292F);
        var eyeRayY = MathHelper.sin(-playerPitch * 0.017453292F);
        var eyeRayX = yawRayX * pitchMultiplier;
        var eyeRayZ = yawRayZ * pitchMultiplier;

        var from = new Vec3d(x, y, z);
        var to = from.add(eyeRayX * reachDistance, eyeRayY * reachDistance, eyeRayZ * reachDistance);

        return new LookDirection(from, to);
    }

    private static boolean isAeWrench(ItemStack tool) {
        if (tool.isEmpty()) {
            return false;
        }

        ResourceLocation registryName = tool.getItem().getRegistryName();
        return AEItemIds.CERTUS_QUARTZ_WRENCH.equals(registryName)
            || AEItemIds.NETHER_QUARTZ_WRENCH.equals(registryName);
    }
}
