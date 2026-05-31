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
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.AmountFormat;
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
import ae2.client.gui.widgets.ItemStackButton;
import ae2.client.gui.widgets.KeyTypeSelectionButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.TabButton;
import ae2.client.gui.widgets.ToolboxPanel;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.client.gui.widgets.ViewCellsPanel;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.common.GridInventoryEntry;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.bidirectional.ConfigValuePacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.integration.abstraction.ItemListMod;
import ae2.items.storage.ViewCellItem;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.text.TextComponentItemStack;
import ae2.util.Platform;
import ae2.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GuiMEStorage<C extends ContainerMEStorage> extends AEBaseGui<C> implements ISortSource, ITextFieldGui {
    private static final String TEXT_ID_ENTRIES_SHOWN = "entriesShown";
    private static final int MIN_ROWS = 2;
    private static final int DEFAULT_ROWS = 5;
    private static String rememberedSearch = "";

    protected final Repo repo;
    private final List<ItemStack> currentViewCells = new ObjectArrayList<>();
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
    private String searchText = "";
    private int rows;

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
            this.addToLeftToolbar(
                KeyTypeSelectionButton.create(this, container.getHost(), GuiText.ConfigureVisibleTypes.text()));
        }

        this.sortDirToggle = this.addToLeftToolbar(
            new SettingToggleButton<>(Settings.SORT_DIRECTION, getSortDir(), this::toggleServerSetting));
        this.addToLeftToolbar(new ActionButton(ActionItems.TERMINAL_SETTINGS, this::showSettings));
        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle));
        addWirelessUniversalTerminalButton();

        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
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

    @Override
    public void initGui() {
        this.rows = getConfiguredRows();
        this.xSize = this.terminalStyle.getScreenWidth();
        this.ySize = this.terminalStyle.getScreenHeight(this.rows);
        refreshRepoSlots();
        super.initGui();
        if (shouldAutoFocusSearch()) {
            setInitialFocus(this.searchField);
        }
        updateScrollbar();
    }

    private int getConfiguredRows() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_ROWS, this.terminalStyle.getPossibleRows(availableHeight));
        return Math.max(MIN_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
    }

    private void refreshRepoSlots() {
        List<RepoSlot> existingSlots = new ObjectArrayList<>();
        for (Slot slot : this.container.inventorySlots) {
            if (slot instanceof RepoSlot) {
                existingSlots.add((RepoSlot) slot);
            }
        }
        for (int i = existingSlots.size() - 1; i >= 0; i--) {
            this.container.removeClientSideSlot(existingSlots.get(i));
        }

        int repoIndex = 0;
        for (int row = 0; row < this.rows; row++) {
            for (int col = 0; col < this.terminalStyle.getSlotsPerRow(); col++) {
                Point pos = this.terminalStyle.getSlotPos(row, col);
                this.container.addClientSideSlot(new RepoSlot(this.repo, repoIndex++, pos.x(), pos.y()), null);
            }
        }
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
        this.repo.setPaused(isShiftKeyDown());
        this.repo.setEnabled(canInteractWithRepo());
        updateSearch();
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
        var nextValue = (ae2.api.config.TerminalStyle) button.getNextValue(backwards);
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

    private void updateScrollbar() {
        int totalRows = (this.repo.size() + this.terminalStyle.getSlotsPerRow() - 1)
            / this.terminalStyle.getSlotsPerRow();
        int scrollRows = Math.max(0, totalRows - this.rows);
        int slotsPerPage = Math.max(1, this.rows / 6);
        this.scrollbar.setHeight(this.rows * 18);
        this.scrollbar.setRange(0, scrollRows, slotsPerPage);
        setTextContent(TEXT_ID_ENTRIES_SHOWN, new TextComponentString(Integer.toString(this.repo.size())));
    }

    void onCloseTerminalSettings() {
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

        if (this.repo.hasPinnedRow()) {
            Blitter.texture("guis/terminal.png")
                   .src(0, 204, 162, 18)
                   .dest(offsetX + 7, offsetY + this.terminalStyle.getHeader().getSrcHeight())
                   .blit();
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

    private void renderPinnedRowDecorations() {
        for (Slot slot : this.container.inventorySlots) {
            if (!(slot instanceof RepoSlot repoSlot)) {
                continue;
            }
            GridInventoryEntry entry = repoSlot.getEntry();
            if (entry == null || entry.what() == null || !PendingCraftingJobs.hasPendingJob(entry.what())) {
                continue;
            }

            TextureAtlasSprite sprite = this.mc.getTextureMapBlocks().getAtlasSprite(
                AppEng.makeId("block/molecular_assembler_lights").toString());
            if (sprite != null && sprite != this.mc.getTextureMapBlocks().getMissingSprite()) {
                Blitter.sprite(sprite, 2, 2, Math.max(0, sprite.getIconWidth() - 4),
                           Math.max(0, sprite.getIconHeight() - 4))
                       .dest(slot.xPos - 1, slot.yPos - 1, 18, 18)
                       .zOffset(150)
                       .blit();
            }
        }
    }

    @Override
    protected void drawSlot(Slot slot) {
        if (slot instanceof RepoSlot repoSlot) {
            drawRepoSlot(repoSlot);
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
        if (craftable && (isViewOnlyCraftable() || storedAmount <= 0)) {
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
                handleGridInventoryEntryMouseClick(repoSlot.getEntry(), mouseButton, clickType);
            }
            return;
        }

        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    private void handleGridInventoryEntryMouseClick(@Nullable GridInventoryEntry entry, int mouseButton,
                                                    ClickType clickType) {
        if (!canInteractWithRepo()) {
            return;
        }

        if (mouseButton == 0 && entry != null && ContainerItemStrategies.isKeySupported(entry.what())) {
            InventoryAction action = clickType != ClickType.QUICK_MOVE
                ? InventoryAction.FILL_ITEM
                : this.playerInventory.getItemStack().isEmpty() ? InventoryAction.FILL_ENTIRE_ITEM_MOVE_TO_PLAYER
                  : InventoryAction.FILL_ENTIRE_ITEM;
            this.container.handleInteraction(entry.serial(), action);
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
            return;
        }

        InventoryAction action = null;
        switch (clickType) {
            case PICKUP -> {
                action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
                if (action == InventoryAction.PICKUP_OR_SET_DOWN
                    && shouldCraftOnClick(entry)
                    && this.playerInventory.getItemStack().isEmpty()) {
                    this.container.handleInteraction(entry.serial(), InventoryAction.AUTO_CRAFT);
                    return;
                }
            }
            case QUICK_MOVE -> action = mouseButton == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
            case CLONE -> {
                if (entry.craftable()) {
                    this.container.handleInteraction(entry.serial(), InventoryAction.AUTO_CRAFT);
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
        }
    }

    private boolean shouldCraftOnClick(GridInventoryEntry entry) {
        if (isViewOnlyCraftable()) {
            return true;
        }
        return entry.storedAmount() == 0 && entry.craftable();
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
                    drawTooltipWithHeader(mouseX, mouseY, getGridInventoryEntryTooltip(entry));
                    return;
                }
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean canFillCarriedItem(@Nullable AEKey what, ItemStack carried) {
        return !carried.isEmpty()
            && ContainerItemStrategies.isKeySupported(what)
            && ContainerItemStrategies.findCarriedContextForKey(what, this.mc.player, this.container) != null;
    }

    private List<ITextComponent> getGridInventoryEntryTooltip(GridInventoryEntry entry) {
        var what = Objects.requireNonNull(entry.what(), "Repo entry is missing a key");
        List<ITextComponent> tooltip = new ObjectArrayList<>(AEKeyRendering.getTooltip(what));

        if (Tooltips.shouldShowAmountTooltip(what, entry.storedAmount())) {
            tooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, what, entry.storedAmount()));
        }

        long requestableAmount = entry.requestableAmount();
        if (requestableAmount > 0) {
            String formattedAmount = what.formatAmount(requestableAmount, AmountFormat.FULL);
            tooltip.add(muted(ButtonToolTips.RequestableAmount.text(formattedAmount)));
        }

        if (entry.craftable() && !(isViewOnlyCraftable() || entry.storedAmount() <= 0)) {
            tooltip.add(muted(ButtonToolTips.Craftable.text()));
        }

        if (this.mc.gameSettings.advancedItemTooltips) {
            tooltip.add(muted(ButtonToolTips.Serial.text(entry.serial())));
        }

        return tooltip;
    }

    private ITextComponent muted(ITextComponent text) {
        return text.createCopy().setStyle(new Style().setColor(TextFormatting.DARK_GRAY));
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
        InitNetwork.CHANNEL.sendToServer(new ConfigValuePacket(btn.getSetting(), nextValue));
        this.repo.updateView();
        updateScrollbar();
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
    public Set<AEKeyType> getSortKeyTypes() {
        Set<AEKeyType> selected = new it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet<>(this.container.getClientKeyTypeSelection().enabledSet());
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
}

