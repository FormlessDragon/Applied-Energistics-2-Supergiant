package appeng.client.ctl.gui.widget.event;

import appeng.client.ctl.gui.widget.base.DynamicWidget;
import appeng.client.ctl.gui.widget.base.WidgetGui;

public abstract class WidgetEvent extends GuiEvent {
    protected final DynamicWidget sender;

    public WidgetEvent(final WidgetGui gui, final DynamicWidget sender) {
        super(gui);
        this.sender = sender;
    }

    public DynamicWidget getSender() {
        return sender;
    }
}