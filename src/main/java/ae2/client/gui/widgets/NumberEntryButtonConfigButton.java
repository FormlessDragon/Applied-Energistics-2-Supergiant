package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import ae2.core.localization.GuiText;

public class NumberEntryButtonConfigButton extends IconButton {
    public NumberEntryButtonConfigButton(Runnable onPress) {
        super(onPress);
        setMessage(GuiText.NumberEntryButtonSettings.text());
    }

    @Override
    protected Icon getIcon() {
        return Icon.COG;
    }
}
