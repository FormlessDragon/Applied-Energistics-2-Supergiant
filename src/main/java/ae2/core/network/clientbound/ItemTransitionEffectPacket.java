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

import ae2.client.render.effects.EnergyParticleData;
import ae2.client.render.effects.ParticleTypes;
import ae2.core.AppEngBase;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemTransitionEffectPacket extends ClientboundPacket {

    private double x;
    private double y;
    private double z;
    private EnumFacing direction = EnumFacing.UP;

    public ItemTransitionEffectPacket() {
    }

    public ItemTransitionEffectPacket(double x, double y, double z, EnumFacing direction) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.direction = direction;
    }

    @Override
    protected void read(ByteBuf buf) {
        var stream = new PacketBuffer(buf);
        this.x = stream.readFloat();
        this.y = stream.readFloat();
        this.z = stream.readFloat();
        this.direction = NetworkPacketHelper.readEnumOrNull(stream, EnumFacing.class);
        if (this.direction == null) {
            this.direction = EnumFacing.UP;
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeFloat((float) this.x);
        data.writeFloat((float) this.y);
        data.writeFloat((float) this.z);
        data.writeEnumValue(this.direction);
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

        EnergyParticleData data = new EnergyParticleData(true, this.direction);
        for (int zz = 0; zz < 8; zz++) {
            double sx = this.x + minecraft.world.rand.nextFloat() * 0.5 - 0.25;
            double sy = this.y + minecraft.world.rand.nextFloat() * 0.5 - 0.25;
            double sz = this.z + minecraft.world.rand.nextFloat() * 0.5 - 0.25;
            double speedX = 0.1f * this.direction.getXOffset();
            double speedY = 0.1f * this.direction.getYOffset();
            double speedZ = 0.1f * this.direction.getZOffset();
            ParticleTypes.ENERGY.spawn(minecraft.world, sx, sy, sz, speedX, speedY, speedZ, data);
        }
    }
}
