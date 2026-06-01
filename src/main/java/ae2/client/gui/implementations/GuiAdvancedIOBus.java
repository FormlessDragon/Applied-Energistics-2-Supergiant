package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerAdvancedIOBus;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiAdvancedIOBus extends GuiStockExportBus<ContainerAdvancedIOBus> {
    private final SettingToggleButton<YesNo> regulateButton;
    private final ContainerAdvancedIOBus advancedContainer;

    public GuiAdvancedIOBus(ContainerAdvancedIOBus container, InventoryPlayer playerInventory, ITextComponent title,
                            GuiStyle style) {
        super(container, playerInventory, title, style);
        this.advancedContainer = container;
        this.regulateButton = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.REGULATE_STOCK, YesNo.YES));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.regulateButton.set(this.advancedContainer.getRegulate());
    }
}
