package appeng.client.gui.implementations;

import appeng.api.config.BlockingMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PatternProviderBlockingType;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.Icon;
import appeng.client.gui.style.GuiStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.container.SlotSemantics;
import appeng.container.implementations.ContainerPatternProvider;
import appeng.core.localization.GuiText;
import appeng.core.localization.ButtonToolTips;
import appeng.core.network.InitNetwork;
import appeng.core.network.bidirectional.ConfigValuePacket;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import appeng.util.EnumCycler;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class GuiPatternProvider extends AEBaseGui<ContainerPatternProvider> {

    private final SettingToggleButton<BlockingMode> blockingModeButton;
    private final SettingToggleButton<LockCraftingMode> lockCraftingModeButton;
    private final ToggleButton blockingTypeButton;
    private final ToggleButton showInPatternAccessTerminalButton;
    private final PatternProviderLockReason lockReason;

    public GuiPatternProvider(ContainerPatternProvider container, InventoryPlayer playerInventory, ITextComponent unusedTitle,
                              GuiStyle style) {
        super(container, playerInventory, style);
        if (unusedTitle != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, unusedTitle);
        }

        this.blockingModeButton = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.BLOCKING_MODE,
            BlockingMode.NO));
        this.blockingTypeButton = new ToggleButton(Icon.BLOCKING_MODE_TYPE_SMART, Icon.BLOCKING_MODE_TYPE_NORMAL,
            ignored -> selectBlockingType());
        this.blockingTypeButton.setTooltipOff(List.of(
            ButtonToolTips.PatternProviderBlockingType.text(),
            ButtonToolTips.NormalBlockingDescription.text()));
        this.blockingTypeButton.setTooltipOn(List.of(
            ButtonToolTips.PatternProviderBlockingType.text(),
            ButtonToolTips.SmartBlockingDescription.text()));
        addToLeftToolbar(this.blockingTypeButton);
        this.lockCraftingModeButton = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE));
        this.widgets.addOpenPriorityButton();
        this.widgets.add("upgrades", UpgradesPanel.create(
            this.widgets,
            container.getSlots(SlotSemantics.UPGRADE),
            this::getCompatibleUpgrades));
        this.showInPatternAccessTerminalButton = new ToggleButton(Icon.PATTERN_ACCESS_SHOW,
            Icon.PATTERN_ACCESS_HIDE,
            GuiText.PatternAccessTerminal.text(),
            GuiText.PatternAccessTerminalHint.text(),
            this::selectPatternProviderMode);
        addToLeftToolbar(this.showInPatternAccessTerminalButton);
        this.lockReason = new PatternProviderLockReason(this);
        this.widgets.add("lockReason", this.lockReason);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.lockReason.setVisible(this.container.getLockCraftingMode() != LockCraftingMode.NONE);
        this.blockingModeButton.set(this.container.getBlockingMode());
        this.blockingTypeButton.setVisibility(this.container.getBlockingMode() != BlockingMode.NO);
        this.blockingTypeButton.setState(this.container.getBlockingType() == PatternProviderBlockingType.SMART);
        this.lockCraftingModeButton.set(this.container.getLockCraftingMode());
        this.showInPatternAccessTerminalButton.setState(this.container.getShowInAccessTerminal() == YesNo.YES);
    }

    private void selectBlockingType() {
        var nextValue = EnumCycler.rotateEnum(
            this.container.getBlockingType(),
            isHandlingRightClick(),
            Settings.PATTERN_PROVIDER_BLOCKING_TYPE.getValues());
        InitNetwork.CHANNEL.sendToServer(new ConfigValuePacket(Settings.PATTERN_PROVIDER_BLOCKING_TYPE, nextValue));
    }

    private void selectPatternProviderMode(boolean ignored) {
        InitNetwork.CHANNEL.sendToServer(new ConfigValuePacket(
            Settings.PATTERN_ACCESS_TERMINAL,
            EnumCycler.rotateEnum(
                this.container.getShowInAccessTerminal(),
                isHandlingRightClick(),
                Settings.PATTERN_ACCESS_TERMINAL.getValues())));
    }

    private List<ITextComponent> getCompatibleUpgrades() {
        var list = new ObjectArrayList<ITextComponent>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(appeng.api.upgrades.Upgrades.getTooltipLinesForInventory(this.container.getLogic().getUpgrades()));
        return list;
    }
}
