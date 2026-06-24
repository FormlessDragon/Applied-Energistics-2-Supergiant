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

package ae2.client.gui.widgets;

import ae2.api.client.AEKeyRendering;
import ae2.api.config.CpuSelectionMode;
import ae2.api.config.Setting;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Tooltip;
import ae2.client.gui.me.crafting.CraftingTimeDisplay;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.Color;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.client.render.overlay.CraftingCpuHighlightHandler;
import ae2.container.implementations.ContainerCraftingStatus;
import ae2.core.AELog;
import ae2.core.definitions.AEParts;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.crafting.execution.CraftingSupplierLocator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class CPUSelectionList implements ICompositeWidget {
    private static final int HEADER_HEIGHT = 31;
    private static final int FOOTER_HEIGHT = 7;
    private static final int SCROLLBAR_X = 86;
    private static final int SCROLLBAR_WIDTH = 17;
    private static final int CONTENT_WIDTH = SCROLLBAR_X;
    private static final int SMALL_SQUARE_BUTTON_SOURCE_SIZE = 16;
    private static final int ROW_SMALL_BUTTON_SIZE = 8;
    private static final int HEADER_BUTTON_SIZE = 12;
    private static final float SMALL_TEXT_SCALE = 0.666f;
    private static final int MODE_BUTTON_X = 58;
    private static final int MODE_BUTTON_Y = 10;
    private static final int MODE_BUTTON_SIZE = ROW_SMALL_BUTTON_SIZE;
    private static final int SEARCH_X = 7;
    private static final int SEARCH_Y = 17;
    private static final int SEARCH_WIDTH = 60;
    private static final int SEARCH_HEIGHT = 12;
    private static final int HEADER_BUTTON_Y = 4;
    private static final int HEADER_BUTTON_WIDTH = HEADER_BUTTON_SIZE;
    private static final int HEADER_BUTTON_HEIGHT = HEADER_BUTTON_SIZE;
    private static final int MODE_FILTER_BUTTON_X = 72;
    private static final int ACTIVITY_FILTER_BUTTON_X = 85;
    private static final int SORT_MODE_BUTTON_X = 72;
    private static final int SORT_DIRECTION_BUTTON_X = 85;
    private static final int SORT_ROW_Y = 17;
    private static final int RENAME_BUTTON_WIDTH = 12;
    private static final int RENAME_BUTTON_HEIGHT = 12;
    private static final int RENAME_BUTTON_X = -14;
    private static final int RENAME_BUTTON_Y = 5;
    private static final int RENAME_FIELD_X = 2;
    private static final int RENAME_FIELD_Y = 3;
    private static final int RENAME_FIELD_HEIGHT = 12;
    private static final int LIST_CONTENT_X = 17;
    private static final int ROW_BACKGROUND_WIDTH = 67;
    private static final int ROW_BACKGROUND_HEIGHT = 22;
    private static final int BUTTON_CONTENT_INSET = 0;
    private static final Setting<ModeFilter> MODE_FILTER_SETTING = new Setting<>(
        "crafting_cpu_mode_filter",
        ModeFilter.class);
    private static final Setting<ActivityFilter> ACTIVITY_FILTER_SETTING = new Setting<>(
        "crafting_cpu_activity_filter",
        ActivityFilter.class);
    private static final Setting<SortMode> SORT_MODE_SETTING = new Setting<>(
        "crafting_cpu_sort_mode",
        SortMode.class);
    private static final Setting<SortDirection> SORT_DIRECTION_SETTING = new Setting<>(
        "crafting_cpu_sort_direction",
        SortDirection.class);
    private final Blitter background;
    private final ContainerCraftingStatus container;
    private final Color textColor;
    private final int selectedColor;
    private final Scrollbar scrollbar;
    private final IntSupplier visibleRowsSupplier;
    private final GuiStyle style;
    private final AETextField searchField;
    private final List<HeaderSettingButton<?>> headerButtons;
    private final List<RowModeButton> rowModeButtons = new ObjectArrayList<>();
    private final ViewState viewState = new ViewState();
    private final ObjectArrayList<ContainerCraftingStatus.CraftingCpuListEntry> visibleCpus = new ObjectArrayList<>();
    private Rectangle bounds = new Rectangle(0, 0, 0, 0);
    @Nullable
    private ViewResult lastViewResult;
    private int lastSelectedCpuSerial = Integer.MIN_VALUE;
    @Nullable
    private ContainerCraftingStatus.CraftingCpuList lastCpuList;
    @Nullable
    private ConfirmableTextField activeRenameField;
    private int activeRenameCpuSerial = -1;
    private String activeRenameOriginalName = "";
    @Nullable
    private Runnable onSelectionChanged;
    private Rectangle screenBounds = new Rectangle();
    @Nullable
    private AEBaseGui<?> screen;
    private RowPressAction pressedRowAction = RowPressAction.NONE;
    private int pressedRowCpuSerial = -1;
    private int pressedRowMouseButton = -1;

    public CPUSelectionList(ContainerCraftingStatus container, Scrollbar scrollbar, GuiStyle style,
                            IntSupplier visibleRowsSupplier) {
        this.container = container;
        this.scrollbar = scrollbar;
        this.visibleRowsSupplier = visibleRowsSupplier;
        this.style = style;
        this.background = style.getImage("cpuList");
        this.textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR);
        this.selectedColor = style.getColor(PaletteColor.SELECTION_COLOR).toARGB();
        this.scrollbar.setCaptureMouseWheel(false);
        this.searchField = new AETextField(style, Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(25);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setVisible(true);
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setResponder(this::setSearchText);
        this.searchField.setTooltipMessage(List.of(GuiText.CraftingCpuListSearchTooltip.text()));
        HeaderSettingButton<ModeFilter> modeFilterButton = new HeaderSettingButton<>(
            MODE_FILTER_SETTING,
            MODE_FILTER_BUTTON_X,
            HEADER_BUTTON_Y,
            viewState::getModeFilter,
            this::applyModeFilter,
            ModeFilter::icon,
            this::getModeFilterTooltipLines);
        HeaderSettingButton<ActivityFilter> activityFilterButton = new HeaderSettingButton<>(
            ACTIVITY_FILTER_SETTING,
            ACTIVITY_FILTER_BUTTON_X,
            HEADER_BUTTON_Y,
            viewState::getActivityFilter,
            this::applyActivityFilter,
            ActivityFilter::icon,
            this::getActivityFilterTooltipLines);
        HeaderSettingButton<SortMode> sortModeButton = new HeaderSettingButton<>(
            SORT_MODE_SETTING,
            SORT_MODE_BUTTON_X,
            SORT_ROW_Y,
            viewState::getSortMode,
            this::applySortMode,
            SortMode::icon,
            this::getSortModeTooltipLines);
        HeaderSettingButton<SortDirection> sortDirectionButton = new HeaderSettingButton<>(
            SORT_DIRECTION_SETTING,
            SORT_DIRECTION_BUTTON_X,
            SORT_ROW_Y,
            viewState::getSortDirection,
            this::applySortDirection,
            SortDirection::icon,
            this::getSortDirectionTooltipLines);
        this.headerButtons = List.of(
            modeFilterButton,
            activityFilterButton,
            sortModeButton,
            sortDirectionButton);
    }

    private static ITextComponent gray(ITextComponent text) {
        return text.setStyle(new Style().setColor(TextFormatting.GRAY));
    }

    private static String normalizedName(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        return getCpuNameStatic(cpu).getFormattedText().toLowerCase(Locale.ROOT);
    }

    static String getRenameInitialText(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        return cpu.name() != null ? cpu.name().getFormattedText() : "";
    }

    private static ITextComponent getCpuNameStatic(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        return cpu.name() != null ? cpu.name() : GuiText.CpuFallbackName.text(cpu.serial());
    }

    private static boolean isActive(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        return cpu.currentJob() != null;
    }

    private static void drawScaledString(String text, int x, int y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1.0f);
        Minecraft.getMinecraft().fontRenderer.drawString(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private static int scaledDimension(int size, float scale) {
        return Math.max(1, Math.round(size * scale));
    }

    private static void drawScaledKey(int x, int y, AEKey what) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1.0f);
        AEKeyRendering.drawInGui(Minecraft.getMinecraft(), 0, 0, what);
        GlStateManager.popMatrix();
    }

    private static void drawSquareButtonBackground(int x, int y, int width, int height, boolean hovered) {
        Icon backgroundIcon = hovered
            ? Icon.SMALL_SQUARE_BUTTON_BACKGROUND_HOVER
            : Icon.SMALL_SQUARE_BUTTON_BACKGROUND;
        drawScaledIcon(
            x,
            y,
            backgroundIcon,
            Math.min(width / (float) SMALL_SQUARE_BUTTON_SOURCE_SIZE, height / (float) SMALL_SQUARE_BUTTON_SOURCE_SIZE),
            10);
    }

    private static void drawScaledIcon(int x, int y, Icon icon, float scale, int zOffset) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1.0f);
        icon.getBlitter().dest(0, 0).zOffset(zOffset).blit();
        GlStateManager.popMatrix();
    }

    private static void drawBoxedIcon(int x, int y, int width, int height, Icon icon) {
        int contentWidth = Math.max(1, width - BUTTON_CONTENT_INSET * 2);
        int contentHeight = Math.max(1, height - BUTTON_CONTENT_INSET * 2);
        float scale = Math.min(contentWidth / (float) icon.width, contentHeight / (float) icon.height);
        int drawWidth = scaledDimension(icon.width, scale);
        int drawHeight = scaledDimension(icon.height, scale);
        drawScaledIcon(
            x + (width - drawWidth) / 2,
            y + (height - drawHeight) / 2,
            icon,
            scale,
            20);
    }

    private static void drawBoxedItemStack(int x, int y, int width, int height, ItemStack stack) {
        int contentWidth = Math.max(1, width - BUTTON_CONTENT_INSET * 2);
        int contentHeight = Math.max(1, height - BUTTON_CONTENT_INSET * 2);
        float scale = Math.min(contentWidth / 16.0f, contentHeight / 16.0f);
        int drawWidth = scaledDimension(16, scale);
        int drawHeight = scaledDimension(16, scale);
        drawScaledItemStack(
            x + (width - drawWidth) / 2,
            y + (height - drawHeight) / 2,
            stack,
            scale);
    }

    private static void drawScaledItemStack(int x, int y, ItemStack stack, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 20);
        GlStateManager.scale(scale, scale, 1.0f);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            stack,
            0,
            0,
            null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private static <T extends Enum<T>> SettingToggleButton.ButtonAppearance createHeaderButtonAppearance(
        Setting<T> setting,
        T value,
        HeaderIcon icon,
        List<ITextComponent> tooltipLines) {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(value, "Value");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(tooltipLines, "tooltipLines");

        return switch (icon.kind()) {
            case ITEM -> {
                var itemStack = Objects.requireNonNull(icon.itemStack(), "itemStack");
                if (itemStack.isEmpty()) {
                    String message = "Header setting button appearance item stack must not be empty for "
                        + setting.getName() + " Value " + value.name();
                    AELog.error(message);
                    throw new IllegalStateException(message);
                }
                yield new SettingToggleButton.ButtonAppearance(null, itemStack.getItem(), List.copyOf(tooltipLines));
            }
            case ICON, SMALL_ICON -> new SettingToggleButton.ButtonAppearance(
                Objects.requireNonNull(icon.icon(), "icon"),
                null,
                List.copyOf(tooltipLines));
        };
    }

    @Override
    public void setPosition(Point position) {
        this.bounds = new Rectangle(position.x(), position.y(), bounds.width, bounds.height);
        moveSearchField();
        moveHeaderButtons();
        moveActiveRenameField();
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void setSize(int width, int height) {
        this.bounds = new Rectangle(bounds.x, bounds.y, width, height);
        resizeSearchField();
        moveActiveRenameField();
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        this.screen = screen;
        this.screenBounds = new Rectangle(bounds);
        moveSearchField();
        moveHeaderButtons();
        moveActiveRenameField();
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (activeRenameField != null && activeRenameField.isFocused()) {
            return false;
        }
        scrollbar.onMouseWheel(mousePos, delta);
        return true;
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        int absoluteMouseX = toAbsoluteX(mousePos.x());
        int absoluteMouseY = toAbsoluteY(mousePos.y());

        if (activeRenameField != null && activeRenameField.getVisible()
            && activeRenameField.isMouseOver(absoluteMouseX, absoluteMouseY)) {
            if (button == 1) {
                activeRenameField.setText("");
            }
            activeRenameField.mouseClicked(absoluteMouseX, absoluteMouseY, button);
            return true;
        }

        finishRename(true);

        if (searchField.isMouseOver(absoluteMouseX, absoluteMouseY)) {
            if (button == 1) {
                searchField.setText("");
            }
            searchField.mouseClicked(absoluteMouseX, absoluteMouseY, button);
            return true;
        }

        for (var headerButton : headerButtons) {
            if (headerButton.handleMouseDown(absoluteMouseX, absoluteMouseY, button)) {
                clearPressedRowAction();
                return true;
            }
        }

        var actionCpu = hitTestActionButton(mousePos);
        if (button == 0 && actionCpu != null) {
            setPressedRowAction(RowPressAction.ACTION, actionCpu.serial(), button);
            return true;
        }

        var modeButtonCpu = hitTestModeButton(mousePos);
        if ((button == 0 || button == 1) && modeButtonCpu != null) {
            setPressedRowAction(RowPressAction.MODE, modeButtonCpu.serial(), button);
            var rowModeButton = getOrCreateRowModeButton(modeButtonCpu, absoluteMouseX, absoluteMouseY);
            if (rowModeButton.handleMouseDown(absoluteMouseX, absoluteMouseY, button)) {
                return true;
            }
            clearPressedRowAction();
        }

        var cpu = hitTestCpu(mousePos);
        if (button == 0 && cpu != null) {
            setPressedRowAction(RowPressAction.SELECT, cpu.serial(), button);
            return true;
        }

        clearPressedRowAction();
        return false;
    }

    @Override
    public boolean wantsAllMouseDownEvents() {
        return activeRenameField != null && activeRenameField.getVisible();
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        int absoluteMouseX = toAbsoluteX(mousePos.x());
        int absoluteMouseY = toAbsoluteY(mousePos.y());

        for (var headerButton : headerButtons) {
            if (headerButton.handleMouseUp(absoluteMouseX, absoluteMouseY, button)) {
                clearPressedRowAction();
                return true;
            }
        }

        if (!isPressedRowAction(button)) {
            clearPressedRowAction();
            return false;
        }

        RowPressAction action = pressedRowAction;
        int cpuSerial = pressedRowCpuSerial;
        clearPressedRowAction();

        var actionCpu = hitTestActionButton(mousePos);
        if (action == RowPressAction.ACTION && actionCpu != null && actionCpu.serial() == cpuSerial) {
            if (GuiScreen.isShiftKeyDown()) {
                highlightCpuAndClose(actionCpu);
            } else {
                openRenameField(actionCpu);
            }
            return true;
        }

        var modeButtonCpu = hitTestModeButton(mousePos);
        if (action == RowPressAction.MODE && modeButtonCpu != null && modeButtonCpu.serial() == cpuSerial) {
            var rowModeButton = getOrCreateRowModeButton(modeButtonCpu, absoluteMouseX, absoluteMouseY);
            return rowModeButton.handleMouseUp(absoluteMouseX, absoluteMouseY, button);
        }

        var cpu = hitTestCpu(mousePos);
        if (action == RowPressAction.SELECT && cpu != null && cpu.serial() == cpuSerial) {
            container.selectCpu(cpu.serial());
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyTyped(char typedChar, int keyCode) {
        if (activeRenameField != null && activeRenameField.getVisible() && activeRenameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                finishRename(false);
                return true;
            }
            return activeRenameField.textboxKeyTyped(typedChar, keyCode);
        }

        if (keyCode == Keyboard.KEY_ESCAPE && searchField.isFocused()) {
            searchField.setFocused(false);
            return true;
        }

        return searchField.textboxKeyTyped(typedChar, keyCode);
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (searchField.isMouseOver(toAbsoluteX(mouseX), toAbsoluteY(mouseY))) {
            return new Tooltip(searchField.getTooltipMessage());
        }

        var headerTooltip = getHeaderButtonTooltip(toAbsoluteX(mouseX), toAbsoluteY(mouseY));
        if (headerTooltip != null) {
            return new Tooltip(headerTooltip);
        }

        var actionCpu = hitTestActionButton(new Point(mouseX, mouseY));
        if (actionCpu != null) {
            return new Tooltip(getActionButtonTooltip(actionCpu));
        }

        var modeButtonCpu = hitTestModeButton(new Point(mouseX, mouseY));
        if (modeButtonCpu != null) {
            var rowModeButton = getOrCreateRowModeButton(modeButtonCpu, toAbsoluteX(mouseX), toAbsoluteY(mouseY));
            return new Tooltip(rowModeButton.getTooltipMessage());
        }

        var cpu = hitTestCpu(new Point(mouseX, mouseY));
        if (cpu == null) {
            return null;
        }

        int tooltipLineCount = 2;
        if (cpu.coProcessors() > 0) {
            tooltipLineCount++;
        }
        if (cpu.mode() != CpuSelectionMode.ANY) {
            tooltipLineCount++;
        }
        if (cpu.currentJob() != null) {
            tooltipLineCount += 2;
        }
        var tooltipLines = new ObjectArrayList<ITextComponent>(tooltipLineCount);
        tooltipLines.add(getCpuName(cpu));
        tooltipLines.add(gray(ButtonToolTips.CpuStatusStorage.text(formatStorage(cpu))));

        int coProcessors = cpu.coProcessors();
        if (coProcessors == 1) {
            tooltipLines.add(gray(ButtonToolTips.CpuStatusCoProcessor.text(String.valueOf(coProcessors))));
        } else if (coProcessors > 1) {
            tooltipLines.add(gray(ButtonToolTips.CpuStatusCoProcessors.text(String.valueOf(coProcessors))));
        }

        switch (cpu.mode()) {
            case PLAYER_ONLY -> tooltipLines.add(gray(ButtonToolTips.CpuSelectionModePlayersOnly.text()));
            case MACHINE_ONLY -> tooltipLines.add(gray(ButtonToolTips.CpuSelectionModeAutomationOnly.text()));
            default -> {
            }
        }

        var currentJob = cpu.currentJob();
        if (currentJob != null) {
            String amount = currentJob.what().formatAmount(currentJob.amount(), AmountFormat.FULL);
            tooltipLines.add(gray(ButtonToolTips.CpuStatusCrafting.text(amount)
                                                                  .appendText(" ")
                                                                  .appendSibling(currentJob.what().getDisplayName())));
            var elapsedTimeTooltip = CraftingTimeDisplay.getElapsedTimeTooltip(cpu.progress(), cpu.elapsedTimeNanos());
            tooltipLines.add(gray(new TextComponentTranslation(
                elapsedTimeTooltip.translationKey(),
                elapsedTimeTooltip.args())));
        }

        return new Tooltip(tooltipLines);
    }

    @Override
    public void updateBeforeRender() {
        refreshView();
        int rows = getVisibleRows();
        this.bounds.height = HEADER_HEIGHT + rows * getButtonRowHeight() + FOOTER_HEIGHT;
        this.scrollbar.setHeight(Math.max(1, rows * getButtonRowHeight() - 1));
        int hiddenRows = Math.max(0, visibleCpus.size() - rows);
        scrollbar.setRange(0, hiddenRows, Math.max(1, rows / 3));
        moveSearchField();
        resizeSearchField();
        moveHeaderButtons();
        moveActiveRenameField();
    }

    @Override
    public void drawBackgroundLayer(Rectangle screenBounds, Point mouse) {
        int x = screenBounds.x + this.bounds.x;

        int y = screenBounds.y + this.bounds.y;
        drawBackground(x, y);
        drawHeaderControls(mouse);

        x += LIST_CONTENT_X;
        y += HEADER_HEIGHT;

        var cpus = visibleCpus.subList(
            MathHelper.clamp(scrollbar.getCurrentScroll(), 0, visibleCpus.size()),
            MathHelper.clamp(scrollbar.getCurrentScroll() + getVisibleRows(), 0, visibleCpus.size()));
        var hoveredModeButtonCpu = hitTestModeButton(mouse);
        var hoveredActionCpu = hitTestActionButton(mouse);
        for (var cpu : cpus) {
            drawCpuRowBackground(cpu, x, y, cpu.serial() == container.getSelectedCpuSerial());

            boolean renamingThisCpu = activeRenameField != null && activeRenameField.getVisible()
                && activeRenameCpuSerial == cpu.serial();
            if (!renamingThisCpu) {
                drawScaledString(getCpuName(cpu).getFormattedText(), x + 3, y + 2, textColor.toARGB());
            }

            drawActionButton(x + RENAME_BUTTON_X, y + RENAME_BUTTON_Y,
                hoveredActionCpu != null && hoveredActionCpu.serial() == cpu.serial());

            var currentJob = cpu.currentJob();
            if (currentJob != null) {
                Icon.S_CRAFT.getBlitter().dest(x + 2, y + 9).blit();
                drawScaledString(currentJob.what().formatAmount(currentJob.amount(), AmountFormat.SLOT),
                    x + 14, y + 13, textColor.toARGB());
                drawScaledKey(x + 55, y + 9, currentJob.what());

                int progress = (int) (cpu.progress() * (ROW_BACKGROUND_WIDTH - 1));
                if (progress > 0) {
                    Gui.drawRect(
                        x,
                        y + ROW_BACKGROUND_HEIGHT - 2,
                        x + progress,
                        y + ROW_BACKGROUND_HEIGHT - 1,
                        container.getSelectedCpuSerial() == cpu.serial() ? 0xFF7da9d2 : selectedColor);
                }
            } else {
                Icon.S_STORAGE.getBlitter().dest(x + 2, y + 9).blit();
                drawScaledString(formatStorage(cpu), x + 14, y + 13, textColor.toARGB());

                if (cpu.coProcessors() > 0) {
                    Icon.S_PROCESSOR.getBlitter().dest(x + 32, y + 9).blit();
                    drawScaledString(String.valueOf(cpu.coProcessors()),
                        x + 44, y + 13, textColor.toARGB());
                }
                drawModeButton(
                    cpu,
                    x + MODE_BUTTON_X,
                    y + MODE_BUTTON_Y,
                    hoveredModeButtonCpu != null && hoveredModeButtonCpu.serial() == cpu.serial());
            }

            y += getButtonRowHeight();
        }
    }

    @Override
    public void drawAbsoluteLayer(Rectangle bounds, Point mouse) {
        searchField.drawTextBox();
        if (activeRenameField != null && activeRenameField.getVisible()) {
            activeRenameField.drawTextBox();
        }
    }

    @Override
    public void tick() {
        searchField.updateCursorCounter();
        searchField.tickKeyRepeat();
        if (activeRenameField != null && activeRenameField.getVisible()) {
            activeRenameField.updateCursorCounter();
            activeRenameField.tickKeyRepeat();
        }
    }

    public Collection<? extends net.minecraft.client.gui.GuiTextField> getTextFields() {
        if (activeRenameField != null && activeRenameField.getVisible()) {
            return List.of(searchField, activeRenameField);
        }
        return List.of(searchField);
    }

    public void setOnSelectionChanged(@Nullable Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    private void setSearchText(String text) {
        viewState.setSearchText(text);
        invalidateView();
    }

    private void invalidateView() {
        this.lastViewResult = null;
        this.scrollbar.setCurrentScroll(0);
    }

    private void refreshView() {
        var cpuList = container.cpuList;
        int selectedCpuSerial = container.getSelectedCpuSerial();
        if (lastViewResult != null && lastCpuList == cpuList && lastSelectedCpuSerial == selectedCpuSerial) {
            return;
        }

        ViewResult result = viewState.compute(cpuList.cpus(), selectedCpuSerial);
        lastViewResult = result;
        lastCpuList = cpuList;
        lastSelectedCpuSerial = selectedCpuSerial;
        visibleCpus.clear();
        visibleCpus.addAll(result.visibleCpus());
        int hiddenRows = Math.max(0, visibleCpus.size() - getVisibleRows());
        scrollbar.setRange(0, hiddenRows, Math.max(1, getVisibleRows() / 3));

        if (selectedCpuSerial != result.selectedCpuSerial()) {
            container.selectCpu(result.selectedCpuSerial());
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
        }

        if (activeRenameCpuSerial != -1 && visibleCpus.stream().noneMatch(cpu -> cpu.serial() == activeRenameCpuSerial)) {
            finishRename(false);
        }
    }

    private void moveSearchField() {
        searchField.move(screenBounds.x + bounds.x + SEARCH_X, screenBounds.y + bounds.y + SEARCH_Y);
    }

    private void resizeSearchField() {
        searchField.resize(SEARCH_WIDTH, SEARCH_HEIGHT);
    }

    private void moveHeaderButtons() {
        int absoluteLeft = screenBounds.x + bounds.x;
        int absoluteTop = screenBounds.y + bounds.y;
        for (var headerButton : headerButtons) {
            headerButton.moveTo(absoluteLeft + headerButton.localX(), absoluteTop + headerButton.localY());
        }
    }

    private void setPressedRowAction(RowPressAction action, int cpuSerial, int mouseButton) {
        this.pressedRowAction = action;
        this.pressedRowCpuSerial = cpuSerial;
        this.pressedRowMouseButton = mouseButton;
    }

    private boolean isPressedRowAction(int mouseButton) {
        return pressedRowAction != RowPressAction.NONE && pressedRowMouseButton == mouseButton;
    }

    private void clearPressedRowAction() {
        this.pressedRowAction = RowPressAction.NONE;
        this.pressedRowCpuSerial = -1;
        this.pressedRowMouseButton = -1;
    }

    private void drawHeaderControls(Point mouse) {
        int absoluteMouseX = toAbsoluteX(mouse.x());
        int absoluteMouseY = toAbsoluteY(mouse.y());
        for (var headerButton : headerButtons) {
            headerButton.drawButton(Minecraft.getMinecraft(), absoluteMouseX, absoluteMouseY, 0.0f);
        }
    }

    private void drawHeaderButton(int x, int y, boolean hovered) {
        drawSquareButtonBackground(x, y, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT, hovered);
    }

    private void drawHeaderButtonContent(int x, int y, HeaderIcon icon) {
        switch (icon.kind()) {
            case ITEM -> drawBoxedItemStack(x, y, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT,
                Objects.requireNonNull(icon.itemStack(), "itemStack"));
            case ICON, SMALL_ICON -> drawHeaderIcon(x, y, Objects.requireNonNull(icon.icon(), "icon"));
        }
    }

    private void drawHeaderIcon(int x, int y, Icon icon) {
        drawBoxedIcon(x, y, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT, icon);
    }

    private void drawActionButton(int x, int y, boolean hovered) {
        drawSquareButtonBackground(x, y, RENAME_BUTTON_WIDTH, RENAME_BUTTON_HEIGHT, hovered);
        Icon icon = GuiScreen.isShiftKeyDown() ? Icon.CRAFTING_CPU_HIGHLIGHT : Icon.CRAFTING_CPU_RENAME;
        drawBoxedIcon(x, y, RENAME_BUTTON_WIDTH, RENAME_BUTTON_HEIGHT, icon);
    }

    private void highlightCpuAndClose(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        CraftingCpuHighlightHandler.INSTANCE.showCpu(
            Minecraft.getMinecraft(),
            cpu.dimensionId(),
            cpu.corePos(),
            cpu.boundsMin(),
            cpu.boundsMax());
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    private void drawCpuRowBackground(ContainerCraftingStatus.CraftingCpuListEntry cpu, int x, int y, boolean focused) {
        resolveCpuRowBackgroundIcon(cpu, focused).getBlitter().dest(x, y).blit();
    }

    private RowModeButton getOrCreateRowModeButton(ContainerCraftingStatus.CraftingCpuListEntry cpu, int buttonX,
                                                   int buttonY) {

        for (var rowModeButton : rowModeButtons) {
            if (rowModeButton.cpuSerial() == cpu.serial()) {
                rowModeButton.moveTo(buttonX, buttonY);
                rowModeButton.set(cpu.mode());
                return rowModeButton;
            }
        }

        var rowModeButton = new RowModeButton(cpu.serial(), cpu.mode(), buttonX, buttonY);
        rowModeButtons.add(rowModeButton);
        return rowModeButton;
    }

    private Icon resolveCpuRowBackgroundIcon(ContainerCraftingStatus.CraftingCpuListEntry cpu, boolean focused) {
        ResourceLocation iconId = focused ? cpu.focusedBackgroundIcon() : cpu.unfocusedBackgroundIcon();
        Icon icon = Icon.byId(iconId);
        if (icon == null) {
            String message = String.format(
                "Received unregistered %s crafting CPU list row background icon %s for serial %d",
                focused ? "focused" : "unfocused",
                iconId,
                cpu.serial());
            AELog.error(message);
            throw new IllegalStateException(message);
        }

        try {
            icon.getBlitter();
        } catch (RuntimeException e) {
            AELog.error(e, String.format(
                "Failed to resolve %s crafting CPU list row background icon %s for serial %d",
                focused ? "focused" : "unfocused",
                iconId,
                cpu.serial()));
            throw e;
        }
        if (icon.width != ROW_BACKGROUND_WIDTH || icon.height != ROW_BACKGROUND_HEIGHT) {
            String message = String.format(
                "Received %s crafting CPU list row background icon %s with invalid size %dx%d for serial %d",
                focused ? "focused" : "unfocused",
                iconId,
                icon.width,
                icon.height,
                cpu.serial());
            AELog.error(message);
            throw new IllegalStateException(message);
        }
        return icon;
    }

    private ContainerCraftingStatus.CraftingCpuListEntry hitTestCpu(Point mousePos) {
        int relX = mousePos.x() - bounds.x - LIST_CONTENT_X;
        if (relX < 0 || relX >= ROW_BACKGROUND_WIDTH) {
            return null;
        }

        int relY = mousePos.y() - bounds.y - HEADER_HEIGHT;
        int buttonHeight = getButtonRowHeight();
        int buttonIdx = scrollbar.getCurrentScroll() + relY / buttonHeight;
        if (relY < 0 || relY >= getVisibleRows() * buttonHeight || relY % buttonHeight == ROW_BACKGROUND_HEIGHT) {
            return null;
        }

        if (buttonIdx < 0 || buttonIdx >= visibleCpus.size()) {
            return null;
        }
        return visibleCpus.get(buttonIdx);
    }

    @Nullable
    private ContainerCraftingStatus.CraftingCpuListEntry hitTestModeButton(Point mousePos) {
        var cpu = hitTestCpu(mousePos);
        if (cpu == null || cpu.currentJob() != null) {
            return null;
        }

        int relX = mousePos.x() - bounds.x - LIST_CONTENT_X;

        int relY = mousePos.y() - bounds.y - HEADER_HEIGHT;
        int buttonHeight = getButtonRowHeight();
        int rowY = relY % buttonHeight;

        if (relX < MODE_BUTTON_X || relX >= MODE_BUTTON_X + MODE_BUTTON_SIZE) {
            return null;
        }

        if (rowY < MODE_BUTTON_Y || rowY >= MODE_BUTTON_Y + MODE_BUTTON_SIZE) {
            return null;
        }

        return cpu;
    }

    private void drawModeButton(ContainerCraftingStatus.CraftingCpuListEntry cpu, int x, int y, boolean hovered) {
        var rowModeButton = getOrCreateRowModeButton(cpu, x, y);
        rowModeButton.moveTo(x, y);
        rowModeButton.set(cpu.mode());
        rowModeButton.setHoveredDirect(hovered);
        rowModeButton.drawButton(Minecraft.getMinecraft(), x, y, 0.0f);
    }

    private SettingToggleButton.ButtonAppearance createCpuModeAppearance(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> new SettingToggleButton.ButtonAppearance(
                Icon.CRAFT_HAMMER,
                null,
                List.of(ButtonToolTips.CpuSelectionMode.text(), gray(ButtonToolTips.CpuSelectionModeAny.text())));
            case PLAYER_ONLY -> new SettingToggleButton.ButtonAppearance(
                null,
                AEParts.TERMINAL.stack().getItem(),
                List.of(ButtonToolTips.CpuSelectionMode.text(), gray(ButtonToolTips.CpuSelectionModePlayersOnly.text())));
            case MACHINE_ONLY -> new SettingToggleButton.ButtonAppearance(
                null,
                AEParts.EXPORT_BUS.stack().getItem(),
                List.of(ButtonToolTips.CpuSelectionMode.text(), gray(ButtonToolTips.CpuSelectionModeAutomationOnly.text())));
        };
    }

    private String formatStorage(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        long storage = cpu.storage();
        if (storage >= 1024 * 1024) {
            return (storage / (1024 * 1024)) + "M";
        }
        return (storage / 1024) + "k";
    }

    @Nullable
    private ContainerCraftingStatus.CraftingCpuListEntry hitTestActionButton(Point mousePos) {
        int relY = mousePos.y() - bounds.y - HEADER_HEIGHT;
        int buttonHeight = getButtonRowHeight();
        int buttonIdx = scrollbar.getCurrentScroll() + relY / buttonHeight;
        if (relY < 0 || relY >= getVisibleRows() * buttonHeight || relY % buttonHeight == ROW_BACKGROUND_HEIGHT) {
            return null;
        }

        int relX = mousePos.x() - bounds.x - LIST_CONTENT_X;
        int rowY = relY % buttonHeight;
        if (buttonIdx < 0 || buttonIdx >= visibleCpus.size()) {
            return null;
        }
        var cpu = visibleCpus.get(buttonIdx);

        if (relX < RENAME_BUTTON_X || relX >= RENAME_BUTTON_X + RENAME_BUTTON_WIDTH) {
            return null;
        }
        if (rowY < RENAME_BUTTON_Y || rowY >= RENAME_BUTTON_Y + RENAME_BUTTON_HEIGHT) {
            return null;
        }
        return cpu;
    }

    private List<ITextComponent> getActionButtonTooltip(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        ObjectArrayList<ITextComponent> tooltip = new ObjectArrayList<>(4);
        tooltip.add(getCpuName(cpu));
        tooltip.add(gray(getCoreLocationTooltip(cpu)));
        tooltip.add(gray(GuiText.CraftingCpuRenameAction.text()));
        tooltip.add(gray(GuiText.CraftingCpuHighlight.text()));
        return tooltip;
    }

    private ITextComponent getCoreLocationTooltip(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        int currentDimension = Minecraft.getMinecraft().world != null
            ? Minecraft.getMinecraft().world.provider.getDimension()
            : cpu.dimensionId();
        if (cpu.dimensionId() == currentDimension) {
            return GuiText.CraftingCpuCoreLocation.text(
                cpu.corePos().getX(),
                cpu.corePos().getY(),
                cpu.corePos().getZ());
        }

        String dimensionName = CraftingSupplierLocator.getDimensionName(cpu.dimensionId());
        return GuiText.CraftingCpuCoreLocationInDimension.text(
            cpu.corePos().getX(),
            cpu.corePos().getY(),
            cpu.corePos().getZ(),
            dimensionName);
    }

    private int getVisibleRows() {
        return Math.max(1, visibleRowsSupplier.getAsInt());
    }

    private ITextComponent getCpuName(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        return getCpuNameStatic(cpu);
    }

    private void drawBackground(int x, int y) {
        background.copy()
                  .src(0, 0, CONTENT_WIDTH, HEADER_HEIGHT)
                  .dest(x, y)
                  .blit();
        drawScrollbarBackground(x + SCROLLBAR_X, y, getVisibleRows());

        int rowY = y + HEADER_HEIGHT;
        int rows = getVisibleRows();
        int rowHeight = getButtonRowHeight();
        int middleRowSourceY = HEADER_HEIGHT + rowHeight;
        int lastRowSourceY = background.getSrcHeight() - FOOTER_HEIGHT - rowHeight;
        for (int row = 0; row < rows; row++) {
            int sourceY;
            if (row == 0) {
                sourceY = HEADER_HEIGHT;
            } else if (row == rows - 1) {
                sourceY = lastRowSourceY;
            } else {
                sourceY = middleRowSourceY;
            }
            background.copy()
                      .src(0, sourceY, CONTENT_WIDTH, rowHeight)
                      .dest(x, rowY)
                      .blit();
            rowY += rowHeight;
        }

        background.copy()
                  .src(0, background.getSrcHeight() - FOOTER_HEIGHT, CONTENT_WIDTH, FOOTER_HEIGHT)
                  .dest(x, rowY)
                  .blit();
        background.copy()
                  .src(SCROLLBAR_X, background.getSrcHeight() - FOOTER_HEIGHT, SCROLLBAR_WIDTH, FOOTER_HEIGHT)
                  .dest(x + SCROLLBAR_X, rowY)
                  .blit();
    }

    private void drawScrollbarBackground(int x, int y, int rows) {
        background.copy()
                  .src(SCROLLBAR_X, 0, SCROLLBAR_WIDTH, HEADER_HEIGHT)
                  .dest(x, y)
                  .blit();

        int rowY = y + HEADER_HEIGHT;
        int rowHeight = getButtonRowHeight();
        for (int row = 0; row < rows; row++) {
            background.copy()
                      .src(SCROLLBAR_X, HEADER_HEIGHT + rowHeight, SCROLLBAR_WIDTH, rowHeight)
                      .dest(x, rowY)
                      .blit();
            rowY += rowHeight;
        }

        background.copy()
                  .src(SCROLLBAR_X, background.getSrcHeight() - FOOTER_HEIGHT - 1, SCROLLBAR_WIDTH, 1)
                  .dest(x, rowY - 1)
                  .blit();
    }

    private int getButtonRowHeight() {
        return ROW_BACKGROUND_HEIGHT + 1;
    }

    private void openRenameField(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
        if (activeRenameField != null && activeRenameCpuSerial == cpu.serial()) {
            activeRenameField.setFocused(true);
            activeRenameField.selectAll();
            return;
        }

        finishRename(false);
        activeRenameCpuSerial = cpu.serial();
        activeRenameOriginalName = getRenameInitialText(cpu);
        activeRenameField = new ConfirmableTextField(style, Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        activeRenameField.setEnableBackgroundDrawing(false);
        activeRenameField.setMaxStringLength(32);
        activeRenameField.setOnConfirm(() -> finishRename(true));
        activeRenameField.setText(activeRenameOriginalName);
        activeRenameField.setVisible(true);
        activeRenameField.setFocused(true);
        activeRenameField.selectAll();
        moveActiveRenameField();
    }

    private void moveActiveRenameField() {
        if (activeRenameField == null) {
            return;
        }

        var cpu = visibleCpus.stream().filter(entry -> entry.serial() == activeRenameCpuSerial).findFirst().orElse(null);
        if (cpu == null) {
            activeRenameField.setVisible(false);
            return;
        }

        int index = visibleCpus.indexOf(cpu) - scrollbar.getCurrentScroll();
        if (index < 0 || index >= getVisibleRows()) {
            activeRenameField.setVisible(false);
            return;
        }

        activeRenameField.move(
            screenBounds.x + bounds.x + LIST_CONTENT_X + RENAME_FIELD_X,
            screenBounds.y + bounds.y + HEADER_HEIGHT + index * getButtonRowHeight() + RENAME_FIELD_Y);
        int renameFieldWidth = (ROW_BACKGROUND_WIDTH - RENAME_FIELD_X - MODE_BUTTON_SIZE - 5) * 2;
        activeRenameField.resize(renameFieldWidth, RENAME_FIELD_HEIGHT);
        activeRenameField.setVisible(true);
    }

    private void finishRename(boolean apply) {
        if (activeRenameField == null) {
            return;
        }

        if (apply) {
            submitRename();
        }

        activeRenameField.setFocused(false);
        activeRenameField.setVisible(false);
        activeRenameField = null;
        activeRenameCpuSerial = -1;
        activeRenameOriginalName = "";
    }

    private void submitRename() {
        if (activeRenameField == null) {
            return;
        }
        String newName = activeRenameField.getText();
        if (Objects.equals(newName, activeRenameOriginalName)) {
            return;
        }
        this.container.renameCpu(activeRenameCpuSerial, newName);
    }

    private void applyModeFilter(ModeFilter modeFilter) {
        if (viewState.getModeFilter() == modeFilter) {
            return;
        }
        viewState.setModeFilter(modeFilter);
        invalidateView();
    }

    private void applyActivityFilter(ActivityFilter activityFilter) {
        if (viewState.getActivityFilter() == activityFilter) {
            return;
        }
        viewState.setActivityFilter(activityFilter);
        invalidateView();
    }

    private void applySortMode(SortMode sortMode) {
        if (viewState.getSortMode() == sortMode) {
            return;
        }
        viewState.setSortMode(sortMode);
        invalidateView();
    }

    private void applySortDirection(SortDirection sortDirection) {
        if (viewState.getSortDirection() == sortDirection) {
            return;
        }
        viewState.setSortDirection(sortDirection);
        invalidateView();
    }

    private List<ITextComponent> getModeFilterTooltipLines(ModeFilter modeFilter) {
        return List.of(
            GuiText.CraftingCpuModeFilter.text(),
            gray(modeFilter.tooltip()));
    }

    private List<ITextComponent> getActivityFilterTooltipLines(ActivityFilter activityFilter) {
        return List.of(
            GuiText.CraftingCpuActivityFilter.text(),
            gray(activityFilter.tooltip()));
    }

    private List<ITextComponent> getSortModeTooltipLines(SortMode sortMode) {
        return List.of(
            GuiText.CraftingCpuSort.text(),
            gray(sortMode.tooltip()));
    }

    private List<ITextComponent> getSortDirectionTooltipLines(SortDirection sortDirection) {
        return List.of(
            ButtonToolTips.SortOrder.text(),
            gray(sortDirection.tooltip()),
            gray(sortDirection.description()));
    }

    @Nullable
    private List<ITextComponent> getHeaderButtonTooltip(int absoluteMouseX, int absoluteMouseY) {
        for (var headerButton : headerButtons) {
            var tooltipLines = headerButton.getTooltipMessage(absoluteMouseX, absoluteMouseY);
            if (tooltipLines != null) {
                return tooltipLines;
            }
        }
        return null;
    }

    private int toAbsoluteX(int relativeMouseX) {
        return screenBounds.x + relativeMouseX;
    }

    private int toAbsoluteY(int relativeMouseY) {
        return screenBounds.y + relativeMouseY;
    }

    private AEBaseGui<?> requireHostScreen() {
        if (screen != null) {
            return screen;
        }

        String message = "CPU selection list header controls require a populated host screen before interaction";
        AELog.error(message);
        throw new IllegalStateException(message);
    }

    public enum ModeFilter {
        ALL(GuiText.CraftingCpuFilterAll.text(),
            HeaderIcon.ofIcon(Icon.CRAFTING_CPU_MODE_ALL)),
        PLAYER_ONLY(ButtonToolTips.CpuSelectionModePlayersOnly.text(),
            HeaderIcon.ofItem(AEParts.TERMINAL.stack())),
        MACHINE_ONLY(ButtonToolTips.CpuSelectionModeAutomationOnly.text(),
            HeaderIcon.ofItem(AEParts.EXPORT_BUS.stack())),
        ANY_ONLY(ButtonToolTips.CpuSelectionModeAny.text(),
            HeaderIcon.ofSmallIcon());

        public static final ModeFilter[] VALUES = ModeFilter.values();
        private final ITextComponent tooltip;
        private final HeaderIcon icon;

        ModeFilter(ITextComponent tooltip, HeaderIcon icon) {
            this.tooltip = tooltip;
            this.icon = icon;
        }

        public ITextComponent tooltip() {
            return tooltip;
        }

        HeaderIcon icon() {
            return icon;
        }

        public ModeFilter next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public boolean matches(CpuSelectionMode mode) {
            return switch (this) {
                case ALL -> true;
                case PLAYER_ONLY -> mode == CpuSelectionMode.PLAYER_ONLY;
                case MACHINE_ONLY -> mode == CpuSelectionMode.MACHINE_ONLY;
                case ANY_ONLY -> mode == CpuSelectionMode.ANY;
            };
        }
    }

    public enum ActivityFilter {
        ALL(GuiText.CraftingCpuFilterAll.text(), HeaderIcon.ofIcon(Icon.CRAFTING_CPU_ACTIVITY_ALL)),
        ACTIVE(GuiText.CraftingCpuActivityActive.text(), HeaderIcon.ofIcon(Icon.CRAFTING_CPU_ACTIVITY_ACTIVE)),
        INACTIVE(GuiText.CraftingCpuActivityInactive.text(), HeaderIcon.ofIcon(Icon.CRAFTING_CPU_ACTIVITY_INACTIVE));

        public static final ActivityFilter[] VALUES = ActivityFilter.values();
        private final ITextComponent tooltip;
        private final HeaderIcon icon;

        ActivityFilter(ITextComponent tooltip, HeaderIcon icon) {
            this.tooltip = tooltip;
            this.icon = icon;
        }

        public ITextComponent tooltip() {
            return tooltip;
        }

        HeaderIcon icon() {
            return icon;
        }

        public ActivityFilter next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public boolean matches(ContainerCraftingStatus.CraftingCpuListEntry cpu) {
            return switch (this) {
                case ALL -> true;
                case ACTIVE -> isActive(cpu);
                case INACTIVE -> !isActive(cpu);
            };
        }
    }

    public enum SortMode {
        NAME(GuiText.CraftingCpuSortName.text(), HeaderIcon.ofIcon(Icon.SORT_BY_NAME)),
        CAPACITY(ButtonToolTips.SortByCapacity.text(), HeaderIcon.ofIcon(Icon.CRAFT_CONFIRM_CPU_LIST_STORAGE)),
        COPROCESSORS(ButtonToolTips.SortByCoProcessors.text(), HeaderIcon.ofIcon(Icon.CRAFT_CONFIRM_CPU_LIST_PROCESSOR));

        public static final SortMode[] VALUES = SortMode.values();
        private final ITextComponent tooltip;
        private final HeaderIcon icon;

        SortMode(ITextComponent tooltip, HeaderIcon icon) {
            this.tooltip = tooltip;
            this.icon = icon;
        }

        public ITextComponent tooltip() {
            return tooltip;
        }

        HeaderIcon icon() {
            return icon;
        }

        public SortMode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    public enum SortDirection {
        ASCENDING(ButtonToolTips.Ascending.text(), ButtonToolTips.SortOrderAscendingDescription.text(), HeaderIcon.ofIcon(Icon.ARROW_UP)),
        DESCENDING(ButtonToolTips.Descending.text(), ButtonToolTips.SortOrderDescendingDescription.text(), HeaderIcon.ofIcon(Icon.ARROW_DOWN));

        private final ITextComponent tooltip;
        private final ITextComponent description;
        private final HeaderIcon icon;

        SortDirection(ITextComponent tooltip, ITextComponent description, HeaderIcon icon) {
            this.tooltip = tooltip;
            this.description = description;
            this.icon = icon;
        }

        public ITextComponent tooltip() {
            return tooltip;
        }

        public ITextComponent description() {
            return description;
        }

        HeaderIcon icon() {
            return icon;
        }

        public SortDirection toggle() {
            return this == ASCENDING ? DESCENDING : ASCENDING;
        }
    }

    private enum HeaderIconKind {
        ICON,
        SMALL_ICON,
        ITEM
    }

    private enum RowPressAction {
        NONE,
        ACTION,
        MODE,
        SELECT
    }

    public static final class ViewState {
        private String searchText = "";
        private ModeFilter modeFilter = ModeFilter.ALL;
        private ActivityFilter activityFilter = ActivityFilter.ALL;
        private SortMode sortMode = SortMode.NAME;
        private SortDirection sortDirection = SortDirection.ASCENDING;

        public void setSearchText(String searchText) {
            this.searchText = searchText == null ? "" : searchText;
        }

        public ModeFilter getModeFilter() {
            return modeFilter;
        }

        public void setModeFilter(ModeFilter modeFilter) {
            this.modeFilter = Objects.requireNonNull(modeFilter, "modeFilter");
        }

        public ActivityFilter getActivityFilter() {
            return activityFilter;
        }

        public void setActivityFilter(ActivityFilter activityFilter) {
            this.activityFilter = Objects.requireNonNull(activityFilter, "activityFilter");
        }

        public SortMode getSortMode() {
            return sortMode;
        }

        public void setSortMode(SortMode sortMode) {
            this.sortMode = Objects.requireNonNull(sortMode, "sortMode");
        }

        public SortDirection getSortDirection() {
            return sortDirection;
        }

        public void setSortDirection(SortDirection sortDirection) {
            this.sortDirection = Objects.requireNonNull(sortDirection, "sortDirection");
        }

        public ViewResult compute(List<ContainerCraftingStatus.CraftingCpuListEntry> cpus, int selectedCpuSerial) {
            String normalizedSearch = searchText.trim().toLowerCase(Locale.ROOT);
            Comparator<ContainerCraftingStatus.CraftingCpuListEntry> comparator = comparator();
            List<ContainerCraftingStatus.CraftingCpuListEntry> filtered = cpus.stream()
                                                                              .filter(cpu -> normalizedSearch.isEmpty() || normalizedName(cpu).contains(normalizedSearch))
                                                                              .filter(cpu -> modeFilter.matches(cpu.mode()))
                                                                              .filter(activityFilter::matches)
                                                                              .sorted(comparator)
                                                                              .toList();

            int resolvedSelectedCpuSerial = filtered.stream().anyMatch(cpu -> cpu.serial() == selectedCpuSerial)
                ? selectedCpuSerial
                : filtered.isEmpty() ? -1 : filtered.getFirst().serial();

            return new ViewResult(List.copyOf(filtered), resolvedSelectedCpuSerial);
        }

        private Comparator<ContainerCraftingStatus.CraftingCpuListEntry> comparator() {
            Comparator<ContainerCraftingStatus.CraftingCpuListEntry> comparator = switch (sortMode) {
                case NAME -> sortDirection == SortDirection.ASCENDING
                    ? Comparator.comparing(CPUSelectionList::normalizedName)
                    : Comparator.comparing(CPUSelectionList::normalizedName, Comparator.reverseOrder());
                case CAPACITY -> sortDirection == SortDirection.ASCENDING
                    ? Comparator.comparingLong(ContainerCraftingStatus.CraftingCpuListEntry::storage)
                    : Comparator.comparingLong(ContainerCraftingStatus.CraftingCpuListEntry::storage).reversed();
                case COPROCESSORS -> sortDirection == SortDirection.ASCENDING
                    ? Comparator.comparingInt(ContainerCraftingStatus.CraftingCpuListEntry::coProcessors)
                    : Comparator.comparingInt(ContainerCraftingStatus.CraftingCpuListEntry::coProcessors).reversed();
            };

            return comparator.thenComparing(CPUSelectionList::normalizedName)
                             .thenComparingInt(ContainerCraftingStatus.CraftingCpuListEntry::serial);
        }
    }

    public record ViewResult(List<ContainerCraftingStatus.CraftingCpuListEntry> visibleCpus, int selectedCpuSerial) {
    }

    private record HeaderIcon(HeaderIconKind kind, @Nullable Icon icon, @Nullable ItemStack itemStack) {
        private static HeaderIcon ofIcon(Icon icon) {
            return new HeaderIcon(HeaderIconKind.ICON, icon, null);
        }

        private static HeaderIcon ofSmallIcon() {
            return new HeaderIcon(HeaderIconKind.SMALL_ICON, Icon.CRAFT_HAMMER, null);
        }

        private static HeaderIcon ofItem(ItemStack itemStack) {
            return new HeaderIcon(HeaderIconKind.ITEM, null, itemStack);
        }
    }

    private final class HeaderSettingButton<T extends Enum<T>> extends SettingToggleButton<T> {
        private final int localX;
        private final int localY;
        private final Supplier<T> currentValueSupplier;
        private final Function<T, HeaderIcon> iconSupplier;
        private boolean pressedInside;
        private int pressedMouseButton = -1;

        private HeaderSettingButton(Setting<T> setting, int localX, int localY, Supplier<T> currentValueSupplier,
                                    Consumer<T> valueSetter, Function<T, HeaderIcon> iconSupplier,
                                    Function<T, List<ITextComponent>> tooltipLinesSupplier) {
            super(
                setting,
                currentValueSupplier.get(),
                ignored -> true,
                (button, backwards) -> {
                    T nextValue = button.getNextValue(backwards);
                    button.set(nextValue);
                    valueSetter.accept(nextValue);
                },
                (button, value) -> {
                    button.set(value);
                    valueSetter.accept(value);
                });
            this.localX = localX;
            this.localY = localY;
            this.currentValueSupplier = Objects.requireNonNull(currentValueSupplier, "currentValueSupplier");
            this.iconSupplier = Objects.requireNonNull(iconSupplier, "iconSupplier");
            Objects.requireNonNull(tooltipLinesSupplier, "tooltipLinesSupplier");
            this.width = HEADER_BUTTON_WIDTH;
            this.height = HEADER_BUTTON_HEIGHT;

            for (T value : getValidValues()) {
                SettingToggleButton.registerAppearance(
                    setting,
                    value,
                    createHeaderButtonAppearance(setting, value, iconSupplier.apply(value), tooltipLinesSupplier.apply(value)));
            }
        }

        private int localX() {
            return localX;
        }

        private int localY() {
            return localY;
        }

        private void moveTo(int x, int y) {
            this.x = x;
            this.y = y;
        }

        private boolean handleMouseDown(int mouseX, int mouseY, int mouseButton) {
            syncCurrentValue();
            if (!super.mousePressed(Minecraft.getMinecraft(), mouseX, mouseY, mouseButton, requireHostScreen())) {
                return false;
            }

            this.pressedInside = true;
            this.pressedMouseButton = mouseButton;
            return true;
        }

        private boolean handleMouseUp(int mouseX, int mouseY, int mouseButton) {
            if (!this.pressedInside || this.pressedMouseButton != mouseButton) {
                return false;
            }

            boolean releasedInside = this.enabled && this.visible
                && mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            this.pressedInside = false;
            this.pressedMouseButton = -1;
            super.mouseReleased(mouseX, mouseY);
            return releasedInside;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            syncCurrentValue();
            this.hovered = mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            drawHeaderButton(this.x, this.y, this.hovered);
            drawHeaderButtonContent(this.x, this.y, iconSupplier.apply(getCurrentValue()));
        }

        @Nullable
        private List<ITextComponent> getTooltipMessage(int mouseX, int mouseY) {
            if (!this.visible
                || mouseX < this.x
                || mouseY < this.y
                || mouseX >= this.x + this.width
                || mouseY >= this.y + this.height) {
                return null;
            }

            syncCurrentValue();
            return super.getTooltipMessage();
        }

        private void syncCurrentValue() {
            set(Objects.requireNonNull(currentValueSupplier.get(), "currentValue"));
        }
    }

    private final class RowModeButton extends SettingToggleButton<CpuSelectionMode> {
        private static final Setting<CpuSelectionMode> ROW_MODE_SETTING = new Setting<>(
            "crafting_cpu_row_mode_button",
            CpuSelectionMode.class);

        private final int cpuSerial;
        private boolean pressedInside;
        private int pressedMouseButton = -1;
        private boolean hoveredDirect;

        private RowModeButton(int cpuSerial, CpuSelectionMode initialValue, int x, int y) {
            super(
                ROW_MODE_SETTING,
                initialValue,
                ignored -> true,
                (button, backwards) -> {
                    container.cycleCpuMode(((RowModeButton) button).cpuSerial(), backwards);
                    invalidateView();
                },
                (button, value) -> {
                    container.setCpuMode(((RowModeButton) button).cpuSerial(), value);
                    invalidateView();
                });
            this.cpuSerial = cpuSerial;
            this.x = x;
            this.y = y;
            this.width = MODE_BUTTON_SIZE;
            this.height = MODE_BUTTON_SIZE;

            for (var value : getValidValues()) {
                SettingToggleButton.registerAppearance(ROW_MODE_SETTING, value, createCpuModeAppearance(value));
            }
        }

        private int cpuSerial() {
            return cpuSerial;
        }

        private void moveTo(int x, int y) {
            this.x = x;
            this.y = y;
        }

        private void setHoveredDirect(boolean hoveredDirect) {
            this.hoveredDirect = hoveredDirect;
        }

        private boolean isMouseOver(int mouseX, int mouseY) {
            return this.visible
                && mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
        }

        private boolean handleMouseDown(int mouseX, int mouseY, int mouseButton) {
            if (!super.mousePressed(Minecraft.getMinecraft(), mouseX, mouseY, mouseButton, requireHostScreen())) {
                return false;
            }

            this.pressedInside = true;
            this.pressedMouseButton = mouseButton;
            return true;
        }

        private boolean handleMouseUp(int mouseX, int mouseY, int mouseButton) {
            if (!this.pressedInside || this.pressedMouseButton != mouseButton) {
                return false;
            }

            boolean releasedInside = isMouseOver(mouseX, mouseY);
            this.pressedInside = false;
            this.pressedMouseButton = -1;
            super.mouseReleased(mouseX, mouseY);
            return releasedInside;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = this.hoveredDirect;
            drawSquareButtonBackground(this.x, this.y, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE, this.hovered);

            var appearance = getAppearance(getCurrentValue());
            if (appearance == null) {
                String message = "Missing crafting CPU row mode button appearance for " + getCurrentValue();
                AELog.error(message);
                throw new IllegalStateException(message);
            }

            if (appearance.item() != null) {
                drawBoxedItemStack(this.x, this.y, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE, new ItemStack(appearance.item()));
            } else if (appearance.icon() != null) {
                drawBoxedIcon(this.x, this.y, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE, appearance.icon());
            } else {
                String message = "Invalid crafting CPU row mode button appearance for " + getCurrentValue();
                AELog.error(message);
                throw new IllegalStateException(message);
            }
        }
    }

}
