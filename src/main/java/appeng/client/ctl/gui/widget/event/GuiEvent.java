package appeng.client.ctl.gui.widget.event;

import appeng.client.ctl.gui.widget.base.WidgetGui;

public abstract class GuiEvent {
    protected final WidgetGui gui;

    public GuiEvent(final WidgetGui gui) {
        this.gui = gui;
    }

    public WidgetGui getGui() {
        return gui;
    }
}