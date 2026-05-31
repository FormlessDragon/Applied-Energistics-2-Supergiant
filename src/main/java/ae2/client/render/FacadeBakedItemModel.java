/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class FacadeBakedItemModel extends DelegateBakedModel {

    private final ItemStack textureStack;
    private final FacadeBuilder facadeBuilder;
    private List<BakedQuad> quads;

    protected FacadeBakedItemModel(IBakedModel base, ItemStack textureStack, FacadeBuilder facadeBuilder) {
        super(base);
        this.textureStack = textureStack.copy();
        this.facadeBuilder = facadeBuilder;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (side != null) {
            return Collections.emptyList();
        }
        if (this.quads == null) {
            it.unimi.dsi.fastutil.objects.ObjectList<BakedQuad> result = new ObjectArrayList<>();
            result.addAll(this.facadeBuilder.buildFacadeItemQuads(this.textureStack, EnumFacing.NORTH));
            result.addAll(this.getBaseModel().getQuads(state, side, rand));
            this.quads = ObjectLists.unmodifiable(result);
        }
        return this.quads;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
