package ae2.client.render.tesr;

import ae2.tile.misc.TileCrystalFixer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

public class CrystalFixerTESR extends TileEntitySpecialRenderer<TileCrystalFixer> {
    @Override
    public void render(TileCrystalFixer te, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        ItemStack stack = te.getClientDisplayFuel();
        if (stack.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5F, y + 0.5F, z + 0.4F);
        GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GROUND);
        GlStateManager.popMatrix();
    }
}
