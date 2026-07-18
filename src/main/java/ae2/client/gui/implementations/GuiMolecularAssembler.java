package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.upgrades.Upgrades;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.PageNavigationButton;
import ae2.client.gui.widgets.PatternModifierPanelWidget;
import ae2.client.gui.widgets.ToggleButton;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerMolecularAssembler;
import ae2.container.slot.AppEngSlot;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.ConfigValueServerPacket;
import ae2.helpers.patternmodifier.PatternModifierToolboxLayout;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.util.EnumCycler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class GuiMolecularAssembler extends AEBaseGui<ContainerMolecularAssembler> {
    private final PageNavigationButton previousPageButton;
    private final PageNavigationButton nextPageButton;
    private final ToggleButton showInPatternAccessTerminalButton;
    private final PatternModifierPanelWidget patternModifierPanel;

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
        this.previousPageButton = new PageNavigationButton(Icon.ARROW_LEFT,
            GuiText.PatternProviderPagePrevious.text(), GuiText.PatternProviderPageNext.text(),
            () -> container.setPage(container.getCurrentPage() - 1));
        this.nextPageButton = new PageNavigationButton(Icon.ARROW_RIGHT,
            GuiText.PatternProviderPagePrevious.text(), GuiText.PatternProviderPageNext.text(),
            () -> container.setPage(container.getCurrentPage() + 1));
        this.widgets.add("previousPage", this.previousPageButton);
        this.widgets.add("nextPage", this.nextPageButton);
        this.patternModifierPanel = new PatternModifierPanelWidget(this, new AssemblerPanelHost());
        this.patternModifierPanel.addButtons();
        addToLeftToolbar(this.patternModifierPanel.getToolbarButton());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.repositionPatternPageSlots();
        this.showInPatternAccessTerminalButton.setState(this.container.getShowInAccessTerminal() == YesNo.YES);
        this.previousPageButton.setVisibility(this.container.getPageCount() > 1 && this.container.getCurrentPage() > 0);
        this.nextPageButton.setVisibility(this.container.getPageCount() > 1
            && this.container.getCurrentPage() + 1 < this.container.getPageCount());
        this.patternModifierPanel.update();
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
        var upgradeLines = Upgrades.getTooltipLinesForInventory(this.container.getAssemblerHost().getUpgrades());
        var list = new ObjectArrayList<ITextComponent>(upgradeLines.size() + 1);
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(upgradeLines);
        return list;
    }

    private final class AssemblerPanelHost implements PatternModifierPanelWidget.PanelHost {
        @Override
        public boolean isPatternModifierPanelAvailable() {
            return container.isPatternModifierPanelAvailable();
        }

        @Override
        public Point getPatternModifierPanelOffset() {
            return new Point(
                PatternModifierToolboxLayout.PANEL_LEFT_OFFSET + 6,
                PatternModifierToolboxLayout.PANEL_TOP_OFFSET - 22);
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
