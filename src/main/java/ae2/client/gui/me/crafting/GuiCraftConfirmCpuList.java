package ae2.client.gui.me.crafting;

import ae2.api.config.Settings;
import ae2.api.config.TerminalStyle;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.TabButton;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.me.crafting.CraftConfirmCpuList;
import ae2.core.AEConfig;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class GuiCraftConfirmCpuList extends AEBaseGui<ContainerCraftConfirm> implements ITextFieldGui {
    private static final String STYLE_PATH = "/screens/craft_confirm_cpu_list.json";
    private static final String TEXTURE = "guis/craft_confirm_cpu_list.png";
    private static final int LIST_X = 8;
    private static final int LIST_Y = 31;
    private static final int LIST_WIDTH = 203;
    private static final int ROW_HEIGHT = 22;
    private static final int MAIN_WIDTH = 218;
    private static final int SCROLLBAR_WIDTH = CraftingScreenLayout.WIDTH - MAIN_WIDTH;
    private static final int ROW_TEXT_X = LIST_X + 4;
    private static final int ROW_STORAGE_ICON_X = LIST_X + 127;
    private static final int ROW_MERGE_RIGHT_X = ROW_STORAGE_ICON_X - 6;
    private static final int ROW_STORAGE_TEXT_X = LIST_X + 139;
    private static final int ROW_PROCESSOR_ICON_X = LIST_X + 170;
    private static final int ROW_PROCESSOR_TEXT_X = LIST_X + 182;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int DEFAULT_VISIBLE_ROWS = 6;
    private static final int ROW_SOURCE_Y = LIST_Y + ROW_HEIGHT;
    private static final int BOTTOM_SOURCE_Y = LIST_Y + DEFAULT_VISIBLE_ROWS * ROW_HEIGHT;
    private static final int FOOTER_HEIGHT = 31;
    private static final int SELECTED_COLOR = 0x663F83B8;
    private static final int HOVER_COLOR = 0x443F83B8;
    private static final int MERGE_COLOR = 0xFF77D67A;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xD8DCE8;

    private final GuiCraftConfirm parent;
    private final AETextField searchField;
    private final Scrollbar scrollbar;
    private final SortButton sortButton;
    private final SettingToggleButton<TerminalStyle> terminalStyleButton;
    private final ObjectArrayList<CraftConfirmCpuList.Entry> visibleCpus = new ObjectArrayList<>();
    private String searchText = "";
    private CraftConfirmCpuList filteredList = CraftConfirmCpuList.EMPTY;
    private CraftConfirmCpuSortMode sortMode = CraftConfirmCpuSortMode.CAPACITY;
    private int visibleRows = DEFAULT_VISIBLE_ROWS;
    @Nullable
    private List<ITextComponent> hoveredTooltip;

    public GuiCraftConfirmCpuList(GuiCraftConfirm parent) {
        this(parent, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiCraftConfirmCpuList(GuiCraftConfirm parent, GuiStyle style) {
        super(parent.getContainer(), getPlayerInventory(parent), style);
        this.parent = parent;

        this.widgets.add("back", new TabButton(Icon.BACK, GuiText.ReturnToPreviousGui.text(),
            this::returnToParent));
        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(List.of(GuiText.CraftingCpuListSearchTooltip.text()));
        this.searchField.setResponder(ignored -> updateSearch());
        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.sortButton = new SortButton(this::toggleSortMode);
        this.widgets.add("sort", this.sortButton);
        this.terminalStyleButton = new SettingToggleButton<>(
            Settings.TERMINAL_STYLE, AEConfig.instance().getTerminalStyle(), this::toggleTerminalStyle);
        addToLeftToolbar(this.terminalStyleButton);
        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.CraftingCpuList.text());
    }

    private static InventoryPlayer getPlayerInventory(GuiCraftConfirm parent) {
        return parent.getContainer().getPlayerInventory();
    }

    private static boolean isRowHovered(int localMouseX, int localMouseY, int row) {
        int top = LIST_Y + row * ROW_HEIGHT;
        return localMouseX >= LIST_X && localMouseX < LIST_X + LIST_WIDTH
            && localMouseY >= top && localMouseY < top + ROW_HEIGHT;
    }

    private static void drawEmptyRow(int y) {
        Gui.drawRect(LIST_X, y, LIST_X + LIST_WIDTH, y + ROW_HEIGHT - 1, 0x11000000);
    }

    private static ITextComponent getCpuName(CraftConfirmCpuList.Entry entry) {
        return entry.name() != null ? entry.name() : GuiText.CpuFallbackName.text(entry.serial());
    }

    private static ITextComponent gray(ITextComponent text) {
        return text.setStyle(new Style().setColor(TextFormatting.GRAY));
    }

    private static String formatStorage(long storage) {
        return storage + " bytes";
    }

    private static String formatCompactStorage(long storage) {
        if (storage >= 1024L * 1024L) {
            return (storage / (1024L * 1024L)) + "M";
        }
        return (storage / 1024L) + "k";
    }

    private static String sortName(CraftConfirmCpuList.Entry entry) {
        return getCpuName(entry).getFormattedText().toLowerCase(Locale.ROOT);
    }

    @Override
    public void initGui() {
        updateLayout();
        super.initGui();
        updateScrollbar();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.sortButton.setMode(this.sortMode);
        this.terminalStyleButton.set(AEConfig.instance().getTerminalStyle());
        updateScrollbar();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        List<CraftConfirmCpuList.Entry> entries = getVisibleCpus();
        this.hoveredTooltip = null;

        int localMouseX = mouseX - offsetX;
        int localMouseY = mouseY - offsetY;
        int start = Math.min(this.scrollbar.getCurrentScroll(), entries.size());
        int end = Math.min(start + this.visibleRows, entries.size());

        for (int row = 0; row < this.visibleRows; row++) {
            int y = LIST_Y + row * ROW_HEIGHT;
            int index = start + row;
            if (index >= end) {
                drawEmptyRow(y);
                continue;
            }

            CraftConfirmCpuList.Entry entry = entries.get(index);
            boolean hovered = isRowHovered(localMouseX, localMouseY, row);
            drawCpuRow(entry, y, hovered);
            if (hovered) {
                this.hoveredTooltip = getCpuTooltip(entry);
            }
        }

        if (entries.isEmpty()) {
            String message = GuiText.CraftingCpuListEmpty.getLocal();
            int messageWidth = this.fontRenderer.getStringWidth(message);
            int messageY = LIST_Y + (this.visibleRows * ROW_HEIGHT - this.fontRenderer.FONT_HEIGHT) / 2;
            this.fontRenderer.drawString(message, LIST_X + (LIST_WIDTH - messageWidth) / 2, messageY,
                MUTED_TEXT_COLOR);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        Blitter.texture(TEXTURE)
               .src(0, 0, MAIN_WIDTH, LIST_Y)
               .dest(offsetX, offsetY)
               .blit();
        Blitter.texture(TEXTURE)
               .src(MAIN_WIDTH, 0, SCROLLBAR_WIDTH, LIST_Y)
               .dest(offsetX + MAIN_WIDTH, offsetY)
               .blit();

        int y = offsetY + LIST_Y;
        for (int row = 0; row < this.visibleRows; row++) {
            int rowSourceY;
            if (row == 0) {
                rowSourceY = LIST_Y;
            } else if (row == this.visibleRows - 1) {
                rowSourceY = BOTTOM_SOURCE_Y - ROW_HEIGHT;
            } else {
                rowSourceY = ROW_SOURCE_Y;
            }

            Blitter.texture(TEXTURE)
                   .src(0, rowSourceY, MAIN_WIDTH, ROW_HEIGHT)
                   .dest(offsetX, y)
                   .blit();
            Blitter.texture(TEXTURE)
                   .src(MAIN_WIDTH, rowSourceY, SCROLLBAR_WIDTH, ROW_HEIGHT)
                   .dest(offsetX + MAIN_WIDTH, y)
                   .blit();
            y += ROW_HEIGHT;
        }

        Blitter.texture(TEXTURE)
               .src(0, BOTTOM_SOURCE_Y, MAIN_WIDTH, FOOTER_HEIGHT)
               .dest(offsetX, y)
               .blit();
        Blitter.texture(TEXTURE)
               .src(MAIN_WIDTH, BOTTOM_SOURCE_Y, SCROLLBAR_WIDTH, FOOTER_HEIGHT)
               .dest(offsetX + MAIN_WIDTH, y)
               .blit();
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);
        if (this.hoveredTooltip != null && !this.hoveredTooltip.isEmpty()) {
            drawTooltipWithHeader(mouseX, mouseY, this.hoveredTooltip);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSelectionPopupMouseClicked(mouseX, mouseY)) {
            return;
        }

        if (this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY) && mouseButton == 1) {
            this.searchField.setText("");
            updateSearch();
            return;
        }

        if (mouseButton == 0) {
            CraftConfirmCpuList.Entry clicked = getEntryAt(mouseX - this.guiLeft, mouseY - this.guiTop);
            if (clicked != null) {
                this.container.selectCpu(clicked.serial());
                returnToParent();
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
        if (keyCode == Keyboard.KEY_ESCAPE) {
            returnToParent();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void returnToParent() {
        switchToScreen(this.parent);
        this.parent.returnFromSubScreen(this);
    }

    private void toggleSortMode() {
        this.sortMode = this.sortMode.next();
        invalidateVisibleCpus();
        updateScrollbar();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void toggleTerminalStyle(SettingToggleButton button, boolean backwards) {
        var nextValue = (TerminalStyle) button.getNextValue(backwards);
        button.set(nextValue);
        AEConfig.instance().setTerminalStyle(nextValue);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private void updateSearch() {
        String text = this.searchField.getText();
        if (!this.searchText.equals(text)) {
            this.searchText = text;
            invalidateVisibleCpus();
            this.scrollbar.setCurrentScroll(0);
        }
    }

    private void invalidateVisibleCpus() {
        this.filteredList = CraftConfirmCpuList.EMPTY;
    }

    private void updateScrollbar() {
        int hiddenRows = Math.max(0, getVisibleCpus().size() - this.visibleRows);
        this.scrollbar.setHeight(this.visibleRows * ROW_HEIGHT - 1);
        this.scrollbar.setRange(0, hiddenRows, Math.max(1, this.visibleRows / 2));
    }

    private List<CraftConfirmCpuList.Entry> getVisibleCpus() {
        CraftConfirmCpuList cpuList = this.container.cpuList;
        if (this.filteredList == cpuList) {
            return this.visibleCpus;
        }

        this.filteredList = cpuList;
        this.visibleCpus.clear();
        String normalizedSearch = this.searchText.trim().toLowerCase(Locale.ROOT);
        for (CraftConfirmCpuList.Entry entry : cpuList.cpus()) {
            if (normalizedSearch.isEmpty()
                || getCpuName(entry).getFormattedText().toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
                this.visibleCpus.add(entry);
            }
        }
        this.visibleCpus.sort(this.sortMode.comparator());
        return this.visibleCpus;
    }

    @Nullable
    private CraftConfirmCpuList.Entry getEntryAt(int localMouseX, int localMouseY) {
        if (localMouseX < LIST_X || localMouseX >= LIST_X + LIST_WIDTH
            || localMouseY < LIST_Y || localMouseY >= LIST_Y + this.visibleRows * ROW_HEIGHT) {
            return null;
        }

        int row = (localMouseY - LIST_Y) / ROW_HEIGHT;
        int index = this.scrollbar.getCurrentScroll() + row;
        List<CraftConfirmCpuList.Entry> entries = getVisibleCpus();
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    private void updateLayout() {
        int availableHeight = this.height - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_VISIBLE_ROWS, (availableHeight - LIST_Y - FOOTER_HEIGHT) / ROW_HEIGHT);
        this.visibleRows = Math.max(MIN_VISIBLE_ROWS, AEConfig.instance().getTerminalStyle().getRows(possibleRows));
        this.xSize = CraftingScreenLayout.WIDTH;
        this.ySize = LIST_Y + this.visibleRows * ROW_HEIGHT + FOOTER_HEIGHT;
        invalidateExclusionZonesCache();
    }

    private void drawCpuRow(CraftConfirmCpuList.Entry entry, int y, boolean hovered) {
        int backgroundColor = entry.selected() ? SELECTED_COLOR : hovered ? HOVER_COLOR : 0x22000000;
        Gui.drawRect(LIST_X, y, LIST_X + LIST_WIDTH, y + ROW_HEIGHT - 1, backgroundColor);

        ITextComponent name = getCpuName(entry);
        int nameRightX = ROW_STORAGE_ICON_X - 8;
        String mergeText = null;
        int mergeX = 0;
        if (entry.mergeable()) {
            mergeText = GuiText.Merge.getLocal();
            mergeX = ROW_MERGE_RIGHT_X - this.fontRenderer.getStringWidth(mergeText);
            nameRightX = mergeX - 4;
        }

        String displayName = this.fontRenderer.trimStringToWidth(name.getFormattedText(), nameRightX - ROW_TEXT_X);
        this.fontRenderer.drawString(displayName, ROW_TEXT_X, y + 3, TEXT_COLOR);
        this.fontRenderer.drawString(formatStorage(entry.storage()), ROW_TEXT_X, y + 12, MUTED_TEXT_COLOR);

        Icon.S_STORAGE.getBlitter().dest(ROW_STORAGE_ICON_X, y + 6).blit();
        this.fontRenderer.drawString(formatCompactStorage(entry.storage()), ROW_STORAGE_TEXT_X, y + 7, TEXT_COLOR);

        Icon.S_PROCESSOR.getBlitter().dest(ROW_PROCESSOR_ICON_X, y + 6).blit();
        this.fontRenderer.drawString(Integer.toString(entry.coProcessors()), ROW_PROCESSOR_TEXT_X, y + 7, TEXT_COLOR);

        if (mergeText != null) {
            this.fontRenderer.drawString(mergeText, mergeX, y + 3, MERGE_COLOR);
        }
    }

    private List<ITextComponent> getCpuTooltip(CraftConfirmCpuList.Entry entry) {
        var tooltip = new ObjectArrayList<ITextComponent>(5);
        tooltip.add(getCpuName(entry));
        tooltip.add(gray(ButtonToolTips.CpuStatusStorage.text(formatStorage(entry.storage()))));
        if (entry.coProcessors() == 1) {
            tooltip.add(gray(ButtonToolTips.CpuStatusCoProcessor.text(String.valueOf(entry.coProcessors()))));
        } else if (entry.coProcessors() > 1) {
            tooltip.add(gray(ButtonToolTips.CpuStatusCoProcessors.text(String.valueOf(entry.coProcessors()))));
        }
        if (entry.mergeable()) {
            tooltip.add(gray(GuiText.ConfirmCraftMergeAvailable.text()));
        }
        return tooltip;
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return Collections.singleton(searchField);
    }

    private enum CraftConfirmCpuSortMode {
        CAPACITY {
            @Override
            Comparator<CraftConfirmCpuList.Entry> comparator() {
                return Comparator.comparingLong(CraftConfirmCpuList.Entry::storage).reversed()
                                 .thenComparing(Comparator.comparingInt(CraftConfirmCpuList.Entry::coProcessors).reversed())
                                 .thenComparing(GuiCraftConfirmCpuList::sortName)
                                 .thenComparingInt(CraftConfirmCpuList.Entry::serial);
            }

            @Override
            ButtonToolTips tooltip() {
                return ButtonToolTips.SortByCapacity;
            }

            @Override
            Icon icon() {
                return Icon.CRAFT_CONFIRM_CPU_LIST_STORAGE;
            }
        },
        COPROCESSORS {
            @Override
            Comparator<CraftConfirmCpuList.Entry> comparator() {
                return Comparator.comparingInt(CraftConfirmCpuList.Entry::coProcessors).reversed()
                                 .thenComparing(Comparator.comparingLong(CraftConfirmCpuList.Entry::storage).reversed())
                                 .thenComparing(GuiCraftConfirmCpuList::sortName)
                                 .thenComparingInt(CraftConfirmCpuList.Entry::serial);
            }

            @Override
            ButtonToolTips tooltip() {
                return ButtonToolTips.SortByCoProcessors;
            }

            @Override
            Icon icon() {
                return Icon.CRAFT_CONFIRM_CPU_LIST_PROCESSOR;
            }
        };

        abstract Comparator<CraftConfirmCpuList.Entry> comparator();

        abstract ButtonToolTips tooltip();

        abstract Icon icon();

        private CraftConfirmCpuSortMode next() {
            return this == CAPACITY ? COPROCESSORS : CAPACITY;
        }
    }

    private static final class SortButton extends IconButton {
        private CraftConfirmCpuSortMode mode = CraftConfirmCpuSortMode.CAPACITY;

        private SortButton(Runnable onPress) {
            super(onPress);
            setMessage(this.mode.tooltip().text());
        }

        private void setMode(CraftConfirmCpuSortMode mode) {
            if (this.mode != mode) {
                this.mode = mode;
                setMessage(mode.tooltip().text());
            }
        }

        @Override
        protected Icon getIcon() {
            return this.mode.icon();
        }
    }
}
