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
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.PatternContainerExternalGuiReturnHandler;
import ae2.client.gui.PreviousExternalGui;
import ae2.client.gui.me.common.GuiTerminalSettings;
import ae2.client.gui.me.items.WirelessUniversalTerminalSelectorWindow;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ConfirmableTextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.ItemStackButton;
import ae2.client.gui.widgets.PatternModifierPanelWidget;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.SmallSquareButtonRenderer;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.client.render.overlay.CraftingSupplierHighlightHandler;
import ae2.client.render.overlay.OverlayHighlightLocation;
import ae2.client.render.overlay.OverlayHighlightShape;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerPatternAccessTerm;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.core.network.serverbound.QuickMovePatternPacket;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.helpers.InventoryAction;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.helpers.patternmodifier.PatternModifierToolboxLayout;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.util.inv.AppEngInternalInventory;
import com.google.common.collect.HashMultimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
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

    private static final int GUI_WIDTH = 213;
    private static final int GUI_PADDING_X = 17;
    private static final int GUI_PADDING_Y = 6;
    private static final int GUI_HEADER_HEIGHT = 30;
    private static final int GUI_FOOTER_HEIGHT = 99;
    private static final int COLUMNS = 9;
    private static final int ROW_HEIGHT = 18;
    private static final int SLOT_SIZE = ROW_HEIGHT;
    private static final int SEARCH_FIELD_WIDTH = 65;
    private static final int CONTENT_RIGHT_X = GUI_PADDING_X + COLUMNS * SLOT_SIZE;
    private static final int RIGHT_SEARCH_FIELD_LEFT = CONTENT_RIGHT_X - SEARCH_FIELD_WIDTH;
    private static final int HEADER_TITLE_RIGHT_GAP = 4;
    private static final int HEADER_TITLE_MAX_WIDTH = RIGHT_SEARCH_FIELD_LEFT - GUI_PADDING_X - HEADER_TITLE_RIGHT_GAP;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int PATTERN_PROVIDER_NAME_MARGIN_X = 2;
    private static final int TEXT_MAX_WIDTH = 173;
    private static final int ROW_ACTION_BUTTON_WIDTH = 12;
    private static final int ROW_ACTION_BUTTON_HEIGHT = 12;
    private static final int ROW_ACTION_BUTTON_X = -14;
    private static final int ROW_ACTION_BUTTON_Y_OFFSET = 3;
    private static final int RENAME_FIELD_X_OFFSET = 10;
    private static final int RENAME_FIELD_Y_OFFSET = 3;
    private static final int RENAME_FIELD_HEIGHT = 12;
    private static final int GROUP_RENAME_FIELD_X_OFFSET = 16;
    private static final int GROUP_RENAME_FIELD_Y_OFFSET = 3;
    private static final int GROUP_RENAME_FIELD_HEIGHT = 12;
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

    private final Long2ObjectMap<PatternContainerEntry> byId = new Long2ObjectOpenHashMap<>();
    private final HashMultimap<PatternContainerGroup, PatternContainerEntry> byGroup = HashMultimap.create();
    private final ObjectArrayList<PatternContainerGroup> groups = new ObjectArrayList<>();
    private final ObjectArrayList<Row> rows = new ObjectArrayList<>();
    private final Map<ItemStack, PatternSearchData> patternSearchText = new Reference2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PatternProviderInfo> providerInfo = new Long2ObjectOpenHashMap<>();
    private final Set<MatchedPatternSlot> matchedPatternSlots = new HashSet<>();
    private final ObjectArrayList<ProviderActionButton> providerActionButtons = new ObjectArrayList<>();

    private final ITextComponent title;
    private final Scrollbar scrollbar;
    private final AETextField groupSearchField;
    private final AETextField inputSearchField;
    private final AETextField outputSearchField;
    private final ServerSettingToggleButton<ShowPatternProviders> showPatternProviders;
    private final PatternModifierPanelWidget patternModifierPanel;

    private int visibleRows = MIN_VISIBLE_ROWS;
    private int lastScroll = Integer.MIN_VALUE;
    private String groupSearchText = "";
    private String inputSearchText = "";
    private String outputSearchText = "";
    @Nullable
    private ConfirmableTextField activeRenameField;
    private long activeRenameContainerId = Long.MIN_VALUE;
    private String activeRenameOriginalName = "";
    @Nullable
    private ConfirmableTextField activeGroupRenameField;
    @Nullable
    private PatternContainerGroup activeRenameGroup;
    private String activeGroupRenameOriginalName = "";

    public GuiPatternAccessTerm(C container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                                GuiStyle style) {
        super(container, playerInventory, Objects.requireNonNull(style, "style"));
        this.title = title != null ? title : GuiText.PatternAccessTerminalShort.text();

        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.groupSearchField = this.widgets.addTextField("search");
        this.groupSearchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.groupSearchField.setTooltipMessage(Collections.singletonList(
            GuiText.PatternAccessTerminalProviderSearchTooltip.text()));
        this.inputSearchField = this.widgets.addTextField("inputSearch");
        this.inputSearchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.inputSearchField.setTooltipMessage(Collections.singletonList(
            GuiText.PatternAccessTerminalSearchTooltipInput.text()));
        this.outputSearchField = this.widgets.addTextField("outputSearch");
        this.outputSearchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.outputSearchField.setTooltipMessage(Collections.singletonList(
            GuiText.PatternAccessTerminalSearchTooltipOutput.text()));
        this.patternModifierPanel = new PatternModifierPanelWidget(this, new PatternAccessPanelHost());
        this.patternModifierPanel.addButtons();

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
        addToLeftToolbar(this.patternModifierPanel.getToolbarButton());

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

    private static void appendOutputs(IPatternDetails pattern, StringBuilder text) {
        for (GenericStack output : pattern.getOutputs()) {
            appendStackName(text, output);
        }
    }

    private static void appendInputs(IPatternDetails pattern, StringBuilder text) {
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            for (GenericStack possibleInput : input.possibleInputs()) {
                appendStackName(text, possibleInput);
            }
        }
    }

    private static void appendStackName(StringBuilder text, @Nullable GenericStack stack) {
        if (stack == null || stack.what() == null) {
            return;
        }
        text.append(stack.what().getDisplayName().getFormattedText().toLowerCase(Locale.ROOT));
        text.append('\n');
    }

    private static void highlightProvider(PatternProviderInfo info) {
        OverlayHighlightShape shape = info.face() == null
            ? OverlayHighlightShape.WHOLE_BLOCK
            : OverlayHighlightShape.PATTERN_PROVIDER;
        CraftingSupplierHighlightHandler.INSTANCE.showHighlightLocations(Minecraft.getMinecraft(), List.of(
            new OverlayHighlightLocation(info.dimensionId(), info.pos(), info.face(), shape)));
    }

    private static void highlightProviderAndClose(PatternProviderInfo info) {
        highlightProvider(info);
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    private static void applySlotUpdates(AppEngInternalInventory inventory, Int2ObjectMap<ItemStack> slots) {
        for (Int2ObjectMap.Entry<ItemStack> entry : slots.int2ObjectEntrySet()) {
            int slot = entry.getIntKey();
            if (slot >= 0 && slot < inventory.size()) {
                inventory.setItemDirect(slot, entry.getValue());
            }
        }
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

    private int getConfiguredRows() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_VISIBLE_ROWS,
            (availableHeight - GUI_HEADER_HEIGHT - GUI_FOOTER_HEIGHT) / ROW_HEIGHT);
        return Math.max(MIN_VISIBLE_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
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
        this.patternModifierPanel.drawBackground();
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
        boolean editingThisGroup = this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()
            && Objects.equals(this.activeRenameGroup, group);
        String displayName = editingThisGroup ? "" : group.name().getFormattedText();
        String countText = entries > 1 ? " (" + entries + ")" : "";
        int textX = GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X + 18;
        int textY = GUI_HEADER_HEIGHT + GUI_PADDING_Y + rowIndex * ROW_HEIGHT;
        int countWidth = this.fontRenderer.getStringWidth(countText);
        int availableNameWidth = Math.max(0, TEXT_MAX_WIDTH - 18 - countWidth);
        displayName = this.fontRenderer.trimStringToWidth(displayName, availableNameWidth);
        this.fontRenderer.drawString(
            displayName,
            textX,
            textY,
            4210752);
        if (!countText.isEmpty()) {
            this.fontRenderer.drawString(
                countText,
                textX + this.fontRenderer.getStringWidth(displayName),
                textY,
                4210752);
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
    public void updateScreen() {
        super.updateScreen();

        if (this.activeRenameField != null && this.activeRenameField.getVisible()) {
            this.activeRenameField.updateCursorCounter();
            this.activeRenameField.tickKeyRepeat();
        }
        if (this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()) {
            this.activeGroupRenameField.updateCursorCounter();
            this.activeGroupRenameField.tickKeyRepeat();
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.showPatternProviders.set(this.container.getShownProviders());
        this.patternModifierPanel.update();

        String groupText = this.groupSearchField.getText();
        String inputText = this.inputSearchField.getText();
        String outputText = this.outputSearchField.getText();
        if (!this.groupSearchText.equals(groupText) || !this.inputSearchText.equals(inputText)
            || !this.outputSearchText.equals(outputText)) {
            this.groupSearchText = groupText;
            this.inputSearchText = inputText;
            this.outputSearchText = outputText;
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
        if (x >= GUI_WIDTH - GUI_PADDING_X || y >= this.visibleRows * ROW_HEIGHT) {
            return -1;
        }

        int rowIndex = this.scrollbar.getCurrentScroll() + y / ROW_HEIGHT;
        return rowIndex >= 0 && rowIndex < this.rows.size() ? rowIndex : -1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSelectionPopupMouseClicked(mouseX, mouseY)) {
            return;
        }

        if (this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()
            && this.activeGroupRenameField.isMouseOver(mouseX, mouseY)) {
            clearSearchFieldOnRightClick(this.activeGroupRenameField, mouseX, mouseY, mouseButton);
            this.activeGroupRenameField.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (this.activeRenameField != null && this.activeRenameField.getVisible()
            && this.activeRenameField.isMouseOver(mouseX, mouseY)) {
            clearSearchFieldOnRightClick(this.activeRenameField, mouseX, mouseY, mouseButton);
            this.activeRenameField.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        finishActiveGroupRename(true);
        finishActiveRename(true);
        updateSearchFieldFocus(mouseX, mouseY);

        clearSearchFieldOnRightClick(this.groupSearchField, mouseX, mouseY, mouseButton);
        clearSearchFieldOnRightClick(this.inputSearchField, mouseX, mouseY, mouseButton);
        clearSearchFieldOnRightClick(this.outputSearchField, mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            PatternContainerGroup clickedGroup = getClickedEditableGroupHeader(mouseX, mouseY);
            if (clickedGroup != null) {
                openGroupRenameField(clickedGroup);
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void clearSearchFieldOnRightClick(AETextField textField, int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 1 && textField.isMouseOver(mouseX, mouseY)) {
            textField.setText("");
            refreshList();
        }
    }

    private void updateSearchFieldFocus(int mouseX, int mouseY) {
        boolean groupClicked = this.groupSearchField.isMouseOver(mouseX, mouseY);
        boolean inputClicked = this.inputSearchField.isMouseOver(mouseX, mouseY);
        boolean outputClicked = this.outputSearchField.isMouseOver(mouseX, mouseY);
        if (groupClicked) {
            this.inputSearchField.setFocused(false);
            this.outputSearchField.setFocused(false);
        } else if (inputClicked) {
            this.groupSearchField.setFocused(false);
            this.outputSearchField.setFocused(false);
        } else if (outputClicked) {
            this.groupSearchField.setFocused(false);
            this.inputSearchField.setFocused(false);
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
    protected Slot findSlot(int mouseX, int mouseY) {
        if (isMouseOverActiveRenameField(mouseX, mouseY)) {
            return null;
        }
        return super.findSlot(mouseX, mouseY);
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

    private boolean isMouseOverActiveRenameField(int mouseX, int mouseY) {
        return (this.activeRenameField != null && this.activeRenameField.getVisible()
            && this.activeRenameField.isMouseOver(mouseX, mouseY))
            || (this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()
            && this.activeGroupRenameField.isMouseOver(mouseX, mouseY));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()
            && this.activeGroupRenameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                finishActiveGroupRename(true);
                return;
            }
            if (this.activeGroupRenameField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        if (this.activeRenameField != null && this.activeRenameField.getVisible() && this.activeRenameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                finishActiveRename(true);
                return;
            }
            if (this.activeRenameField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }

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

    @Override
    public void onGuiClosed() {
        finishActiveGroupRename(true);
        finishActiveRename(true);
        super.onGuiClosed();
    }

    @Nullable
    private AETextField getFocusedSearchField() {
        if (this.groupSearchField.isFocused()) {
            return this.groupSearchField;
        }
        if (this.inputSearchField.isFocused()) {
            return this.inputSearchField;
        }
        if (this.outputSearchField.isFocused()) {
            return this.outputSearchField;
        }
        return null;
    }

    @Override
    public void initGui() {
        clearPatternSlots();
        clearProviderActionButtons();

        this.visibleRows = getConfiguredRows();
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEADER_HEIGHT + GUI_FOOTER_HEIGHT + this.visibleRows * ROW_HEIGHT;

        super.initGui();

        if (AEConfig.instance().isAutoFocusSearch()) {
            setInitialFocus(this.groupSearchField);
        }

        resetScrollbar();
        refreshVisiblePatternSlots();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        updateRowActionButtons();
        String titleText = this.fontRenderer.trimStringToWidth(
            getGuiDisplayName(this.title).getFormattedText(),
            HEADER_TITLE_MAX_WIDTH);
        this.fontRenderer.drawString(titleText, GUI_PADDING_X, GUI_PADDING_Y, 4210752);

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

        drawActiveRenameField();
        drawActiveGroupRenameField();
        renderLinkStatus(this.container.getLinkStatus());
    }

    private void drawInvalidPatternOverlays(SlotsRow slotsRow, int rowIndex) {
        var level = this.container.getPlayer().world;
        for (int col = 0; col < slotsRow.slots(); col++) {
            int slotIndex = slotsRow.offset() + col;
            ItemStack pattern = slotsRow.container().getInventory().getStackInSlot(slotIndex);
            int x = GUI_PADDING_X + col * SLOT_SIZE;
            int y = GUI_HEADER_HEIGHT + rowIndex * ROW_HEIGHT + 1;
            if (hasActivePatternFilter()) {
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
        if (isMouseOverActiveRenameField(mouseX, mouseY)) {
            return;
        }
        if (!this.container.getLinkStatus().connected() && this.getSlotUnderMouse() instanceof GuiPatternSlot) {
            return;
        }

        for (ProviderActionButton button : this.providerActionButtons) {
            if (button.visible && button.getTooltipArea().contains(mouseX, mouseY)) {
                var tooltipMessage = button.getTooltipMessage();
                var tooltipLines = new ObjectArrayList<String>(tooltipMessage.size());
                for (ITextComponent component : tooltipMessage) {
                    tooltipLines.add(component.getFormattedText());
                }
                drawTooltipLines(mouseX, mouseY, tooltipLines);
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
        finishActiveGroupRename(false);
        finishActiveRename(false);
        this.byId.clear();
        this.providerInfo.clear();
        this.patternSearchText.clear();
        refreshList();
    }

    public void postProviderInfo(long inventoryId, int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
        this.providerInfo.put(inventoryId, new PatternProviderInfo(dimensionId, pos, face));
        refreshVisiblePatternSlots();
    }

    public void postFullUpdate(long inventoryId, long sortBy, boolean canEditTerminalName,
                               boolean canModifyTerminalVisibility, PatternContainerGroup group,
                               int inventorySize, Int2ObjectMap<ItemStack> slots) {
        if (group == null) {
            return;
        }
        PatternContainerEntry patternContainer = new PatternContainerEntry(inventoryId, inventorySize, sortBy,
            canEditTerminalName, canModifyTerminalVisibility, group);
        this.byId.put(inventoryId, patternContainer);

        AppEngInternalInventory inventory = patternContainer.getInventory();
        applySlotUpdates(inventory, slots);

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
        applySlotUpdates(inventory, slots);

        this.patternSearchText.clear();
        refreshList();
    }

    private void refreshList() {
        this.byGroup.clear();
        this.matchedPatternSlots.clear();

        String groupFilter = this.groupSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String inputFilter = this.inputSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String outputFilter = this.outputSearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (PatternContainerEntry entry : this.byId.values()) {
            if (matchesSearch(entry, groupFilter, inputFilter, outputFilter)) {
                this.byGroup.put(entry.getGroup(), entry);
            }
        }

        int groupCount = this.byGroup.keySet().size();
        int rowCapacity = groupCount;
        for (PatternContainerEntry container : this.byGroup.values()) {
            rowCapacity += (container.getInventory().size() + COLUMNS - 1) / COLUMNS;
        }

        this.groups.clear();
        this.groups.ensureCapacity(groupCount);
        this.groups.addAll(this.byGroup.keySet());
        this.groups.sort(GROUP_COMPARATOR);

        this.rows.clear();
        this.rows.ensureCapacity(rowCapacity);
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

    private boolean matchesSearch(PatternContainerEntry entry, String groupFilter, String inputFilter,
                                  String outputFilter) {
        if (!groupFilter.isEmpty() && !entry.getSearchName().contains(groupFilter)) {
            return false;
        }

        if (inputFilter.isEmpty() && outputFilter.isEmpty()) {
            return true;
        }

        boolean inputMatched = inputFilter.isEmpty();
        boolean outputMatched = outputFilter.isEmpty();
        Set<MatchedPatternSlot> matchedSlots = new HashSet<>();
        for (int slot = 0; slot < entry.getInventory().size(); slot++) {
            ItemStack stack = entry.getInventory().getStackInSlot(slot);
            boolean slotInputMatched = stackMatchesInputFilter(stack, inputFilter);
            boolean slotOutputMatched = stackMatchesOutputFilter(stack, outputFilter);
            if (slotInputMatched || slotOutputMatched) {
                matchedSlots.add(new MatchedPatternSlot(entry.getServerId(), slot));
            }
            inputMatched |= slotInputMatched;
            outputMatched |= slotOutputMatched;
        }

        if (!inputMatched || !outputMatched) {
            return false;
        }

        this.matchedPatternSlots.addAll(matchedSlots);
        return true;
    }

    private boolean stackMatchesInputFilter(ItemStack stack, String inputFilter) {
        if (stack.isEmpty()) {
            return false;
        }
        if (inputFilter.isEmpty()) {
            return true;
        }
        PatternSearchData searchData = this.patternSearchText.computeIfAbsent(stack, this::getPatternSearchText);
        return searchData.inputs().contains(inputFilter);
    }

    private boolean stackMatchesOutputFilter(ItemStack stack, String outputFilter) {
        if (stack.isEmpty()) {
            return false;
        }
        if (outputFilter.isEmpty()) {
            return true;
        }
        PatternSearchData searchData = this.patternSearchText.computeIfAbsent(stack, this::getPatternSearchText);
        return searchData.outputs().contains(outputFilter);
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
        clearProviderActionButtons();

        int scroll = this.scrollbar.getCurrentScroll();
        this.providerActionButtons.ensureCapacity(Math.clamp(this.rows.size() - scroll, 0, this.visibleRows));
        boolean renameFieldVisible = false;
        boolean groupRenameFieldVisible = false;
        for (int i = 0; i < this.visibleRows; i++) {
            if (scroll + i >= this.rows.size()) {
                break;
            }

            Row row = this.rows.get(scroll + i);
            if (row instanceof GroupHeaderRow(PatternContainerGroup group)) {
                if (Objects.equals(this.activeRenameGroup, group)) {
                    moveActiveGroupRenameField(group, i);
                    groupRenameFieldVisible = true;
                }
                continue;
            }
            if (row instanceof SlotsRow(PatternContainerEntry patternContainer, int offset, int slots)) {
                if (offset == 0) {
                    PatternProviderInfo info = this.providerInfo.get(patternContainer.getServerId());
                    if (info != null || isEditable(patternContainer)) {
                        ProviderActionButton button = new ProviderActionButton(patternContainer, info);
                        button.x = this.guiLeft + GUI_PADDING_X + ROW_ACTION_BUTTON_X;
                        button.y = this.guiTop + GUI_HEADER_HEIGHT + i * ROW_HEIGHT + ROW_ACTION_BUTTON_Y_OFFSET;
                        this.providerActionButtons.add(button);
                        this.buttonList.add(button);
                    }

                    if (this.activeRenameContainerId == patternContainer.getServerId()) {
                        moveActiveRenameField(i);
                        renameFieldVisible = true;
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

        if (!groupRenameFieldVisible) {
            finishActiveGroupRename(true);
        }
        if (!renameFieldVisible) {
            finishActiveRename(true);
        }

        this.lastScroll = scroll;
    }

    private void updateRowActionButtons() {
        for (ProviderActionButton button : this.providerActionButtons) {
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

    private void clearProviderActionButtons() {
        if (this.providerActionButtons.isEmpty()) {
            return;
        }
        this.buttonList.removeAll(this.providerActionButtons);
        this.providerActionButtons.clear();
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
        if (this.activeRenameField != null && this.activeRenameField.getVisible()
            && this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()) {
            return List.of(this.groupSearchField, this.inputSearchField, this.outputSearchField, this.activeRenameField,
                this.activeGroupRenameField);
        }
        if (this.activeRenameField != null && this.activeRenameField.getVisible()) {
            return List.of(this.groupSearchField, this.inputSearchField, this.outputSearchField, this.activeRenameField);
        }
        if (this.activeGroupRenameField != null && this.activeGroupRenameField.getVisible()) {
            return List.of(this.groupSearchField, this.inputSearchField, this.outputSearchField,
                this.activeGroupRenameField);
        }
        return List.of(this.groupSearchField, this.inputSearchField, this.outputSearchField);
    }

    private boolean hasActivePatternFilter() {
        return !this.inputSearchField.getText().trim().isEmpty() || !this.outputSearchField.getText().trim().isEmpty();
    }

    private interface Row {
    }

    private record GroupHeaderRow(PatternContainerGroup group) implements Row {
    }

    private record SlotsRow(PatternContainerEntry container, int offset, int slots) implements Row {
    }

    private record PatternSearchData(String inputs, String outputs) {
    }

    private record PatternProviderInfo(int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
    }

    private record MatchedPatternSlot(long inventoryId, int slot) {
    }

    private boolean isEditable(PatternContainerEntry entry) {
        return entry.canEditTerminalName();
    }

    @Nullable
    private PatternContainerGroup getClickedEditableGroupHeader(int mouseX, int mouseY) {
        int hoveredLine = getHoveredLineIndex(mouseX, mouseY);
        if (hoveredLine < 0) {
            return null;
        }
        Row row = this.rows.get(hoveredLine);
        if (!(row instanceof GroupHeaderRow(PatternContainerGroup group)) || !canRenameGroup(group)) {
            return null;
        }
        int visibleRow = hoveredLine - this.scrollbar.getCurrentScroll();
        if (visibleRow < 0 || visibleRow >= this.visibleRows) {
            return null;
        }
        if (!getGroupRenameFieldBounds(group, visibleRow).contains(mouseX, mouseY)) {
            return null;
        }
        return group;
    }

    private boolean canRenameGroup(PatternContainerGroup group) {
        for (PatternContainerEntry entry : this.byGroup.get(group)) {
            if (entry.canEditTerminalName()) {
                return true;
            }
        }
        return false;
    }

    private long[] getEditableGroupServerIds(PatternContainerGroup group) {
        LongList ids = new LongArrayList();
        for (PatternContainerEntry entry : this.byGroup.get(group)) {
            if (entry.canEditTerminalName()) {
                ids.add(entry.getServerId());
            }
        }
        return ids.toLongArray();
    }

    private void openGroupRenameField(PatternContainerGroup group) {
        if (this.activeGroupRenameField != null && Objects.equals(this.activeRenameGroup, group)) {
            this.activeGroupRenameField.setFocused(true);
            this.activeGroupRenameField.selectAll();
            return;
        }

        finishActiveRename(true);
        finishActiveGroupRename(true);

        long[] inventoryIds = getEditableGroupServerIds(group);
        if (inventoryIds.length == 0) {
            return;
        }

        this.activeRenameGroup = group;
        this.activeGroupRenameOriginalName = group.name().getFormattedText();
        ConfirmableTextField field = new ConfirmableTextField(this.style, this.fontRenderer, 0, 0, 0, 0);
        field.setMaxStringLength(32);
        field.setOnConfirm(() -> {
            field.setFocused(false);
            finishActiveGroupRename(true);
        });
        this.activeGroupRenameField = field;
        refreshVisiblePatternSlots();
        field.setText(this.activeGroupRenameOriginalName);
        field.setVisible(true);
        field.setFocused(true);
        field.selectAll();
    }

    private void moveActiveGroupRenameField(PatternContainerGroup group, int visibleRow) {
        if (this.activeGroupRenameField == null) {
            return;
        }

        GroupRenameFieldLayout layout = getGroupRenameFieldLayout(group, visibleRow);
        this.activeGroupRenameField.move(layout.x(), layout.y());
        this.activeGroupRenameField.resize(layout.width(), GROUP_RENAME_FIELD_HEIGHT);
        this.activeGroupRenameField.setVisible(true);
    }

    private GroupRenameFieldLayout getGroupRenameFieldLayout(PatternContainerGroup group, int visibleRow) {
        int entries = this.byGroup.get(group).size();
        String countText = entries > 1 ? " (" + entries + ")" : "";
        int countWidth = this.fontRenderer.getStringWidth(countText);
        int x = GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X + GROUP_RENAME_FIELD_X_OFFSET + this.guiLeft;
        int y = GUI_HEADER_HEIGHT + visibleRow * ROW_HEIGHT + GROUP_RENAME_FIELD_Y_OFFSET + this.guiTop;
        return new GroupRenameFieldLayout(x, y, Math.max(24, TEXT_MAX_WIDTH - 18 - countWidth));
    }

    private Rectangle getGroupRenameFieldBounds(PatternContainerGroup group, int visibleRow) {
        GroupRenameFieldLayout layout = getGroupRenameFieldLayout(group, visibleRow);
        return new Rectangle(
            layout.x() - 2,
            layout.y() - 2,
            layout.width() + 4 + this.fontRenderer.getStringWidth("_"),
            GROUP_RENAME_FIELD_HEIGHT + 4);
    }

    private void finishActiveGroupRename(boolean apply) {
        if (this.activeGroupRenameField == null || this.activeRenameGroup == null) {
            return;
        }

        String newName = this.activeGroupRenameField.getText();
        boolean changed = !Objects.equals(newName, this.activeGroupRenameOriginalName);
        if (apply && changed) {
            this.container.renamePatternGroup(getEditableGroupServerIds(this.activeRenameGroup), newName);
        }

        this.activeGroupRenameField.setFocused(false);
        this.activeGroupRenameField.setVisible(false);
        this.activeGroupRenameField = null;
        this.activeRenameGroup = null;
        this.activeGroupRenameOriginalName = "";
    }

    private void openRenameField(PatternContainerEntry entry) {
        if (this.activeRenameField != null && this.activeRenameContainerId == entry.getServerId()) {
            this.activeRenameField.setFocused(true);
            this.activeRenameField.selectAll();
            return;
        }

        finishActiveGroupRename(true);
        finishActiveRename(true);

        this.activeRenameContainerId = entry.getServerId();
        this.activeRenameOriginalName = entry.getGroup().name().getFormattedText();
        ConfirmableTextField field = new ConfirmableTextField(this.style, this.fontRenderer, 0, 0, 0, 0);
        field.setMaxStringLength(32);
        field.setOnConfirm(() -> {
            field.setFocused(false);
            finishActiveRename(true);
        });
        this.activeRenameField = field;
        refreshVisiblePatternSlots();
        field.setText(this.activeRenameOriginalName);
        field.setVisible(true);
        field.setFocused(true);
        field.selectAll();
    }

    private void moveActiveRenameField(int visibleRow) {
        if (this.activeRenameField == null) {
            return;
        }

        int width = SLOT_SIZE * COLUMNS - 2 * RENAME_FIELD_X_OFFSET;
        this.activeRenameField.move(
            this.guiLeft + GUI_PADDING_X + RENAME_FIELD_X_OFFSET,
            this.guiTop + GUI_HEADER_HEIGHT + visibleRow * ROW_HEIGHT + RENAME_FIELD_Y_OFFSET);
        this.activeRenameField.resize(width, RENAME_FIELD_HEIGHT);
        this.activeRenameField.setVisible(true);
    }

    private void drawActiveRenameField() {
        if (this.activeRenameField == null || !this.activeRenameField.getVisible()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-this.guiLeft, -this.guiTop, 0.0F);
        try {
            this.activeRenameField.drawTextBox();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawActiveGroupRenameField() {
        if (this.activeGroupRenameField == null || !this.activeGroupRenameField.getVisible()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-this.guiLeft, -this.guiTop, 0.0F);
        try {
            this.activeGroupRenameField.drawTextBox();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void finishActiveRename(boolean apply) {
        if (this.activeRenameField == null) {
            return;
        }

        String newName = this.activeRenameField.getText();
        boolean changed = !Objects.equals(newName, this.activeRenameOriginalName);
        if (apply && changed) {
            this.container.renamePatternProvider(this.activeRenameContainerId, newName);
        }

        this.activeRenameField.setFocused(false);
        this.activeRenameField.setVisible(false);
        this.activeRenameField = null;
        this.activeRenameContainerId = Long.MIN_VALUE;
        this.activeRenameOriginalName = "";
    }

    private record GroupRenameFieldLayout(int x, int y, int width) {
    }

    private final class ProviderActionButton extends GuiButton implements ITooltip {
        private final PatternContainerEntry entry;
        @Nullable
        private final PatternProviderInfo info;

        ProviderActionButton(PatternContainerEntry entry, @Nullable PatternProviderInfo info) {
            super(0, 0, 0, ROW_ACTION_BUTTON_WIDTH, ROW_ACTION_BUTTON_HEIGHT, "");
            this.entry = entry;
            this.info = info;
        }

        private boolean canToggleVisibility() {
            return this.entry.canModifyTerminalVisibility();
        }

        @Override
        public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
            if (super.mousePressed(minecraft, mouseX, mouseY)) {
                if (GuiScreen.isCtrlKeyDown() && isEditable(this.entry)) {
                    openRenameField(this.entry);
                    return true;
                }
                if (GuiScreen.isAltKeyDown() && canToggleVisibility()) {
                    GuiPatternAccessTerm.this.container.togglePatternProviderVisibility(this.entry.getServerId());
                    return true;
                }
                if (GuiScreen.isShiftKeyDown() && this.info != null) {
                    PreviousExternalGui.capture(GuiPatternAccessTerm.this);
                    PatternContainerExternalGuiReturnHandler.INSTANCE.expectPatternContainerGui();
                    GuiPatternAccessTerm.this.container.openPatternProvider(this.entry.getServerId());
                    return true;
                }
                if (this.info != null) {
                    highlightProviderAndClose(this.info);
                }
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
            SmallSquareButtonRenderer.drawBackground(this.x, this.y, this.width, this.height, this.hovered);
            Icon icon;
            if (GuiScreen.isCtrlKeyDown() && isEditable(this.entry)) {
                icon = Icon.RENAME;
            } else if (GuiScreen.isAltKeyDown() && canToggleVisibility()) {
                icon = Icon.PATTERN_TERMINAL_HIDDEN;
            } else {
                icon = Icon.HIGHLIGHT;
            }
            SmallSquareButtonRenderer.drawIcon(this.x, this.y, this.width, this.height, icon, 0);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            boolean editable = isEditable(this.entry);
            boolean visibilityModifiable = canToggleVisibility();
            int lineCount = 0;
            if (this.info != null) {
                lineCount += 3;
            }
            if (visibilityModifiable) {
                lineCount++;
            }
            if (editable) {
                lineCount++;
            }

            ObjectList<ITextComponent> tooltip = new ObjectArrayList<>(lineCount);
            if (this.info != null) {
                tooltip.add(GuiText.PatternAccessTerminalHighlightProvider.text());
                String dimensionName = CraftingSupplierLocator.getDimensionName(this.info.dimensionId());
                tooltip.add(GuiText.CraftingTreeLocationInDimension.text(
                    this.info.pos().getX(),
                    this.info.pos().getY(),
                    this.info.pos().getZ(),
                    dimensionName));
                tooltip.add(GuiText.PatternAccessTerminalOpenProvider.text());
            }
            if (visibilityModifiable) {
                tooltip.add(GuiText.PatternAccessTerminalToggleVisibility.text());
            }
            if (editable) {
                tooltip.add(GuiText.PatternAccessTerminalRenameProvider.text());
            }
            return tooltip;
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

    private final class PatternAccessPanelHost implements PatternModifierPanelWidget.PanelHost {
        @Override
        public boolean isPatternModifierPanelAvailable() {
            return container.isPatternModifierPanelAvailable();
        }

        @Override
        public Point getPatternModifierPanelOffset() {
            return new Point(
                PatternModifierToolboxLayout.PANEL_LEFT_OFFSET + 8,
                PatternModifierToolboxLayout.PANEL_TOP_OFFSET - 52);
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
