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

public class MatterCannonPacket extends ClientboundPacket {

    private static final float MAX_PARTICLE_DISTANCE = 64.0F;
    private static final int MAX_PARTICLE_COUNT = 64;

    private double x;
    private double y;
    private double z;
    private double dx;
    private double dy;
    private double dz;
    private float length;

    public MatterCannonPacket() {
    }

    public MatterCannonPacket(double x, double y, double z, double dx, double dy, double dz, float length) {
        var dl = dx * dx + dy * dy + dz * dz;
        var dlz = Math.sqrt(dl);

        this.x = x;
        this.y = y;
        this.z = z;
        if (dlz > 0) {
            this.dx = dx / dlz;
            this.dy = dy / dlz;
            this.dz = dz / dlz;
        } else {
            this.dx = 0;
            this.dy = 0;
            this.dz = 0;
        }
        this.length = sanitizeLength(length);
    }

    private static float sanitizeLength(float length) {
        if (!Float.isFinite(length)) {
            return 0;
        }
        return Math.clamp(length, 0, MAX_PARTICLE_DISTANCE);
    }

    @Override
    protected void read(ByteBuf buf) {
        var stream = new PacketBuffer(buf);
        this.x = stream.readFloat();
        this.y = stream.readFloat();
        this.z = stream.readFloat();
        this.dx = stream.readFloat();
        this.dy = stream.readFloat();
        this.dz = stream.readFloat();
        this.length = sanitizeLength(stream.readFloat());
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeFloat((float) this.x);
        data.writeFloat((float) this.y);
        data.writeFloat((float) this.z);
        data.writeFloat((float) this.dx);
        data.writeFloat((float) this.dy);
        data.writeFloat((float) this.dz);
        data.writeFloat(this.length);
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

        var particles = Math.clamp((int) Math.ceil(this.length), 1, MAX_PARTICLE_COUNT);
        var step = this.length / particles;
        for (int a = 1; a <= particles; a++) {
            var offset = step * a;
            ParticleTypes.MATTER_CANNON.spawn(minecraft.world, this.x + this.dx * offset, this.y + this.dy * offset,
                this.z + this.dz * offset, 0, 0, 0, null);
        }
    }
}
