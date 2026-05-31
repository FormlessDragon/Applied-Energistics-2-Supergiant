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

package ae2.client.gui.me.patternaccess;

import ae2.api.client.AEKeyRendering;
import ae2.api.config.ActionItems;
import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.config.TerminalStyle;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ILinkStatus;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.me.common.GuiTerminalSettings;
import ae2.client.gui.me.items.WirelessUniversalTerminalSelectorWindow;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.ItemStackButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.client.render.overlay.CraftingSupplierHighlightHandler;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.core.network.serverbound.QuickMovePatternPacket;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.util.inv.AppEngInternalInventory;
import com.google.common.collect.HashMultimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GuiPatternAccessTerm<C extends ContainerPatternAccessTerm> extends AEBaseGui<C> implements ITextFieldGui {

    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/ex_pattern_access_terminal.png");
    private static final ResourceLocation EAE_NICONS_TEXTURE = AppEng.makeId("textures/guis/nicons.png");

    private static final int GUI_WIDTH = 195;
    private static final int GUI_PADDING_X = 8;
    private static final int GUI_PADDING_Y = 6;
    private static final int GUI_HEADER_HEIGHT = 30;
    private static final int GUI_FOOTER_HEIGHT = 99;
    private static final int COLUMNS = 9;
    private static final int ROW_HEIGHT = 18;
    private static final int SLOT_SIZE = ROW_HEIGHT;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int PATTERN_PROVIDER_NAME_MARGIN_X = 2;
    private static final int TEXT_MAX_WIDTH = 155;

    private static final Rectangle HEADER_BBOX = new Rectangle(0, 0, GUI_WIDTH, GUI_HEADER_HEIGHT);
    private static final Rectangle ROW_TEXT_TOP_BBOX = new Rectangle(0, 30, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_INVENTORY_TOP_BBOX = new Rectangle(0, 48, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_TEXT_MIDDLE_BBOX = new Rectangle(0, 66, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_INVENTORY_MIDDLE_BBOX = new Rectangle(0, 84, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_TEXT_BOTTOM_BBOX = new Rectangle(0, 102, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle ROW_INVENTORY_BOTTOM_BBOX = new Rectangle(0, 120, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle FOOTER_BBOX = new Rectangle(0, 138, GUI_WIDTH, GUI_FOOTER_HEIGHT);

    private static final Comparator<PatternContainerGroup> GROUP_COMPARATOR = Comparator
        .comparing(group -> group.name().getFormattedText().toLowerCase(Locale.ROOT));

    private final Long2ObjectOpenHashMap<PatternContainerEntry> byId = new Long2ObjectOpenHashMap<>();
    private final HashMultimap<PatternContainerGroup, PatternContainerEntry> byGroup = HashMultimap.create();
    private final List<PatternContainerGroup> groups = new ObjectArrayList<>();
    private final List<Row> rows = new ObjectArrayList<>();
    private final Map<ItemStack, PatternSearchData> patternSearchText = new Reference2ObjectOpenHashMap<>();
    private final Map<Long, PatternProviderInfo> providerInfo = new Long2ObjectOpenHashMap<>();
    private final Set<MatchedPatternSlot> matchedPatternSlots = new HashSet<>();
    private final List<ProviderHighlightButton> providerHighlightButtons = new ObjectArrayList<>();

    private final ITextComponent title;
    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final AETextField providerSearchField;
    private final SearchModeButton searchModeButton;
    private final ServerSettingToggleButton<ShowPatternProviders> showPatternProviders;

    private int visibleRows = MIN_VISIBLE_ROWS;
    private int lastScroll = Integer.MIN_VALUE;
    private String searchText = "";
    private String providerSearchText = "";
    private SearchMode searchMode = SearchMode.IN_OUT;

    public GuiPatternAccessTerm(C container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                                GuiStyle style) {
        super(container, playerInventory, Objects.requireNonNull(style, "style"));
        this.title = title != null ? title : GuiText.PatternAccessTerminalShort.text();

        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setTooltipMessage(Collections.singletonList(this.searchMode.tooltip()));
        this.providerSearchField = this.widgets.addTextField("providerSearch");
        this.providerSearchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.providerSearchField.setTooltipMessage(Collections.singletonList(
            new TextComponentTranslation("gui.ae2.PatternAccessTerminalProviderSearchTooltip")));
        this.searchModeButton = new SearchModeButton(this::cycleSearchMode);
        this.widgets.add("searchMode", this.searchModeButton);

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

    private static void appendOutputs(IPatternDetails pattern, StringBuilder text) {
        for (GenericStack output : pattern.getOutputs()) {
            appendStackName(text, output);
        }
    }

    private int getConfiguredRows() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_VISIBLE_ROWS,
            (availableHeight - GUI_HEADER_HEIGHT - GUI_FOOTER_HEIGHT) / ROW_HEIGHT);
        return Math.max(MIN_VISIBLE_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
    }

    private static void appendInputs(IPatternDetails pattern, StringBuilder text) {
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            for (GenericStack possibleInput : input.possibleInputs()) {
                appendStackName(text, possibleInput);
            }
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

    private static void appendStackName(StringBuilder text, @Nullable GenericStack stack) {
        if (stack == null || stack.what() == null) {
            return;
        }
        text.append(stack.what().getDisplayName().getFormattedText().toLowerCase(Locale.ROOT));
        text.append('\n');
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

    private static void highlightProvider(PatternProviderInfo info) {
        CraftingSupplierHighlightHandler.INSTANCE.showLocations(Minecraft.getMinecraft(), List.of(
            new CraftingSupplierLocation(info.dimensionId(), info.pos().getX(), info.pos().getY(),
                info.pos().getZ())));
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

    private static void highlightProviderAndClose(PatternProviderInfo info) {
        highlightProvider(info);
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.showPatternProviders.set(this.container.getShownProviders());

        String text = this.searchField.getText();
        String providerText = this.providerSearchField.getText();
        if (!this.searchText.equals(text) || !this.providerSearchText.equals(providerText)) {
            this.searchText = text;
            this.providerSearchText = providerText;
            refreshList();
            return;
        }

        int scroll = this.scrollbar.getCurrentScroll();
        if (scroll != this.lastScroll) {
            refreshVisiblePatternSlots();
        }
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
        updateSearchFieldFocus(mouseX, mouseY);

        if (mouseButton == 1 && this.searchField.isMouseOver(mouseX, mouseY)) {
            this.searchField.setText("");
            this.searchText = "";
            refreshList();
        }
        if (mouseButton == 1 && this.providerSearchField.isMouseOver(mouseX, mouseY)) {
            this.providerSearchField.setText("");
            this.providerSearchText = "";
            refreshList();
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void updateSearchFieldFocus(int mouseX, int mouseY) {
        boolean searchClicked = this.searchField.isMouseOver(mouseX, mouseY);
        boolean providerSearchClicked = this.providerSearchField.isMouseOver(mouseX, mouseY);
        if (searchClicked) {
            this.providerSearchField.setFocused(false);
        } else if (providerSearchClicked) {
            this.searchField.setFocused(false);
        }
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
        LongList visiblePatternContainerIds = new LongArrayList();
        LongList visiblePatternSlots = new LongArrayList();
        int scroll = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.visibleRows; i++) {
            if (scroll + i >= this.rows.size()) {
                break;
            }

            Row row = this.rows.get(scroll + i);
            if (row instanceof SlotsRow(PatternContainerEntry container1, int offset, int count)) {
                int end = offset + count;
                for (int slot = offset; slot < end; slot++) {
                    visiblePatternContainerIds.add(container1.getServerId());
                    visiblePatternSlots.add(slot);
                }
            }
        }

        InitNetwork.sendToServer(new QuickMovePatternPacket(
            this.container.windowId,
            clickedSlot.slotNumber,
            visiblePatternContainerIds,
            visiblePatternSlots));
    }

    @Override
    public Slot MT_getSlotUnderMouse() {
        Slot slot = super.MT_getSlotUnderMouse();
        if (slot instanceof GuiPatternSlot patternSlot && !isVisiblePatternSlot(patternSlot)) {
            return null;
        }
        return slot;
    }

    @Override
    public boolean MT_isIgnored(Slot slot) {
        return super.MT_isIgnored(slot)
            || slot instanceof GuiPatternSlot patternSlot && !isVisiblePatternSlot(patternSlot);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        AETextField focusedSearchField = getFocusedSearchField();
        if (keyCode == Keyboard.KEY_ESCAPE && focusedSearchField != null) {
            focusedSearchField.setFocused(false);
            return;
        }

        if (typedChar == ' ' && focusedSearchField != null && focusedSearchField.getText().isEmpty()) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Nullable
    private AETextField getFocusedSearchField() {
        if (this.searchField.isFocused()) {
            return this.searchField;
        }
        if (this.providerSearchField.isFocused()) {
            return this.providerSearchField;
        }
        return null;
    }

    @Override
    public void initGui() {
        clearPatternSlots();
        clearProviderHighlightButtons();

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

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        updateProviderHighlightButtons();
        this.fontRenderer.drawString(getGuiDisplayName(this.title).getFormattedText(), 7, 6, 4210752);

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

    private void drawInvalidPatternOverlays(SlotsRow slotsRow, int rowIndex) {
        var level = this.container.getPlayer().world;
        for (int col = 0; col < slotsRow.slots(); col++) {
            int slotIndex = slotsRow.offset() + col;
            ItemStack pattern = slotsRow.container().getInventory().getStackInSlot(slotIndex);
            int x = GUI_PADDING_X + col * SLOT_SIZE;
            int y = GUI_HEADER_HEIGHT + rowIndex * ROW_HEIGHT + 1;
            if (!this.searchText.trim().isEmpty()) {
                if (this.matchedPatternSlots.contains(new MatchedPatternSlot(
                    slotsRow.container().getServerId(), slotIndex))) {
                    drawGradientRect(x - 1, y - 1, x + 17, y + 17, 0x669CD3FF, 0x669CD3FF);
                } else {
                    drawGradientRect(x, y, x + 16, y + 16, 0x6A000000, 0x6A000000);
                }
            }
            if (!pattern.isEmpty() && PatternDetailsHelper.decodePattern(pattern, level) == null) {
                drawGradientRect(x, y, x + 16, y + 16, 0x7FFF0000, 0x7FFF0000);
            }
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (!this.container.getLinkStatus().connected() && this.getSlotUnderMouse() instanceof GuiPatternSlot) {
            return;
        }

        for (ProviderHighlightButton button : this.providerHighlightButtons) {
            if (button.visible && button.getTooltipArea().contains(mouseX, mouseY)) {
                drawTooltipLines(mouseX, mouseY,
                    button.getTooltipMessage().stream().map(ITextComponent::getFormattedText).toList());
                return;
            }
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

    public void clear() {
        this.byId.clear();
        this.providerInfo.clear();
        this.patternSearchText.clear();
        refreshList();
    }

    public void postProviderInfo(long inventoryId, int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
        this.providerInfo.put(inventoryId, new PatternProviderInfo(dimensionId, pos, face));
        refreshVisiblePatternSlots();
    }

    public void postFullUpdate(long inventoryId, long sortBy, PatternContainerGroup group, int inventorySize,
                               Int2ObjectMap<ItemStack> slots) {
        PatternContainerEntry patternContainer = new PatternContainerEntry(inventoryId, inventorySize, sortBy, group);
        this.byId.put(inventoryId, patternContainer);

        AppEngInternalInventory inventory = patternContainer.getInventory();
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

        AppEngInternalInventory inventory = patternContainer.getInventory();
        for (Int2ObjectMap.Entry<ItemStack> entry : slots.int2ObjectEntrySet()) {
            inventory.setItemDirect(entry.getIntKey(), entry.getValue());
        }

        this.patternSearchText.clear();
        refreshList();
    }

    private void refreshList() {
        this.byGroup.clear();
        this.matchedPatternSlots.clear();

        String patternFilter = this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        String providerFilter = this.providerSearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (PatternContainerEntry entry : this.byId.values()) {
            if (matchesSearch(entry, providerFilter, patternFilter)) {
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

    private boolean matchesSearch(PatternContainerEntry entry, String providerFilter, String patternFilter) {
        if (!providerFilter.isEmpty() && !entry.getSearchName().contains(providerFilter)) {
            return false;
        }

        if (patternFilter.isEmpty()) {
            return true;
        }

        boolean matched = false;
        for (int slot = 0; slot < entry.getInventory().size(); slot++) {
            ItemStack stack = entry.getInventory().getStackInSlot(slot);
            if (itemStackMatchesSearchTerm(stack, patternFilter)) {
                this.matchedPatternSlots.add(new MatchedPatternSlot(entry.getServerId(), slot));
                matched = true;
            }
        }

        return matched;
    }

    private boolean itemStackMatchesSearchTerm(ItemStack stack, String filter) {
        if (stack.isEmpty()) {
            return false;
        }
        PatternSearchData searchData = this.patternSearchText.computeIfAbsent(stack, this::getPatternSearchText);
        return this.searchMode.matches(searchData, filter);
    }

    private void resetScrollbar() {
        this.scrollbar.setHeight(this.visibleRows * ROW_HEIGHT - 2);
        this.scrollbar.setRange(0, Math.max(0, this.rows.size() - this.visibleRows), 2);
    }

    private PatternSearchData getPatternSearchText(ItemStack stack) {
        World level = this.container.getPlayer().world;
        StringBuilder inputs = new StringBuilder();
        StringBuilder outputs = new StringBuilder();
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, level);

        if (pattern != null) {
            appendOutputs(pattern, outputs);
            appendInputs(pattern, inputs);
        }

        return new PatternSearchData(inputs.toString(), outputs.toString());
    }

    private void refreshVisiblePatternSlots() {
        clearPatternSlots();
        clearProviderHighlightButtons();

        int scroll = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.visibleRows; i++) {
            if (scroll + i >= this.rows.size()) {
                break;
            }

            Row row = this.rows.get(scroll + i);
            if (row instanceof SlotsRow(PatternContainerEntry patternContainer, int offset, int slots)) {
                if (offset == 0) {
                    PatternProviderInfo info = this.providerInfo.get(patternContainer.getServerId());
                    if (info != null) {
                        ProviderHighlightButton button = new ProviderHighlightButton(patternContainer.getServerId(),
                            info);
                        button.x = this.guiLeft + GUI_PADDING_X + SLOT_SIZE * COLUMNS - 1;
                        button.y = this.guiTop + GUI_HEADER_HEIGHT + i * ROW_HEIGHT;
                        this.providerHighlightButtons.add(button);
                        this.buttonList.add(button);
                    }
                }
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

    private void updateProviderHighlightButtons() {
        for (ProviderHighlightButton button : this.providerHighlightButtons) {
            button.visible = true;
            button.enabled = true;
        }
    }

    private boolean isVisiblePatternSlot(GuiPatternSlot slot) {
        if (!this.container.isClientSideSlot(slot)) {
            return false;
        }

        int x = slot.xPos - GUI_PADDING_X;
        int y = slot.yPos - GUI_HEADER_HEIGHT - 1;
        if (x < 0 || y < 0 || x % SLOT_SIZE != 0 || y % ROW_HEIGHT != 0) {
            return false;
        }

        int column = x / SLOT_SIZE;
        int visibleRow = y / ROW_HEIGHT;
        if (column < 0 || column >= COLUMNS || visibleRow < 0 || visibleRow >= this.visibleRows) {
            return false;
        }

        int rowIndex = this.scrollbar.getCurrentScroll() + visibleRow;
        if (rowIndex < 0 || rowIndex >= this.rows.size()) {
            return false;
        }

        Row row = this.rows.get(rowIndex);
        if (!(row instanceof SlotsRow(PatternContainerEntry container1, int offset, int slots))) {
            return false;
        }

        return slot.getMachineInv() == container1
            && slot.getSlotIndex() >= offset
            && slot.getSlotIndex() < offset + slots;
    }

    private void clearPatternSlots() {
        for (int i = this.container.inventorySlots.size() - 1; i >= 0; i--) {
            Slot slot = this.container.inventorySlots.get(i);
            if (slot instanceof GuiPatternSlot && this.container.isClientSideSlot(slot)) {
                this.container.removeClientSideSlot(slot);
            }
        }
    }

    private void clearProviderHighlightButtons() {
        if (this.providerHighlightButtons.isEmpty()) {
            return;
        }
        this.buttonList.removeAll(this.providerHighlightButtons);
        this.providerHighlightButtons.clear();
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
        return List.of(this.searchField, this.providerSearchField);
    }

    private interface Row {
    }

    private record GroupHeaderRow(PatternContainerGroup group) implements Row {
    }

    private record SlotsRow(PatternContainerEntry container, int offset, int slots) implements Row {
    }

    private void cycleSearchMode() {
        this.searchMode = this.searchMode.next();
        this.searchModeButton.updateLabel();
        this.searchField.setTooltipMessage(Collections.singletonList(this.searchMode.tooltip()));
        this.patternSearchText.clear();
        refreshList();
    }

    private enum SearchMode {
        OUT("gui.ae2.PatternAccessTerminalSearchModeOutput",
            "gui.ae2.PatternAccessTerminalSearchTooltipOutput") {
            @Override
            boolean matches(PatternSearchData data, String filter) {
                return data.outputs().contains(filter);
            }
        },
        IN("gui.ae2.PatternAccessTerminalSearchModeInput",
            "gui.ae2.PatternAccessTerminalSearchTooltipInput") {
            @Override
            boolean matches(PatternSearchData data, String filter) {
                return data.inputs().contains(filter);
            }
        },
        IN_OUT("gui.ae2.PatternAccessTerminalSearchModeInputOutput",
            "gui.ae2.PatternAccessTerminalSearchTooltipInputOutput") {
            @Override
            boolean matches(PatternSearchData data, String filter) {
                return data.inputs().contains(filter) || data.outputs().contains(filter);
            }
        };

        private final String titleKey;
        private final String tooltipKey;

        SearchMode(String titleKey, String tooltipKey) {
            this.titleKey = titleKey;
            this.tooltipKey = tooltipKey;
        }

        abstract boolean matches(PatternSearchData data, String filter);

        SearchMode next() {
            SearchMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        ITextComponent title() {
            return new TextComponentTranslation(this.titleKey);
        }

        ITextComponent tooltip() {
            return new TextComponentTranslation(this.tooltipKey);
        }
    }

    private record PatternSearchData(String inputs, String outputs) {
    }

    private record PatternProviderInfo(int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
    }

    private record MatchedPatternSlot(long inventoryId, int slot) {
    }

    private final class ProviderHighlightButton extends GuiButton implements ITooltip {
        private final long inventoryId;
        private final PatternProviderInfo info;

        ProviderHighlightButton(long inventoryId, PatternProviderInfo info) {
            super(0, 0, 0, 6, 11, "");
            this.inventoryId = inventoryId;
            this.info = info;
        }

        @Override
        public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
            if (super.mousePressed(minecraft, mouseX, mouseY)) {
                if (GuiScreen.isShiftKeyDown()) {
                    GuiPatternAccessTerm.this.container.openPatternProvider(this.inventoryId);
                    return true;
                }
                highlightProviderAndClose(this.info);
                return true;
            }
            return false;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                && mouseY < this.y + this.height;

            minecraft.getTextureManager().bindTexture(EAE_NICONS_TEXTURE);
            GlStateManager.color(1.0F, 1.0F, 1.0F, this.enabled ? 1.0F : 0.5F);
            Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, 48, 32, this.width, this.height, 64, 64);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            String dimensionName = CraftingSupplierLocator.getDimensionName(this.info.dimensionId());
            return List.of(
                new TextComponentTranslation("gui.ae2.PatternAccessTerminalHighlightProvider"),
                new TextComponentTranslation("gui.ae2.PatternAccessTerminalOpenProvider"),
                new TextComponentString(this.info.pos().getX() + " " + this.info.pos().getY() + " "
                    + this.info.pos().getZ() + " (" + dimensionName + ")"));
        }

        @Override
        public Rectangle getTooltipArea() {
            return new Rectangle(this.x, this.y, this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return this.visible;
        }
    }

    private final class SearchModeButton extends GuiButton implements ITooltip {
        private final Runnable onPress;

        SearchModeButton(Runnable onPress) {
            super(0, 0, 0, 12, 12, "");
            this.onPress = onPress;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            super.mouseReleased(mouseX, mouseY);
            boolean releasedInside = this.enabled && this.visible
                && mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            if (releasedInside) {
                this.onPress.run();
            }
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
                && mouseY < this.y + this.height;

            minecraft.getTextureManager().bindTexture(EAE_NICONS_TEXTURE);
            GlStateManager.color(1.0F, 1.0F, 1.0F, this.enabled ? 1.0F : 0.5F);
            int yOffset = this.hovered ? 1 : 0;
            Gui.drawModalRectWithCustomSizedTexture(this.x, this.y + yOffset, getBackgroundU(), 16,
                this.width, this.height, 64, 64);
            Gui.drawModalRectWithCustomSizedTexture(this.x, this.y + yOffset, getIconU(), getIconV(),
                this.width, this.height, 64, 64);
        }

        private int getBackgroundU() {
            if (this.hovered) {
                return 40;
            }
            return 16;
        }

        private int getIconU() {
            return switch (GuiPatternAccessTerm.this.searchMode) {
                case OUT -> 16;
                case IN -> 0;
                case IN_OUT -> 32;
            };
        }

        private int getIconV() {
            return 48;
        }

        void updateLabel() {
            // The visible state is derived from searchMode in drawButton.
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return List.of(
                new TextComponentTranslation("gui.ae2.PatternAccessTerminalSearchMode"),
                GuiPatternAccessTerm.this.searchMode.title());
        }

        @Override
        public Rectangle getTooltipArea() {
            return new Rectangle(this.x, this.y, this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return this.visible;
        }
    }
}

