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

package ae2.core.definitions;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.MainCreativeTab;
import ae2.util.helpers.ItemComparisonHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public class ItemDefinition<T extends Item> implements Supplier<T> {

    private final String englishName;
    private final ResourceLocation id;
    private final T item;

    public ItemDefinition(ResourceLocation id, T item) {
        this(defaultEnglishName(id), id, item, MainCreativeTab.INSTANCE);
    }

    public ItemDefinition(ResourceLocation id, T item, @Nullable CreativeTabs creativeTab) {
        this(defaultEnglishName(id), id, item, creativeTab);
    }

    public ItemDefinition(String englishName, ResourceLocation id, T item) {
        this(englishName, id, item, MainCreativeTab.INSTANCE);
    }

    public ItemDefinition(String englishName, ResourceLocation id, T item, @Nullable CreativeTabs creativeTab) {
        this.englishName = englishName;
        this.id = id;
        this.item = item;
        this.item.setRegistryName(id);
        this.item.setTranslationKey(id.getNamespace() + "." + id.getPath());
        if (creativeTab != null) {
            this.item.setCreativeTab(creativeTab);
            if (creativeTab != MainCreativeTab.INSTANCE) {
                MainCreativeTab.addExternal(creativeTab, this);
            }
        }
    }

    private static String defaultEnglishName(ResourceLocation id) {
        String[] parts = id.getPath().split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.toString();
    }

    @SuppressWarnings("unused")
    public String getEnglishName() {
        return this.englishName;
    }

    public ResourceLocation id() {
        return this.id;
    }

    public ItemStack stack() {
        return this.stack(1);
    }

    public ItemStack stack(int stackSize) {
        return new ItemStack(this.item, stackSize);
    }

    public GenericStack genericStack(long stackSize) {
        return new GenericStack(Objects.requireNonNull(AEItemKey.of(this.item)), stackSize);
    }

    @Nullable
    public T item() {
        return this.item;
    }

    @Nullable
    public T asItem() {
        return this.item;
    }

    /**
     * Compare {@link ItemStack} with this
     *
     * @param comparableStack compared item
     * @return true if the item stack is a matching item.
     */
    @Deprecated
    public final boolean isSameAs(ItemStack comparableStack) {
        return this.is(comparableStack);
    }

    /**
     * Compare {@link ItemStack} with this
     *
     * @param comparableStack compared item
     * @return true if the item stack is a matching item.
     */
    public final boolean is(ItemStack comparableStack) {
        return ItemComparisonHelper.isEqualItemType(comparableStack, this.stack());
    }

    /**
     * @return True if this item is represented by the given key.
     */
    public final boolean is(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return this.asItem() == itemKey.getItem();
        }
        return false;
    }

    /**
     * @return True if this item is represented by the given key.
     */
    @Deprecated
    public final boolean isSameAs(AEKey key) {
        return this.is(key);
    }

    @Override
    public T get() {
        return this.item;
    }
}
