package ae2.client.gui.implementations;

import ae2.api.config.CondenserOutput;
import ae2.api.config.Settings;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.PortableCellPickupFilterButton;
import ae2.client.gui.widgets.ProgressBar;
import ae2.client.gui.widgets.ProgressBar.Direction;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerPortableVoidCell;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchGuisPacket;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiPortableVoidCell extends AEBaseGui<ContainerPortableVoidCell> {

    private final SettingToggleButton<CondenserOutput> mode;

    public GuiPortableVoidCell(ContainerPortableVoidCell container, InventoryPlayer playerInventory,
                               ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        setTextContent(TEXT_ID_DIALOG_TITLE, title != null ? title : GuiText.PortableVoidCell.text());

        this.mode = new SettingToggleButton<>(Settings.CONDENSER_OUTPUT, this.container.getOutput(), ignored -> true,
            this::toggleMode, this::selectMode);

        this.widgets.add("mode", this.mode);
        this.widgets.add("progressBar", new ProgressBar(this.container, style.getImage("progressBar"),
            Direction.VERTICAL, GuiText.StoredEnergy.text()));
        addToLeftToolbar(new PortableCellPickupFilterButton(this::showPortableCellPickupFilter));
    }

    private void showPortableCellPickupFilter() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.PORTABLE_CELL_PICKUP_FILTER));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.mode.set(this.container.getOutput());
    }

    private void toggleMode(SettingToggleButton<CondenserOutput> button, boolean backwards) {
        this.container.setModeFromClient(button.getNextValue(backwards));
    }

    private void selectMode(SettingToggleButton<CondenserOutput> button, CondenserOutput output) {
        button.set(output);
        this.container.setModeFromClient(output);
    }
}
