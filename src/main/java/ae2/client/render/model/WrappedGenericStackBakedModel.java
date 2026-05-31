package ae2.client.render.model;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.style.FluidBlitter;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.model.BakedItemModel;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.IModelState;

import java.util.Collections;
import java.util.Optional;

@SuppressWarnings("deprecation")
class WrappedGenericStackBakedModel extends BakedItemModel {
    private final VertexFormat format;
    private final IModelState state;

    WrappedGenericStackBakedModel(VertexFormat format, IModelState state, TextureAtlasSprite particle) {
        super(ImmutableList.of(), particle, PerspectiveMapWrapper.getTransforms(state), ItemOverrideList.NONE, false);
        this.format = format;
        this.state = state;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return new ItemOverrideList(Collections.emptyList()) {
            @Override
            public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world,
                                               EntityLivingBase entity) {
                GenericStack genericStack = GenericStack.unwrapItemStack(stack);
                if (genericStack == null) {
                    return WrappedGenericStackBakedModel.this;
                }

                AEKey key = genericStack.what();
                if (key instanceof AEItemKey itemKey) {
                    ItemStack displayStack = itemKey.toStack();
                    return Minecraft.getMinecraft().getRenderItem()
                                    .getItemModelWithOverrides(displayStack, world, entity);
                }

                if (key instanceof AEFluidKey fluidKey) {
                    TextureAtlasSprite sprite = FluidBlitter.getStillSprite(fluidKey.toStack(1));
                    return new BakedItemModel(
                        ItemLayerModel.getQuadsForSprite(0, sprite, format, state.apply(Optional.empty())),
                        sprite,
                        PerspectiveMapWrapper.getTransforms(state),
                        ItemOverrideList.NONE,
                        false) {
                        @Override
                        public boolean isGui3d() {
                            return false;
                        }
                    };
                }

                return WrappedGenericStackBakedModel.this;
            }
        };
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }
}
