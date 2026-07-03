/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.client.gui.me.common;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.client.AEKeyRendering;
import ae2.api.config.ActionItems;
import ae2.api.config.PinDisplayMode;
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.AEKeyFilter;
import ae2.api.storage.ILinkStatus;
import ae2.api.util.IConfigManager;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.me.items.WirelessUniversalTerminalSelectorWindow;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.TerminalStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ISortSource;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.ItemStackButton;
import ae2.client.gui.widgets.PortableCellPickupFilterButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.TabButton;
import ae2.client.gui.widgets.ToolboxPanel;
import ae2.client.gui.widgets.ToggleButton;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.client.gui.widgets.ViewCellsPanel;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.common.GridInventoryEntry;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.ConfigValueServerPacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.integration.Integrations;
import ae2.integration.abstraction.ItemListMod;
import ae2.integration.modules.hei.target.HeiGhostTargetSupport;
import ae2.integration.modules.hei.target.ManualPinTarget;
import ae2.items.contents.PortableCellGuiHost;
import ae2.items.storage.ViewCellItem;
import ae2.items.tools.powered.PortableCellItem;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.text.TextComponentItemStack;
import ae2.util.Platform;
import ae2.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Optional;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class GuiMEStorage<C extends ContainerMEStorage> extends AEBaseGui<C> implements ISortSource, ITextFieldGui {
    private static final String TEXT_ID_ENTRIES_SHOWN = "entriesShown";
    private static final int MIN_ROWS = 2;
    private static final int DEFAULT_ROWS = 5;
    private static final int RAINBOW_BORDER_PIXELS = 72;
    private static final int RAINBOW_BORDER_ALPHA = 220;
    private static String rememberedSearch = "";

    protected final Repo repo;
    private final ObjectArrayList<ItemStack> currentViewCells = new ObjectArrayList<>();
    private final List<RepoSlot> repoSlots = new ObjectArrayList<>();
    private final IConfigManager configSrc;
    private final boolean supportsViewCells;
    private final TerminalStyle terminalStyle;
    private final Scrollbar scrollbar;
    private final AETextField searchField;
    @Nullable
    private final TabButton craftingStatusButton;
    @Nullable
    private final SettingToggleButton<SortOrder> sortByToggle;
    @Nullable
    private final SettingToggleButton<ViewItems> viewModeToggle;
    private final SettingToggleButton<SortDir> sortDirToggle;
    private final ToggleButton displayFreezeButton;
    private String searchText = "";
    private int rows;
    private boolean displayFrozen;

    public GuiMEStorage(C container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
        this.terminalStyle = style.getTerminalStyle();
        if (this.terminalStyle == null) {
            throw new IllegalStateException(
                "Cannot construct screen " + getClass() + " without a terminalStyles setting");
        }

        this.rows = Math.max(MIN_ROWS, DEFAULT_ROWS);
        this.terminalStyle.validate();
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.repo = new Repo(scrollbar, this);
        this.repo.setRowSize(this.terminalStyle.getSlotsPerRow());
        this.repo.setTerminalRows(this.rows);
        this.repo.setEnabled(canInteractWithRepo());
        container.setClientRepo(this.repo);
        this.repo.setUpdateViewListener(this::updateScrollbar);
        this.configSrc = container.getConfigManager();
        this.container.setGui(this::onContainerReceivedClientUpdate);

        List<Slot> viewCellSlots = container.getSlots(SlotSemantics.VIEW_CELL);
        this.supportsViewCells = !viewCellSlots.isEmpty();
        if (this.supportsViewCells) {
            List<ITextComponent> tooltip = Collections.singletonList(GuiText.TerminalViewCellsTooltip.text());
            this.widgets.add("viewCells", new ViewCellsPanel(viewCellSlots, () -> tooltip));
        }

        this.widgets.add("upgrades", UpgradesPanel.create(
            this.widgets,
            container.getSlots(SlotSemantics.UPGRADE),
            container.getSlots(SlotSemantics.WIRELESS_SINGULARITY),
            container.getHost()));
        if (container.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new ToolboxPanel(style, container.getToolbox().getName()));
        }

        if (this.terminalStyle.isSupportsAutoCrafting()) {
            this.craftingStatusButton = new TabButton(Icon.CRAFT_HAMMER, GuiText.CraftingStatus.text(),
                this::showCraftingStatus);
            this.widgets.add("craftingStatus", this.craftingStatusButton);
        } else {
            this.craftingStatusButton = null;
        }

        if (this.terminalStyle.isSortable()) {
            this.sortByToggle = this.addToLeftToolbar(
                new SettingToggleButton<>(Settings.SORT_BY, getSortBy(), Platform::isSortOrderAvailable,
                    this::toggleServerSetting));
        } else {
            this.sortByToggle = null;
        }

        if (this.terminalStyle.isSupportsAutoCrafting()) {
            this.viewModeToggle = this.addToLeftToolbar(
                new SettingToggleButton<>(Settings.VIEW_MODE, getSortDisplay(), this::toggleServerSetting));
        } else {
            this.viewModeToggle = null;
        }

        if (this.container.canConfigureTypeFilter()) {
            KeyTypeSelectionWindow<C> keyTypeSelectionWindow =
                new KeyTypeSelectionWindow<>(this, GuiText.ConfigureVisibleTypes.text());
            this.widgets.add("keyTypeSelectionWindow", keyTypeSelectionWindow);
            this.addToLeftToolbar(new TerminalKeyTypeSelectionButton(keyTypeSelectionWindow));
        }

        this.sortDirToggle = this.addToLeftToolbar(
            new SettingToggleButton<>(Settings.SORT_DIRECTION, getSortDir(), this::toggleServerSetting));
        this.displayFreezeButton = this.addToLeftToolbar(new ToggleButton(Icon.LOCKED, Icon.UNLOCKED,
            this::setDisplayFrozen));
        this.displayFreezeButton.setTooltipOn(List.of(
            ButtonToolTips.TerminalDisplayFreeze.text(),
            ButtonToolTips.TerminalDisplayFreezeOn.text()));
        this.displayFreezeButton.setTooltipOff(List.of(
            ButtonToolTips.TerminalDisplayFreeze.text(),
            ButtonToolTips.TerminalDisplayFreezeOff.text()));
        this.addToLeftToolbar(new ActionButton(ActionItems.TERMINAL_SETTINGS, this::showSettings));
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle));
        addPortableCellPickupFilterButton();
        addWirelessUniversalTerminalButton();

        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(Arrays.asList(
            GuiText.SearchTooltip.text(),
            GuiText.SearchTooltipModId.text(),
            GuiText.SearchTooltipTag.text(),
            GuiText.SearchTooltipToolTips.text(),
            GuiText.SearchTooltipItemId.text()));
        if ((container.isReturnedFromSubScreen() || AEConfig.instance().isRememberLastSearch())
            && rememberedSearch != null
            && !rememberedSearch.isEmpty()) {
            this.searchField.setText(rememberedSearch);
            this.searchField.selectAll();
            setSearchText(rememberedSearch);
        }
        this.xSize = this.terminalStyle.getScreenWidth();
        this.ySize = this.terminalStyle.getScreenHeight(this.rows);
        updateScrollbar();

        if (!container.isReturnedFromSubScreen()
            && isExternalSearchActive()
            && AEConfig.instance().isClearExternalSearchOnOpen()) {
            ItemListMod.setSearchText("");
        }

        updateSearch();
    }

    private static void restoreGuiStateAfterRepoItem() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void addRainbowBorderPixel(BufferBuilder buffer, int x, int y, int index, int red, int green,
                                              int blue) {
        int side = index / 18;
        int offset = index % 18;
        int px;
        int py;
        switch (side) {
            case 0 -> {
                px = x + offset;
                py = y;
            }
            case 1 -> {
                px = x + 17;
                py = y + offset;
            }
            case 2 -> {
                px = x + 17 - offset;
                py = y + 17;
            }
            default -> {
                px = x;
                py = y + 17 - offset;
            }
        }
        addColoredQuad(buffer, px, py, px + 1, py + 1, red, green, blue);
    }

    private int getConfiguredRows() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_ROWS, this.terminalStyle.getPossibleRows(availableHeight));
        return Math.max(MIN_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
    }

    private static void addColoredQuad(BufferBuilder buffer, int left, int top, int right, int bottom,
                                       int red, int green, int blue) {
        buffer.pos(left, bottom, 0).color(red, green, blue, RAINBOW_BORDER_ALPHA).endVertex();
        buffer.pos(right, bottom, 0).color(red, green, blue, RAINBOW_BORDER_ALPHA).endVertex();
        buffer.pos(right, top, 0).color(red, green, blue, RAINBOW_BORDER_ALPHA).endVertex();
        buffer.pos(left, top, 0).color(red, green, blue, RAINBOW_BORDER_ALPHA).endVertex();
    }

    private void onContainerReceivedClientUpdate() {
        syncButtons();
        syncViewCellFilter();
        this.repo.setEnabled(canInteractWithRepo());
        this.repo.updateView();
        updateScrollbar();
    }

    private void syncButtons() {
        if (this.sortByToggle != null) {
            this.sortByToggle.set(getSortBy());
        }
        if (this.viewModeToggle != null) {
            this.viewModeToggle.set(getSortDisplay());
        }
        this.sortDirToggle.set(getSortDir());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.repo.setPaused(shouldPauseRepo());
        this.repo.setEnabled(canInteractWithRepo());
        updateSearch();
    }

    private boolean shouldPauseRepo() {
        return this.displayFrozen || isShiftKeyDown();
    }

    private void setDisplayFrozen(boolean displayFrozen) {
        if (this.displayFrozen == displayFrozen) {
            return;
        }

        this.displayFrozen = displayFrozen;
        this.displayFreezeButton.setState(displayFrozen);
        this.repo.setPaused(shouldPauseRepo());
        updateScrollbar();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        syncViewCellFilter();
    }

    private void syncViewCellFilter() {
        if (!this.supportsViewCells) {
            return;
        }

        List<ItemStack> viewCells = this.container.getViewCells();
        if (!this.currentViewCells.equals(viewCells)) {
            this.currentViewCells.clear();
            this.currentViewCells.ensureCapacity(viewCells.size());
            this.currentViewCells.addAll(viewCells);
            this.repo.setPartitionList(createPartitionList(viewCells));
        }
    }

    @Nullable
    protected IPartitionList createPartitionList(List<ItemStack> viewCells) {
        return ViewCellItem.createFilter(AEKeyFilter.none(), viewCells);
    }

    private void showSettings() {
        switchToScreen(new GuiTerminalSettings(this));
    }

    private void addPortableCellPickupFilterButton() {
        if (!isItemPortableCellTerminal()) {
            return;
        }
        this.addToLeftToolbar(new PortableCellPickupFilterButton(this::showPortableCellPickupFilter));
    }

    private boolean isItemPortableCellTerminal() {
        if (this.container.getGuiKey() != GuiIds.GuiKey.PORTABLE_ITEM_CELL) {
            return false;
        }
        if (!(this.container.getHost() instanceof PortableCellGuiHost<?> portableCellHost)) {
            return false;
        }
        ItemStack stack = portableCellHost.getItemStack();
        return stack.getItem() instanceof PortableCellItem portableCellItem
            && portableCellItem.getKeyType() == AEKeyType.items();
    }

    private void showPortableCellPickupFilter() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.PORTABLE_CELL_PICKUP_FILTER));
    }

    private void addWirelessUniversalTerminalButton() {
        if (!(this.container.getHost() instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
            return;
        }
        ItemStack stack = wirelessHost.getItemStack();
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem)) {
            return;
        }
        WirelessUniversalTerminalSelectorWindow selector = new WirelessUniversalTerminalSelectorWindow(this);
        this.widgets.add("wirelessUniversalTerminalSelector", selector);
        this.addToLeftToolbar(new ItemStackButton(
            () -> wirelessHost.getTerminalItem().getWirelessTerminalDefinition().icon(wirelessHost.getTerminalItem()),
            GuiText.WirelessTerminalSelector.text(),
            selector::toggle));
    }

    private void showCraftingStatus() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.CRAFTING_STATUS));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void toggleTerminalStyle(SettingToggleButton button, boolean backwards) {
        var nextValue = AEConfig.instance().getTerminalStyle().getDeclaringClass()
                                .cast(button.getNextValue(backwards));
        button.set(nextValue);
        AEConfig.instance().setTerminalStyle(nextValue);
        rememberedSearch = this.searchField.getText();
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private void setSearchText(String searchText) {
        this.repo.setSearchString(searchText);
        this.repo.updateView();
        updateScrollbar();
    }

    @Override
    public void initGui() {
        this.rows = getConfiguredRows();
        this.xSize = this.terminalStyle.getScreenWidth();
        this.ySize = this.terminalStyle.getScreenHeight(this.rows);
        this.repo.setTerminalRows(this.rows);
        refreshRepoSlots();
        super.initGui();
        if (shouldAutoFocusSearch()) {
            setInitialFocus(this.searchField);
        }
        updateScrollbar();
    }

    void onCloseTerminalSettings() {
        this.repo.updateView();
        updateScrollbar();
        updateSearch();
        if (!isExternalSearchActive()) {
            setSearchText(this.searchField.getText());
        }
    }

    private void updateSearch() {
        if (isExternalSearchActive()) {
            this.searchField.setVisible(false);
            this.searchField.setFocused(false);

            String externalSearchText = ItemListMod.getSearchText();
            if (!Objects.equals(this.repo.getSearchString(), externalSearchText)) {
                setSearchText(externalSearchText);
            }

            int allEntries = this.repo.getAllEntries().size();
            int visibleEntries = this.repo.size();
            if (allEntries != visibleEntries) {
                setTextHidden(TEXT_ID_ENTRIES_SHOWN, false);
                setTextContent(TEXT_ID_ENTRIES_SHOWN,
                    new TextComponentString(visibleEntries + " / " + allEntries));
            } else {
                setTextHidden(TEXT_ID_ENTRIES_SHOWN, true);
            }
            return;
        }

        this.searchField.setVisible(true);
        setTextHidden(TEXT_ID_ENTRIES_SHOWN, true);
        this.searchField.setTooltipMessage(Arrays.asList(
            GuiText.SearchTooltip.text(),
            GuiText.SearchTooltipModId.text(),
            GuiText.SearchTooltipTag.text(),
            GuiText.SearchTooltipToolTips.text(),
            GuiText.SearchTooltipItemId.text()));

        String text = this.searchField.getText();
        if (!this.searchText.equals(text)) {
            this.searchText = text;
            setSearchText(text);
        }

        if (AEConfig.instance().isSyncWithExternalSearch() && ItemListMod.isEnabled()) {
            if (this.searchField.isFocused()) {
                ItemListMod.setSearchText(text);
            } else if (ItemListMod.hasSearchFocus()) {
                String externalSearchText = ItemListMod.getSearchText();
                if (!Objects.equals(externalSearchText, this.searchField.getText())) {
                    this.searchField.setText(externalSearchText);
                    this.searchText = externalSearchText;
                    setSearchText(externalSearchText);
                }
            }
        }
    }

    private boolean isExternalSearchActive() {
        return AEConfig.instance().isUseExternalSearch() && ItemListMod.isEnabled();
    }

    private void refreshRepoSlots() {
        this.repo.setTerminalRows(this.rows);
        int repoSlotCount = this.rows * this.terminalStyle.getSlotsPerRow();
        List<RepoSlot> existingSlots = new ObjectArrayList<>(Math.max(this.repoSlots.size(), repoSlotCount));
        for (Slot slot : this.container.inventorySlots) {
            if (slot instanceof RepoSlot) {
                existingSlots.add((RepoSlot) slot);
            }
        }
        for (int i = existingSlots.size() - 1; i >= 0; i--) {
            this.container.removeClientSideSlot(existingSlots.get(i));
        }
        this.repoSlots.clear();

        int repoIndex = 0;
        for (int row = 0; row < this.rows; row++) {
            for (int col = 0; col < this.terminalStyle.getSlotsPerRow(); col++) {
                Point pos = this.terminalStyle.getSlotPos(row, col);
                RepoSlot slot = new RepoSlot(this.repo, repoIndex++, pos.x(), pos.y());
                this.repoSlots.add(slot);
                this.container.addClientSideSlot(slot, null);
            }
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.repo.hasPinnedRow()) {
            renderPinnedRowDecorations();
        }

        if (this.craftingStatusButton != null && this.container.activeCraftingJobs != -1) {
            String label = Integer.toString(this.container.activeCraftingJobs);
            int x = this.craftingStatusButton.x - offsetX + this.craftingStatusButton.width - 3;
            int y = this.craftingStatusButton.y - offsetY + this.craftingStatusButton.height - 4;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 200);
            GlStateManager.scale(0.5F, 0.5F, 1.0F);
            this.fontRenderer.drawStringWithShadow(label,
                -this.fontRenderer.getStringWidth(label),
                -this.fontRenderer.FONT_HEIGHT,
                0xFFFFFF);
            GlStateManager.popMatrix();
        }

        renderLinkStatus(this.container.getLinkStatus());
    }

    private void updateScrollbar() {
        int normalRows = Math.max(1, this.rows - this.repo.getPinnedRowCount());
        int totalRows = (this.repo.getScrollableSize() + this.terminalStyle.getSlotsPerRow() - 1)
            / this.terminalStyle.getSlotsPerRow();
        int scrollRows = Math.max(0, totalRows - normalRows);
        int slotsPerPage = Math.max(1, normalRows / 6);
        this.scrollbar.setHeight(this.rows * 18);
        this.scrollbar.setRange(0, scrollRows, slotsPerPage);
        setTextContent(TEXT_ID_ENTRIES_SHOWN, new TextComponentString(Integer.toString(this.repo.size())));
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        this.terminalStyle.getHeader().dest(offsetX, offsetY).blit();
        int y = offsetY + this.terminalStyle.getHeader().getSrcHeight();
        this.terminalStyle.getFirstRow().dest(offsetX, y).blit();
        y += this.terminalStyle.getFirstRow().getSrcHeight();
        for (int row = 0; row < Math.max(0, this.rows - 2); row++) {
            this.terminalStyle.getRow().dest(offsetX, y).blit();
            y += this.terminalStyle.getRow().getSrcHeight();
        }
        this.terminalStyle.getLastRow().dest(offsetX, y).blit();
        y += this.terminalStyle.getLastRow().getSrcHeight();
        this.terminalStyle.getBottom().dest(offsetX, y).blit();

        int pinnedRowCount = this.repo.getPinnedRowCount();
        for (int row = 0; row < pinnedRowCount; row++) {
            Blitter.texture("guis/terminal.png")
                   .src(0, 204, 162, 18)
                   .dest(offsetX + 7, offsetY + this.terminalStyle.getHeader().getSrcHeight() + row * 18)
                   .blit();
        }

        if (getPinDisplayMode() == PinDisplayMode.SORT_TOP) {
            renderScrollableUserPinSlotBackgrounds(offsetX, offsetY);
        }
    }

    private void renderScrollableUserPinSlotBackgrounds(int offsetX, int offsetY) {
        for (RepoSlot slot : this.repoSlots) {
            if (slot.isUserPinSlot()) {
                int column = slot.getRepoViewIndex() % this.terminalStyle.getSlotsPerRow();
                Blitter.texture("guis/terminal.png")
                       .src(column * 18, 204, 18, 18)
                       .dest(offsetX + slot.xPos - 1, offsetY + slot.yPos - 1)
                       .blit();
            }
        }
    }

    private void renderPinnedRowDecorations() {
        for (Slot slot : this.container.inventorySlots) {
            if (!(slot instanceof RepoSlot repoSlot)) {
                continue;
            }
            GridInventoryEntry entry = repoSlot.getEntry();
            if (entry == null || entry.what() == null
                || this.repo.getPinReason(entry) != PinnedKeys.PinReason.CRAFTING) {
                continue;
            }

            boolean animated = PendingCraftingJobs.hasPendingJob(entry.what());
            renderRainbowBorder(slot.xPos - 1, slot.yPos - 1, animated);
        }
    }

    private void renderRainbowBorder(int x, int y, boolean animated) {
        float phase = animated ? (Minecraft.getSystemTime() % 2400L) / 2400.0F : 0.0F;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.translate(0.0F, 0.0F, 180.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < RAINBOW_BORDER_PIXELS; i++) {
            int color = Color.HSBtoRGB((i / (float) RAINBOW_BORDER_PIXELS + phase) % 1.0F, 0.95F, 1.0F);
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            addRainbowBorderPixel(buffer, x, y, i, r, g, b);
        }
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    @Override
    protected void drawSlot(Slot slot) {
        if (slot instanceof RepoSlot repoSlot) {
            drawRepoSlot(repoSlot);
            renderHoveredSlotOverlayIfNeeded(slot);
            return;
        }

        super.drawSlot(slot);
    }

    private void drawRepoSlot(RepoSlot slot) {
        if (!canInteractWithRepo()) {
            return;
        }

        GridInventoryEntry entry = slot.getEntry();
        if (entry == null || entry.what() == null) {
            return;
        }

        try {
            AEKeyRendering.drawInGui(this.mc, slot.xPos, slot.yPos, entry.what());
        } catch (Exception err) {
            AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
        }
        restoreGuiStateAfterRepoItem();

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 300.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        long storedAmount = entry.storedAmount();
        boolean craftable = entry.craftable();
        boolean useLargeFonts = AEConfig.instance().isUseLargeFonts();
        if (shouldTreatAsZeroAmountCraftable(entry)) {
            StackSizeRenderer.renderSizeLabel(this.fontRenderer, slot.xPos, slot.yPos, "+");
        } else {
            AmountFormat format = useLargeFonts ? AmountFormat.SLOT_LARGE_FONT : AmountFormat.SLOT;
            String amount = entry.what().formatAmount(storedAmount, format);
            renderRepoSizeLabel(slot, amount, useLargeFonts);
            if (craftable) {
                StackSizeRenderer.renderSizeLabel(this.fontRenderer, slot.xPos - 11, slot.yPos - 11, "+", false);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        restoreGuiStateAfterRepoItem();
    }

    private void renderRepoSizeLabel(RepoSlot slot, String amount, boolean useLargeFonts) {
        StackSizeRenderer.renderSizeLabel(this.fontRenderer, slot.xPos, slot.yPos, amount, useLargeFonts);
    }

    private void renderLinkStatus(ILinkStatus linkStatus) {
        if (linkStatus.connected()) {
            return;
        }

        Point firstSlot = this.terminalStyle.getSlotPos(0, 0);
        Point lastSlot = this.terminalStyle.getSlotPos(this.rows - 1, this.terminalStyle.getSlotsPerRow() - 1);
        int left = firstSlot.x() - 1;
        int top = firstSlot.y() - 1;
        int right = lastSlot.x() + 17;
        int bottom = lastSlot.y() + 17;

        drawGradientRect(left, top, right, bottom, 0x3F000000, 0x3F000000);

        ITextComponent statusDescription = linkStatus.statusDescription();
        if (statusDescription != null) {
            List<String> lines = this.fontRenderer.listFormattedStringToWidth(
                statusDescription.getFormattedText(),
                Math.max(10, right - left - 8));
            int y = top + (bottom - top - lines.size() * this.fontRenderer.FONT_HEIGHT) / 2;
            for (String line : lines) {
                int x = left + (right - left - this.fontRenderer.getStringWidth(line)) / 2;
                this.fontRenderer.drawStringWithShadow(line, x, y, 0xFFFFFF);
                y += this.fontRenderer.FONT_HEIGHT;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSelectionPopupMouseClicked(mouseX, mouseY)) {
            return;
        }

        if (this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY) && mouseButton == 1) {
            this.searchField.setText("");
            setSearchText("");
        }

        if (mouseButton == 2 && canInteractWithRepo()) {
            RepoSlot repoSlot = getRepoSlotAt(mouseX, mouseY);
            if (repoSlot != null && repoSlot.isCraftable()) {
                handleGridInventoryEntryMouseClick(repoSlot.getEntry(), mouseButton, ClickType.CLONE);
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void handleMouseClick(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (slot instanceof RepoSlot repoSlot) {
            if (canInteractWithRepo()) {
                handleRepoSlotMouseClick(repoSlot, mouseButton, clickType);
            }
            return;
        }

        if (handlePlayerInventoryPinShortcut(slot, mouseButton, clickType)) {
            return;
        }

        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    public boolean handleBogoSorterGuardedClick(int mouseButton) {
        Slot slot = getSlotUnderMouse();
        ClickType clickType = isShiftKeyDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;

        if (slot instanceof RepoSlot repoSlot) {
            if (canInteractWithRepo()) {
                handleRepoSlotMouseClick(repoSlot, mouseButton, clickType);
            }
            return true;
        }

        return handlePlayerInventoryPinShortcut(slot, mouseButton, clickType);
    }

    private boolean handleGridInventoryEntryPinShortcut(@Nullable GridInventoryEntry entry,
                                                        boolean autoPinAfterAction) {
        if (autoPinAfterAction
            || entry == null
            || entry.what() == null
            || !isCtrlKeyDown()) {
            return false;
        }

        AEKey key = entry.what();
        if (PinnedKeys.isPlayerPinned(key)) {
            toggleAutoPin(key);
            return true;
        }

        if (!canAutoPinToCurrentPlayerCapacity(key)) {
            return false;
        }

        autoPin(key);
        return true;
    }

    private void handleRepoSlotMouseClick(RepoSlot repoSlot, int mouseButton, ClickType clickType) {
        if (clickType == ClickType.PICKUP && repoSlot.isEmptyUserPinSlot()) {
            if (manualPinCarriedStack(repoSlot.getUserPinSlotIndex(), mouseButton)) {
                return;
            }
        }

        handleGridInventoryEntryMouseClick(repoSlot.getEntry(), mouseButton, clickType);
    }

    private boolean handlePlayerInventoryPinShortcut(@Nullable Slot slot, int mouseButton, ClickType clickType) {
        if (!canInteractWithRepo()
            || slot == null
            || mouseButton != 0
            || !isCtrlKeyDown()
            || !isPlayerSideSlot(slot)
            || !slot.getHasStack()) {
            return false;
        }

        AEKey key = keyFromItemStack(slot.getStack());
        if (key == null) {
            return false;
        }

        boolean quickMove = clickType == ClickType.QUICK_MOVE || isShiftKeyDown();
        if (quickMove) {
            super.handleMouseClick(slot, slot.slotNumber, mouseButton, ClickType.QUICK_MOVE);
            if (canAutoPinToCurrentPlayerCapacity(key)) {
                autoPin(key);
            }
        } else if (clickType == ClickType.PICKUP) {
            if (PinnedKeys.isPlayerPinned(key)) {
                toggleAutoPin(key);
                return true;
            }
            if (!canAutoPinToCurrentPlayerCapacity(key)) {
                return false;
            }
            autoPin(key);
        } else {
            return false;
        }
        return true;
    }

    private void handleGridInventoryEntryMouseClick(@Nullable GridInventoryEntry entry, int mouseButton,
                                                    ClickType clickType) {
        if (!canInteractWithRepo()) {
            return;
        }

        boolean autoPinAfterAction = shouldAutoPinAfterGridAction(entry, mouseButton, clickType);
        if (mouseButton == 0
            && clickType == ClickType.PICKUP
            && handleGridInventoryEntryPinShortcut(entry, autoPinAfterAction)) {
            return;
        }

        if (mouseButton == 0
            && clickType == ClickType.PICKUP
            && entry != null
            && shouldCraftOnClick(entry)
            && this.playerInventory.getItemStack().isEmpty()) {
            this.container.handleInteraction(entry.serial(), InventoryAction.AUTO_CRAFT);
            autoPinAfterGridAction(entry, autoPinAfterAction);
            return;
        }

        if (mouseButton == 0 && entry != null && ContainerItemStrategies.isKeySupported(entry.what())) {
            InventoryAction action = clickType != ClickType.QUICK_MOVE
                ? InventoryAction.FILL_ITEM
                : this.playerInventory.getItemStack().isEmpty() ? InventoryAction.FILL_ENTIRE_ITEM_MOVE_TO_PLAYER
                  : InventoryAction.FILL_ENTIRE_ITEM;
            this.container.handleInteraction(entry.serial(), action);
            autoPinAfterGridAction(entry, autoPinAfterAction);
            return;
        }

        if (mouseButton == 1 && !this.playerInventory.getItemStack().isEmpty()) {
            EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(this.playerInventory.getItemStack());
            if (emptyingAction != null && this.container.isKeyVisible(emptyingAction.what())) {
                this.container.handleInteraction(-1,
                    clickType == ClickType.QUICK_MOVE ? InventoryAction.EMPTY_ENTIRE_ITEM
                        : InventoryAction.EMPTY_ITEM);
                return;
            }
        }

        if (entry == null) {
            if (clickType == ClickType.PICKUP && !this.playerInventory.getItemStack().isEmpty()) {
                this.container.handleInteraction(-1,
                    mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN);
            }
            return;
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            this.container.handleInteraction(entry.serial(), InventoryAction.MOVE_REGION);
            autoPinAfterGridAction(entry, autoPinAfterAction);
            return;
        }

        InventoryAction action = null;
        switch (clickType) {
            case PICKUP ->
                action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
            case QUICK_MOVE -> action = mouseButton == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
            case CLONE -> {
                if (entry.craftable()) {
                    this.container.handleInteraction(entry.serial(), InventoryAction.AUTO_CRAFT);
                    autoPinAfterGridAction(entry, autoPinAfterAction);
                    return;
                }
                if (this.mc.player.capabilities.isCreativeMode) {
                    action = InventoryAction.CREATIVE_DUPLICATE;
                }
            }
            default -> {
            }
        }

        if (action != null) {
            this.container.handleInteraction(entry.serial(), action);
            autoPinAfterGridAction(entry, autoPinAfterAction);
        }
    }

    private boolean shouldAutoPinAfterGridAction(@Nullable GridInventoryEntry entry, int mouseButton,
                                                 ClickType clickType) {
        return mouseButton == 0
            && entry != null
            && entry.what() != null
            && isCtrlKeyDown()
            && (isShiftKeyDown() || clickType == ClickType.QUICK_MOVE)
            && canAutoPinToCurrentPlayerCapacity(entry.what());
    }

    private void autoPinAfterGridAction(GridInventoryEntry entry, boolean autoPinAfterAction) {
        if (autoPinAfterAction) {
            autoPin(entry.what());
        }
    }

    private boolean manualPinCarriedStack(int slotIndex, int mouseButton) {
        AEKey key = keyFromCarriedStackForManualPin(this.playerInventory.getItemStack(), mouseButton);
        return key != null && manualPin(key, slotIndex);
    }

    public boolean acceptManualPinGhost(AEKey key, RepoSlot repoSlot) {
        if (!canInteractWithRepo() || !repoSlot.isEmptyUserPinSlot()) {
            return false;
        }
        return manualPin(key, repoSlot.getUserPinSlotIndex());
    }

    @Override
    @Optional.Method(modid = "jei")
    public <I> List<IGhostIngredientHandler.Target<I>> getHEITargets(I ingredient, int ghostMouseButton) {
        List<IGhostIngredientHandler.Target<I>> targets = super.getHEITargets(ingredient, ghostMouseButton);

        GenericStack leftClickStack = HeiGhostTargetSupport.toManualPinStack(ingredient, 0);
        GenericStack rightClickStack = HeiGhostTargetSupport.toManualPinStack(ingredient, 1);
        if (leftClickStack != null || rightClickStack != null) {
            for (var slot : getContainer().inventorySlots) {
                if (slot instanceof RepoSlot repoSlot && repoSlot.isEmptyUserPinSlot()) {
                    targets.add(new ManualPinTarget<>(this, repoSlot, ghostMouseButton, leftClickStack,
                        rightClickStack));
                }
            }
        }
        return targets;
    }

    public void acceptAutoPin(AEKey key) {
        if (canInteractWithRepo()) {
            autoPin(key);
        }
    }

    private boolean manualPin(AEKey key, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.repo.getPlayerPinCapacity()) {
            return false;
        }
        if (PinnedKeys.manualPin(key, slotIndex)) {
            this.repo.updateView();
            updateScrollbar();
            return true;
        }
        return false;
    }

    private void toggleAutoPin(AEKey key) {
        if (PinnedKeys.togglePlayerPin(key, getCurrentPlayerPinCapacity())) {
            this.repo.updateView();
            updateScrollbar();
        }
    }

    private void autoPin(AEKey key) {
        if (PinnedKeys.autoPin(key, getCurrentPlayerPinCapacity())) {
            this.repo.updateView();
            updateScrollbar();
        }
    }

    private boolean canAutoPinToCurrentPlayerCapacity(AEKey key) {
        if (PinnedKeys.isPlayerPinned(key)) {
            return true;
        }

        int capacity = getCurrentPlayerPinCapacity();
        if (capacity <= 0) {
            return false;
        }

        int maxSlots = Math.min(capacity, PinnedKeys.MAX_PLAYER_PIN_ROWS * PinnedKeys.MAX_PINNED);
        for (int slotIndex = 0; slotIndex < maxSlots; slotIndex++) {
            if (PinnedKeys.getPlayerPinSlot(slotIndex) == null) {
                return true;
            }
        }
        return false;
    }

    private int getCurrentPlayerPinCapacity() {
        return Math.min(this.repo.getPlayerPinCapacity(), PinnedKeys.MAX_PLAYER_PIN_ROWS * PinnedKeys.MAX_PINNED);
    }

    @Nullable
    private AEKey keyFromItemStack(ItemStack stack) {
        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack != null) {
            return genericStack.what();
        }
        return AEItemKey.of(stack);
    }

    @Nullable
    private AEKey keyFromCarriedStackForManualPin(ItemStack carried, int mouseButton) {
        if (carried.isEmpty()) {
            return null;
        }

        if (mouseButton == 1) {
            EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
            if (emptyingAction != null && this.container.isKeyVisible(emptyingAction.what())) {
                return emptyingAction.what();
            }
            return null;
        }

        if (mouseButton == 0) {
            return keyFromItemStack(carried);
        }

        return null;
    }

    private boolean shouldCraftOnClick(GridInventoryEntry entry) {
        return shouldTreatAsZeroAmountCraftable(entry);
    }

    private boolean shouldTreatAsZeroAmountCraftable(GridInventoryEntry entry) {
        return entry.craftable() && (isViewOnlyCraftable() || isAltKeyDown() || entry.storedAmount() <= 0);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0 && isShiftKeyDown() && canInteractWithRepo()) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            RepoSlot repoSlot = getRepoSlotAt(mouseX, mouseY);
            if (repoSlot != null) {
                GridInventoryEntry entry = repoSlot.getEntry();
                long serial = entry != null ? entry.serial() : -1;
                InventoryAction action = delta > 0 ? InventoryAction.ROLL_DOWN : InventoryAction.ROLL_UP;
                int times = Math.max(1, Math.abs(delta) / 120);
                for (int i = 0; i < times; i++) {
                    this.container.handleInteraction(serial, action);
                }
                return;
            }
        }

        super.handleMouseInput();
    }

    @Nullable
    private RepoSlot getRepoSlotAt(int mouseX, int mouseY) {
        for (Slot slot : this.container.inventorySlots) {
            if (slot instanceof RepoSlot && isHovering(slot, mouseX, mouseY)) {
                return (RepoSlot) slot;
            }
        }
        return null;
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot hoveredSlot = getSlotUnderMouse();
        if (hoveredSlot instanceof RepoSlot repoSlot) {
            ItemStack carried = this.playerInventory.getItemStack();
            if (repoSlot.isEmptyUserPinSlot()) {
                if (!carried.isEmpty()) {
                    EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
                    if (emptyingAction != null && this.container.isKeyVisible(emptyingAction.what())) {
                        drawTooltipWithHeader(mouseX, mouseY,
                            Tooltips.getEmptyingTooltip(ButtonToolTips.SetAction, carried, emptyingAction));
                    }
                    return;
                }
                if (renderEmptyUserPinSlotHeiGhostTooltip(mouseX, mouseY)) {
                    return;
                }
                drawTooltipWithHeader(mouseX, mouseY, List.of(muted(ButtonToolTips.PlayerPinEmptySlot.text())));
                return;
            }
            if (!carried.isEmpty()) {
                EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
                if (emptyingAction != null && this.container.isKeyVisible(emptyingAction.what())) {
                    drawTooltipWithHeader(mouseX, mouseY,
                        Tooltips.getEmptyingTooltip(ButtonToolTips.StoreAction, carried, emptyingAction));
                    return;
                }

                GridInventoryEntry entry = repoSlot.getEntry();
                if (entry != null && canFillCarriedItem(entry.what(), carried)) {
                    drawTooltipWithHeader(mouseX, mouseY, List.of(muted(ButtonToolTips.FillAction.text(
                        Tooltips.getMouseButtonText(0), Tooltips.of(TextComponentItemStack.of(carried))))));
                    return;
                }
            }

            if (carried.isEmpty() || this.terminalStyle.isShowTooltipsWithItemInHand()) {
                GridInventoryEntry entry = repoSlot.getEntry();
                if (entry != null && entry.what() != null) {
                    drawKeyTooltipWithImages(mouseX, mouseY, entry.what(),
                        getGridInventoryEntryTooltip(entry, repoSlot.isUserPinSlot()));
                    return;
                }
            }
        }

        if (hoveredSlot != null && isPlayerSideSlot(hoveredSlot) && hoveredSlot.getHasStack()) {
            ItemStack stack = hoveredSlot.getStack();
            List<String> tooltip = new ObjectArrayList<>(getItemToolTip(stack));
            AEKey key = keyFromItemStack(stack);
            if (key != null && PinnedKeys.getPlayerPinRows() > 0) {
                if (PinnedKeys.isPlayerPinned(key)) {
                    tooltip.add(muted(ButtonToolTips.PlayerPinUnpinShortcut.text()).getFormattedText());
                } else if (canAutoPinToCurrentPlayerCapacity(key)) {
                    tooltip.add(muted(ButtonToolTips.PlayerPinPinShortcut.text()).getFormattedText());
                    tooltip.add(muted(ButtonToolTips.PlayerPinInsertAndPinShortcut.text()).getFormattedText());
                }
            }
            drawItemTooltipWithImages(mouseX, mouseY, stack, tooltip);
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean renderEmptyUserPinSlotHeiGhostTooltip(int mouseX, int mouseY) {
        var hei = Integrations.hei();
        if (!hei.isEnabled()) {
            return false;
        }

        Object ingredient = hei.getCurrentGhostIngredient();
        if (ingredient == null) {
            return false;
        }

        ItemStack displayStack = hei.getDisplayStack(ingredient);
        if (displayStack.isEmpty()) {
            return false;
        }

        EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(displayStack);
        if (emptyingAction != null && this.container.isKeyVisible(emptyingAction.what())) {
            drawTooltipWithHeader(mouseX, mouseY,
                Tooltips.getEmptyingTooltip(ButtonToolTips.SetAction, displayStack, emptyingAction));
            return true;
        }

        GenericStack stack = hei.ingredientToStack(ingredient);
        if (stack != null && stack.what() != null) {
            drawKeyTooltipWithImages(mouseX, mouseY, stack.what(), normalizeGridTooltip(AEKeyRendering.getTooltip(stack.what())));
            return true;
        }

        drawItemTooltipWithImages(mouseX, mouseY, displayStack, getItemToolTip(displayStack));
        return true;
    }

    private boolean canFillCarriedItem(@Nullable AEKey what, ItemStack carried) {
        return !carried.isEmpty()
            && ContainerItemStrategies.isKeySupported(what)
            && ContainerItemStrategies.findCarriedContextForKey(what, this.mc.player, this.container) != null;
    }

    private List<ITextComponent> getGridInventoryEntryTooltip(GridInventoryEntry entry, boolean inUserPinBar) {
        var what = Objects.requireNonNull(entry.what(), "Repo entry is missing a key");
        List<ITextComponent> tooltip = normalizeGridTooltip(AEKeyRendering.getTooltip(what));

        if (Tooltips.shouldShowAmountTooltip(what, entry.storedAmount())) {
            tooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, what, entry.storedAmount()));
        }

        long requestableAmount = entry.requestableAmount();
        if (requestableAmount > 0) {
            String formattedAmount = what.formatAmount(requestableAmount, AmountFormat.FULL);
            tooltip.add(muted(ButtonToolTips.RequestableAmount.text(formattedAmount)));
        }

        if (entry.craftable() && !shouldTreatAsZeroAmountCraftable(entry)) {
            tooltip.add(muted(ButtonToolTips.Craftable.text()));
        }

        if (this.mc.gameSettings.advancedItemTooltips) {
            tooltip.add(muted(ButtonToolTips.Serial.text(entry.serial())));
        }

        if (canInteractWithRepo() && PinnedKeys.getPlayerPinRows() > 0) {
            if (inUserPinBar || PinnedKeys.isPlayerPinned(what)) {
                tooltip.add(muted(ButtonToolTips.PlayerPinUnpinShortcut.text()));
            } else if (canAutoPinToCurrentPlayerCapacity(what)) {
                tooltip.add(muted(ButtonToolTips.PlayerPinPinShortcut.text()));
            }
        }

        return tooltip;
    }

    private List<ITextComponent> normalizeGridTooltip(List<ITextComponent> source) {
        List<ITextComponent> tooltip = new ObjectArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            ITextComponent line = source.get(i).createCopy();
            Style style = line.getStyle();
            if (i > 0 && style.getColor() == null) {
                line.setStyle(style.createShallowCopy().setColor(TextFormatting.GRAY));
            }
            tooltip.add(line);
        }
        return tooltip;
    }

    private ITextComponent muted(ITextComponent text) {
        return text.setStyle(new Style().setColor(TextFormatting.DARK_GRAY));
    }

    private boolean isViewOnlyCraftable() {
        return this.viewModeToggle != null && this.viewModeToggle.getCurrentValue() == ViewItems.CRAFTABLE;
    }

    private boolean canInteractWithRepo() {
        return this.container.getLinkStatus().connected();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean shouldAutoFocusSearch() {
        return AEConfig.instance().isAutoFocusSearch() && !AEConfig.instance().isUseExternalSearch();
    }

    private <T extends Enum<T>> void toggleServerSetting(SettingToggleButton<T> btn, boolean backwards) {
        T nextValue = btn.getNextValue(backwards);
        btn.set(nextValue);
        this.configSrc.putSetting(btn.getSetting(), nextValue);
        InitNetwork.sendToServer(new ConfigValueServerPacket(this.container.windowId, btn.getSetting(), nextValue));
        this.repo.updateView();
        updateScrollbar();
    }

    private void cycleVisibleKeyTypes() {
        Set<AEKeyType> newSelection = getNextKeyTypeSelection(this.container.getClientKeyTypeSelection());

        for (AEKeyType keyType : newSelection) {
            this.container.selectKeyType(this.container.windowId, keyType, true);
        }
        for (AEKeyType keyType : this.container.getClientKeyTypeSelection().enabledSet()) {
            if (!newSelection.contains(keyType)) {
                this.container.selectKeyType(this.container.windowId, keyType, false);
            }
        }
        this.repo.updateView();
        updateScrollbar();
    }

    private Set<AEKeyType> getNextKeyTypeSelection(IKeyTypeSelectionContainer.SyncedKeyTypes keyTypes) {
        int totalCount = keyTypes.keyTypes().size();
        int enabledCount = keyTypes.enabledSet().size();

        if (totalCount == enabledCount) {
            return Collections.singleton(keyTypes.keyTypes().keySet().iterator().next());
        } else if (enabledCount > 1) {
            return new ObjectLinkedOpenHashSet<>(keyTypes.keyTypes().keySet());
        } else {
            ObjectLinkedOpenHashSet<AEKeyType> enabledKeys = new ObjectLinkedOpenHashSet<>(keyTypes.enabledSet());
            AEKeyType currentKey = enabledKeys.getFirst();
            boolean foundCurrent = false;

            for (AEKeyType keyType : keyTypes.keyTypes().keySet()) {
                if (foundCurrent) {
                    return Collections.singleton(keyType);
                }
                if (keyType == currentKey) {
                    foundCurrent = true;
                }
            }

            return new ObjectLinkedOpenHashSet<>(keyTypes.keyTypes().keySet());
        }
    }

    @Override
    public SortOrder getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public SortDir getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public ViewItems getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    @Override
    public PinDisplayMode getPinDisplayMode() {
        return AEConfig.instance().getPinDisplayMode();
    }

    @Override
    public Set<AEKeyType> getSortKeyTypes() {
        Set<AEKeyType> selected = new ObjectLinkedOpenHashSet<>(this.container.getClientKeyTypeSelection().enabledSet());
        if (selected.isEmpty()) {
            selected.addAll(AEKeyTypes.getAll());
        }
        return selected;
    }

    @Override
    public void onGuiClosed() {
        rememberedSearch = this.searchField.getText();
        super.onGuiClosed();
        for (GridInventoryEntry entry : this.repo.getPinnedEntries()) {
            PinnedKeys.PinInfo info = PinnedKeys.getPinInfo(entry.what());
            if (info != null
                && info.reason == PinnedKeys.PinReason.CRAFTING
                && !PendingCraftingJobs.hasPendingJob(entry.what())) {
                info.canPrune = true;
            }
        }
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return ObjectLists.singleton(this.searchField);
    }

    private final class TerminalKeyTypeSelectionButton extends IconButton {
        private TerminalKeyTypeSelectionButton(KeyTypeSelectionWindow<C> keyTypeSelectionWindow) {
            super(() -> {
                if (isShiftKeyDown()) {
                    cycleVisibleKeyTypes();
                } else {
                    keyTypeSelectionWindow.toggle();
                }
            });
            setMessage(GuiText.ConfigureVisibleTypes.text());
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            StringJoiner joiner = new StringJoiner(", ");
            for (AEKeyType keyType : container.getClientKeyTypeSelection().enabledSet()) {
                joiner.add(keyType.getDescription().getFormattedText());
            }
            return List.of(GuiText.ConfigureVisibleTypes.text(), new TextComponentString(joiner.toString()));
        }

        @Override
        protected Icon getIcon() {
            return Icon.TYPE_FILTER_ALL;
        }
    }
}

