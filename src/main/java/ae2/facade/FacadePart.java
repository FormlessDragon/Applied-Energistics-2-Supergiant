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

import ae2.api.parts.IFacadePart;
import ae2.api.parts.IPartCollisionHelper;
import ae2.core.definitions.AEItems;
import ae2.core.localization.PlayerMessages;
import ae2.util.InteractionUtil;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public class FacadePart implements IFacadePart {
    private static final String FACADE_CYCLE_PROPERTY = "facade_cycle_property";

    private final EnumFacing side;
    private IBlockState facade;

    public FacadePart(IBlockState facade, EnumFacing side) {
        this.facade = facade;
        this.side = side;
    }

    private static String getCyclePropertyName(ItemStack heldItem, String defaultValue) {
        NBTTagCompound tag = heldItem.getTagCompound();
        if (tag == null) {
            return defaultValue;
        }

        return tag.hasKey(FACADE_CYCLE_PROPERTY, 8) ? tag.getString(FACADE_CYCLE_PROPERTY) : defaultValue;
    }

    private static void setCyclePropertyName(ItemStack heldItem, String propertyName) {
        NBTTagCompound tag = heldItem.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            heldItem.setTagCompound(tag);
        }

        tag.setString(FACADE_CYCLE_PROPERTY, propertyName);
    }

    private static void clearCyclePropertyName(ItemStack heldItem) {
        NBTTagCompound tag = heldItem.getTagCompound();
        if (tag == null) {
            return;
        }

        tag.removeTag(FACADE_CYCLE_PROPERTY);
        if (tag.getKeySet().isEmpty()) {
            heldItem.setTagCompound(null);
        }
    }

    private static @Nullable IProperty<?> getProperty(Collection<IProperty<?>> properties, String name) {
        for (IProperty<?> property : properties) {
            if (property.getName().equals(name)) {
                return property;
            }
        }

        return null;
    }

    private static IProperty<?> getNextProperty(Collection<IProperty<?>> properties, IProperty<?> currentProperty) {
        boolean returnNext = false;
        for (IProperty<?> property : properties) {
            if (returnNext) {
                return property;
            }

            if (property == currentProperty) {
                returnNext = true;
            }
        }

        return properties.iterator().next();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState cyclePropertyUnchecked(IBlockState blockState, IProperty<?> property) {
        Comparable currentValue = blockState.getValue((IProperty) property);
        Comparable nextValue = getNextValueRaw(property.getAllowedValues(), currentValue);
        return blockState.withProperty((IProperty) property, nextValue);
    }

    @SuppressWarnings("rawtypes")
    private static Comparable getNextValueRaw(Collection values, Comparable currentValue) {
        boolean returnNext = false;
        for (Object value : values) {
            if (returnNext) {
                return (Comparable) value;
            }

            if (Objects.equals(value, currentValue)) {
                returnNext = true;
            }
        }

        return (Comparable) values.iterator().next();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getPropertyValue(IBlockState blockState, IProperty<?> property) {
        return blockState.getValue((IProperty) property);
    }

    private static void message(EntityPlayer player, ITextComponent message) {
        if (!player.world.isRemote) {
            player.sendStatusMessage(message, true);
        }
    }

    @Override
    public ItemStack getItemStack() {
        return AEItems.FACADE.get().createFacadeForItemUnchecked(getTextureItem());
    }

    @Override
    public void getBoxes(IPartCollisionHelper ch, boolean itemEntity) {
        if (itemEntity) {
            ch.addBox(0.0, 0.0, 14.0, 16.0, 16.0, 15.9);
        } else {
            ch.addBox(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);
        }
    }

    @Override
    public EnumFacing getSide() {
        return this.side;
    }

    @Override
    public Item getItem() {
        return Item.getItemFromBlock(this.facade.getBlock());
    }

    @Override
    public ItemStack getTextureItem() {
        Item item = getItem();
        return item == Items.AIR ? ItemStack.EMPTY
            : new ItemStack(item, 1, this.facade.getBlock().damageDropped(this.facade));
    }

    @Override
    public IBlockState getBlockState() {
        return this.facade;
    }

    private void setBlockState(IBlockState blockState) {
        this.facade = blockState;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!InteractionUtil.canWrenchRotate(heldItem)) {
            return false;
        }

        return handleInteraction(player, true, heldItem);
    }

    @Override
    public boolean onClicked(EntityPlayer player, Vec3d pos) {
        ItemStack heldItem = player.getHeldItemMainhand();
        if (!InteractionUtil.canWrenchRotate(heldItem)) {
            return false;
        }

        return handleInteraction(player, false, heldItem);
    }

    private boolean handleInteraction(EntityPlayer player, boolean shouldCycleState, ItemStack heldItem) {
        Collection<IProperty<?>> properties = getBlockState().getPropertyKeys();
        if (properties.isEmpty()) {
            return false;
        }

        IProperty<?> firstProperty = properties.iterator().next();
        String cyclePropertyName = getCyclePropertyName(heldItem, firstProperty.getName());
        IProperty<?> property = getProperty(properties, cyclePropertyName);
        if (property == null) {
            property = firstProperty;
        }

        if (shouldCycleState) {
            IBlockState newState = cyclePropertyUnchecked(getBlockState(), property);
            setBlockState(newState);

            Object defaultValue = getPropertyValue(newState.getBlock().getDefaultState(), property);
            if (Objects.equals(getPropertyValue(newState, property), defaultValue)) {
                message(player, PlayerMessages.FacadePropertyWrapped.text(property.getName()));
            }
        } else {
            property = getNextProperty(properties, property);
            if (property == firstProperty) {
                clearCyclePropertyName(heldItem);
            } else {
                setCyclePropertyName(heldItem, property.getName());
            }
            message(player, PlayerMessages.FacadePropertySelected.text(property.getName()));
        }

        return true;
    }
}
