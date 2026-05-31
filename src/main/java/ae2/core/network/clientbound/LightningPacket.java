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

import ae2.client.render.effects.ParticleTypes;
import ae2.core.AppEngBase;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class LightningPacket extends ClientboundPacket {

    private double x;
    private double y;
    private double z;

    public LightningPacket() {
    }

    public LightningPacket(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    protected void read(ByteBuf buf) {
        var stream = new PacketBuffer(buf);
        this.x = stream.readFloat();
        this.y = stream.readFloat();
        this.z = stream.readFloat();
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeFloat((float) this.x);
        data.writeFloat((float) this.y);
        data.writeFloat((float) this.z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.world == null) {
            return;
        }

        if (!AppEngBase.runtime().shouldSpawnParticleEffects(minecraft.world)) {
            return;
        }

        ParticleTypes.LIGHTNING.spawn(minecraft.world, this.x, this.y, this.z, 0.0f, 0.0f, 0.0f, null);
    }
}
