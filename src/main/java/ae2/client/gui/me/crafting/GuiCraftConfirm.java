/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.me.crafting;

import ae2.api.config.CraftingPlanSortMode;
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.TerminalStyle;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.StackWithBounds;
import ae2.client.gui.me.search.AEKeySearch;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.TabButton;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.me.crafting.CraftingPlanSummary;
import ae2.container.me.crafting.CraftingPlanSummaryEntry;
import ae2.core.AEConfig;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchCraftingTreePacket;
import ae2.integration.Integrations;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * This screen shows the computed crafting plan and allows the player to select a CPU on which it should be scheduled
 * for crafting.
 */
public class GuiCraftConfirm extends AEBaseGui<ContainerCraftConfirm> {
    private static final String TEXTURE = "guis/craftingreport.png";
    private static final int FIXED_HEADER_HEIGHT = CraftingScreenLayout.TABLE_TOP;
    private static final int FIXED_FOOTER_HEIGHT = 72;
    private static final int ROW_SOURCE_Y = 42;
    private static final int BOTTOM_SOURCE_Y = 134;
    private static final int RIGHT_FOOTER_HEIGHT = 0;

    private final CraftConfirmTableRenderer table;
    private final AE2Button start;
    private final AE2Button bookmarkMissing;
    private final AE2Button selectCPU;
    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final SettingToggleButton<TerminalStyle> terminalStyleButton;
    private final SettingToggleButton<CraftingPlanSortMode> sortModeButton;
    private final SettingToggleButton<SortDir> sortDirectionButton;
    private final AEKeySearch search = new AEKeySearch();
    private final List<CraftingPlanSummaryEntry> visibleEntries = new ObjectArrayList<>();
    private String searchText = "";
    private CraftingPlanSortMode lastSortMode = AEConfig.instance().getCraftingPlanSortMode();
    private SortDir lastSortDirection = AEConfig.instance().getCraftingPlanSortDirection();
    @Nullable
    private CraftingPlanSummary filteredPlan;

    public GuiCraftConfirm(ContainerCraftConfirm container, InventoryPlayer playerInventory, ITextComponent title,
                           GuiStyle style) {
        super(container, playerInventory, style);
        this.table = new CraftConfirmTableRenderer(this, 9, 19);

        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.searchField = widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(Arrays.asList(
            GuiText.SearchTooltip.text(),
            GuiText.SearchTooltipModId.text(),
            GuiText.SearchTooltipTag.text(),
            GuiText.SearchTooltipToolTips.text(),
            GuiText.SearchTooltipItemId.text()));

        this.start = widgets.addButton("start", GuiText.Start.text(), this::start);
        this.start.enabled = false;

        this.bookmarkMissing = widgets.addButton("bookmarkMissing", GuiText.BookmarkMissing.text(), this::bookmarkMissing);
        this.bookmarkMissing.enabled = false;
        this.bookmarkMissing.visible = false;

        this.selectCPU = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        this.selectCPU.enabled = false;

        widgets.addButton("cancel", GuiText.Cancel.text(), container::goBack);

        TabButton craftTree = new TabButton(Icon.CTL_CRAFT_TREE, GuiText.CraftTree.text(), this::showCraftingTree);
        widgets.add("craftTree", craftTree);
        this.terminalStyleButton = new SettingToggleButton<>(
            Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle);
        addToLeftToolbar(this.terminalStyleButton);
        this.sortModeButton = new SettingToggleButton<>(
            Settings.CRAFTING_PLAN_SORT_MODE, AEConfig.instance().getCraftingPlanSortMode(),
            this::toggleCraftingPlanSortMode);
        addToLeftToolbar(this.sortModeButton);
        this.sortDirectionButton = new SettingToggleButton<>(
            Settings.CRAFTING_PLAN_SORT_DIRECTION, AEConfig.instance().getCraftingPlanSortDirection(),
            this::toggleCraftingPlanSortDirection);
        addToLeftToolbar(this.sortDirectionButton);

        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
    }

    private static long getUsedResourceCount(CraftingPlanSummaryEntry entry) {
        return entry.inventoryUsageAmount();
    }

    private ITextComponent getNextCpuButtonLabel() {
        if (this.container.hasNoCPU()) {
            return GuiText.NoCraftingCPUs.text();
        }

        ITextComponent cpuName;
        if (this.container.cpuName == null) {
            cpuName = GuiText.Automatic.text();
        } else {
            cpuName = this.container.cpuName;
        }

        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    private static double getUsedResourcePercent(CraftingPlanSummaryEntry entry) {
        long used = getUsedResourceCount(entry);
        if (entry.inventoryAmount() <= 0) {
            return used > 0 ? Double.MAX_VALUE : 0.0D;
        }
        return used / (double) entry.inventoryAmount();
    }

    private static int getAvailabilitySortGroup(CraftingPlanSummaryEntry entry) {
        if (entry.missingAmount() > 0) {
            return 0;
        }
        if (entry.craftAmount() > 0 || entry.intermediateCraftAmount() > 0) {
            return 1;
        }
        return 2;
    }

    @Override
    public void initGui() {
        updateLayout();
        super.initGui();
        updateScrollbar();
    }

    @Nullable
    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = table.getHoveredStack();
        if (hovered != null) {
            return hovered;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        CraftingPlanSummary plan = container.getPlan();
        if (plan != null) {
            this.table.render(mouseX - offsetX, mouseY - offsetY, getVisibleEntries(plan),
                scrollbar.getCurrentScroll());
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var errorResult = container.submitError.result();
        if (errorResult != null && errorResult.errorCode() != null) {
            switchToScreen(new GuiCraftError(this, errorResult.errorCode(), errorResult.errorDetail()));
            return;
        }

        this.selectCPU.setMessage(getNextCpuButtonLabel());

        CraftingPlanSummary plan = container.getPlan();
        boolean planIsStartable = plan != null && !plan.simulation();
        boolean hasMissingEntries = plan != null && plan.hasMissingEntries();
        boolean shiftDown = isShiftKeyDown();
        boolean ctrlDown = isCtrlKeyDown();
        var startButtonState = CraftConfirmStartButtonState.compute(this.container.hasNoCPU(), planIsStartable,
            hasMissingEntries, shiftDown, this.container.canMerge(), ctrlDown);
        this.start.setMessage(startButtonState.label().text());
        this.start.enabled = startButtonState.clickable();
        this.start.setForceHighlighted(startButtonState.highlighted());
        this.selectCPU.enabled = planIsStartable;
        boolean canBookmarkMissing = Integrations.hei().isEnabled() && hasMissingEntries;
        this.bookmarkMissing.visible = canBookmarkMissing;
        this.bookmarkMissing.enabled = canBookmarkMissing;

        ITextComponent planDetails = GuiText.CalculatingWait.text();
        ITextComponent cpuDetails = new TextComponentString("");
        if (plan != null) {
            String byteUsed = NumberFormat.getInstance().format(plan.usedBytes());
            planDetails = GuiText.BytesUsed.text(byteUsed);

            if (plan.simulation()) {
                cpuDetails = GuiText.PartialPlan.text();
            } else if (this.container.getCpuAvailableBytes() > 0) {
                cpuDetails = GuiText.ConfirmCraftCpuStatus.text(
                    this.container.getCpuAvailableBytes(),
                    this.container.getCpuCoProcessors());
            } else {
                cpuDetails = GuiText.ConfirmCraftNoCpu.text();
            }
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.CraftingPlan.text(planDetails));
        setTextContent("cpu_status", cpuDetails);

        this.terminalStyleButton.set(AEConfig.instance().getTerminalStyle());
        CraftingPlanSortMode sortMode = AEConfig.instance().getCraftingPlanSortMode();
        SortDir sortDirection = AEConfig.instance().getCraftingPlanSortDirection();
        if (sortMode != this.lastSortMode || sortDirection != this.lastSortDirection) {
            this.lastSortMode = sortMode;
            this.lastSortDirection = sortDirection;
            invalidateSortedPlan();
        }
        this.sortModeButton.set(sortMode);
        this.sortDirectionButton.set(sortDirection);
        updateScrollbar();
    }

    private void selectNextCpu() {
        getContainer().cycleSelectedCPU(!isHandlingRightClick());
    }

    private void start() {
        getContainer().startJob(isShiftKeyDown(), isCtrlKeyDown());
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);

        List<ITextComponent> hoveredTooltip = this.table.getHoveredTooltip();
        if (hoveredTooltip != null) {
            drawTooltipWithHeader(mouseX, mouseY, hoveredTooltip);
        }

        CraftingPlanSummary plan = container.getPlan();
        if (plan != null && this.start.visible
            && mouseX >= this.start.x && mouseY >= this.start.y
            && mouseX < this.start.x + this.start.width && mouseY < this.start.y + this.start.height) {
            var tooltip = new ObjectArrayList<ITextComponent>();
            if (plan.hasMissingEntries()) {
                tooltip.add(GuiText.ForceStartHoldShift.text());
            }
            if (this.container.canMerge()) {
                tooltip.add(GuiText.StartWithoutMergingHoldCtrl.text());
            }
            if (!tooltip.isEmpty()) {
                drawTooltipWithHeader(mouseX, mouseY, tooltip);
            }
        }

    }

    private void bookmarkMissing() {
        CraftingPlanSummary plan = container.getPlan();
        if (plan == null) {
            return;
        }

        List<GenericStack> missingStacks = new ObjectArrayList<>();
        for (CraftingPlanSummaryEntry entry : plan.entries()) {
            if (entry.missingAmount() > 0) {
                missingStacks.add(new GenericStack(entry.what(), entry.missingAmount()));
            }
        }

        Integrations.hei().addBookmarkGroup(missingStacks);
    }

    private void showCraftingTree() {
        InitNetwork.sendToServer(new SwitchCraftingTreePacket(this.container.windowId));
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        CraftingScreenBackground.draw(TEXTURE, offsetX, offsetY, this.table.getRows(), FIXED_HEADER_HEIGHT,
            ROW_SOURCE_Y, BOTTOM_SOURCE_Y, FIXED_FOOTER_HEIGHT, RIGHT_FOOTER_HEIGHT);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)
            && !this.searchField.isFocused()) {
            this.start();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY) && mouseButton == 1) {
            this.searchField.setText("");
            updateSearch();
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void toggleTerminalStyle(SettingToggleButton button, boolean backwards) {
        var nextValue = (TerminalStyle) button.getNextValue(backwards);
        button.set(nextValue);
        AEConfig.instance().setTerminalStyle(nextValue);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void toggleCraftingPlanSortMode(SettingToggleButton button, boolean backwards) {
        var nextValue = (CraftingPlanSortMode) button.getNextValue(backwards);
        button.set(nextValue);
        AEConfig.instance().setCraftingPlanSortMode(nextValue);
        this.lastSortMode = nextValue;
        invalidateSortedPlan();
        updateScrollbar();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void toggleCraftingPlanSortDirection(SettingToggleButton button, boolean backwards) {
        var nextValue = (SortDir) button.getNextValue(backwards);
        button.set(nextValue);
        AEConfig.instance().setCraftingPlanSortDirection(nextValue);
        this.lastSortDirection = nextValue;
        invalidateSortedPlan();
        updateScrollbar();
    }

    private void updateLayout() {
        int rows = CraftingScreenLayout.getRows(this.height, FIXED_HEADER_HEIGHT, FIXED_FOOTER_HEIGHT);
        this.table.setRows(rows);
        this.xSize = CraftingScreenLayout.WIDTH;
        this.ySize = CraftingScreenLayout.getHeight(rows, FIXED_HEADER_HEIGHT, FIXED_FOOTER_HEIGHT);
        invalidateExclusionZonesCache();
    }

    private void updateScrollbar() {
        CraftingPlanSummary plan = container.getPlan();
        int size = plan != null ? getVisibleEntries(plan).size() : 0;
        this.scrollbar.setHeight(CraftingScreenLayout.getScrollbarHeight(this.table.getRows()));
        this.scrollbar.setRange(0, this.table.getScrollableRows(size), 1);
    }

    private List<CraftingPlanSummaryEntry> getVisibleEntries(CraftingPlanSummary plan) {
        updateSearch();
        if (this.filteredPlan == plan) {
            return this.visibleEntries;
        }

        this.filteredPlan = plan;
        this.visibleEntries.clear();
        for (CraftingPlanSummaryEntry entry : plan.entries()) {
            if (this.search.matches(entry.what())) {
                this.visibleEntries.add(entry);
            }
        }
        sortVisibleEntries();
        return this.visibleEntries;
    }

    private void sortVisibleEntries() {
        CraftingPlanSummaryEntry finalOutput = null;
        for (CraftingPlanSummaryEntry entry : this.visibleEntries) {
            if (entry.finalOutput()) {
                finalOutput = entry;
                break;
            }
        }

        if (finalOutput != null) {
            this.visibleEntries.remove(finalOutput);
        }

        this.visibleEntries.sort(getSortComparator());

        if (finalOutput != null) {
            this.visibleEntries.addFirst(finalOutput);
        }
    }

    private Comparator<CraftingPlanSummaryEntry> getSortComparator() {
        Comparator<CraftingPlanSummaryEntry> comparator = switch (AEConfig.instance().getCraftingPlanSortMode()) {
            case USED_COUNT -> Comparator.comparingLong(GuiCraftConfirm::getUsedResourceCount);
            case USED_PERCENT -> Comparator.comparingDouble(GuiCraftConfirm::getUsedResourcePercent);
            case AVAILABILITY -> Comparator.comparingInt(GuiCraftConfirm::getAvailabilitySortGroup);
        };

        if (AEConfig.instance().getCraftingPlanSortDirection() == SortDir.DESCENDING) {
            comparator = comparator.reversed();
        }

        return comparator.thenComparing(Comparator.naturalOrder());
    }

    private void updateSearch() {
        String text = this.searchField.getText();
        if (!this.searchText.equals(text)) {
            this.searchText = text;
            this.search.setSearchString(text);
            invalidateSortedPlan();
        }
    }

    private void invalidateSortedPlan() {
        this.filteredPlan = null;
    }
}
