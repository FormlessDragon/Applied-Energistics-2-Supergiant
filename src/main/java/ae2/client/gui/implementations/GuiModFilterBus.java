package ae2.client.gui.implementations;

import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.container.implementations.ContainerModFilterBus;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiModFilterBus extends GuiSpecialExportBus<ContainerModFilterBus<?>> {
    private final AETextField modExpression;

    public GuiModFilterBus(ContainerModFilterBus<?> container, InventoryPlayer playerInventory, ITextComponent title,
                           GuiStyle style) {
        super(container, playerInventory, title, style);
        this.modExpression = widgets.addTextField("modExpression");
        this.modExpression.setResponder(container::setModExpression);
        this.modExpression.setMaxStringLength(512);
        this.modExpression.setText(container.modExpression);
        this.modExpression.setPlaceholder(GuiText.ModFilterTooltip.text());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (!this.modExpression.isFocused() && !this.modExpression.getText().equals(container.modExpression)) {
            this.modExpression.setText(container.modExpression);
        }
    }

}
