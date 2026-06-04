package ae2.client.gui.color;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;

import java.awt.Rectangle;

public class ClickableArea implements ICompositeWidget {
    public int x;
    public int y;
    public int w;
    public int h;
    protected final AEBaseGui<?> screen;
    protected final Runnable job;

    public ClickableArea(int x, int y, int width, int height, AEBaseGui<?> parent, Runnable job) {
        this.x = x;
        this.y = y;
        this.w = width;
        this.h = height;
        this.screen = parent;
        this.job = job;
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.x();
        this.y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
        this.w = width;
        this.h = height;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this.x, this.y, this.w, this.h);
    }

    public boolean isMouseOverRelative(double x, double y) {
        return x >= this.x && x < this.x + this.w
            && y >= this.y && y < this.y + this.h;
    }

    public boolean isMouseOver(double x, double y) {
        return x >= this.x + this.screen.getGuiLeft() && x < this.x + this.screen.getGuiLeft() + this.w
            && y >= this.y + this.screen.getGuiTop() && y < this.y + this.screen.getGuiTop() + this.h;
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        return clickRelative(mousePos.x(), mousePos.y());
    }

    public boolean click(double x, double y) {
        if (isMouseOver(x, y)) {
            job.run();
            return true;
        }
        return false;
    }

    public boolean clickRelative(double x, double y) {
        if (isMouseOverRelative(x, y)) {
            job.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        releaseRelative(mousePos.x(), mousePos.y());
        return false;
    }

    public void release(double x, double y) {
    }

    public void releaseRelative(double x, double y) {
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
