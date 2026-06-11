package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.upgrades.Upgrades;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.PatternModifierPanelWidget;
import ae2.client.gui.widgets.ProgressBar;
import ae2.client.gui.widgets.ToggleButton;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerMolecularAssembler;
import ae2.container.slot.AppEngSlot;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.ConfigValueServerPacket;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.util.EnumCycler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

public class GuiMolecularAssembler extends AEBaseGui<ContainerMolecularAssembler> {
    private final ProgressBar progressBar;
    private final PageButton previousPageButton;
    private final PageButton nextPageButton;
    private final ToggleButton showInPatternAccessTerminalButton;
    private final PatternModifierPanelWidget patternModifierPanel;
    private int lastProgress = Integer.MIN_VALUE;

    public GuiMolecularAssembler(ContainerMolecularAssembler container, InventoryPlayer playerInventory, ITextComponent title,
                                 GuiStyle style) {
        super(container, playerInventory, style);
        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }

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
        this.previousPageButton = new PageButton(Icon.ARROW_LEFT, () -> container.setPage(container.getCurrentPage() - 1));
        this.nextPageButton = new PageButton(Icon.ARROW_RIGHT, () -> container.setPage(container.getCurrentPage() + 1));
        this.widgets.add("previousPage", this.previousPageButton);
        this.widgets.add("nextPage", this.nextPageButton);
        this.progressBar = new ProgressBar(this.container, style.getImage("progressBar"), ProgressBar.Direction.HORIZONTAL);
        this.widgets.add("progressBar", this.progressBar);
        this.patternModifierPanel = new PatternModifierPanelWidget(this, new AssemblerPanelHost());
        this.patternModifierPanel.addButtons();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.repositionPatternPageSlots();
        updateProgressTooltip(this.container.getCurrentProgress());
        this.showInPatternAccessTerminalButton.setState(this.container.getShowInAccessTerminal() == YesNo.YES);
        this.previousPageButton.setVisibility(this.container.getPageCount() > 1 && this.container.getCurrentPage() > 0);
        this.nextPageButton.setVisibility(this.container.getPageCount() > 1
            && this.container.getCurrentPage() + 1 < this.container.getPageCount());
        this.patternModifierPanel.update();
    }

    private void updateProgressTooltip(int progress) {
        if (progress != this.lastProgress) {
            this.lastProgress = progress;
            this.progressBar.setFullMsg(new TextComponentString(progress + "%"));
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY, partialTicks);
        this.patternModifierPanel.drawBackground();
    }

    private void repositionPatternPageSlots() {
        int firstSlotOnPage = PatternProviderCapacity.getFirstSlotOnPage(this.container.getCurrentPage());
        for (Slot slot : this.container.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            if (!slot.isEnabled() || !(slot instanceof AppEngSlot appEngSlot)) {
                continue;
            }

            int pageIndex = appEngSlot.getSlotIndex() - firstSlotOnPage;
            if (pageIndex < 0 || pageIndex >= PatternProviderCapacity.GUI_PATTERN_SLOTS_PER_PAGE) {
                continue;
            }

            slot.xPos = 8 + pageIndex % 9 * 18;
            slot.yPos = 42 + pageIndex / 9 * 18;
        }
    }

    private void selectPatternProviderMode(boolean ignored) {
        InitNetwork.sendToServer(new ConfigValueServerPacket(
            this.container.windowId,
            Settings.PATTERN_ACCESS_TERMINAL,
            EnumCycler.rotateEnum(
                this.container.getShowInAccessTerminal(),
                isHandlingRightClick(),
                Settings.PATTERN_ACCESS_TERMINAL.getValues())));
    }

    private List<ITextComponent> getCompatibleUpgrades() {
        var list = new ObjectArrayList<ITextComponent>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForInventory(this.container.getHost().getUpgrades()));
        return list;
    }

    private static class PageButton extends IconButton {
        private final Icon icon;

        PageButton(Icon icon, Runnable onPress) {
            super(onPress);
            this.icon = icon;
            this.setMessage((icon == Icon.ARROW_LEFT
                ? GuiText.PatternProviderPagePrevious
                : GuiText.PatternProviderPageNext).text());
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }

    private final class AssemblerPanelHost implements PatternModifierPanelWidget.PanelHost {
        @Override
        public boolean isPatternModifierPanelAvailable() {
            return container.isPatternModifierPanelAvailable();
        }

        @Override
        public void updatePatternModifierPanelVisibleSlots(boolean visible) {
            container.updatePatternModifierPanelVisibleSlots(visible);
        }

        @Override
        public void clearPatternModifierPanel() {
            container.getPatternModifierPanel().clearPatterns();
        }

        @Override
        public void modifyPatternModifierPanelAmounts(int factor, boolean divide) {
            container.getPatternModifierPanel().modifyAmounts(factor, divide);
        }
    }
}
