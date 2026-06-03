package ae2.client.gui.elements;

import ae2.client.gui.AEBaseGui;
import ae2.util.ColorData;
import net.minecraft.client.gui.Gui;

public class ColorArea extends DrawableArea {
    private ColorData color = new ColorData(1.0F, 1.0F, 1.0F);

    public ColorArea(int x, int y, int width, int height, AEBaseGui<?> parent, Runnable job) {
        super(x, y, width, height, parent, job);
    }

    public void setColor(ColorData color) {
        this.color = color;
    }

    public ColorData getColor() {
        return this.color;
    }

    @Override
    public void draw() {
        int x = this.x + this.screen.getGuiLeft();
        int y = this.y + this.screen.getGuiTop();
        Gui.drawRect(x, y, x + this.w, y + this.h, this.color.toARGB());
    }
}
