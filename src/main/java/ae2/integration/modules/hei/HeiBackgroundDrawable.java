package ae2.integration.modules.hei;

import ae2.client.gui.style.BackgroundGenerator;
import mezz.jei.api.gui.IDrawable;
import net.minecraft.client.Minecraft;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class HeiBackgroundDrawable implements IDrawable {
    private final int width;
    private final int height;

    HeiBackgroundDrawable(int width, int height) {
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
        BackgroundGenerator.draw(this.width, this.height, 0, 0);
    }

    @Override
    public void draw(Minecraft minecraft, int xOffset, int yOffset) {
        BackgroundGenerator.draw(this.width, this.height, xOffset, yOffset);
    }
}
