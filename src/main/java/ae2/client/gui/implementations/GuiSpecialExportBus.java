package ae2.client.gui.implementations;

import ae2.api.config.RedstoneMode;
import ae2.api.config.Settings;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.UpgradeableContainer;
import ae2.core.definitions.AEItems;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiSpecialExportBus<T extends UpgradeableContainer<?>> extends GuiUpgradeable<T> {
    private final SettingToggleButton<RedstoneMode> redstoneMode;

    public GuiSpecialExportBus(T container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        this.redstoneMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.redstoneMode.set(container.getRedStoneMode());
        this.redstoneMode.setVisibility(container.hasUpgrade(AEItems.REDSTONE_CARD.item()));
    }
}
