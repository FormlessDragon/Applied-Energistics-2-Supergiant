package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import ae2.core.localization.GuiText;

public class PortableCellPickupFilterButton extends IconButton {
    public PortableCellPickupFilterButton(Runnable onPress) {
        super(onPress);
        setMessage(GuiText.PortableCellPickupFilterConfigure.text());
    }

    @Override
    protected Icon getIcon() {
        return Icon.FILTER_ON_EXTRACT_ENABLED;
    }
}
