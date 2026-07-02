package ae2.client.gui.implementations;

import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.container.implementations.ContainerODStorageBus;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiODStorageBus extends GuiSpecialStorageBus<ContainerODStorageBus> {
    private final AETextField white;
    private final AETextField black;

    public GuiODStorageBus(ContainerODStorageBus container, InventoryPlayer playerInventory, ITextComponent title,
                           GuiStyle style) {
        super(container, playerInventory, title, style);
        this.white = widgets.addTextField("whiteExpression");
        this.black = widgets.addTextField("blackExpression");
        this.white.setResponder(container::setWhiteExpression);
        this.black.setResponder(container::setBlackExpression);
        this.white.setMaxStringLength(1024);
        this.black.setMaxStringLength(1024);
        this.white.setText(container.whiteExpression);
        this.black.setText(container.blackExpression);
        this.white.setPlaceholder(GuiText.ODFilterWhiteTooltip.getLocal());
        this.black.setPlaceholder(GuiText.ODFilterBlackTooltip.getLocal());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (!this.white.isFocused() && !this.white.getText().equals(container.whiteExpression)) {
            this.white.setText(container.whiteExpression);
        }
        if (!this.black.isFocused() && !this.black.getText().equals(container.blackExpression)) {
            this.black.setText(container.blackExpression);
        }
    }

}
