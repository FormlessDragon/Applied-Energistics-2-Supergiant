package ae2.client.gui.me.crafting;

import ae2.client.gui.Icon;
import ae2.client.gui.widgets.IconButton;
import net.minecraft.util.text.ITextComponent;

final class CraftingTreeButton extends IconButton {
    private Icon icon;

    CraftingTreeButton(Icon icon, ITextComponent tooltip, Runnable onPress) {
        super(onPress);
        this.icon = icon;
        setMessage(tooltip);
    }

    void setTooltip(ITextComponent tooltip) {
        setMessage(tooltip);
    }

    void setIcon(Icon icon) {
        this.icon = icon;
    }

    @Override
    protected Icon getIcon() {
        return icon;
    }
}
