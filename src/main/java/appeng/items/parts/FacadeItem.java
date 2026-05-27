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

package appeng.items.parts;

import appeng.api.implementations.items.IFacadeItem;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.facade.FacadePart;
import appeng.items.AEBaseItem;
import appeng.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FacadeItem extends AEBaseItem implements IFacadeItem {

    private static final String FACADE_ITEM = "facade_item";
    private static final String TAG_ITEM_ID = "item";
    private static final String TAG_DAMAGE = "damage";
    private static final Set<ResourceLocation> FACADE_BLOCK_WHITELIST = createFacadeBlockWhitelist();
    private List<ItemStack> subTypes;

    public FacadeItem() {
        this.setHasSubtypes(true);
    }

    private static Set<ResourceLocation> createFacadeBlockWhitelist() {
        Set<ResourceLocation> whitelist = new ObjectOpenHashSet<>();
        whitelist.add(new ResourceLocation("ae2", "quartz_glass"));
        whitelist.add(new ResourceLocation("ae2", "quartz_vibrant_glass"));
        whitelist.add(new ResourceLocation("minecraft", "furnace"));
        whitelist.add(new ResourceLocation("minecraft", "dropper"));
        whitelist.add(new ResourceLocation("minecraft", "dispenser"));
        return whitelist;
    }

    private static boolean placeFacade(FacadePart facade, World world, BlockPos pos) {
        IPartHost host = appeng.api.parts.PartHelper.getPartHost(world, pos);
        if (host == null) {
            return false;
        }

        if (!canPlaceFacade(host, facade)) {
            return false;
        }

        if (!host.getFacadeContainer().addFacade(facade)) {
            return false;
        }

        SoundType soundType = facade.getBlockState().getBlock().getSoundType(facade.getBlockState(), world, pos, null);
        world.playSound(null, pos, soundType.getPlaceSound(), SoundCategory.BLOCKS,
            (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

        host.markForSave();
        host.markForUpdate();
        return true;
    }

    public static boolean canPlaceFacade(IPartHost host, FacadePart facade) {
        if (host == null || facade == null) {
            return false;
        }

        if (host.getPart(null) == null) {
            return false;
        }

        return host.getFacadeContainer().canAddFacade(facade);
    }

    @SuppressWarnings("unused")
    @Nullable
    public static IFacadePart createFacade(ItemStack held, EnumFacing side) {
        if (held.getItem() instanceof IFacadeItem facadeItem) {
            return facadeItem.createPartFromItemStack(held, side);
        }
        return null;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != this) {
            return EnumActionResult.PASS;
        }

        FacadePart facade = createPartFromItemStack(stack, side);
        if (!placeFacade(facade, world, pos)) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            if (!player.capabilities.isCreativeMode) {
                stack.grow(-1);
                if (stack.isEmpty()) {
                    player.setHeldItem(hand, ItemStack.EMPTY);
                    MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, stack, hand));
                }
            }
            return EnumActionResult.SUCCESS;
        }

        player.swingArm(hand);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        try {
            ItemStack textureItem = this.getTextureItem(stack);
            if (!textureItem.isEmpty()) {
                return super.getItemStackDisplayName(stack) + " - " + TextComponentItemStack.of(textureItem).getFormattedText();
            }
        } catch (Throwable ignored) {
        }

        return super.getItemStackDisplayName(stack);
    }

    @Override
    protected void getCheckedSubItems(CreativeTabs creativeTab, net.minecraft.util.NonNullList<ItemStack> itemStacks) {
        itemStacks.addAll(this.getFacades());
    }

    public List<ItemStack> getFacades() {
        if (this.subTypes == null) {
            this.subTypes = this.calculateSubTypes();
        }
        return this.subTypes;
    }

    public ItemStack getCreativeTabIcon() {
        List<ItemStack> facades = this.getFacades();
        return facades.isEmpty() ? new ItemStack(Items.CAKE) : facades.getFirst();
    }

    private List<ItemStack> calculateSubTypes() {
        List<ItemStack> result = new ObjectArrayList<>(1000);
        for (Block block : Block.REGISTRY) {
            try {
                Item item = Item.getItemFromBlock(block);
                if (item == Items.AIR) {
                    continue;
                }

                net.minecraft.util.NonNullList<ItemStack> subBlocks = net.minecraft.util.NonNullList.create();
                block.getSubBlocks(block.getCreativeTab(), subBlocks);
                for (ItemStack subBlock : subBlocks) {
                    ItemStack facade = this.createFacadeForItem(subBlock, false);
                    if (!facade.isEmpty()) {
                        result.add(facade);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("deprecation")
    public ItemStack createFacadeForItem(ItemStack itemStack, boolean returnItem) {
        if (itemStack.isEmpty() || itemStack.hasTagCompound()) {
            return ItemStack.EMPTY;
        }

        Block block = Block.getBlockFromItem(itemStack.getItem());
        if (block == Blocks.AIR) {
            return ItemStack.EMPTY;
        }

        int metadata = itemStack.getItem().getMetadata(itemStack.getItemDamage());
        IBlockState blockState;
        try {
            blockState = block.getStateFromMeta(metadata);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }

        boolean isModel = blockState.getRenderType() == EnumBlockRenderType.MODEL;
        boolean isTileEntity = block.hasTileEntity(block.getDefaultState());
        boolean isFullCube = block.isFullCube(block.getDefaultState());
        ResourceLocation registryName = block.getRegistryName();
        boolean isWhitelisted = registryName != null && FACADE_BLOCK_WHITELIST.contains(registryName);

        if (isModel && (isFullCube || isWhitelisted) && (!isTileEntity || isWhitelisted)) {
            if (returnItem) {
                return itemStack;
            }

            return this.createFacadeForItemUnchecked(itemStack);
        }

        return ItemStack.EMPTY;
    }

    public ItemStack createFacadeForItemUnchecked(ItemStack itemStack) {
        ItemStack facade = new ItemStack(this);
        NBTTagCompound data = new NBTTagCompound();
        data.setString(FACADE_ITEM, Objects.requireNonNull(itemStack.getItem().getRegistryName()).toString());
        data.setInteger(TAG_DAMAGE, itemStack.getItemDamage());
        facade.setTagCompound(data);
        return facade;
    }

    @Override
    @Nullable
    public FacadePart createPartFromItemStack(ItemStack is, EnumFacing side) {
        ItemStack in = this.getTextureItem(is);
        if (!in.isEmpty()) {
            return new FacadePart(this.getTextureBlockState(is), side);
        }
        return null;
    }

    @Override
    public ItemStack getTextureItem(ItemStack is) {
        NBTTagCompound nbt = is.getTagCompound();
        if (nbt == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId;
        int itemDamage;

        if (nbt.hasKey(FACADE_ITEM, 8)) {
            itemId = new ResourceLocation(nbt.getString(FACADE_ITEM));
            itemDamage = nbt.getInteger(TAG_DAMAGE);
        } else if (nbt.hasKey("x")) {
            int[] data = nbt.getIntArray("x");
            if (data.length != 2) {
                return ItemStack.EMPTY;
            }

            Item item = Item.REGISTRY.getObjectById(data[0]);
            if (item == null) {
                return ItemStack.EMPTY;
            }

            itemId = item.getRegistryName();
            itemDamage = data[1];
        } else {
            itemId = new ResourceLocation(nbt.getString(TAG_ITEM_ID));
            itemDamage = nbt.getInteger(TAG_DAMAGE);
        }

        Item baseItem = Item.REGISTRY.getObject(itemId);
        if (baseItem == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(baseItem, 1, itemDamage);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getTextureBlockState(ItemStack is) {
        ItemStack baseItemStack = this.getTextureItem(is);
        if (baseItemStack.isEmpty()) {
            return Blocks.GLASS.getDefaultState();
        }

        Block block = Block.getBlockFromItem(baseItemStack.getItem());
        if (block == Blocks.AIR) {
            return Blocks.GLASS.getDefaultState();
        }

        int metadata = baseItemStack.getItem().getMetadata(baseItemStack.getItemDamage());
        try {
            return block.getStateFromMeta(metadata);
        } catch (Exception e) {
            return Blocks.GLASS.getDefaultState();
        }
    }
}
