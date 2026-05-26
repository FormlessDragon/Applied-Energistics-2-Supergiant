package appeng.client.ctl.gui.widget;

import appeng.client.ctl.gui.widget.base.DynamicWidget;
import appeng.client.ctl.gui.widget.container.Column;

public class SizedColumn extends Column {

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public DynamicWidget setHeight(final int height) {
        this.height = height;
        return this;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public DynamicWidget setWidth(final int width) {
        this.width = width;
        return this;
    }

    @Override
    public DynamicWidget setWidthHeight(final int width, final int height) {
        return setWidth(width).setHeight(height);
    }

    public int getTotalWidth() {
        return super.getWidth();
    }

    public int getTotalHeight() {
        return super.getHeight();
    }

}