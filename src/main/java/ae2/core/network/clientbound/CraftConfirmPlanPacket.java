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

package ae2.core.network.clientbound;

import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.me.crafting.CraftingPlanSummary;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;

public class CraftConfirmPlanPacket extends ClientboundPacket {
    private CraftingPlanSummary plan;

    public CraftConfirmPlanPacket() {
    }

    public CraftConfirmPlanPacket(CraftingPlanSummary plan) {
        this.plan = plan;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.plan = CraftingPlanSummary.read(new PacketBuffer(buf));
    }

    @Override
    protected void write(ByteBuf buf) {
        this.plan.write(new PacketBuffer(buf));
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.openContainer instanceof ContainerCraftConfirm container) {
            container.setPlan(this.plan);
        }
    }
}
