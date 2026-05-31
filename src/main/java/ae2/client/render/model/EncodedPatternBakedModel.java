package ae2.client.render.model;

import ae2.client.render.DelegateBakedModel;
import ae2.crafting.pattern.EncodedPatternItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Collections;

public class EncodedPatternBakedModel extends DelegateBakedModel {
    private final ItemOverrideList overrides = new ItemOverrideList(Collections.emptyList()) {
        @Override
        public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world,
                                           EntityLivingBase entity) {
            if (GuiScreen.isShiftKeyDown() && stack.getItem() instanceof EncodedPatternItem<?> encodedPattern) {
                World level = world != null ? world : Minecraft.getMinecraft().world;
                ItemStack output = encodedPattern.getOutput(stack, level);
                if (!output.isEmpty()) {
                    return Minecraft.getMinecraft().getRenderItem()
                                    .getItemModelWithOverrides(output, world, entity);
                }
            }

            return EncodedPatternBakedModel.this.getBaseModel()
                                                .getOverrides()
                                                .handleItemState(EncodedPatternBakedModel.this.getBaseModel(), stack, world, entity);
        }
    };

    public EncodedPatternBakedModel(IBakedModel base) {
        super(base);
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.overrides;
    }
}
