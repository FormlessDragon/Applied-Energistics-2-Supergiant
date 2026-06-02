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
import ae2.block.AEBaseBlock;
import ae2.block.AEBaseBlockItem;
import ae2.core.MainCreativeTab;
import ae2.util.helpers.ItemComparisonHelper;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class BlockDefinition<T extends Block> implements Supplier<T> {

    private final String englishName;
    private final ResourceLocation id;
    private final T block;
    private final ItemBlock itemBlock;

    public BlockDefinition(ResourceLocation id, T block) {
        this(defaultEnglishName(id), id, block);
    }

    public BlockDefinition(ResourceLocation id, T block, CreativeTabs creativeTab) {
        this(defaultEnglishName(id), id, block, creativeTab);
    }

    public BlockDefinition(String englishName, ResourceLocation id, T block) {
        this(englishName, id, block, MainCreativeTab.INSTANCE,
            actualBlock -> actualBlock instanceof AEBaseBlock ? new AEBaseBlockItem(actualBlock)
                : new ItemBlock(actualBlock));
    }

    public BlockDefinition(String englishName, ResourceLocation id, T block, CreativeTabs creativeTab) {
        this(englishName, id, block, creativeTab,
            actualBlock -> actualBlock instanceof AEBaseBlock ? new AEBaseBlockItem(actualBlock)
                : new ItemBlock(actualBlock));
    }

    public BlockDefinition(ResourceLocation id, T block, Function<T, ItemBlock> itemFactory) {
        this(defaultEnglishName(id), id, block, itemFactory);
    }

    public BlockDefinition(String englishName, ResourceLocation id, T block, Function<T, ItemBlock> itemFactory) {
        this(englishName, id, block, MainCreativeTab.INSTANCE, itemFactory);
    }

    private BlockDefinition(String englishName, ResourceLocation id, T block, @Nullable CreativeTabs creativeTab,
                            Function<T, ItemBlock> itemFactory) {
        this.englishName = englishName;
        this.id = id;
        this.block = block;
        this.itemBlock = itemFactory.apply(block);
        this.block.setRegistryName(id);
        this.block.setTranslationKey(id.getNamespace() + "." + id.getPath());
        if (this.itemBlock != null) {
            this.block.setCreativeTab(creativeTab);
            if (creativeTab != MainCreativeTab.INSTANCE) {
                MainCreativeTab.addExternal(creativeTab, this.asItemDefinition());
            } else {
                itemBlock.setRegistryName(id);
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

    public T block() {
        return this.block;
    }

    @Nullable
    public ItemBlock item() {
        return this.itemBlock;
    }

    public ItemStack stack() {
        return this.stack(1);
    }

    public ItemStack stack(int stackSize) {
        return this.itemBlock == null ? ItemStack.EMPTY : new ItemStack(this.itemBlock, stackSize);
    }

    @Nullable
    public GenericStack genericStack(long stackSize) {
        return this.itemBlock == null ? null
            : new GenericStack(Objects.requireNonNull(AEItemKey.of(this.itemBlock)), stackSize);
    }

    public boolean is(ItemStack comparableStack) {
        return this.itemBlock != null && ItemComparisonHelper.isEqualItemType(comparableStack, this.stack());
    }

    public boolean is(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return this.itemBlock == itemKey.getItem();
        }
        return false;
    }

    @Nullable
    public ItemBlock asItem() {
        return this.itemBlock;
    }

    @Override
    public T get() {
        return this.block;
    }

    private ItemDefinition<ItemBlock> asItemDefinition() {
        return new ItemDefinition<>(this.englishName, this.id, this.itemBlock, null);
    }
}
