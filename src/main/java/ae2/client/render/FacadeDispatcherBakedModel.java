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

package ae2.client.render;

import ae2.client.render.cablebus.FacadeBuilder;
import ae2.items.parts.FacadeItem;
import ae2.util.helpers.ItemComparisonHelper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class FacadeDispatcherBakedModel extends DelegateBakedModel {
    private final FacadeBuilder facadeBuilder;
    private final Map<CacheKey, FacadeBakedItemModel> cache = new Object2ObjectOpenHashMap<>();
    private final ItemOverrideList overrides = new ItemOverrideList(
        Collections.emptyList()) {
        @Override
        public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world,
                                           EntityLivingBase entity) {
            if (!(stack.getItem() instanceof FacadeItem facadeItem)) {
                return FacadeDispatcherBakedModel.this;
            }

            ItemStack textureItem = facadeItem.getTextureItem(stack);
            if (textureItem.isEmpty()) {
                return FacadeDispatcherBakedModel.this;
            }

            CacheKey cacheKey = new CacheKey(textureItem);
            synchronized (cache) {
                FacadeBakedItemModel model = cache.get(cacheKey);
                if (model == null) {
                    model = new FacadeBakedItemModel(getBaseModel(), textureItem, facadeBuilder);
                    cache.put(cacheKey, model);
                }
                return model;
            }
        }
    };

    public FacadeDispatcherBakedModel(IBakedModel baseModel, FacadeBuilder facadeBuilder) {
        super(baseModel);
        this.facadeBuilder = facadeBuilder;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.overrides;
    }

    private record CacheKey(ItemStack stack) {
        private CacheKey {
            stack = stack.copy();
            stack.setCount(1);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CacheKey(ItemStack stack1))) {
                return false;
            }

            return ItemComparisonHelper.isEqualItemType(this.stack, stack1)
                && ItemComparisonHelper.isNbtTagEqual(this.stack.getTagCompound(), stack1.getTagCompound());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.stack.getItem(), this.stack.isItemStackDamageable() ? 0 : this.stack.getItemDamage(),
                this.stack.getTagCompound());
        }
    }
}
