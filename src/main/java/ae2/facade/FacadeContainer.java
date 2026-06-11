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

package ae2.facade;

import ae2.api.implementations.items.IFacadeItem;
import ae2.api.parts.IFacadeContainer;
import ae2.api.parts.IFacadePart;
import ae2.api.parts.IPartHost;
import ae2.parts.CableBusStorage;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import java.util.function.Consumer;

public class FacadeContainer implements IFacadeContainer {

    private static final String NBT_KEY_PREFIX = "facadeState:";
    private static final String LEGACY_NBT_KEY_PREFIX = "facade:";

    private final CableBusStorage storage;
    private final Consumer<EnumFacing> changeCallback;

    public FacadeContainer(CableBusStorage storage, Consumer<EnumFacing> changeCallback) {
        this.storage = storage;
        this.changeCallback = changeCallback;
    }

    private static boolean isValidFacadeState(IBlockState blockState) {
        return blockState != null && blockState.getBlock() != Blocks.AIR;
    }

    @Override
    public boolean addFacade(IFacadePart facade) {
        if (!canAddFacade(facade)) {
            return false;
        }

        this.storage.setFacade(facade.getSide(), facade);
        notifyChange(facade.getSide());
        return true;
    }

    @Override
    public void removeFacade(IPartHost host, EnumFacing side) {
        if (side == null || this.storage.getFacade(side) == null) {
            return;
        }

        this.storage.removeFacade(side);
        notifyChange(side);
        if (host != null) {
            host.markForUpdate();
        }
    }

    @Override
    public IFacadePart getFacade(EnumFacing side) {
        return side == null ? null : this.storage.getFacade(side);
    }

    @Override
    public boolean canAddFacade(IFacadePart facade) {
        return facade != null
            && facade.getSide() != null
            && isValidFacadeState(facade.getBlockState())
            && getFacade(facade.getSide()) == null;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        for (EnumFacing side : EnumFacing.VALUES) {
            IFacadePart facade = this.storage.getFacade(side);
            if (facade != null && isValidFacadeState(facade.getBlockState())) {
                data.setInteger(NBT_KEY_PREFIX + side.ordinal(), Block.getStateId(facade.getBlockState()));
            }
        }
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean changed = false;
        int facadeMask = data.readUnsignedByte();

        for (EnumFacing side : EnumFacing.VALUES) {
            int bit = 1 << side.ordinal();
            if ((facadeMask & bit) != 0) {
                IBlockState blockState = Block.getStateById(data.readVarInt());
                if (isValidFacadeState(blockState)) {
                    this.storage.setFacade(side, new FacadePart(blockState, side));
                    changed = true;
                } else if (this.storage.getFacade(side) != null) {
                    this.storage.removeFacade(side);
                    changed = true;
                }
            } else if (this.storage.getFacade(side) != null) {
                this.storage.removeFacade(side);
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        for (EnumFacing side : EnumFacing.VALUES) {
            this.storage.removeFacade(side);

            String stateKey = NBT_KEY_PREFIX + side.ordinal();
            if (data.hasKey(stateKey, Constants.NBT.TAG_INT)) {
                IBlockState blockState = Block.getStateById(data.getInteger(stateKey));
                if (isValidFacadeState(blockState)) {
                    this.storage.setFacade(side, new FacadePart(blockState, side));
                }
                continue;
            }

            String legacyKey = LEGACY_NBT_KEY_PREFIX + side.ordinal();
            if (data.hasKey(legacyKey, Constants.NBT.TAG_COMPOUND)) {
                ItemStack facadeStack = new ItemStack(data.getCompoundTag(legacyKey));
                if (!facadeStack.isEmpty() && facadeStack.getItem() instanceof IFacadeItem facadeItem) {
                    IFacadePart facade = facadeItem.createPartFromItemStack(facadeStack, side);
                    if (facade != null && isValidFacadeState(facade.getBlockState())) {
                        this.storage.setFacade(side, facade);
                    }
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        for (EnumFacing side : EnumFacing.VALUES) {
            if (this.storage.getFacade(side) != null) {
                return false;
            }
        }

        return true;
    }

    private void notifyChange(EnumFacing side) {
        this.changeCallback.accept(side);
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        int facadeMask = 0;
        for (EnumFacing side : EnumFacing.VALUES) {
            IFacadePart facade = this.storage.getFacade(side);
            if (facade != null && isValidFacadeState(facade.getBlockState())) {
                facadeMask |= 1 << side.ordinal();
            }
        }

        data.writeByte(facadeMask);
        for (EnumFacing side : EnumFacing.VALUES) {
            IFacadePart facade = this.storage.getFacade(side);
            if (facade != null && isValidFacadeState(facade.getBlockState())) {
                data.writeVarInt(Block.getStateId(facade.getBlockState()));
            }
        }
    }
}
