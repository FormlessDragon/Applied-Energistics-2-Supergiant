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
import ae2.core.AELog;
import ae2.core.AppEngBase;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("deprecation")
public class BlockTransitionEffectPacket extends ClientboundPacket {

    private BlockPos pos = BlockPos.ORIGIN;
    private IBlockState blockState;
    private EnumFacing direction = EnumFacing.UP;
    private SoundMode soundMode = SoundMode.NONE;

    public BlockTransitionEffectPacket() {
    }

    public BlockTransitionEffectPacket(BlockPos pos, IBlockState blockState, EnumFacing direction, SoundMode soundMode) {
        this.pos = pos;
        this.blockState = blockState;
        this.direction = direction;
        this.soundMode = soundMode;
    }

    @Override
    protected void read(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        this.pos = data.readBlockPos();
        int blockStateId = data.readVarInt();
        this.blockState = Block.getStateById(blockStateId);
        this.direction = NetworkPacketHelper.readEnumOrNull(data, EnumFacing.class);
        if (this.direction == null) {
            this.direction = EnumFacing.UP;
        }
        this.soundMode = NetworkPacketHelper.readEnumOrNull(data, SoundMode.class);
        if (this.soundMode == null) {
            this.soundMode = SoundMode.NONE;
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeBlockPos(this.pos);
        int blockStateId = Block.getStateId(this.blockState);
        if (blockStateId < 0) {
            AELog.warn("Failed to find numeric id for block state %s", this.blockState);
            blockStateId = 0;
        }
        data.writeVarInt(blockStateId);
        data.writeEnumValue(this.direction);
        data.writeEnumValue(this.soundMode);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.world == null) {
            return;
        }

        spawnParticles(minecraft);
        playBreakOrPickupSound(minecraft);
    }

    @SideOnly(Side.CLIENT)
    private void spawnParticles(Minecraft minecraft) {
        if (!AppEngBase.runtime().shouldSpawnParticleEffects(minecraft.world)) {
            return;
        }

        EnergyParticleData data = new EnergyParticleData(false, this.direction);
        for (int zz = 0; zz < 32; zz++) {
            double x = this.pos.getX() + minecraft.world.rand.nextFloat();
            double y = this.pos.getY() + minecraft.world.rand.nextFloat();
            double z = this.pos.getZ() + minecraft.world.rand.nextFloat();
            double speedX = 0.1f * this.direction.getXOffset();
            double speedY = 0.1f * this.direction.getYOffset();
            double speedZ = 0.1f * this.direction.getZOffset();
            ParticleTypes.ENERGY.spawn(minecraft.world, x, y, z, speedX, speedY, speedZ, data);
        }
    }

    @SideOnly(Side.CLIENT)
    private void playBreakOrPickupSound(Minecraft minecraft) {
        if (this.soundMode == SoundMode.NONE || this.blockState == null) {
            return;
        }

        if (this.soundMode == SoundMode.FLUID) {
            SoundEvent sound = getFluidFillSound();
            minecraft.getSoundHandler().playSound(new PositionedSoundRecord(sound, SoundCategory.BLOCKS, 1.0F, 1.0F,
                (float) this.pos.getX() + 0.5F, (float) this.pos.getY() + 0.5F, (float) this.pos.getZ() + 0.5F));
            return;
        }

        SoundType soundType = this.blockState.getBlock().getSoundType();
        minecraft.getSoundHandler().playSound(new PositionedSoundRecord(soundType.getBreakSound(),
            SoundCategory.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F,
            (float) this.pos.getX() + 0.5F, (float) this.pos.getY() + 0.5F, (float) this.pos.getZ() + 0.5F));
    }

    @SideOnly(Side.CLIENT)
    private SoundEvent getFluidFillSound() {
        Fluid fluid = FluidRegistry.lookupFluidForBlock(this.blockState.getBlock());
        if (fluid != null) {
            FluidStack stack = new FluidStack(fluid, 1);
            SoundEvent sound = fluid.getFillSound(stack);
            if (sound != null) {
                return sound;
            }
            if (fluid == FluidRegistry.LAVA) {
                return SoundEvents.ITEM_BUCKET_FILL_LAVA;
            }
        } else if (isLavaBlock(this.blockState.getBlock())) {
            return SoundEvents.ITEM_BUCKET_FILL_LAVA;
        } else if (this.blockState.getMaterial().isLiquid()) {
            return SoundEvents.ITEM_BUCKET_FILL;
        }

        return SoundEvents.ITEM_BUCKET_FILL;
    }

    @SideOnly(Side.CLIENT)
    private boolean isLavaBlock(Block block) {
        return block == Blocks.LAVA || block == Blocks.FLOWING_LAVA;
    }

    public enum SoundMode {
        BLOCK,
        FLUID,
        NONE
    }
}
