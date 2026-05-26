package appeng.client.ctl.gui.widget.impl.craftingtree;

import appeng.client.ctl.gui.util.MousePos;
import appeng.client.ctl.gui.util.RenderFunction;
import appeng.client.ctl.gui.util.RenderPos;
import appeng.client.ctl.gui.util.RenderSize;
import appeng.client.ctl.gui.widget.base.DynamicWidget;
import appeng.client.ctl.gui.widget.base.WidgetGui;
import appeng.client.ctl.gui.widget.container.Row;


public class TreeRow extends Row {

    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedWidgetSize = -1;

    public TreeRow() {
        setUseScissor(false);
    }

    @Override
    protected void doRender(final WidgetGui gui, final RenderSize renderSize, final RenderPos renderPos, final MousePos mousePos, final RenderFunction renderFunction) {
        int x = 0;

        int height = getHeight();

        for (int i = 0; i < widgets.size(); i++) {
            final DynamicWidget widget = widgets.get(i);
            if (widget.isDisabled()) {
                continue;
            }
            if (widget instanceof PlaceHolder) {
                x += widget.getMarginLeft() + widget.getWidth() + widget.getMarginRight();
                continue;
            }

            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, height, x);
            if (widgetRenderPos == null) {
                continue;
            }

            if (widget.isVisible()) {
                RenderPos absRenderPos = widgetRenderPos.add(renderPos);
                int totalWidth = widget.getWidth() + widget.getMarginRight() + getPlaceHolderWidth(i + 1);
                boolean insideLeftEdge = absRenderPos.posX() + totalWidth > 0;
                boolean insideRightEdge = !renderSize.isWidthLimited()
                    || absRenderPos.posX() < renderSize.width() + Math.min(renderPos.posX(), 0);
                if (insideLeftEdge && insideRightEdge) {
                    renderFunction.doRender(widget, gui, new RenderSize(widget.getWidth(), widget.getHeight()).smaller(renderSize), absRenderPos, mousePos.relativeTo(absRenderPos));
                }
            }

            x += widget.getMarginLeft() + widget.getWidth() + widget.getMarginRight();
            if (renderSize.isWidthLimited() && x > renderSize.width()) {
                break;
            }
        }
    }

    public int getPlaceHolderWidth(final int startIdx) {
        int width = 0;
        for (int i = startIdx; i < widgets.size(); i++) {
            DynamicWidget widget = widgets.get(i);
            if (widget instanceof PlaceHolder) {
                width += widget.getMarginLeft() + widget.getWidth() + widget.getMarginRight();
            } else {
                break;
            }
        }
        return width;
    }

    public RenderPos getRelativeRenderPos(final TreeNode node) {
        if (!widgets.contains(node)) {
            return null;
        }

        int x = 0;
        int height = getHeight();

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }

            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, height, x);
            if (widgetRenderPos == null) {
                continue;
            }
            if (widget == node) {
                return widgetRenderPos;
            }

            x += widget.getMarginLeft() + widget.getWidth() + widget.getMarginRight();
        }

        return null;
    }

    @Override
    public TreeRow addWidget(final DynamicWidget widget) {
        super.addWidget(widget);
        if (cachedWidgetSize + 1 == widgets.size()) {
            cachedWidgetSize++;
            cachedWidth += widget.getMarginLeft() + widget.getWidth() + widget.getMarginRight();

            int widgetHeight = widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
            if (widget.isUseAbsPos()) {
                widgetHeight += widget.getAbsY();
            }
            if (widgetHeight > cachedHeight) {
                cachedHeight = widgetHeight;
            }
        } else {
            cachedWidgetSize = widgets.size();
            cachedWidth = super.getWidth();
            cachedHeight = super.getHeight();
        }
        return this;
    }

    @Override
    public int getWidth() {
        if (cachedWidgetSize == widgets.size()) {
            return cachedWidth;
        }
        cachedWidgetSize = widgets.size();
        cachedHeight = super.getHeight();
        cachedWidth = super.getWidth();
        return cachedWidth;
    }

    @Override
    public int getHeight() {
        if (cachedWidgetSize == widgets.size()) {
            return cachedHeight;
        }
        cachedWidgetSize = widgets.size();
        cachedWidth = super.getWidth();
        cachedHeight = super.getHeight();
        return cachedHeight;
    }

}
