package ae2.client.gui.implementations;

import ae2.api.config.ActionItems;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.container.implementations.ContainerModStorageBus;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public class GuiModStorageBus extends GuiSpecialStorageBus<ContainerModStorageBus> {
    private final AETextField modExpression;

    public GuiModStorageBus(ContainerModStorageBus container, InventoryPlayer playerInventory, ITextComponent title,
                            GuiStyle style) {
        super(container, playerInventory, title, style);
        addToLeftToolbar(new ActionButton(ActionItems.COG, container::partition));
        this.modExpression = widgets.addTextField("modExpression");
        this.modExpression.setResponder(container::setModExpression);
        this.modExpression.setMaxStringLength(512);
        this.modExpression.setText(container.modExpression);
        this.modExpression.setPlaceholder(new TextComponentTranslation("gui.ae2.ModFilterTooltip"));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (!this.modExpression.isFocused() && !this.modExpression.getText().equals(container.modExpression)) {
            this.modExpression.setText(container.modExpression);
        }
    }

}
