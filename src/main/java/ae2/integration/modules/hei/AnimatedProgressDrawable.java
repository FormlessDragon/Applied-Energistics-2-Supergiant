package ae2.integration.modules.hei;

import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class AnimatedProgressDrawable implements IDrawableAnimated {
    private static final int ANIMATION_TIME_MS = 2000;

    private final IDrawableStatic source;
    private final int width;
    private final int height;

    AnimatedProgressDrawable(IDrawableStatic source, int width, int height) {
        this.source = source;
        this.width = width;
        this.height = height;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void draw(Minecraft minecraft) {
        this.draw(minecraft, 0, 0);
    }

    @Override
    public void draw(Minecraft minecraft, int xOffset, int yOffset) {
        int subTime = (int) (System.currentTimeMillis() % ANIMATION_TIME_MS);
        subTime = ANIMATION_TIME_MS - subTime;
        int maskTop = this.height * subTime / ANIMATION_TIME_MS;
        int drawnHeight = this.height - maskTop;
        if (drawnHeight <= 0) {
            return;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.source.draw(minecraft, xOffset, yOffset, maskTop, 0, 0, 0);
    }
}
