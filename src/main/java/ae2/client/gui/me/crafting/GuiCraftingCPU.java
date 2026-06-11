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

import ae2.api.config.CpuSelectionMode;
import ae2.api.config.Settings;
import ae2.api.config.TerminalStyle;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.StackWithBounds;
import ae2.client.gui.me.search.AEKeySearch;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerCraftingCPU;
import ae2.container.me.crafting.CraftingStatus;
import ae2.container.me.crafting.CraftingStatusEntry;
import ae2.core.AEConfig;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.TraceCraftingSupplierPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GuiCraftingCPU<T extends ContainerCraftingCPU> extends AEBaseGui<T> {
    private static final String TEXTURE = "guis/craftingcpu.png";
    private static final int FIXED_HEADER_HEIGHT = CraftingScreenLayout.TABLE_TOP;
    private static final int FIXED_FOOTER_HEIGHT = 28;
    private static final int ROW_SOURCE_Y = 42;
    private static final int BOTTOM_SOURCE_Y = 157;
    private static final int RIGHT_FOOTER_HEIGHT = 7;

    private final CraftingStatusTableRenderer table;
    private final AE2Button cancel;
    private final AE2Button suspend;
    private final Scrollbar scrollbar;
    private final SettingToggleButton<CpuSelectionMode> schedulingModeButton;
    private final SettingToggleButton<TerminalStyle> terminalStyleButton;
    private final AETextField searchField;
    private final AEKeySearch search = new AEKeySearch();
    private final List<CraftingStatusEntry> visibleEntries = new ObjectArrayList<>();
    private String searchText = "";
    @Nullable
    private CraftingStatus filteredStatus;
    @Nullable
    private CraftingStatus status;

    public GuiCraftingCPU(T container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                          GuiStyle style) {
        super(container, playerInventory, style);

        this.table = new CraftingStatusTableRenderer(this, 9, 19);
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.searchField = widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(Arrays.asList(
            GuiText.SearchTooltip.text(),
            GuiText.SearchTooltipModId.text(),
            GuiText.SearchTooltipTag.text(),
            GuiText.SearchTooltipToolTips.text(),
            GuiText.SearchTooltipItemId.text()));
        this.cancel = widgets.addButton("cancel", GuiText.Cancel.text(), container::cancelCrafting);
        this.suspend = widgets.addButton("suspend", GuiText.Suspend.text(), container::toggleScheduling);
        this.schedulingModeButton = new ServerSettingToggleButton<>(Settings.CPU_SELECTION_MODE, CpuSelectionMode.ANY);
        this.terminalStyleButton = new SettingToggleButton<>(
            Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle);

        if (container.allowConfiguration()) {
            this.addToLeftToolbar(this.schedulingModeButton);
        }
        addTerminalStyleButton();

        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
    }

    @Override
    public void initGui() {
        updateLayout();
        super.initGui();
        updateScrollbar(getVisualEntries().size());
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        List<CraftingStatusEntry> visibleEntries = getVisualEntries();
        List<CraftingStatusEntry> allEntries = this.status != null ? this.status.entries() : Collections.emptyList();
        this.cancel.enabled = !allEntries.isEmpty();
        this.suspend.enabled = this.cancel.enabled;

        ITextComponent title = this.getGuiDisplayName(GuiText.CraftingStatus.text());
        String elapsedTimeSuffix = CraftingTimeDisplay.getElapsedTimeTitleSuffix(status, allEntries.size());
        if (elapsedTimeSuffix != null) {
            title = title.createCopy().appendText(" - " + elapsedTimeSuffix);
        }
        if (container.isCantStoreItems()) {
            ITextComponent suffix = new TextComponentString(" - ");
            suffix.appendSibling(GuiText.CantStoreItems.text().setStyle(new Style().setColor(TextFormatting.RED)));
            title = title.createCopy().appendSibling(suffix);
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, title);

        updateScrollbar(visibleEntries.size());
        this.schedulingModeButton.set(this.container.getSchedulingMode());
        this.terminalStyleButton.set(AEConfig.instance().getTerminalStyle());
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.status != null) {
            this.table.render(mouseX - offsetX, mouseY - offsetY, getVisualEntries(), scrollbar.getCurrentScroll());
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        CraftingScreenBackground.draw(TEXTURE, offsetX, offsetY, this.table.getRows(), FIXED_HEADER_HEIGHT,
            ROW_SOURCE_Y, BOTTOM_SOURCE_Y, FIXED_FOOTER_HEIGHT, RIGHT_FOOTER_HEIGHT);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);

        List<ITextComponent> hoveredTooltip = this.table.getHoveredTooltip();
        if (hoveredTooltip != null) {
            drawTooltipWithHeader(mouseX, mouseY, hoveredTooltip);
        }
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY) && mouseButton == 1) {
            this.searchField.setText("");
            updateSearch();
        }

        if (mouseButton == 0 && isShiftKeyDown()) {
            var hoveredEntry = this.table.getHoveredEntry();
            if (hoveredEntry != null && (hoveredEntry.activeAmount() > 0 || hoveredEntry.pendingAmount() > 0)) {
                InitNetwork.sendToServer(new TraceCraftingSupplierPacket(this.container.windowId, hoveredEntry.serial()));
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    public void postUpdate(CraftingStatus status) {
        Long2ObjectMap<CraftingStatusEntry> entries;
        if (this.status == null || status.fullStatus()) {
            entries = new Long2ObjectLinkedOpenHashMap<>();
        } else {
            entries = new Long2ObjectLinkedOpenHashMap<>(this.status.entries().size());
            for (CraftingStatusEntry entry : this.status.entries()) {
                entries.put(entry.serial(), entry);
            }
        }

        for (CraftingStatusEntry entry : status.entries()) {
            if (entry.isDeleted()) {
                entries.remove(entry.serial());
                continue;
            }

            CraftingStatusEntry existingEntry = entries.get(entry.serial());
            if (existingEntry != null) {
                entries.put(entry.serial(), new CraftingStatusEntry(
                    existingEntry.serial(),
                    existingEntry.what(),
                    entry.storedAmount(),
                    entry.activeAmount(),
                    entry.pendingAmount()));
            } else if (entry.what() != null) {
                entries.put(entry.serial(), entry);
            }
        }

        List<CraftingStatusEntry> sortedEntries = new ObjectArrayList<>(entries.values());
        Collections.sort(sortedEntries);
        this.status = new CraftingStatus(
            true,
            status.elapsedTime(),
            status.remainingItemCount(),
            status.startItemCount(),
            sortedEntries,
            status.suspended());
        this.filteredStatus = null;
        this.suspend.setMessage(status.suspended() ? GuiText.Resume.text() : GuiText.Suspend.text());
    }

    protected ITextComponent getGuiDisplayName(ITextComponent in) {
        return super.getGuiDisplayName(in);
    }

    protected int getCraftingRows() {
        return this.table.getRows();
    }

    protected void addTerminalStyleButton() {
        this.addToLeftToolbar(this.terminalStyleButton);
    }

    protected SettingToggleButton<TerminalStyle> getTerminalStyleButton() {
        return this.terminalStyleButton;
    }

    private List<CraftingStatusEntry> getVisualEntries() {
        updateSearch();
        if (this.status == null) {
            return Collections.emptyList();
        }
        if (this.filteredStatus == this.status) {
            return this.visibleEntries;
        }

        this.filteredStatus = this.status;
        this.visibleEntries.clear();
        for (CraftingStatusEntry entry : this.status.entries()) {
            if (entry.what() != null && this.search.matches(entry.what())) {
                this.visibleEntries.add(entry);
            }
        }
        return this.visibleEntries;
    }

    private void updateSearch() {
        String text = this.searchField.getText();
        if (!this.searchText.equals(text)) {
            this.searchText = text;
            this.search.setSearchString(text);
            this.filteredStatus = null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void toggleTerminalStyle(SettingToggleButton button, boolean backwards) {
        var nextValue = (TerminalStyle) button.getNextValue(backwards);
        button.set(nextValue);
        AEConfig.instance().setTerminalStyle(nextValue);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private void updateLayout() {
        int rows = CraftingScreenLayout.getRows(this.height, FIXED_HEADER_HEIGHT, FIXED_FOOTER_HEIGHT);
        this.table.setRows(rows);
        this.xSize = CraftingScreenLayout.WIDTH;
        this.ySize = CraftingScreenLayout.getHeight(rows, FIXED_HEADER_HEIGHT, FIXED_FOOTER_HEIGHT);
    }

    private void updateScrollbar(int entryCount) {
        this.scrollbar.setHeight(CraftingScreenLayout.getScrollbarHeight(this.table.getRows()));
        this.scrollbar.setRange(0, this.table.getScrollableRows(entryCount), 1);
    }
}
