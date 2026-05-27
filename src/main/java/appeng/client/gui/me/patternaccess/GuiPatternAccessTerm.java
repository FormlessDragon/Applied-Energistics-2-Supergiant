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

package appeng.client.gui.me.patternaccess;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.config.TerminalStyle;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.storage.ILinkStatus;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.me.common.GuiTerminalSettings;
import appeng.client.gui.me.items.WirelessUniversalTerminalSelectorWindow;
import appeng.client.gui.style.GuiStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ITextFieldGui;
import appeng.client.gui.widgets.ItemStackButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.container.SlotSemantics;
import appeng.container.implementations.ContainerPatternAccessTerm;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.core.network.serverbound.QuickMovePatternPacket;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiHost;
import appeng.items.tools.powered.WirelessUniversalTerminalItem;
import com.google.common.collect.HashMultimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class GuiPatternAccessTerm<C extends ContainerPatternAccessTerm> extends AEBaseGui<C> implements ITextFieldGui {

    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/patternaccessterminal.png");

    private static final int GUI_WIDTH = 195;
    private static final int GUI_PADDING_X = 8;
    private static final int GUI_PADDING_Y = 6;
    private static final int GUI_HEADER_HEIGHT = 17;
    private static final int GUI_FOOTER_HEIGHT = 99;
    private static final int COLUMNS = 9;
    private static final int ROW_HEIGHT = 18;
    private static final int SLOT_SIZE = ROW_HEIGHT;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int PATTERN_PROVIDER_NAME_MARGIN_X = 2;
    private static final int TEXT_MAX_WIDTH = 155;

    private static final Rectangle HEADER_BBOX = new Rectangle(0, 0, GUI_WIDTH, GUI_HEADER_HEIGHT);
    private static final Rectangle ROW_TEXT_TOP_BBOX = new Rectangle(0, 17, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_INVENTORY_TOP_BBOX = new Rectangle(0, 35, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_TEXT_MIDDLE_BBOX = new Rectangle(0, 53, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_INVENTORY_MIDDLE_BBOX = new Rectangle(0, 71, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_TEXT_BOTTOM_BBOX = new Rectangle(0, 89, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_INVENTORY_BOTTOM_BBOX = new Rectangle(0, 107, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle FOOTER_BBOX = new Rectangle(0, 125, GUI_WIDTH, GUI_FOOTER_HEIGHT);

    private static final Comparator<PatternContainerGroup> GROUP_COMPARATOR = Comparator
        .comparing(group -> group.name().getFormattedText().toLowerCase(Locale.ROOT));

    private final Long2ObjectOpenHashMap<PatternContainerEntry> byId = new Long2ObjectOpenHashMap<>();
    private final HashMultimap<PatternContainerGroup, PatternContainerEntry> byGroup = HashMultimap.create();
    private final List<PatternContainerGroup> groups = new ObjectArrayList<>();
    private final List<Row> rows = new ObjectArrayList<>();
    private final Map<ItemStack, String> patternSearchText = new Reference2ObjectOpenHashMap<>();

    private final ITextComponent title;
    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final ServerSettingToggleButton<ShowPatternProviders> showPatternProviders;

    private int visibleRows = MIN_VISIBLE_ROWS;
    private int lastScroll = Integer.MIN_VALUE;
    private String searchText = "";

    public GuiPatternAccessTerm(C container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                                GuiStyle style) {
        super(container, playerInventory, Objects.requireNonNull(style, "style"));
        this.title = title != null ? title : GuiText.PatternAccessTerminalShort.text();

        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setTooltipMessage(Collections.singletonList(GuiText.SearchTooltip.text()));

        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.TERMINAL_STYLE,
            AEConfig.instance().getTerminalStyle(),
            this::toggleTerminalStyle));
        addWirelessSettingsButton();
        this.showPatternProviders = this.addToLeftToolbar(new ServerSettingToggleButton<>(
            Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
            ShowPatternProviders.VISIBLE));
        if (container.getItemGuiHost() instanceof IUpgradeableObject upgradeableObject) {
            this.widgets.add("upgrades", UpgradesPanel.create(
                this.widgets,
                container.getSlots(SlotSemantics.UPGRADE),
                container.getSlots(SlotSemantics.WIRELESS_SINGULARITY),
                upgradeableObject));
        }
        addWirelessUniversalTerminalButton();

        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEADER_HEIGHT + GUI_FOOTER_HEIGHT + MIN_VISIBLE_ROWS * ROW_HEIGHT;
        this.setTextHidden("entriesShown", true);
    }

    private static void restoreGuiStateAfterHeaderIcon() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void addWirelessUniversalTerminalButton() {
        if (!(this.container.getItemGuiHost() instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
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

    private void addWirelessSettingsButton() {
        if (!(this.container.getItemGuiHost() instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
            return;
        }
        this.addToLeftToolbar(new ActionButton(ActionItems.TERMINAL_SETTINGS,
            () -> switchToScreen(new GuiTerminalSettings(
                this,
                this.container,
                wirelessHost,
                wirelessHost.getMainContainerIcon(),
                () -> {
                },
                true))));
    }

    @Override
    public void initGui() {
        clearPatternSlots();

        this.visibleRows = getConfiguredRows();
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEADER_HEIGHT + GUI_FOOTER_HEIGHT + this.visibleRows * ROW_HEIGHT;

        super.initGui();

        if (AEConfig.instance().isAutoFocusSearch()) {
            setInitialFocus(this.searchField);
        }

        resetScrollbar();
        refreshVisiblePatternSlots();
    }

    private int getConfiguredRows() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_VISIBLE_ROWS,
            (availableHeight - GUI_HEADER_HEIGHT - GUI_FOOTER_HEIGHT) / ROW_HEIGHT);
        return Math.max(MIN_VISIBLE_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.showPatternProviders.set(this.container.getShownProviders());

        String text = this.searchField.getText();
        if (!this.searchText.equals(text)) {
            this.searchText = text;
            refreshList();
            return;
        }

        int scroll = this.scrollbar.getCurrentScroll();
        if (scroll != this.lastScroll) {
            refreshVisiblePatternSlots();
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        blit(offsetX, offsetY, HEADER_BBOX);

        int scrollLevel = this.scrollbar.getCurrentScroll();
        int currentY = offsetY + GUI_HEADER_HEIGHT;

        blit(offsetX, currentY + this.visibleRows * ROW_HEIGHT, FOOTER_BBOX);

        for (int i = 0; i < this.visibleRows; i++) {
            boolean firstLine = i == 0;
            boolean lastLine = i == this.visibleRows - 1;

            Rectangle textRow = selectRowBackgroundBox(false, firstLine, lastLine);
            blit(offsetX, currentY, textRow);

            if (scrollLevel + i < this.rows.size()) {
                Row row = this.rows.get(scrollLevel + i);
                if (row instanceof SlotsRow slotsRow) {
                    Rectangle invRow = selectRowBackgroundBox(true, firstLine, lastLine);
                    int width = GUI_PADDING_X + SLOT_SIZE * slotsRow.slots() - 1;

                    blit(offsetX, currentY, invRow.x, invRow.y, width, invRow.height);
                }
            }

            currentY += ROW_HEIGHT;
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(getGuiDisplayName(this.title).getFormattedText(), 8, 6, 4210752);

        int scrollLevel = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.visibleRows; i++) {
            if (scrollLevel + i >= this.rows.size()) {
                continue;
            }

            Row row = this.rows.get(scrollLevel + i);
            if (row instanceof GroupHeaderRow) {
                drawGroupHeader(((GroupHeaderRow) row).group(), i);
            } else if (row instanceof SlotsRow) {
                drawInvalidPatternOverlays((SlotsRow) row, i);
            }
        }

        renderLinkStatus(this.container.getLinkStatus());
    }

    private void drawGroupHeader(PatternContainerGroup group, int rowIndex) {
        if (group.icon() != null) {
            AEKeyRendering.drawInGui(this.mc,
                GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X,
                GUI_HEADER_HEIGHT + rowIndex * ROW_HEIGHT + 1,
                group.icon());
            restoreGuiStateAfterHeaderIcon();
        }

        int entries = this.byGroup.get(group).size();
        String displayName = group.name().getFormattedText();
        if (entries > 1) {
            displayName += " (" + entries + ")";
        }

        displayName = this.fontRenderer.trimStringToWidth(displayName, TEXT_MAX_WIDTH - 18);
        this.fontRenderer.drawString(
            displayName,
            GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X + 18,
            GUI_HEADER_HEIGHT + GUI_PADDING_Y + rowIndex * ROW_HEIGHT,
            4210752);
    }

    private void drawInvalidPatternOverlays(SlotsRow slotsRow, int rowIndex) {
        var level = this.container.getPlayer().world;
        for (int col = 0; col < slotsRow.slots(); col++) {
            int slotIndex = slotsRow.offset() + col;
            ItemStack pattern = slotsRow.container().getInventory().getStackInSlot(slotIndex);
            if (!pattern.isEmpty() && PatternDetailsHelper.decodePattern(pattern, level) == null) {
                int x = GUI_PADDING_X + col * SLOT_SIZE;
                int y = GUI_HEADER_HEIGHT + rowIndex * ROW_HEIGHT + 1;
                drawGradientRect(x, y, x + 16, y + 16, 0x7FFF0000, 0x7FFF0000);
            }
        }
    }

    private void renderLinkStatus(ILinkStatus linkStatus) {
        if (linkStatus.connected()) {
            return;
        }

        int left = GUI_PADDING_X - 1;
        int top = GUI_HEADER_HEIGHT;
        int right = left + COLUMNS * SLOT_SIZE;
        int bottom = top + this.visibleRows * ROW_HEIGHT;

        drawGradientRect(left, top, right, bottom, 0x3F000000, 0x3F000000);

        ITextComponent statusDescription = linkStatus.statusDescription();
        if (statusDescription == null) {
            return;
        }

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

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (!this.container.getLinkStatus().connected() && this.getSlotUnderMouse() instanceof GuiPatternSlot) {
            return;
        }

        if (this.getSlotUnderMouse() == null) {
            int hoveredLine = getHoveredLineIndex(mouseX, mouseY);
            if (hoveredLine != -1) {
                Row row = this.rows.get(hoveredLine);
                if (row instanceof GroupHeaderRow(PatternContainerGroup group)) {
                    if (!group.tooltip().isEmpty()) {
                        drawTooltipWithHeader(mouseX, mouseY, group.tooltip());
                        return;
                    }
                }
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private int getHoveredLineIndex(int mouseX, int mouseY) {
        int x = mouseX - this.guiLeft - GUI_PADDING_X;
        int y = mouseY - this.guiTop - GUI_HEADER_HEIGHT;

        if (x < 0 || y < 0) {
            return -1;
        }
        if (x >= SLOT_SIZE * COLUMNS || y >= this.visibleRows * ROW_HEIGHT) {
            return -1;
        }

        int rowIndex = this.scrollbar.getCurrentScroll() + y / ROW_HEIGHT;
        return rowIndex >= 0 && rowIndex < this.rows.size() ? rowIndex : -1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 1 && this.searchField.isMouseOver(mouseX, mouseY)) {
            this.searchField.setText("");
            this.searchText = "";
            refreshList();
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int previousScroll = this.scrollbar.getCurrentScroll();
        super.handleMouseInput();
        if (this.scrollbar.getCurrentScroll() != previousScroll) {
            refreshVisiblePatternSlots();
        }
    }

    @Override
    protected void handleMouseClick(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (slot instanceof GuiPatternSlot patternSlot) {
            if (canInteractWithPatterns()) {
                handlePatternSlotClick(patternSlot, mouseButton, clickType);
            }
            return;
        }

        if (slot != null && clickType == ClickType.QUICK_MOVE && this.container.isPlayerSideSlot(slot)
            && canInteractWithPatterns()) {
            sendQuickMovePacket(slot);
            return;
        }

        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    private boolean canInteractWithPatterns() {
        return this.container.getLinkStatus().connected();
    }

    private void handlePatternSlotClick(GuiPatternSlot slot, int mouseButton, ClickType clickType) {
        InventoryAction action = null;

        switch (clickType) {
            case PICKUP -> action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                : InventoryAction.PICKUP_OR_SET_DOWN;
            case QUICK_MOVE -> action = mouseButton == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
            case CLONE -> {
                if (this.mc.player.capabilities.isCreativeMode) {
                    action = InventoryAction.CREATIVE_DUPLICATE;
                }
            }
            default -> {
            }
        }

        if (action != null) {
            InitNetwork.sendToServer(
                new InventoryActionPacket(this.container.windowId, action, slot.getSlotIndex(),
                    slot.getMachineInv().getServerId()));
        }
    }

    private void sendQuickMovePacket(Slot clickedSlot) {
        var visiblePatternContainers = new LongLinkedOpenHashSet();
        for (Row row : this.rows) {
            if (row instanceof SlotsRow slotsRow) {
                visiblePatternContainers.add(slotsRow.container().getServerId());
            }
        }

        InitNetwork.sendToServer(new QuickMovePatternPacket(this.container.windowId, clickedSlot.slotNumber, visiblePatternContainers));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return;
        }

        if (typedChar == ' ' && this.searchField.isFocused() && this.searchField.getText().isEmpty()) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    public void clear() {
        this.byId.clear();
        this.patternSearchText.clear();
        refreshList();
    }

    public void postFullUpdate(long inventoryId, long sortBy, PatternContainerGroup group, int inventorySize,
                               Int2ObjectMap<ItemStack> slots) {
        PatternContainerEntry patternContainer = new PatternContainerEntry(inventoryId, inventorySize, sortBy, group);
        this.byId.put(inventoryId, patternContainer);

        appeng.util.inv.AppEngInternalInventory inventory = patternContainer.getInventory();
        for (Int2ObjectMap.Entry<ItemStack> entry : slots.int2ObjectEntrySet()) {
            inventory.setItemDirect(entry.getIntKey(), entry.getValue());
        }

        this.patternSearchText.clear();
        refreshList();
    }

    public void postIncrementalUpdate(long inventoryId, Int2ObjectMap<ItemStack> slots) {
        PatternContainerEntry patternContainer = this.byId.get(inventoryId);
        if (patternContainer == null) {
            AELog.warn("Ignoring incremental update for unknown inventory id %d", inventoryId);
            return;
        }

        appeng.util.inv.AppEngInternalInventory inventory = patternContainer.getInventory();
        for (Int2ObjectMap.Entry<ItemStack> entry : slots.int2ObjectEntrySet()) {
            inventory.setItemDirect(entry.getIntKey(), entry.getValue());
        }

        this.patternSearchText.clear();
        refreshList();
    }

    private void refreshList() {
        this.byGroup.clear();

        String filter = this.searchField.getText().toLowerCase(Locale.ROOT);
        for (PatternContainerEntry entry : this.byId.values()) {
            if (matchesSearch(entry, filter)) {
                this.byGroup.put(entry.getGroup(), entry);
            }
        }

        this.groups.clear();
        this.groups.addAll(this.byGroup.keySet());
        this.groups.sort(GROUP_COMPARATOR);

        this.rows.clear();
        for (PatternContainerGroup group : this.groups) {
            this.rows.add(new GroupHeaderRow(group));

            ObjectList<PatternContainerEntry> containers = new ObjectArrayList<>(this.byGroup.get(group));
            Collections.sort(containers);
            for (PatternContainerEntry container : containers) {
                int size = container.getInventory().size();
                for (int offset = 0; offset < size; offset += COLUMNS) {
                    int slots = Math.min(size - offset, COLUMNS);
                    this.rows.add(new SlotsRow(container, offset, slots));
                }
            }
        }

        resetScrollbar();
        refreshVisiblePatternSlots();
    }

    private boolean matchesSearch(PatternContainerEntry entry, String filter) {
        if (filter.isEmpty()) {
            return true;
        }

        if (entry.getSearchName().contains(filter)) {
            return true;
        }

        for (ItemStack stack : entry.getInventory()) {
            if (itemStackMatchesSearchTerm(stack, filter)) {
                return true;
            }
        }

        return false;
    }

    private boolean itemStackMatchesSearchTerm(ItemStack stack, String filter) {
        if (stack.isEmpty()) {
            return false;
        }
        return this.patternSearchText.computeIfAbsent(stack, this::getPatternSearchText).contains(filter);
    }

    private String getPatternSearchText(ItemStack stack) {
        net.minecraft.world.World level = this.container.getPlayer().world;
        StringBuilder text = new StringBuilder();
        appeng.api.crafting.IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, level);

        if (pattern != null) {
            for (appeng.api.stacks.GenericStack output : pattern.getOutputs()) {
                text.append(output.what().getDisplayName().getFormattedText().toLowerCase(Locale.ROOT));
                text.append('\n');
            }
        }

        return text.toString();
    }

    private void resetScrollbar() {
        this.scrollbar.setHeight(this.visibleRows * ROW_HEIGHT - 2);
        this.scrollbar.setRange(0, Math.max(0, this.rows.size() - this.visibleRows), 2);
    }

    private void refreshVisiblePatternSlots() {
        clearPatternSlots();

        int scroll = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.visibleRows; i++) {
            if (scroll + i >= this.rows.size()) {
                break;
            }

            Row row = this.rows.get(scroll + i);
            if (row instanceof SlotsRow(PatternContainerEntry patternContainer, int offset, int slots)) {
                for (int col = 0; col < slots; col++) {
                    GuiPatternSlot slot = new GuiPatternSlot(
                        patternContainer,
                        offset + col,
                        GUI_PADDING_X + col * SLOT_SIZE,
                        GUI_HEADER_HEIGHT + i * ROW_HEIGHT + 1);
                    this.container.addClientSideSlot(slot, null);
                }
            }
        }

        this.lastScroll = scroll;
    }

    private void clearPatternSlots() {
        for (int i = this.container.inventorySlots.size() - 1; i >= 0; i--) {
            Slot slot = this.container.inventorySlots.get(i);
            if (slot instanceof GuiPatternSlot && this.container.isClientSideSlot(slot)) {
                this.container.removeClientSideSlot(slot);
            }
        }
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        TerminalStyle next = button.getNextValue(backwards);
        button.set(next);
        AEConfig.instance().setTerminalStyle(next);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private Rectangle selectRowBackgroundBox(boolean inventoryRow, boolean firstLine, boolean lastLine) {
        if (inventoryRow) {
            if (firstLine) {
                return ROW_INVENTORY_TOP_BBOX;
            }
            if (lastLine) {
                return ROW_INVENTORY_BOTTOM_BBOX;
            }
            return ROW_INVENTORY_MIDDLE_BBOX;
        }

        if (firstLine) {
            return ROW_TEXT_TOP_BBOX;
        }
        if (lastLine) {
            return ROW_TEXT_BOTTOM_BBOX;
        }
        return ROW_TEXT_MIDDLE_BBOX;
    }

    private void blit(int x, int y, Rectangle srcRect) {

        blit(x, y, srcRect.x, srcRect.y, srcRect.width, srcRect.height);
    }

    private void blit(int x, int y, int u, int v, int width, int height) {
        bindTexture(TEXTURE);
        drawTexturedModalRect(x, y, u, v, width, height);
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return ObjectLists.singleton(this.searchField);
    }

    private interface Row {
    }

    private record GroupHeaderRow(PatternContainerGroup group) implements Row {
    }

    private record SlotsRow(PatternContainerEntry container, int offset, int slots) implements Row {
    }
}

