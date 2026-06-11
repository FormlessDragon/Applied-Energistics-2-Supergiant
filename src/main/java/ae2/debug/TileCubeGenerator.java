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

package ae2.debug;

import ae2.core.AppEng;
import ae2.core.localization.PlayerMessages;
import ae2.tile.AEBaseTile;
import ae2.tile.ServerTickingTile;
import ae2.util.InteractionUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

public class TileCubeGenerator extends AEBaseTile implements ServerTickingTile {

    private int size = 3;
    private ItemStack itemStack = ItemStack.EMPTY;
    private int countdown = 20 * 10;
    private EntityPlayer player;

    @Override
    public void serverTick() {
        if (!this.itemStack.isEmpty() && player != null) {
            this.countdown--;

            if (this.countdown % 20 == 0) {
                AppEng.instance().getPlayers().forEach(p -> p.sendMessage(
                    PlayerMessages.CubeGeneratorSpawningIn.text(this.countdown / 20)));
            }

            if (this.countdown <= 0) {
                spawn();
            }
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey("size", Constants.NBT.TAG_ANY_NUMERIC)) {
            this.size = data.getInteger("size");
        }
        if (data.hasKey("countdown", Constants.NBT.TAG_ANY_NUMERIC)) {
            this.countdown = data.getInteger("countdown");
        }
        if (data.hasKey("item", Constants.NBT.TAG_COMPOUND)) {
            this.itemStack = new ItemStack(data.getCompoundTag("item"));
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setInteger("size", this.size);
        data.setInteger("countdown", this.countdown);
        if (!this.itemStack.isEmpty()) {
            data.setTag("item", this.itemStack.writeToNBT(new NBTTagCompound()));
        }
    }

    void click(EntityPlayer player) {
        if (this.getWorld() == null || this.getWorld().isRemote) {
            return;
        }
        this.player = player;

        ItemStack hand = player.getHeldItem(EnumHand.MAIN_HAND);
        if (hand.isEmpty()) {
            this.itemStack = ItemStack.EMPTY;

            if (InteractionUtil.isInAlternateUseMode(player)) {
                this.size--;
            } else {
                this.size++;
            }

            if (this.size < 3) {
                this.size = 3;
            }
            if (this.size > 64) {
                this.size = 64;
            }

            player.sendMessage(PlayerMessages.CubeGeneratorSize.text(this.size));
        } else {
            this.countdown = 20 * 10;
            this.itemStack = hand.copy();
            this.itemStack.setCount(1);
        }

        this.markForUpdate();
        this.saveChanges();
    }

    private void spawn() {
        if (this.getWorld() == null || this.itemStack.isEmpty()) {
            return;
        }

        this.getWorld().setBlockToAir(this.pos);

        EnumFacing side = EnumFacing.UP;
        int half = (int) Math.floor(this.size / 2.0D);

        for (int y = 0; y < this.size; y++) {
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    BlockPos placePos = this.pos.add(x, y - 1, z);
                    this.itemStack.onItemUse(this.player, this.getWorld(), placePos, EnumHand.MAIN_HAND, side, 0.5F, 0.5F,
                        0.5F);
                }
            }
        }
    }
}
