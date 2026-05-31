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

package ae2.util;

import ae2.api.config.PowerUnit;
import ae2.api.config.SortOrder;
import ae2.api.util.AEPartLocation;
import ae2.api.util.DimensionalBlockPos;
import ae2.core.AEConfig;
import ae2.integration.Integrations;
import ae2.integration.modules.bogosorter.InventoryBogoSortModule;
import ae2.util.helpers.P2PHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Platform {
    public static final EnumFacing[] DIRECTIONS_WITH_NULL = new EnumFacing[]{
        EnumFacing.DOWN,
        EnumFacing.UP,
        EnumFacing.NORTH,
        EnumFacing.SOUTH,
        EnumFacing.WEST,
        EnumFacing.EAST,
        null
    };
    private static final UUID DEFAULT_FAKE_PLAYER_UUID = UUID.fromString("60C173A5-E1E6-4B87-85B1-272CE424521D");
    private static final P2PHelper P2P_HELPER = new P2PHelper();

    private Platform() {
    }

    public static P2PHelper p2p() {
        return P2P_HELPER;
    }

    public static NBTTagCompound openNbtData(final ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    public static boolean isNbtEmpty(final NBTTagCompound tag) {
        return tag == null || tag.getKeySet().isEmpty();
    }

    public static boolean isServer() {
        return FMLCommonHandler.instance().getEffectiveSide().isServer();
    }

    /**
     * This displays the value for encoded longs ( double *100 )
     *
     * @param n      to be formatted long value
     * @param isRate if true it adds a /t to the formatted string
     * @return formatted long value
     */
    public static String formatPowerLong(long n, boolean isRate) {
        return formatPower((double) n / 100, isRate);
    }

    public static String formatPower(double p, boolean isRate) {
        var displayUnits = AEConfig.instance().getSelectedEnergyUnit();
        p = PowerUnit.AE.convertTo(displayUnits, p);

        final String[] preFixes = {"k", "M", "G", "T", "P", "T", "P", "E", "Z", "Y"};
        var unitName = displayUnits.getSymbolName();

        String level = "";
        int offset = 0;
        while (p > 1000 && offset < preFixes.length) {
            p /= 1000;
            level = preFixes[offset];
            offset++;
        }

        final DecimalFormat df = new DecimalFormat("#.##");
        return df.format(p) + ' ' + level + unitName + (isRate ? "/t" : "");
    }

    public static String formatTimeMeasurement(long nanos) {
        if (nanos <= 0) {
            return "0 ns";
        } else if (nanos < 1000) {
            return "<1 µs";
        } else if (nanos <= 1000 * 1000) {
            final long ms = TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS);
            return ms + "µs";
        }

        final long ms = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
        return ms + "ms";
    }

    public static String getModName(String modId) {
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        if (mod == null) {
            return modId;
        }
        return mod.getName();
    }

    public static boolean areBlockEntitiesTicking(@Nullable World level, BlockPos pos) {
        if (level == null || level.isRemote) {
            return false;
        }
        return level.isBlockLoaded(pos);
    }

    @Nullable
    public static TileEntity getTickingTile(@Nullable World level, BlockPos pos) {
        if (!areBlockEntitiesTicking(level, pos)) {
            return null;
        }
        return level.getTileEntity(pos);
    }

    public static boolean hasPermissions(DimensionalBlockPos dc, net.minecraft.entity.player.EntityPlayer player) {
        if (!dc.isInWorld(player.world)) {
            return false;
        }
        return player.canPlayerEdit(dc.getPos(), EnumFacing.UP, ItemStack.EMPTY);
    }

    public static FakePlayer getFakeEntityPlayer(World level, @Nullable UUID playerUuid) {
        if (!(level instanceof WorldServer worldServer)) {
            throw new IllegalArgumentException("Fake players can only be created for server worlds.");
        }

        if (playerUuid == null) {
            playerUuid = DEFAULT_FAKE_PLAYER_UUID;
        }
        return FakePlayerFactory.get(worldServer, new GameProfile(playerUuid, "[AE2]"));
    }

    public static void configurePlayer(final EntityPlayer player, final AEPartLocation side, final TileEntity tile) {
        float pitch = 0.0f;
        float yaw = 0.0f;

        switch (side) {
            case DOWN -> pitch = 90.0f;
            case EAST -> yaw = -90.0f;
            case NORTH -> yaw = 180.0f;
            case SOUTH, INTERNAL -> {
            }
            case UP -> pitch = -90.0f;
            case WEST -> yaw = 90.0f;
        }

        player.posX = tile.getPos().getX() + 0.5;
        player.posY = tile.getPos().getY() + 0.5;
        player.posZ = tile.getPos().getZ() + 0.5;

        player.rotationPitch = player.prevCameraPitch = player.cameraPitch = pitch;
        player.rotationYaw = player.prevCameraYaw = player.cameraYaw = yaw;
    }

    public static EnumFacing rotateAround(EnumFacing forward, EnumFacing axis) {
        if (forward.getAxis() == axis.getAxis()) {
            return forward;
        }

        Vec3i newForward = forward.getDirectionVec().crossProduct(axis.getDirectionVec());
        return EnumFacing.getFacingFromVector(newForward.getX(), newForward.getY(), newForward.getZ());
    }

    public static void spawnDrops(World level, BlockPos pos, List<net.minecraft.item.ItemStack> drops) {
        if (level == null || level.isRemote) {
            return;
        }

        for (var stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }

            double offsetX = (level.rand.nextFloat() * 0.5F) + 0.25F;
            double offsetY = (level.rand.nextFloat() * 0.5F) + 0.25F;
            double offsetZ = (level.rand.nextFloat() * 0.5F) + 0.25F;
            var entity = new EntityItem(level,
                pos.getX() + offsetX,
                pos.getY() + offsetY,
                pos.getZ() + offsetZ,
                stack.copy());
            entity.motionX = MathHelper.clamp(level.rand.nextGaussian() * 0.05D, -0.25D, 0.25D);
            entity.motionY = MathHelper.clamp(level.rand.nextGaussian() * 0.05D + 0.2D, -0.25D, 0.25D);
            entity.motionZ = MathHelper.clamp(level.rand.nextGaussian() * 0.05D, -0.25D, 0.25D);
            level.spawnEntity(entity);
        }
    }

    public static void notifyBlocksOfNeighbors(World level, BlockPos pos) {
        if (level == null || level.isRemote) {
            return;
        }

        var state = level.getBlockState(pos);
        level.notifyNeighborsOfStateChange(pos, state.getBlock(), true);
    }

    public static boolean isSortOrderAvailable(SortOrder order) {
        return order != SortOrder.INVTWEAKS
            || Integrations.invTweaks().isEnabled()
            || InventoryBogoSortModule.isLoaded();
    }

    public static void sendImmediateTileEntityUpdate(net.minecraft.entity.player.EntityPlayer player, BlockPos pos) {
        if (!(player instanceof EntityPlayerMP serverPlayer)) {
            return;
        }

        TileEntity tile = serverPlayer.world.getTileEntity(pos);
        if (tile == null) {
            return;
        }

        SPacketUpdateTileEntity packet = tile.getUpdatePacket();
        if (packet != null) {
            serverPlayer.connection.sendPacket(packet);
        }
    }
}


