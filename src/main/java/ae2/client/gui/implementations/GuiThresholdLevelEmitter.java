package ae2.client.gui.implementations;

import ae2.api.config.FuzzyMode;
import ae2.api.config.RedstoneMode;
import ae2.api.config.Settings;
import ae2.client.gui.NumberEntryType;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerThresholdLevelEmitter;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiThresholdLevelEmitter extends GuiUpgradeable<ContainerThresholdLevelEmitter> {
    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final NumberEntryWidget upperLevel;
    private final NumberEntryWidget lowerLevel;

    public GuiThresholdLevelEmitter(ContainerThresholdLevelEmitter container, InventoryPlayer playerInventory,
                                    ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        this.redstoneMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL));
        this.fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));
        this.upperLevel = widgets.addNumberEntryWidget("upperLevel", NumberEntryType.of(container.getConfiguredFilter()));
        this.upperLevel.setShowIncrementButtons(false);
        this.upperLevel.setTextFieldStyle(style.getWidget("upperLevelInput"));
        this.upperLevel.setLongValue(this.container.getUpperValue());
        this.upperLevel.setOnChange(this::saveUpperValue);
        this.upperLevel.setOnConfirm(this::saveValues);
        this.upperLevel.setFocused(false);
        this.lowerLevel = widgets.addNumberEntryWidget("lowerLevel", NumberEntryType.of(container.getConfiguredFilter()));
        this.lowerLevel.setShowIncrementButtons(false);
        this.lowerLevel.setTextFieldStyle(style.getWidget("lowerLevelInput"));
        this.lowerLevel.setLongValue(this.container.getLowerValue());
        this.lowerLevel.setOnChange(this::saveLowerValue);
        this.lowerLevel.setOnConfirm(this::saveValues);
        this.lowerLevel.setFocused(false);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.upperLevel.setType(NumberEntryType.of(container.getConfiguredFilter()));
        this.lowerLevel.setType(NumberEntryType.of(container.getConfiguredFilter()));
        syncValuesFromContainer();
        this.redstoneMode.set(container.getRedStoneMode());
        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.supportsFuzzySearch());
    }

    private void syncValuesFromContainer() {
        syncValueFromContainer(this.upperLevel, this.container.getUpperValue());
        syncValueFromContainer(this.lowerLevel, this.container.getLowerValue());
    }

    private static void syncValueFromContainer(NumberEntryWidget field, long value) {
        var currentValue = field.getLongValue();
        if (!field.isFocused() && (currentValue.isEmpty() || currentValue.getAsLong() != value)) {
            field.setLongValue(value);
        }
    }

    private void saveValues() {
        saveUpperValue();
        saveLowerValue();
    }

    private void saveUpperValue() {
        this.upperLevel.getLongValue().ifPresent(container::setUpperValue);
    }

    private void saveLowerValue() {
        this.lowerLevel.getLongValue().ifPresent(container::setLowerValue);
    }
}
