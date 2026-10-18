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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CraftConfirmPlanPacket extends ClientboundPacket {
    private int windowId = -1;
    private String session;
    private UUID request;
    private long revision;
    private boolean valid;
    @Nullable
    private CraftingPlanSummary plan;

    public CraftConfirmPlanPacket() {
    }

    public CraftConfirmPlanPacket(int windowId, String session, UUID request, long revision,
                                  @Nullable CraftingPlanSummary plan) {
        this.windowId = windowId;
        this.session = session;
        this.request = request;
        this.revision = revision;
        this.plan = plan;
        this.valid = true;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.valid = false;
        try {
            var packet = new PacketBuffer(buf);
            this.windowId = packet.readVarInt();
            this.session = packet.readString(36);
            this.request = packet.readUniqueId();
            this.revision = packet.readVarLong();
            this.plan = packet.readBoolean() ? CraftingPlanSummary.read(packet) : null;
            if (this.revision < 0 || packet.isReadable()) {
                throw new IllegalArgumentException("Invalid crafting plan revision or trailing bytes");
            }
            this.valid = true;
        } catch (RuntimeException e) {
            this.plan = null;
            buf.skipBytes(buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packet = new PacketBuffer(buf);
        packet.writeVarInt(this.windowId);
        packet.writeString(this.session);
        packet.writeUniqueId(this.request);
        packet.writeVarLong(this.revision);
        packet.writeBoolean(this.plan != null);
        if (this.plan != null) {
            this.plan.write(packet);
        }
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        if (this.valid && minecraft.player != null
            && minecraft.player.openContainer instanceof ContainerCraftConfirm container
            && this.windowId == container.windowId
            && this.windowId >= 0 && container.isValidContainer()) {
            container.receivePlan(this.session, this.request, this.revision, this.plan);
        }
    }
}
