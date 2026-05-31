package ae2.client.render;

import ae2.api.util.AEColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemStack;

public class StaticItemColor implements IItemColor {
    private final AEColor color;

    public StaticItemColor(AEColor color) {
        this.color = color;
    }

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        return this.color.getVariantByTintIndex(tintIndex);
    }
}
