package ae2.integration.modules.hei;

import ae2.client.gui.Icon;
import mezz.jei.api.gui.IDrawable;
import net.minecraft.client.Minecraft;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class IconDrawable implements IDrawable {
    private final Icon icon;

    IconDrawable(Icon icon) {
        this.icon = icon;
    }

    @Override
    public int getWidth() {
        return this.icon.width;
    }

    @Override
    public int getHeight() {
        return this.icon.height;
    }

    @Override
    public void draw(Minecraft minecraft) {
        this.draw(minecraft, 0, 0);
    }

    @Override
    public void draw(Minecraft minecraft, int xOffset, int yOffset) {
        this.icon.getBlitter().dest(xOffset, yOffset).blit();
    }
}
