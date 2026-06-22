package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.container.implementations.ContainerWorkInterval;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;

public class GuiWorkInterval extends AEBaseGui<ContainerWorkInterval> {

    private static final String STYLE_PATH = "/screens/work_interval.json";

    private final NumberEntryWidget workInterval;

    public GuiWorkInterval(ContainerWorkInterval container, InventoryPlayer playerInventory, ITextComponent title) {
        this(container, playerInventory, title, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiWorkInterval(ContainerWorkInterval container, InventoryPlayer playerInventory, ITextComponent title,
                            GuiStyle style) {
        super(container, playerInventory, style);
        var background = style.getBackground();
        if (background != null) {
            Rectangle bounds = background.getDestRect();
            this.xSize = bounds.width;
            this.ySize = bounds.height;
        } else {
            this.xSize = 176;
            this.ySize = 125;
        }

        AESubGui.addBackButton(container, "back", widgets);

        this.workInterval = widgets.addNumberEntryWidget("workInterval", NumberEntryType.UNITLESS);
        this.workInterval.setTextFieldStyle(style.getWidget("workIntervalInput"));
        this.workInterval.setMinValue(1);
        this.workInterval.setLongValue(this.container.getWorkInterval());
        this.workInterval.setOnChange(this::saveWorkInterval);
        this.workInterval.setOnConfirm(() -> {
            saveWorkInterval();
            AESubGui.goBack();
        });
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var currentValue = this.workInterval.getLongValue();
        long containerValue = this.container.getWorkInterval();
        if (!this.workInterval.isFocused()
            && (currentValue.isEmpty() || currentValue.getAsLong() != containerValue)) {
            this.workInterval.setLongValue(containerValue);
        }
    }

    private void saveWorkInterval() {
        this.workInterval.getLongValue().ifPresent(this.container::setWorkInterval);
    }
}
