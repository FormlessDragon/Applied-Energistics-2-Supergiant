package appeng.client.ctl.gui.util;

import appeng.client.ctl.gui.widget.base.DynamicWidget;
import appeng.client.ctl.gui.widget.base.WidgetGui;

@FunctionalInterface
public interface RenderFunction {

    void doRender(DynamicWidget dynamicWidget, WidgetGui gui, RenderSize renderSize, RenderPos renderPos, MousePos mousePos);

}