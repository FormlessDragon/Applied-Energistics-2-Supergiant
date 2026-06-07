package ae2.client.gui.me.crafting;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.implementations.AESubGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.container.implementations.ContainerSetStockAmount;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiSetStockAmount extends AEBaseGui<ContainerSetStockAmount> {

    private final NumberEntryWidget amount;
    private boolean amountInitialized;

    public GuiSetStockAmount(ContainerSetStockAmount container, InventoryPlayer playerInventory, ITextComponent title,
                             GuiStyle style) {
        super(container, playerInventory, style);

        widgets.addButton("save", GuiText.Set.text(), this::confirm);
        AESubGui.addBackButton(container, "back", widgets);

        this.amount = widgets.addNumberEntryWidget("amountToStock", NumberEntryType.UNITLESS);
        this.amount.setLongValue(1);
        this.amount.setTextFieldStyle(style.getWidget("amountToStockInput"));
        this.amount.setPreviewFieldStyle(style.getWidget("amountToStockPreview"));
        this.amount.setMinValue(0);
        this.amount.setHideValidationIcon(true);
        this.amount.setOnConfirm(this::confirm);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (!this.amountInitialized) {
            var whatToStock = container.getWhatToStock();
            if (whatToStock != null) {
                this.amount.setType(NumberEntryType.of(whatToStock));
                this.amount.setLongValue(container.getInitialAmount());
                this.amount.setMaxValue(container.getMaxAmount());
                this.amountInitialized = true;
            }
        }
    }

    private void confirm() {
        this.amount.getIntValue().ifPresent(container::confirm);
    }
}
