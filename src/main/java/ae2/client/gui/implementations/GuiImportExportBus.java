package ae2.client.gui.implementations;

import ae2.api.config.RedstoneMode;
import ae2.api.config.SchedulingMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerIOBus;
import ae2.core.definitions.AEItems;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiImportExportBus extends GuiUpgradeable<ContainerIOBus> {
    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final SettingToggleButton<YesNo> craftMode;
    private final SettingToggleButton<SchedulingMode> schedulingMode;

    public GuiImportExportBus(ContainerIOBus container, InventoryPlayer playerInventory, ITextComponent title,
                              GuiStyle style) {
        super(container, playerInventory, title, style);

        this.redstoneMode = new ServerSettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);

        if (container.getHost().getConfigManager().hasSetting(Settings.CRAFT_ONLY)) {
            this.craftMode = new ServerSettingToggleButton<>(Settings.CRAFT_ONLY, YesNo.NO);
        } else {
            this.craftMode = null;
        }

        if (container.getHost().getConfigManager().hasSetting(Settings.SCHEDULING_MODE)) {
            this.schedulingMode = addToLeftToolbar(
                new ServerSettingToggleButton<>(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT));
        } else {
            this.schedulingMode = null;
        }
        addToLeftToolbar(this.redstoneMode);
        if (this.craftMode != null) {
            addToLeftToolbar(this.craftMode);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.redstoneMode.set(container.getRedStoneMode());
        this.redstoneMode.setVisibility(container.hasUpgrade(AEItems.REDSTONE_CARD.item()));

        if (this.craftMode != null) {
            this.craftMode.set(container.getCraftingMode());
            this.craftMode.setVisibility(container.hasUpgrade(AEItems.CRAFTING_CARD.item()));
        }

        if (this.schedulingMode != null) {
            this.schedulingMode.set(container.getSchedulingMode());
        }
    }
}
