package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.container.implementations.ContainerPriorityTuner;
import ae2.core.localization.GuiText;
import ae2.items.tools.PriorityTunerItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.OptionalInt;

public class GuiPriorityTuner extends AEBaseGui<ContainerPriorityTuner> {
    private final AE2Button modeButton;
    private final NumberEntryWidget priority;

    public GuiPriorityTuner(ContainerPriorityTuner container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        this.modeButton = widgets.addButton("mode", GuiText.PriorityTunerChangeMode.text(),
            () -> this.container.setMode(this.container.getMode().next()));
        this.priority = widgets.addNumberEntryWidget("priority", NumberEntryType.UNITLESS);
        this.priority.setTextFieldStyle(style.getWidget("priorityInput"));
        this.priority.setMinValue(Integer.MIN_VALUE);
        this.priority.setLongValue(this.container.getPriority());
        this.priority.setOnChange(this::savePriority);
        this.priority.setOnConfirm(this::savePriority);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.modeButton.setMessage(getModeText(this.container.getMode()));
    }

    private void savePriority() {
        OptionalInt priority = this.priority.getIntValue();
        priority.ifPresent(value -> this.container.setPriority(value));
    }

    private static ITextComponent getModeText(PriorityTunerItem.Mode mode) {
        return switch (mode) {
            case INPUT -> GuiText.PriorityTunerModeInput.text();
            case OUTPUT -> GuiText.PriorityTunerModeOutput.text();
            case INCREMENT -> GuiText.PriorityTunerModeIncrement.text();
            case DECREMENT -> GuiText.PriorityTunerModeDecrement.text();
        };
    }
}
