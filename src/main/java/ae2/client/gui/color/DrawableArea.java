package ae2.client.gui.color;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;

import java.awt.Rectangle;

public abstract class DrawableArea extends ClickableArea {
    public DrawableArea(int x, int y, int width, int height, AEBaseGui<?> parent, Runnable job) {
        super(x, y, width, height, parent, job);
    }

    public abstract void draw();

    public abstract void drawRelative();

    @Override
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
        drawRelative();
    }
}
