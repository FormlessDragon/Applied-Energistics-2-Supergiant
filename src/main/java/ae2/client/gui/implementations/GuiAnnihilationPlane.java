package ae2.client.gui.implementations;

import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerAnnihilationPlane;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiAnnihilationPlane extends GuiUpgradeable<ContainerAnnihilationPlane> {
    private final SettingToggleButton<FuzzyMode> fuzzyMode;

    public GuiAnnihilationPlane(ContainerAnnihilationPlane container, InventoryPlayer playerInventory,
                                ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        this.fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));
        widgets.addOpenPriorityButton();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.supportsFuzzyMode());
    }
}
