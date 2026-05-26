package appeng.client.ctl.gui.widget.impl.craftingtree;

import appeng.client.ctl.gui.util.MousePos;
import appeng.client.ctl.gui.util.RenderPos;
import appeng.client.ctl.gui.util.RenderSize;
import appeng.client.ctl.gui.widget.base.DynamicWidget;
import appeng.client.ctl.gui.widget.base.WidgetGui;

public class PlaceHolder extends DynamicWidget {

    public PlaceHolder() {
        setVisible(false);
    }

    @Override
    public void render(final WidgetGui gui, final RenderSize renderSize, final RenderPos renderPos, final MousePos mousePos) {
        // Do nothing
    }

}