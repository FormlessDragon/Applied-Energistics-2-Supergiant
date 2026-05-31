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

package ae2.debug;

import ae2.core.localization.PlayerMessages;
import ae2.items.AEBaseItem;
import ae2.util.InteractionUtil;
import ae2.worldgen.meteorite.CraterType;
import ae2.worldgen.meteorite.MeteoritePlacer;
import ae2.worldgen.meteorite.PlacedMeteoriteSettings;
import ae2.worldgen.meteorite.debug.MeteoriteSpawner;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;

public class MeteoritePlacerItem extends AEBaseItem {

    private static final String MODE_TAG = "mode";

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote && InteractionUtil.isInAlternateUseMode(player)) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }

            byte mode = tag.hasKey(MODE_TAG) ? tag.getByte(MODE_TAG) : (byte) CraterType.NORMAL.ordinal();
            tag.setByte(MODE_TAG, (byte) ((mode + 1) % CraterType.values().length));
            player.sendMessage(new TextComponentString(getCraterType(stack).name()));
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote || player == null) {
            return EnumActionResult.PASS;
        }

        ItemStack stack = player.getHeldItem(hand);
        float coreRadius = world.rand.nextFloat() * 6.0f + 2;
        boolean pureCrater = world.rand.nextFloat() > 0.5f;
        CraterType craterType = getCraterType(stack);

        MeteoriteSpawner spawner = new MeteoriteSpawner();
        PlacedMeteoriteSettings spawned = spawner.trySpawnMeteoriteAtSuitableHeight(world, pos, coreRadius, craterType,
            pureCrater);

        if (spawned == null) {
            player.sendMessage(PlayerMessages.MeteoriteUnsuitableLocation.text());
            return EnumActionResult.FAIL;
        }

        int range = (int) Math.ceil((coreRadius * 2 + 5) * 5f);
        StructureBoundingBox boundingBox = new StructureBoundingBox(pos.getX() - range, pos.getY() - 10,
            pos.getZ() - range, pos.getX() + range, pos.getY() + 10, pos.getZ() + range);

        MeteoritePlacer.place(world, spawned, boundingBox, world.rand);
        syncVisibleChunksToPlayer((WorldServer) world, (EntityPlayerMP) player, spawned.pos());
        player.sendMessage(PlayerMessages.MeteoriteSpawned.text(spawned.pos().getY(), range));
        return EnumActionResult.SUCCESS;
    }

    private void syncVisibleChunksToPlayer(WorldServer world, EntityPlayerMP player, BlockPos center) {
        ChunkPos centerChunk = new ChunkPos(center);
        for (int chunkX = centerChunk.x - 1; chunkX <= centerChunk.x + 1; chunkX++) {
            for (int chunkZ = centerChunk.z - 1; chunkZ <= centerChunk.z + 1; chunkZ++) {
                Chunk chunk = world.getChunk(chunkX, chunkZ);
                player.connection.sendPacket(new SPacketChunkData(chunk, 65535));
            }
        }
    }

    private CraterType getCraterType(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? CraterType.values()[CraterType.NORMAL.ordinal()]
            : CraterType.values()[tag.getByte(MODE_TAG)];
    }
}
