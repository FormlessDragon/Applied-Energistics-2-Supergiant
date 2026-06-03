package ae2.client.gui.elements;

import ae2.client.gui.AEBaseGui;

public abstract class DrawableArea extends ClickableArea {
    public DrawableArea(int x, int y, int width, int height, AEBaseGui<?> parent, Runnable job) {
        super(x, y, width, height, parent, job);
    }

    public abstract void draw();
}
