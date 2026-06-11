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

package ae2.block;

import ae2.api.implementations.items.IMemoryCard;
import ae2.api.implementations.items.MemoryCardMessages;
import ae2.block.networking.CableBusBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.items.tools.MemoryCardItem;
import ae2.items.tools.quartz.QuartzCuttingKnifeItem;
import ae2.tile.AEBaseInvTile;
import ae2.tile.AEBaseTile;
import ae2.util.CustomNameUtil;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class AEBaseTileBlock<T extends AEBaseTile> extends AEBaseBlock implements ITileEntityProvider {

    public static final UnlistedDirection FORWARD = new UnlistedDirection("forward");
    public static final UnlistedDirection UP = new UnlistedDirection("up");
    private static final String MEMORY_CARD_SETTINGS_TAG = "settings";
    private static final String MEMORY_CARD_SOURCE_TAG = "exported_settings_source";
    @Nullable
    private Class<T> tileEntityClass;

    protected AEBaseTileBlock(Material material) {
        super(material);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, getOrientationStrategy().getProperties().toArray(new IProperty<?>[0]),
            new IUnlistedProperty<?>[]{FORWARD, UP});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess level, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        T tile = this.getTileEntity(level, pos);
        if (tile == null) {
            return state;
        }

        return ((IExtendedBlockState) state).withProperty(FORWARD, tile.getForward()).withProperty(UP, tile.getUp());
    }

    public void setTileEntity(Class<T> tileEntityClass) {
        this.tileEntityClass = tileEntityClass;
        this.setInventory(AEBaseInvTile.class.isAssignableFrom(tileEntityClass));
    }

    @Nullable
    public T getTileEntity(IBlockAccess level, int x, int y, int z) {
        return this.getTileEntity(level, new BlockPos(x, y, z));
    }

    @Nullable
    public T getTileEntity(IBlockAccess level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }

        final TileEntity te = level.getTileEntity(pos);
        if (this.tileEntityClass != null && this.tileEntityClass.isInstance(te)) {
            return this.tileEntityClass.cast(te);
        }
        return null;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return this.tileEntityClass != null;
    }

    @Nullable
    @Override
    public final TileEntity createNewTileEntity(World worldIn, int meta) {
        if (this.tileEntityClass == null) {
            return null;
        }

        try {
            return this.tileEntityClass.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("Failed to create a new instance of an illegal class " + this.tileEntityClass,
                e);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(
                "Failed to create a new instance of " + this.tileEntityClass + ", because lack of permissions", e);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        T tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return;
        }

        tile.syncOrientationFromBlockState(state);

        if (tile instanceof IOwnerAwareTile && placer instanceof EntityPlayer) {
            ((IOwnerAwareTile) tile).setOwner((EntityPlayer) placer);
        }

        String customName = CustomNameUtil.getDisplayName(stack);
        if (customName != null) {
            tile.setCustomName(customName);
        }

        EntityPlayer player = placer instanceof EntityPlayer ? (EntityPlayer) placer : null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tile.importSettings(SettingsFrom.DISMANTLE_ITEM, tag, player);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && this.useMemoryCard(world, pos, heldItem, player)) {
            return true;
        }
        if (!heldItem.isEmpty()
            && heldItem.getItem() instanceof QuartzCuttingKnifeItem
            && !(this instanceof CableBusBlock)) {
            T tile = this.getTileEntity(world, pos);
            if (tile == null) {
                return true;
            }
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.RENAMER, tile);
            }
            return true;
        }
        return super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasComparatorInputOverride(IBlockState state) {
        return this.tileEntityClass != null && AEBaseInvTile.class.isAssignableFrom(this.tileEntityClass);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        T tile = this.getTileEntity(world, pos);
        if (tile instanceof AEBaseInvTile invTile) {
            if (!invTile.getInternalInventory().isEmpty()) {
                return invTile.getInternalInventory().getRedstoneSignal();
            }
        }
        return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean eventReceived(IBlockState state, World world, BlockPos pos, int id, int param) {
        super.eventReceived(state, world, pos, id, param);
        TileEntity tile = world.getTileEntity(pos);
        return tile != null && tile.receiveClientEvent(id, param);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        T tile = this.getTileEntity(world, pos);
        if (tile != null && !world.isRemote) {
            List<ItemStack> drops = new ObjectArrayList<>();
            tile.addAdditionalDrops(drops);
            tile.clearContent();
            Platform.spawnDrops(world, pos, drops);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state,
                         int fortune) {
        super.getDrops(drops, world, pos, state, fortune);

        T tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return;
        }

        NBTTagCompound settings = tile.exportSettings(SettingsFrom.DISMANTLE_ITEM);
        if (Platform.isNbtEmpty(settings)) {
            return;
        }

        for (ItemStack drop : drops) {
            if (drop.getItem() instanceof ItemBlock && ((ItemBlock) drop.getItem()).getBlock() == this) {
                NBTTagCompound tag = Platform.openNbtData(drop);
                tag.merge(settings);
                break;
            }
        }
    }

    public final IBlockState getTileEntityBlockState(IBlockState current, TileEntity tileEntity) {
        if (current.getBlock() != this || this.tileEntityClass == null || !this.tileEntityClass.isInstance(tileEntity)) {
            return current;
        }

        IBlockState state = current;
        if (tileEntity instanceof AEBaseTile baseTile) {
            state = baseTile.applyOrientationToBlockState(state);
        }

        return updateBlockStateFromTileEntity(state, this.tileEntityClass.cast(tileEntity));
    }

    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, T tileEntity) {
        return currentState;
    }

    private boolean useMemoryCard(World world, BlockPos pos, ItemStack memoryCardStack, EntityPlayer player) {
        if (!(memoryCardStack.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }

        T tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return true;
        }

        String sourceId = this.getSettingsSourceName();
        NBTTagCompound settings = tile.exportSettings(SettingsFrom.MEMORY_CARD);

        if (InteractionUtil.isInAlternateUseMode(player)) {
            if (!world.isRemote) {
                MemoryCardItem.clearCard(memoryCardStack);
                if (!Platform.isNbtEmpty(settings)) {
                    NBTTagCompound tag = Platform.openNbtData(memoryCardStack);
                    tag.setTag(MEMORY_CARD_SETTINGS_TAG, settings);
                    tag.setString(MEMORY_CARD_SOURCE_TAG, sourceId);
                    memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
                }
            }
            return true;
        }

        NBTTagCompound tag = memoryCardStack.getTagCompound();
        if (tag == null || !tag.hasKey(MEMORY_CARD_SETTINGS_TAG, 10)) {
            return true;
        }

        if (!world.isRemote) {
            if (sourceId.equals(tag.getString(MEMORY_CARD_SOURCE_TAG))) {
                tile.importSettings(SettingsFrom.MEMORY_CARD, tag.getCompoundTag(MEMORY_CARD_SETTINGS_TAG), player);
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            } else {
                MemoryCardItem.importGenericSettingsAndNotify(tile, tag.getCompoundTag(MEMORY_CARD_SETTINGS_TAG),
                    player);
            }
        }

        return true;
    }

    private String getSettingsSourceName() {
        ItemStack item = new ItemStack(this);
        if (item.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) item.getItem()).getBlock();
            if (block == this) {
                return item.getTranslationKey() + ".name";
            }
        }
        return this.getLocalizedName();
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        var i = super.getPickBlock(state, target, world, pos, player);
        if (this.hasTileEntity(state)) {
            var t = this.getTileEntity(world, pos);
            if (t != null && t.hasCustomName()) {
                i.setStackDisplayName(t.getCustomName());
            }
        }
        return i;
    }
}
