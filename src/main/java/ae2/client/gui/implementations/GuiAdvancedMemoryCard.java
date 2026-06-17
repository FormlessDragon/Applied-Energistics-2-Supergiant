package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.TerminalStyle;
import ae2.api.features.P2PTunnelAttunementInternal;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ConfirmableTextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.render.overlay.AdvancedMemoryCardHighlightHandler;
import ae2.container.implementations.ContainerAdvancedMemoryCard;
import ae2.core.AEConfig;
import ae2.core.localization.GuiText;
import ae2.core.localization.P2PText;
import ae2.items.parts.P2PPartItem;
import ae2.items.tools.AdvancedMemoryCardItem;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardAction;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PEntry;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PIdentity;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PSnapshot;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class GuiAdvancedMemoryCard extends AEBaseGui<ContainerAdvancedMemoryCard> implements ITextFieldGui {
    private static final String STYLE_PATH = "/screens/advanced_memory_card.json";

    private static final String IMAGE_BACKGROUND_TOP = "backgroundTop";
    private static final String IMAGE_BACKGROUND_ROW = "backgroundRow";
    private static final String IMAGE_BACKGROUND_BOTTOM = "backgroundBottom";
    private static final String WIDGET_ENTRY_LIST = "entryList";
    private static final String WIDGET_EMPTY_MESSAGE = "emptyMessage";
    private static final String WIDGET_ENTRY_ICON = "entryIcon";
    private static final String WIDGET_ENTRY_IO_ICON = "entryIoIcon";
    private static final String WIDGET_ENTRY_STATUS_ICON = "entryStatusIcon";
    private static final String WIDGET_ENTRY_NAME = "entryName";
    private static final String WIDGET_ENTRY_TYPE = "entryType";
    private static final String WIDGET_ENTRY_FREQUENCY = "entryFrequency";
    private static final String WIDGET_ENTRY_POSITION = "entryPosition";
    private static final String WIDGET_BIND_BUTTON = "bindButton";
    private static final String WIDGET_SCROLLBAR = "scrollbar";
    private static final String WIDGET_SEARCH = "search";

    private static final int MIN_ROWS = 2;
    private static final int DEFAULT_ROWS = 5;
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private static final int RENAME_FIELD_HEIGHT = 12;

    private static final int OUTPUT_COLOR = 0x4566ccff;
    private static final int SELECTED_COLOR = 0x4545da75;
    private static final int ERROR_COLOR = 0x45da4527;
    private static final int INACTIVE_COLOR = 0x45ffea05;
    private static final int ENTRY_COLOR_OVERLAY_RIGHT_INSET = 4;

    private final Blitter backgroundTop;
    private final Blitter backgroundRow;
    private final Blitter backgroundBottom;
    private final WidgetStyle entryListStyle;
    private final WidgetStyle emptyMessageStyle;
    private final WidgetStyle entryIconStyle;
    private final WidgetStyle entryIoIconStyle;
    private final WidgetStyle entryStatusIconStyle;
    private final WidgetStyle entryNameStyle;
    private final WidgetStyle entryTypeStyle;
    private final WidgetStyle entryFrequencyStyle;
    private final WidgetStyle entryPositionStyle;
    private final WidgetStyle bindButtonStyle;
    private final WidgetStyle scrollbarStyle;
    private final AETextField searchField;
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
    private final P2PIconRenderer p2pIconRenderer = new P2PIconRenderer();
    private final AE2Button bindButton = new AE2Button(GuiText.AdvancedMemoryCardBind.text(), null);
    private final SettingToggleButton<TerminalStyle> terminalStyleButton = new SettingToggleButton<>(
        Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle);
    private final ToolbarButton typeButton;
    private final TypeSelector typeSelector = new TypeSelector();
    private final List<ResourceLocation> manageableTunnelTypes;
    private final Map<ResourceLocation, ItemStack> typeStacks;

    private int selectedEntryId = -1;
    private AdvancedMemoryCardP2PIdentity selectedIdentity;
    private boolean initialFocusApplied;
    private int rows = DEFAULT_ROWS;
    private AdvancedMemoryCardAction.Mode mode = AdvancedMemoryCardAction.Mode.BIND_OUTPUT;
    private ResourceLocation filteredTunnelType;
    private AdvancedMemoryCardP2PSnapshot cachedFilteredSnapshot;
    private ResourceLocation cachedFilteredTunnelType;
    private String cachedSearchText = "";
    private List<AdvancedMemoryCardP2PEntry> cachedFilteredEntries = List.of();
    private ConfirmableTextField activeRenameField;
    private AdvancedMemoryCardP2PIdentity activeRenameIdentity;
    private String activeRenameOriginalName = "";

    public GuiAdvancedMemoryCard(ContainerAdvancedMemoryCard container, InventoryPlayer playerInventory) {
        super(container, playerInventory, GuiStyleManager.loadStyleDoc(STYLE_PATH));
        var style = Objects.requireNonNull(this.style, "Advanced memory card GUI style");
        this.backgroundTop = style.getImage(IMAGE_BACKGROUND_TOP);
        this.backgroundRow = style.getImage(IMAGE_BACKGROUND_ROW);
        this.backgroundBottom = style.getImage(IMAGE_BACKGROUND_BOTTOM);
        this.entryListStyle = style.getWidget(WIDGET_ENTRY_LIST);
        this.emptyMessageStyle = style.getWidget(WIDGET_EMPTY_MESSAGE);
        this.entryIconStyle = style.getWidget(WIDGET_ENTRY_ICON);
        this.entryIoIconStyle = style.getWidget(WIDGET_ENTRY_IO_ICON);
        this.entryStatusIconStyle = style.getWidget(WIDGET_ENTRY_STATUS_ICON);
        this.entryNameStyle = style.getWidget(WIDGET_ENTRY_NAME);
        this.entryTypeStyle = style.getWidget(WIDGET_ENTRY_TYPE);
        this.entryFrequencyStyle = style.getWidget(WIDGET_ENTRY_FREQUENCY);
        this.entryPositionStyle = style.getWidget(WIDGET_ENTRY_POSITION);
        this.bindButtonStyle = style.getWidget(WIDGET_BIND_BUTTON);
        this.scrollbarStyle = style.getWidget(WIDGET_SCROLLBAR);
        this.searchField = this.widgets.addTextField(WIDGET_SEARCH);
        this.searchField.setResponder(this::onSearchChanged);
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(List.of(GuiText.AdvancedMemoryCardSearchTooltip.text()));
        this.typeStacks = createTypeStacks();
        this.manageableTunnelTypes = List.copyOf(this.typeStacks.keySet());
        if (container.getItemGuiHost() != null) {
            this.mode = AdvancedMemoryCardItem.getMode(container.getItemGuiHost().getItemStack());
        }
        this.xSize = this.backgroundTop.getSrcWidth();
        this.ySize = getPreferredHeight();
        addToLeftToolbar(this.terminalStyleButton);
        addToLeftToolbar(new ToolbarButton(
            this::modeIcon,
            () -> ItemStack.EMPTY,
            this::modeTooltip,
            this::cycleMode));
        this.typeButton = addToLeftToolbar(new ToolbarButton(
            this::typeIcon,
            () -> ItemStack.EMPTY,
            () -> this.filteredTunnelType,
            this::typeTooltip,
            this::handleTypeButton));
        addToLeftToolbar(new ToolbarButton(
            () -> Icon.ADVANCED_MEMORY_CARD_REFRESH,
            () -> ItemStack.EMPTY,
            () -> List.of(GuiText.AdvancedMemoryCardRefresh.text()),
            ignored -> this.container.refresh()));
    }

    private static Map<ResourceLocation, ItemStack> createTypeStacks() {
        Map<ResourceLocation, ItemStack> stacks = new Object2ObjectLinkedOpenHashMap<>();
        for (Item item : P2PTunnelAttunementInternal.getManageableTunnels()) {
            ResourceLocation id = item.getRegistryName();
            if (id != null && item != Items.AIR) {
                stacks.putIfAbsent(id, new ItemStack(item));
            }
        }
        return stacks;
    }

    private static Point widgetPos(WidgetStyle style) {
        return style.resolve(new Rectangle(0, 0, 0, 0));
    }

    @Override
    public void initGui() {
        this.rows = getConfiguredRows();
        this.xSize = this.backgroundTop.getSrcWidth();
        this.ySize = getPreferredHeight();
        super.initGui();
        this.terminalStyleButton.set(AEConfig.instance().getTerminalStyle());
        updateScrollbar();
        if (AEConfig.instance().isAutoFocusSearch()) {
            setInitialFocus(this.searchField);
        }
        invalidateExclusionZonesCache();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        this.backgroundTop.copy().dest(offsetX, offsetY).blit();

        int visibleRows = visibleRows();
        for (int i = 0; i < visibleRows - 2; i++) {
            int y = offsetY + this.backgroundTop.getSrcHeight() + entryPitch() * i;
            this.backgroundRow.copy().dest(offsetX, y).blit();
        }

        int bottomY = offsetY + this.ySize - this.backgroundBottom.getSrcHeight();
        this.backgroundBottom.copy().dest(offsetX, bottomY).blit();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        updateScrollbar();
        applyInitialFocus();

        List<AdvancedMemoryCardP2PEntry> entries = visibleEntries();
        AdvancedMemoryCardP2PEntry selected = selectedEntry();
        for (int i = 0; i < entries.size(); i++) {
            drawEntry(entries.get(i), selected, entryY(i), mouseX, mouseY);
        }
        updateActiveRenameFieldPosition();

        this.scrollbar.drawForegroundLayer(new Rectangle(0, 0, this.xSize, this.ySize),
            new Point(mouseX - this.guiLeft, mouseY - this.guiTop));

        if (this.container.getSnapshot().entries().isEmpty()) {
            Point pos = widgetPos(this.emptyMessageStyle);
            this.fontRenderer.drawString(GuiText.AdvancedMemoryCardEmpty.getLocal(), pos.x(), pos.y(), 0);
        } else if (filteredEntries().isEmpty()) {
            Point pos = widgetPos(this.emptyMessageStyle);
            this.fontRenderer.drawString(GuiText.AdvancedMemoryCardFilterEmpty.getLocal(), pos.x(), pos.y(), 0);
        }

        this.typeSelector.render(mouseX - this.guiLeft, mouseY - this.guiTop);
        drawActiveRenameField();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.activeRenameField != null && this.activeRenameField.getVisible()
            && this.activeRenameField.isMouseOver(mouseX, mouseY)) {
            if (mouseButton == 1) {
                this.activeRenameField.setText("");
            }
            this.activeRenameField.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        finishActiveRename(true);

        if (mouseButton == 1 && this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY)) {
            this.searchField.setText("");
            onSearchChanged("");
            return;
        }

        int localX = mouseX - this.guiLeft;
        int localY = mouseY - this.guiTop;

        if (this.typeSelector.mousePressed(localX, localY)) {
            return;
        }

        Point localMouse = new Point(localX, localY);
        if (localMouse.isIn(this.scrollbar.getBounds()) && this.scrollbar.onMouseDown(localMouse, mouseButton)) {
            return;
        }

        if (clickEntry(localX, localY, mouseButton)) {
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scrollbar.onMouseWheel(new Point(
                Mouse.getEventX() * this.width / this.mc.displayWidth - this.guiLeft,
                this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1 - this.guiTop), wheel);
            return;
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.scrollbar.onMouseUp(new Point(mouseX - this.guiLeft, mouseY - this.guiTop), state);
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.scrollbar.onMouseDrag(new Point(mouseX - this.guiLeft, mouseY - this.guiTop), clickedMouseButton)) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.activeRenameField != null && this.activeRenameField.getVisible()
            && this.activeRenameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                finishActiveRename(false);
                return;
            }
            if (this.activeRenameField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return;
        }
        if (typedChar == ' ' && this.searchField.isFocused() && this.searchField.getText().isEmpty()) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        finishActiveRename(true);
        super.onGuiClosed();
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (this.activeRenameField != null && this.activeRenameField.getVisible()
            && this.activeRenameField.isMouseOver(mouseX, mouseY)) {
            super.renderHoveredToolTip(mouseX, mouseY);
            return;
        }

        if (this.typeSelector.renderTooltip(mouseX - this.guiLeft, mouseY - this.guiTop, mouseX, mouseY)
            || this.typeSelector.isVisible()) {
            return;
        }

        int localX = mouseX - this.guiLeft;
        int localY = mouseY - this.guiTop;
        int row = rowAt(localX, localY);
        AdvancedMemoryCardP2PEntry hovered = hoveredEntry(row);
        if (hovered != null) {
            if (isEntryNameHovered(hovered, row, localX, localY)) {
                drawHoveringText(List.of(GuiText.AdvancedMemoryCardRenameHint.getLocal()), mouseX, mouseY);
                return;
            }

            List<String> lines = new ObjectArrayList<>();
            lines.add(GuiText.AdvancedMemoryCardType.getLocal(localizedTypeName(hovered)));
            lines.add(GuiText.AdvancedMemoryCardPos.getLocal(hovered.pos().getX(), hovered.pos().getY(),
                hovered.pos().getZ()));
            lines.add(GuiText.AdvancedMemoryCardSide.getLocal(
                hovered.side() == null ? GuiText.AdvancedMemoryCardUnknown.getLocal() : hovered.side().name()));
            lines.add(GuiText.AdvancedMemoryCardDim.getLocal(hovered.dimension()));
            lines.add(hovered.error() || hovered.frequency() == 0
                ? GuiText.AdvancedMemoryCardUnbound.getLocal()
                : GuiText.AdvancedMemoryCardBound.getLocal());
            if (hovered.missingChannel()) {
                lines.add(GuiText.AdvancedMemoryCardOffline.getLocal());
            }
            lines.add(GuiText.AdvancedMemoryCardChangeTypeHint.getLocal());
            drawHoveringText(lines, mouseX, mouseY);
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private void drawEntry(AdvancedMemoryCardP2PEntry entry, AdvancedMemoryCardP2PEntry selected, int y, int mouseX,
                           int mouseY) {
        int x = entryX();
        if (entry.entryId() == this.selectedEntryId) {
            drawEntryColorOverlay(x, y, SELECTED_COLOR);
        } else if (entry.error()) {
            drawEntryColorOverlay(x, y, ERROR_COLOR);
        } else if (entry.missingChannel() && entry.frequency() != 0) {
            drawEntryColorOverlay(x, y, INACTIVE_COLOR);
        } else if (selected != null && selected.frequency() != 0 && selected.frequency() == entry.frequency()) {
            drawEntryColorOverlay(x, y, OUTPUT_COLOR);
        }

        Point entryIconPos = widgetPos(this.entryIconStyle);
        drawTunnelIcon(entry, x + entryIconPos.x(), y + entryIconPos.y());
        Icon ioIcon = entry.input() ? Icon.ADVANCED_MEMORY_CARD_INPUT : Icon.ADVANCED_MEMORY_CARD_OUTPUT;
        Point ioIconPos = widgetPos(this.entryIoIconStyle);
        ioIcon.getBlitter().dest(x + ioIconPos.x(), y + ioIconPos.y()).blit();
        Icon statusIcon = entry.error() || entry.frequency() == 0 || entry.missingChannel()
            ? Icon.ADVANCED_MEMORY_CARD_UNBOUND
            : Icon.ADVANCED_MEMORY_CARD_BOUND;
        Point statusIconPos = widgetPos(this.entryStatusIconStyle);
        statusIcon.getBlitter().dest(x + statusIconPos.x(), y + statusIconPos.y()).blit();

        Point namePos = widgetPos(this.entryNameStyle);
        this.fontRenderer.drawString(GuiText.AdvancedMemoryCardName.getLocal(visibleEntryName(entry)),
            x + namePos.x(), y + namePos.y(), 0);
        Point typePos = widgetPos(this.entryTypeStyle);
        this.fontRenderer.drawString(
            GuiText.AdvancedMemoryCardType.getLocal(localizedTypeName(entry)),
            x + typePos.x(), y + typePos.y(), 0);
        Point frequencyPos = widgetPos(this.entryFrequencyStyle);
        this.fontRenderer.drawString(GuiText.AdvancedMemoryCardFrequency.getLocal(frequencyText(entry)),
            x + frequencyPos.x(), y + frequencyPos.y(), 0);
        Point positionPos = widgetPos(this.entryPositionStyle);
        this.fontRenderer.drawString(
            GuiText.AdvancedMemoryCardPos.getLocal(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ()),
            x + positionPos.x(), y + positionPos.y(), 0);

        if (canBind(entry)) {
            configureBindButton(y);
            this.bindButton.drawButton(this.mc, mouseX - this.guiLeft, mouseY - this.guiTop, 0);
        }
    }

    private void drawEntryColorOverlay(int x, int y, int color) {
        drawRect(x, y, x + entryWidth() - ENTRY_COLOR_OVERLAY_RIGHT_INSET, y + entryHeight(), color);
    }

    private void applyInitialFocus() {
        if (this.initialFocusApplied) {
            return;
        }

        int entryId = this.container.getSnapshot().initialFocusEntryId();
        if (entryId < 0) {
            return;
        }

        this.initialFocusApplied = true;

        List<AdvancedMemoryCardP2PEntry> entries = filteredEntries();
        for (int i = 0; i < entries.size(); i++) {
            AdvancedMemoryCardP2PEntry entry = entries.get(i);
            if (entry.entryId() == entryId) {
                selectEntry(entry);
                this.scrollbar.setCurrentScroll(Math.clamp(i, 0, maxScroll()));
                if (entry.frequency() != 0) {
                    AdvancedMemoryCardHighlightHandler.INSTANCE.showFrequency(this.mc, entry.frequency(),
                        this.container.getSnapshot().entries());
                }
                return;
            }
        }
    }

    private void drawTunnelIcon(AdvancedMemoryCardP2PEntry entry, int x, int y) {
        this.p2pIconRenderer.render(entry.tunnelType(), x, y);
    }

    private boolean clickEntry(int localX, int localY, int mouseButton) {
        int row = rowAt(localX, localY);
        if (row < 0) {
            return false;
        }

        List<AdvancedMemoryCardP2PEntry> entries = visibleEntries();
        if (row >= entries.size()) {
            return false;
        }

        AdvancedMemoryCardP2PEntry clicked = entries.get(row);
        int rowY = entryY(row);
        if (mouseButton == 0 && isEntryNameHovered(clicked, row, localX, localY)) {
            openRenameField(clicked);
            return true;
        }

        if (mouseButton == 1) {
            this.typeSelector.openForEntry(entryX() + 40, rowY, clicked.entryId());
            return true;
        }

        if (canBind(clicked)) {
            configureBindButton(rowY);
            if (this.bindButton.mousePressed(this.mc, localX, localY)) {
                this.container.apply(this.mode, this.selectedEntryId, clicked.entryId());
                return true;
            }
        }

        selectEntry(clicked);
        if (clicked.frequency() != 0) {
            AdvancedMemoryCardHighlightHandler.INSTANCE.showFrequency(this.mc, clicked.frequency(),
                this.container.getSnapshot().entries());
        }
        return true;
    }

    private void configureBindButton(int y) {
        Point bindButtonPos = widgetPos(this.bindButtonStyle);
        this.bindButton.setMessage(this.mode == AdvancedMemoryCardAction.Mode.DELETE_BINDING
            ? GuiText.AdvancedMemoryCardUnbind.text()
            : GuiText.AdvancedMemoryCardBind.text());
        this.bindButton.x = entryX() + bindButtonPos.x();
        this.bindButton.y = y + bindButtonPos.y();
        this.bindButton.width = this.bindButtonStyle.getWidth();
        this.bindButton.height = this.bindButtonStyle.getHeight();
        this.bindButton.visible = true;
        this.bindButton.enabled = true;
    }

    private AdvancedMemoryCardP2PEntry hoveredEntry(int row) {
        if (row < 0) {
            return null;
        }
        List<AdvancedMemoryCardP2PEntry> entries = visibleEntries();
        return row < entries.size() ? entries.get(row) : null;
    }

    private boolean isEntryNameHovered(AdvancedMemoryCardP2PEntry entry, int row, int localX, int localY) {
        Point namePos = widgetPos(this.entryNameStyle);
        int nameX = entryX() + namePos.x();
        int nameY = entryY(row) + namePos.y();
        int nameWidth = this.fontRenderer.getStringWidth(GuiText.AdvancedMemoryCardName.getLocal(visibleEntryName(entry)));
        return localX >= nameX
            && localX < nameX + nameWidth
            && localY >= nameY
            && localY < nameY + this.fontRenderer.FONT_HEIGHT;
    }

    private void openRenameField(AdvancedMemoryCardP2PEntry entry) {
        if (this.activeRenameField != null && Objects.equals(this.activeRenameIdentity,
            AdvancedMemoryCardP2PIdentity.of(entry))) {
            this.activeRenameField.setFocused(true);
            this.activeRenameField.selectAll();
            return;
        }

        finishActiveRename(true);
        this.activeRenameIdentity = AdvancedMemoryCardP2PIdentity.of(entry);
        this.activeRenameOriginalName = entry.customNameOrEmpty();

        ConfirmableTextField field = new ConfirmableTextField(this.style, this.fontRenderer, 0, 0, 0, 0);
        field.setMaxStringLength(MAX_CUSTOM_NAME_LENGTH);
        field.setOnConfirm(() -> {
            field.setFocused(false);
            finishActiveRename(true);
        });
        this.activeRenameField = field;
        updateActiveRenameFieldPosition();
        field.setText(this.activeRenameOriginalName);
        field.setVisible(true);
        field.setFocused(true);
        field.selectAll();
    }

    private void updateActiveRenameFieldPosition() {
        if (this.activeRenameField == null || this.activeRenameIdentity == null) {
            return;
        }

        List<AdvancedMemoryCardP2PEntry> entries = visibleEntries();
        for (int i = 0; i < entries.size(); i++) {
            AdvancedMemoryCardP2PEntry entry = entries.get(i);
            if (Objects.equals(this.activeRenameIdentity, AdvancedMemoryCardP2PIdentity.of(entry))) {
                Point namePos = widgetPos(this.entryNameStyle);
                this.activeRenameField.move(this.guiLeft + entryX() + namePos.x(),
                    this.guiTop + entryY(i) + namePos.y() - 2);
                this.activeRenameField.resize(Math.max(24, entryWidth() - namePos.x() - 8), RENAME_FIELD_HEIGHT);
                this.activeRenameField.setVisible(true);
                return;
            }
        }
        this.activeRenameField.setVisible(false);
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

    private void finishActiveRename(boolean apply) {
        if (this.activeRenameField == null) {
            return;
        }

        String newName = this.activeRenameField.getText();
        boolean changed = !Objects.equals(newName, this.activeRenameOriginalName);
        AdvancedMemoryCardP2PEntry entry = this.activeRenameIdentity == null
            ? null
            : this.activeRenameIdentity.findIn(this.container.getSnapshot().entries());
        if (apply && changed && entry != null) {
            this.container.rename(entry.entryId(), newName);
        }

        this.activeRenameField.setFocused(false);
        this.activeRenameField.setVisible(false);
        this.activeRenameField = null;
        this.activeRenameIdentity = null;
        this.activeRenameOriginalName = "";
    }

    private int rowAt(int localX, int localY) {
        int entryX = entryX();
        int entryY = entryY(0);
        if (localX <= entryX || localX >= entryX + entryWidth()) {
            return -1;
        }
        if (localY <= entryY || localY >= entryY + visibleRows() * entryPitch()) {
            return -1;
        }
        return (localY - entryY) / entryPitch();
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        TerminalStyle nextValue = button.getNextValue(backwards);
        button.set(nextValue);
        AEConfig.instance().setTerminalStyle(nextValue);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private void cycleMode(boolean reverse) {
        this.mode = switch (this.mode) {
            case BIND_OUTPUT -> reverse ? AdvancedMemoryCardAction.Mode.DELETE_BINDING
                : AdvancedMemoryCardAction.Mode.BIND_INPUT;
            case BIND_INPUT -> reverse ? AdvancedMemoryCardAction.Mode.BIND_OUTPUT
                : AdvancedMemoryCardAction.Mode.COPY_OUTPUT;
            case COPY_OUTPUT -> reverse ? AdvancedMemoryCardAction.Mode.BIND_INPUT
                : AdvancedMemoryCardAction.Mode.DELETE_BINDING;
            case DELETE_BINDING -> reverse ? AdvancedMemoryCardAction.Mode.COPY_OUTPUT
                : AdvancedMemoryCardAction.Mode.BIND_OUTPUT;
        };
        this.container.setMode(this.mode);
    }

    private void handleTypeButton(boolean rightClick) {
        if (rightClick) {
            this.typeSelector.openForFilter(this.typeButton.y - this.guiTop);
            return;
        }
        cycleTypeFilter();
    }

    private void cycleTypeFilter() {
        List<ResourceLocation> types = manageableTunnelTypes();
        if (types.isEmpty()) {
            this.filteredTunnelType = null;
            updateScrollbar();
            return;
        }

        if (this.filteredTunnelType == null) {
            this.filteredTunnelType = types.getFirst();
        } else {
            int current = types.indexOf(this.filteredTunnelType);
            if (current < 0) {
                this.filteredTunnelType = null;
            } else {
                int next = current + 1;
                if (next >= types.size()) {
                    this.filteredTunnelType = null;
                } else {
                    this.filteredTunnelType = types.get(next);
                }
            }
        }
        updateScrollbar();
    }

    private void onSearchChanged(String ignored) {
        invalidateFilteredEntries();
        updateScrollbar();
    }

    private void invalidateFilteredEntries() {
        this.cachedFilteredSnapshot = null;
        this.cachedFilteredTunnelType = null;
        this.cachedSearchText = "";
        this.cachedFilteredEntries = List.of();
    }

    private Icon modeIcon() {
        return switch (this.mode) {
            case BIND_OUTPUT -> Icon.ADVANCED_MEMORY_CARD_BIND_OUTPUT;
            case BIND_INPUT -> Icon.ADVANCED_MEMORY_CARD_BIND_INPUT;
            case COPY_OUTPUT -> Icon.ADVANCED_MEMORY_CARD_COPY_OUTPUT;
            case DELETE_BINDING -> Icon.ADVANCED_MEMORY_CARD_DELETE_BINDING;
        };
    }

    private Icon typeIcon() {
        return this.filteredTunnelType == null ? Icon.TYPE_FILTER_ALL : null;
    }

    private List<ITextComponent> modeTooltip() {
        return List.of(modeText(), GuiText.AdvancedMemoryCardModeHint.text());
    }

    private ITextComponent modeText() {
        return switch (this.mode) {
            case BIND_OUTPUT -> GuiText.AdvancedMemoryCardModeOutput.text();
            case BIND_INPUT -> GuiText.AdvancedMemoryCardModeInput.text();
            case COPY_OUTPUT -> GuiText.AdvancedMemoryCardModeCopy.text();
            case DELETE_BINDING -> GuiText.AdvancedMemoryCardModeDelete.text();
        };
    }

    private List<ITextComponent> typeTooltip() {
        ITextComponent current = this.filteredTunnelType == null
            ? GuiText.AdvancedMemoryCardFilterAny.text()
            : GuiText.AdvancedMemoryCardFilterType.text(displayNameForType(this.filteredTunnelType));
        return List.of(current, GuiText.AdvancedMemoryCardFilterHint.text());
    }

    private boolean canBind(AdvancedMemoryCardP2PEntry entry) {
        AdvancedMemoryCardP2PEntry selected = selectedEntry();
        if (selected == null) {
            return false;
        }
        if (this.mode == AdvancedMemoryCardAction.Mode.DELETE_BINDING) {
            return entry.frequency() != 0;
        }
        if (entry.entryId() == selected.entryId()) {
            return false;
        }
        if (this.mode == AdvancedMemoryCardAction.Mode.COPY_OUTPUT) {
            return selected.input() && selected.frequency() != 0 && (!entry.input() || entry.frequency() == 0);
        }
        return selected.frequency() == 0 || selected.frequency() != entry.frequency();
    }

    private List<AdvancedMemoryCardP2PEntry> visibleEntries() {
        List<AdvancedMemoryCardP2PEntry> entries = filteredEntries();
        int from = Math.min(this.scrollbar.getCurrentScroll(), entries.size());
        int to = Math.min(from + visibleRows(), entries.size());
        return entries.subList(from, to);
    }

    private List<AdvancedMemoryCardP2PEntry> filteredEntries() {
        AdvancedMemoryCardP2PSnapshot currentSnapshot = this.container.getSnapshot();
        String searchText = normalizedSearchText();
        if (currentSnapshot == this.cachedFilteredSnapshot
            && Objects.equals(this.filteredTunnelType, this.cachedFilteredTunnelType)
            && Objects.equals(searchText, this.cachedSearchText)) {
            return this.cachedFilteredEntries;
        }

        this.cachedFilteredSnapshot = currentSnapshot;
        this.cachedFilteredTunnelType = this.filteredTunnelType;
        this.cachedSearchText = searchText;
        if (this.filteredTunnelType == null && searchText.isEmpty()) {
            this.cachedFilteredEntries = currentSnapshot.entries();
            return this.cachedFilteredEntries;
        }
        this.cachedFilteredEntries = currentSnapshot.entries().stream()
                                                    .filter(entry -> this.filteredTunnelType == null
                                                        || this.filteredTunnelType.equals(entry.tunnelType()))
                                                    .filter(entry -> matchesSearch(entry, searchText))
                                                    .toList();
        return this.cachedFilteredEntries;
    }

    private String normalizedSearchText() {
        return this.searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(AdvancedMemoryCardP2PEntry entry, String searchText) {
        if (searchText.isEmpty()) {
            return true;
        }

        String visibleName = visibleEntryName(entry).toLowerCase(Locale.ROOT);
        if (visibleName.contains(searchText)) {
            return true;
        }

        String frequency = Integer.toHexString(Short.toUnsignedInt(entry.frequency())).toLowerCase(Locale.ROOT);
        if (frequency.contains(searchText)) {
            return true;
        }

        String paddedFrequency = String.format(Locale.ROOT, "%04x", Short.toUnsignedInt(entry.frequency()));
        return paddedFrequency.contains(searchText);
    }

    private List<ResourceLocation> manageableTunnelTypes() {
        return this.manageableTunnelTypes;
    }

    private AdvancedMemoryCardP2PEntry selectedEntry() {
        if (this.selectedIdentity != null) {
            AdvancedMemoryCardP2PEntry entry = this.selectedIdentity.findIn(this.container.getSnapshot().entries());
            if (entry != null) {
                this.selectedEntryId = entry.entryId();
                return entry;
            }
            this.selectedEntryId = -1;
            return null;
        }

        for (AdvancedMemoryCardP2PEntry entry : this.container.getSnapshot().entries()) {
            if (entry.entryId() == this.selectedEntryId) {
                return entry;
            }
        }
        return null;
    }

    private void selectEntry(AdvancedMemoryCardP2PEntry entry) {
        this.selectedEntryId = entry.entryId();
        this.selectedIdentity = AdvancedMemoryCardP2PIdentity.of(entry);
    }

    private String frequencyText(AdvancedMemoryCardP2PEntry entry) {
        if (entry.frequency() == 0) {
            return GuiText.AdvancedMemoryCardNotSet.getLocal();
        }
        return Integer.toHexString(Short.toUnsignedInt(entry.frequency())).toUpperCase();
    }

    private String displayNameForType(ResourceLocation type) {
        ItemStack stack = stackForType(type);
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof P2PPartItem<?> p2pPartItem) {
                return I18n.format(p2pPartItem.getP2PTypeTranslationKey(stack));
            }
            return stack.getDisplayName();
        }
        return type.toString();
    }

    private String localizedTypeName(AdvancedMemoryCardP2PEntry entry) {
        return I18n.format(entry.displayNameKey());
    }

    private String visibleEntryName(AdvancedMemoryCardP2PEntry entry) {
        return entry.customName() == null ? P2PText.NamePrefix.getLocal(localizedTypeName(entry)) : entry.customName();
    }

    private ItemStack stackForType(ResourceLocation type) {
        return this.typeStacks.getOrDefault(type, ItemStack.EMPTY);
    }

    private int getConfiguredRows() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_ROWS, (availableHeight - fixedHeight() + entryGap()) / entryPitch());
        return Math.max(MIN_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
    }

    private int getPreferredHeight() {
        return fixedHeight() + this.rows * entryHeight() + (this.rows - 1) * entryGap();
    }

    private int visibleRows() {
        return (this.ySize - fixedHeight() + entryGap()) / entryPitch();
    }

    private int maxScroll() {
        return Math.max(0, filteredEntries().size() - visibleRows());
    }

    private void updateScrollbar() {
        Point position = widgetPos(this.scrollbarStyle);
        this.scrollbar.setPosition(position);
        this.scrollbar.setHeight(this.ySize - position.y() - scrollbarBottomMargin());
        this.scrollbar.setRange(0, maxScroll(), 1);
        this.scrollbar.setCaptureMouseWheel(false);
    }

    private int entryX() {
        return widgetPos(this.entryListStyle).x();
    }

    private int entryY(int row) {
        return widgetPos(this.entryListStyle).y() + row * entryPitch();
    }

    private int entryWidth() {
        return this.entryListStyle.getWidth();
    }

    private int entryHeight() {
        return this.entryListStyle.getHeight();
    }

    private int entryGap() {
        return this.backgroundRow.getSrcHeight() - entryHeight();
    }

    private int entryPitch() {
        return entryHeight() + entryGap();
    }

    private int fixedHeight() {
        return this.backgroundTop.getSrcHeight() + this.backgroundBottom.getSrcHeight() - 2 * entryHeight() - entryGap();
    }

    private int scrollbarBottomMargin() {
        return this.backgroundBottom.getSrcY() + this.backgroundBottom.getSrcHeight()
            - widgetPos(this.scrollbarStyle).y() - this.scrollbarStyle.getHeight();
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return this.activeRenameField == null ? List.of(this.searchField) : List.of(this.searchField,
            this.activeRenameField);
    }

    @FunctionalInterface
    private interface ToolbarAction {
        void handle(boolean backwards);
    }

    private class TypeSelector {
        private static final int ICONS_PER_ROW = 5;
        private static final int CELL_SIZE = 18;
        private static final int PADDING = 4;
        private static final int BACKGROUND_COLOR = 0xAA000000;
        private static final int HOVER_COLOR = 0xFF00FF00;

        private int x;
        private int y;
        private boolean visible;
        private boolean useAny;
        private int entryId;
        private int hoveredIndex = -1;
        private List<ResourceLocation> types = List.of();

        boolean isVisible() {
            return this.visible;
        }

        void openForFilter(int y) {
            open(0, y, true, -1);
        }

        void openForEntry(int x, int y, int entryId) {
            open(x, y, false, entryId);
        }

        private void open(int x, int y, boolean useAny, int entryId) {
            this.x = x;
            this.y = y;
            this.visible = true;
            this.useAny = useAny;
            this.entryId = entryId;
            this.types = manageableTunnelTypes();
            this.hoveredIndex = -1;
        }

        void render(int mouseX, int mouseY) {
            if (!this.visible || typeCount() == 0) {
                return;
            }

            this.hoveredIndex = hoveredIndex(mouseX, mouseY);
            drawRect(this.x, this.y, this.x + width(), this.y + height(), BACKGROUND_COLOR);

            for (int i = 0; i < this.types.size(); i++) {
                drawCell(i);
            }
            if (this.useAny) {
                drawCell(this.types.size());
            }
        }

        private void drawCell(int index) {
            int iconX = iconX(index);
            int iconY = iconY(index);
            if (this.hoveredIndex == index) {
                drawRect(iconX, iconY, iconX + CELL_SIZE, iconY + CELL_SIZE, HOVER_COLOR);
            }

            if (index == this.types.size()) {
                Icon.TYPE_FILTER_ALL.getBlitter().dest(iconX + 1, iconY + 1).blit();
                return;
            }
            p2pIconRenderer.render(this.types.get(index), iconX + 1, iconY + 1);
        }

        boolean mousePressed(int mouseX, int mouseY) {
            if (!this.visible) {
                return false;
            }

            if (!contains(mouseX, mouseY)) {
                this.visible = false;
                return true;
            }

            int index = hoveredIndex(mouseX, mouseY);
            if (index < 0) {
                return true;
            }

            if (this.useAny && index == this.types.size()) {
                filteredTunnelType = null;
                updateScrollbar();
                this.visible = false;
                return true;
            }
            if (index < this.types.size()) {
                ResourceLocation type = this.types.get(index);
                if (this.useAny) {
                    filteredTunnelType = type;
                    updateScrollbar();
                } else {
                    container.changeType(this.entryId, type);
                }
                this.visible = false;
            }
            return true;
        }

        boolean renderTooltip(int mouseX, int mouseY, int screenX, int screenY) {
            if (!this.visible || !contains(mouseX, mouseY)) {
                return false;
            }

            int index = hoveredIndex(mouseX, mouseY);
            if (index < 0) {
                return true;
            }

            if (this.useAny && index == this.types.size()) {
                drawHoveringText(List.of(GuiText.AdvancedMemoryCardTypeAny.getLocal()), screenX, screenY);
            } else if (index < this.types.size()) {
                drawHoveringText(List.of(displayNameForType(this.types.get(index))), screenX, screenY);
            }
            return true;
        }

        private int hoveredIndex(int mouseX, int mouseY) {
            int count = typeCount();
            for (int i = 0; i < count; i++) {
                int iconX = iconX(i);
                int iconY = iconY(i);
                if (mouseX > iconX && mouseX < iconX + CELL_SIZE && mouseY > iconY && mouseY < iconY + CELL_SIZE) {
                    return i;
                }
            }
            return -1;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX > this.x && mouseX < this.x + width() && mouseY > this.y && mouseY < this.y + height();
        }

        private int iconX(int index) {
            return this.x + PADDING + index % ICONS_PER_ROW * CELL_SIZE;
        }

        private int iconY(int index) {
            return this.y + PADDING + index / ICONS_PER_ROW * CELL_SIZE;
        }

        private int width() {
            return Math.min(ICONS_PER_ROW, typeCount()) * CELL_SIZE + PADDING * 2;
        }

        private int height() {
            return (typeCount() + ICONS_PER_ROW - 1) / ICONS_PER_ROW * CELL_SIZE + PADDING * 2;
        }

        private int typeCount() {
            return this.types.size() + (this.useAny ? 1 : 0);
        }
    }

    private class ToolbarButton extends IconButton {
        private final Supplier<Icon> iconSupplier;
        private final Supplier<ItemStack> stackSupplier;
        private final Supplier<ResourceLocation> p2pTypeSupplier;
        private final Supplier<List<ITextComponent>> tooltipSupplier;
        private final ToolbarAction action;
        private boolean pressedBackwards;
        private boolean triggeredOnPress;

        ToolbarButton(Supplier<Icon> iconSupplier, Supplier<ItemStack> stackSupplier,
                      Supplier<List<ITextComponent>> tooltipSupplier, ToolbarAction action) {
            this(iconSupplier, stackSupplier, () -> null, tooltipSupplier, action);
        }

        ToolbarButton(Supplier<Icon> iconSupplier, Supplier<ItemStack> stackSupplier,
                      Supplier<ResourceLocation> p2pTypeSupplier,
                      Supplier<List<ITextComponent>> tooltipSupplier, ToolbarAction action) {
            super(() -> {
            });
            this.iconSupplier = iconSupplier;
            this.stackSupplier = stackSupplier;
            this.p2pTypeSupplier = p2pTypeSupplier;
            this.tooltipSupplier = tooltipSupplier;
            this.action = action;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            super.drawButton(minecraft, mouseX, mouseY, partialTicks);

            ResourceLocation p2pType = this.p2pTypeSupplier.get();
            if (!this.visible || p2pType == null) {
                return;
            }

            int yOffset = this.hovered ? 1 : 0;
            int iconY = this.isHalfSize() ? this.y : this.y + 1 + yOffset;
            p2pIconRenderer.render(p2pType, this.x, iconY);
        }

        @Override
        public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
            boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
            if (pressed) {
                var screen = Minecraft.getMinecraft().currentScreen;
                this.pressedBackwards = screen instanceof AEBaseGui<?> gui && gui.isHandlingRightClick();
                if (this.pressedBackwards) {
                    this.action.handle(true);
                    this.triggeredOnPress = true;
                } else {
                    this.triggeredOnPress = false;
                }
            } else {
                this.pressedBackwards = false;
                this.triggeredOnPress = false;
            }
            return pressed;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            boolean releasedInside = this.enabled && this.visible
                && mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            super.mouseReleased(mouseX, mouseY);
            if (releasedInside && !this.triggeredOnPress) {
                this.action.handle(this.pressedBackwards);
            }
            this.pressedBackwards = false;
            this.triggeredOnPress = false;
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return this.tooltipSupplier.get();
        }

        @Override
        protected Icon getIcon() {
            return this.iconSupplier.get();
        }

        @Override
        protected ItemStack getItemStackOverlay() {
            return this.stackSupplier.get();
        }
    }
}
