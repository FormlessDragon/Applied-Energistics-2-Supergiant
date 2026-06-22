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

package ae2.client.gui;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.IStackTooltipDataProvider;
import ae2.client.Point;
import ae2.client.gui.layout.SlotGridLayout;
import ae2.client.gui.me.common.StackSizeRenderer;
import ae2.client.gui.style.BackgroundGenerator;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GeneratedBackground;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.SlotPosition;
import ae2.client.gui.style.Text;
import ae2.client.gui.style.TextAlignment;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.GridSelectionPopup;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.VerticalButtonBar;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantic;
import ae2.container.SlotSemantics;
import ae2.container.slot.AppEngCraftingSlot;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.CraftingTermSlot;
import ae2.container.slot.DisabledSlot;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.FakeSlotFilterSupport;
import ae2.container.slot.IOptionalSlot;
import ae2.container.slot.OutputSlot;
import ae2.container.slot.ResizableSlot;
import ae2.core.AEConfig;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.CycleWirelessTerminalPacket;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.core.network.serverbound.SwapSlotsPacket;
import ae2.helpers.InventoryAction;
import ae2.integration.Integrations;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.util.EmptyArrays;
import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Optional;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import yalter.mousetweaks.api.IMTModGuiContainer2;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Optional.Interface(iface = "yalter.mousetweaks.api.IMTModGuiContainer2", modid = "mousetweaks")
public abstract class AEBaseGui<T extends AEBaseContainer> extends GuiContainer implements IMTModGuiContainer2 {
    /**
     * Commonly used id for text that is used to show the dialog title.
     */
    public static final String TEXT_ID_DIALOG_TITLE = "dialog_title";
    private static final Point HIDDEN_SLOT_POS = new Point(-9999, -9999);
    private static final float TOOLTIP_Z_LEVEL = 500.0F;
    protected final T container;
    protected final InventoryPlayer playerInventory;
    protected final WidgetContainer widgets;
    @Nullable
    protected final GuiStyle style;
    private final VerticalButtonBar verticalToolbar;
    private final Set<Slot> drag_click = new ReferenceOpenHashSet<>();
    private final Set<Slot> drag_click_sent = new ReferenceOpenHashSet<>();
    private final Map<String, TextOverride> textOverrides = new Object2ObjectOpenHashMap<>();
    private final Set<SlotSemantic> hiddenSlots = new ObjectOpenHashSet<>();
    private final List<SavedSlotInfo> savedSlotInfos = new ObjectArrayList<>();
    private List<Rectangle> cachedExclusionZones = new ObjectArrayList<>();
    private Rectangle[] arrayExclusionZones = EmptyArrays.EMPTY_RECTANGLE_ARRAY;
    private boolean disableShiftClick;
    private Stopwatch dbl_clickTimer = Stopwatch.createStarted();
    private ItemStack dbl_whichItem = ItemStack.EMPTY;
    private Slot bl_clicked;
    private boolean handlingRightClick;
    private boolean suppressVanillaSlotHover;
    @Nullable
    private GridSelectionPopup<?> activeSelectionPopup;
    private final Rectangle rectangle = new Rectangle(guiLeft, guiTop, xSize, ySize);
    private final Rectangle rectangle2 = new Rectangle(0, 0, xSize, ySize);
    private int cachedGuiLeft;
    private int cachedGuiTop;
    private int cachedXSize;
    private int cachedYSize;

    protected AEBaseGui(T container, InventoryPlayer playerInventory) {
        this(container, playerInventory, null);
    }

    protected AEBaseGui(T container, InventoryPlayer playerInventory, @Nullable GuiStyle style) {
        super(container);
        this.container = Objects.requireNonNull(container, "container");
        this.playerInventory = Objects.requireNonNull(playerInventory, "playerInventory");
        this.style = style;
        this.widgets = new WidgetContainer(style);
        this.verticalToolbar = new VerticalButtonBar();
        if (this.container.getGuiTitle() != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, this.container.getGuiTitle());
        }

        if (style != null) {
            try {
                if (shouldAddToolbar()) {
                    style.getWidget("verticalToolbar");
                    this.widgets.add("verticalToolbar", this.verticalToolbar);
                }
            } catch (IllegalStateException ignored) {
            }
            GeneratedBackground generatedBackground = style.getGeneratedBackground();
            if (generatedBackground != null) {
                this.xSize = generatedBackground.getWidth();
                this.ySize = generatedBackground.getHeight();
            } else if (style.getBackground() != null) {
                this.xSize = style.getBackground().getSrcWidth();
                this.ySize = style.getBackground().getSrcHeight();
            }
        }
    }

    private static ITextComponent buildTextFieldInsertionAction(int mouseButton, String insertedText) {
        return Tooltips.of(ButtonToolTips.SetAction.text(
            Tooltips.getMouseButtonText(mouseButton),
            Tooltips.of(new TextComponentString(insertedText))));
    }

    private static boolean isClickedTextField(GuiTextField textField, int mouseX, int mouseY) {
        if (!textField.getVisible()) {
            return false;
        }

        if (textField instanceof AETextField aeTextField) {
            return aeTextField.isMouseOver(mouseX, mouseY);
        }

        return mouseX >= textField.x && mouseX < textField.x + textField.width
            && mouseY >= textField.y && mouseY < textField.y + textField.height;
    }

    public static String getTextFieldInsertionText(ItemStack stack, int mouseButton) {
        if (mouseButton == 1) {
            GenericStack containedStack = ContainerItemStrategies.getContainedStack(stack);
            if (containedStack != null) {
                return containedStack.what().getDisplayName().getFormattedText();
            }
        }

        return stack.getDisplayName();
    }

    private static ItemStack getRawStack(AppEngSlot slot) {
        return slot.getRawStack();
    }

    private static void drainQueuedKeyPresses(KeyBinding keyBinding) {
        int drainedPresses = 0;
        while (keyBinding.isPressed()) {
            drainedPresses++;
        }
        if (drainedPresses > 0) {
            KeyBinding.setKeyBindState(keyBinding.getKeyCode(), false);
        }
    }

    public static boolean isSameInventory(Slot a, Slot b) {
        if (a instanceof AppEngSlot appEngSlotA && b instanceof AppEngSlot appEngSlotB) {
            return appEngSlotA.getInventory() == appEngSlotB.getInventory();
        }
        return a.inventory == b.inventory;
    }

    private GuiStyle requireStyle() {
        return Objects.requireNonNull(style, "GUI style has not been initialized");
    }
    private long cachedWidgetLayoutVersion = -1;

    protected final <B extends GuiButton> B addToLeftToolbar(B button) {
        this.verticalToolbar.add(button);
        return button;
    }

    public void setInitialFocus(GuiTextField textField) {
        textField.setFocused(true);
    }

    protected void bindTexture(ResourceLocation texture) {
        this.mc.getTextureManager().bindTexture(texture);
    }

    protected boolean isPlayerSideSlot(Slot slot) {
        return container.isPlayerSideSlot(slot);
    }

    private void positionSlots() {
        if (style == null) {
            return;
        }

        for (Map.Entry<String, SlotPosition> entry : style.getSlots().entrySet()) {
            SlotSemantic semantic = SlotSemantics.getOrThrow(entry.getKey());
            if (hiddenSlots.contains(semantic)) {
                continue;
            }

            repositionSlots(semantic);
        }
    }

    private Point getSlotPosition(SlotPosition position, int semanticIndex) {
        Point pos = position.resolve(getBounds(false));

        SlotGridLayout grid = position.getGrid();
        if (grid != null) {
            pos = grid.getPosition(pos.x(), pos.y(), semanticIndex);
        }
        return pos;
    }

    public final void repositionSlots(SlotSemantic semantic) {
        if (style == null) {
            return;
        }

        SlotPosition position = style.getSlots().get(semantic.id());
        if (position == null) {
            return;
        }

        if (position.isHidden()) {
            container.hideSlot(semantic.id());
            setSlotsHidden(semantic, true);
            return;
        }

        List<Slot> slots = container.getSlots(semantic);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot instanceof ResizableSlot resizableSlot) {
                WidgetStyle widgetStyle = style.getWidget(resizableSlot.getStyleId());
                Point pos = widgetStyle.resolve(getBounds(false));
                slot.xPos = pos.x();
                slot.yPos = pos.y();
                resizableSlot.setWidth(widgetStyle.getWidth());
                resizableSlot.setHeight(widgetStyle.getHeight());
            } else {
                Point pos = getSlotPosition(position, i);
                slot.xPos = pos.x();
                slot.yPos = pos.y();
            }
        }
    }

    private List<Slot> getInventorySlots() {
        return this.container.inventorySlots;
    }

    protected void updateBeforeRender() {
    }

    protected boolean shouldAddToolbar() {
        return true;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        widgets.tick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateBeforeRender();
        widgets.updateBeforeRender();
        Point mousePos = getMousePoint(mouseX, mouseY);
        Slot aeHoveredSlot = widgets.hitTest(mousePos) ? null : findSlot(mouseX, mouseY);
        this.hoveredSlot = aeHoveredSlot;

        super.drawDefaultBackground();
        prepareDisplayStacksForVanillaRender();
        this.suppressVanillaSlotHover = true;
        try {
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            this.suppressVanillaSlotHover = false;
        }

        this.hoveredSlot = aeHoveredSlot;

        if (this.hoveredSlot != null) {
            renderSlotHighlight(this.hoveredSlot, mouseX, mouseY);
        }

        if (this.activeSelectionPopup == null) {
            renderHoveredToolTip(mouseX, mouseY);
        }
        if (!renderSelectionPopupTooltip(mouseX, mouseY)) {
            renderWidgetTooltip(mouseX, mouseY);
        }
        renderDebugGuiOverlays();
    }
    private boolean exclusionZonesDirty = true;

    @Override
    public void initGui() {
        super.initGui();
        closeSelectionPopup();
        positionSlots();
        widgets.populateScreen(this::addButton, getBounds(true), this);
        invalidateExclusionZonesCache();
    }

    public final Rectangle getBounds(boolean absolute) {
        if (absolute) {
            rectangle.setBounds(guiLeft, guiTop, xSize, ySize);
            return rectangle;
        } else {
            rectangle2.setBounds(0, 0, xSize, ySize);
            return rectangle2;
        }
    }

    private boolean renderEmptyingTooltip(int mouseX, int mouseY) {
        EmptyingAction emptyingAction = getEmptyingAction(this.hoveredSlot, playerInventory.getItemStack());
        if (emptyingAction != null) {
            drawTooltip(mouseX, mouseY,
                Tooltips.getEmptyingTooltip(ButtonToolTips.SetAction, playerInventory.getItemStack(),
                    emptyingAction));
            return true;
        }

        return false;
    }

    private void renderWidgetTooltip(int mouseX, int mouseY) {
        Point mousePos = getMousePoint(mouseX, mouseY);
        if (this.hoveredSlot != null
            && !this.hoveredSlot.getStack().isEmpty()
            && widgets.isInCompositeWidgetBounds(mousePos)) {
            return;
        }

        if (widgets.hitTest(mousePos)
            && this.hoveredSlot != null
            && !this.hoveredSlot.getStack().isEmpty()) {
            return;
        }

        if (getEmptyingAction(this.hoveredSlot, playerInventory.getItemStack()) != null) {
            return;
        }

        if (getTextFieldInsertionTooltip(mouseX, mouseY) != null) {
            return;
        }

        Tooltip tooltip = widgets.getTooltip(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip != null && !tooltip.content().isEmpty()) {
            drawTooltip(mouseX, mouseY, tooltip.content());
        }

    }

    private void renderDebugGuiOverlays() {
        if (!AEConfig.instance().isShowDebugGuiOverlays()) {
            return;
        }

        for (Rectangle rect : getArrayExclusionZones()) {
            drawGradientRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
                0x7f00ff00, 0x7f00ff00);
        }

        drawHorizontalLine(guiLeft, guiLeft + xSize - 1, guiTop, 0xffffffff);
        drawHorizontalLine(guiLeft, guiLeft + xSize - 1, guiTop + ySize - 1, 0xffffffff);
        drawVerticalLine(guiLeft, guiTop, guiTop + ySize - 1, 0xffffffff);
        drawVerticalLine(guiLeft + xSize - 1, guiTop, guiTop + ySize - 1, 0xffffffff);
    }

    private boolean renderDisplayStackTooltip(int mouseX, int mouseY) {
        if (this.hoveredSlot == null) {
            return false;
        }

        if (this.hoveredSlot instanceof AppEngSlot appEngSlot && appEngSlot.hasGenericDisplayStack()) {
            AEKey what = appEngSlot.getGenericDisplayKey();
            if (what == null) {
                return false;
            }
            drawKeyTooltipWithImages(mouseX, mouseY, what,
                getGenericStackTooltip(new GenericStack(what, appEngSlot.getGenericDisplayAmount())));
            return true;
        }

        ItemStack displayStack = this.hoveredSlot.getStack();
        if (displayStack.isEmpty()) {
            return false;
        }

        GenericStack genericStack = GenericStack.unwrapItemStack(displayStack);
        if (genericStack != null) {
            drawKeyTooltipWithImages(mouseX, mouseY, genericStack.what(), getGenericStackTooltip(genericStack));
            return true;
        }

        if (displayStack.getItem() instanceof IStackTooltipDataProvider) {
            return false;
        }

        drawHoveringTextAtTopZ(getItemToolTip(displayStack), mouseX, mouseY);
        return true;
    }

    private boolean renderHeiIngredientTooltip(int mouseX, int mouseY) {
        if (!(this.hoveredSlot instanceof FakeSlot) || !this.playerInventory.getItemStack().isEmpty()) {
            return false;
        }

        var hei = Integrations.hei();
        if (!hei.isEnabled()) {
            return false;
        }

        Object ingredient = hei.getCurrentGhostIngredient();
        if (!(ingredient instanceof ItemStack displayStack)) {
            return false;
        }

        if (displayStack.isEmpty()) {
            return false;
        }

        EmptyingAction emptyingAction = getEmptyingAction(this.hoveredSlot, displayStack);
        if (emptyingAction != null) {
            drawTooltip(mouseX, mouseY,
                Tooltips.getEmptyingTooltip(ButtonToolTips.SetAction, displayStack, emptyingAction));
            return true;
        }

        GenericStack genericStack = hei.ingredientToStack(ingredient);
        if (genericStack != null) {
            drawKeyTooltipWithImages(mouseX, mouseY, genericStack.what(), getGenericStackTooltip(genericStack));
            return true;
        }

        drawHoveringTextAtTopZ(getItemToolTip(displayStack), mouseX, mouseY);
        return true;
    }

    private boolean renderStorageCellTooltip(int mouseX, int mouseY) {
        ItemStack hoveredStack = this.hoveredSlot != null ? this.hoveredSlot.getStack() : ItemStack.EMPTY;
        if (hoveredStack.isEmpty() || !(hoveredStack.getItem() instanceof IStackTooltipDataProvider)) {
            return false;
        }

        drawItemTooltipWithImages(mouseX, mouseY, hoveredStack, getItemToolTip(hoveredStack));
        return true;
    }

    protected final void drawItemTooltipWithImages(int mouseX, int mouseY, ItemStack hoveredStack,
                                                   List<String> anchorTooltipLines) {
        if (hoveredStack.isEmpty() || !(hoveredStack.getItem() instanceof IStackTooltipDataProvider)) {
            AEKey what = GenericStack.unwrapWhat(hoveredStack);
            if (what != null) {
                drawKeyTooltipLinesWithImages(mouseX, mouseY, what, anchorTooltipLines);
            } else {
                drawTooltipLines(mouseX, mouseY, anchorTooltipLines);
            }
            return;
        }

        drawHoveringTextAtTopZ(anchorTooltipLines, mouseX, mouseY);
        renderStorageCellTooltipImage(mouseX, mouseY, hoveredStack, anchorTooltipLines);
    }

    private void renderStorageCellTooltipImage(int mouseX, int mouseY, ItemStack hoveredStack, List<String> tooltipLines) {
        int tooltipWidth = 0;
        for (String line : tooltipLines) {
            tooltipWidth = Math.max(tooltipWidth, this.fontRenderer.getStringWidth(line));
        }

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        int lineCount = tooltipLines.size();
        int tooltipHeight = 8;
        if (lineCount > 1) {
            tooltipHeight += 2 + (lineCount - 1) * 10;
        }

        if (tooltipX + tooltipWidth + 4 > this.width) {
            tooltipX = mouseX - 16 - tooltipWidth;
            if (tooltipX < 4) {
                tooltipX = mouseX + 12;
            }
        }
        if (tooltipY + tooltipHeight + 6 > this.height) {
            tooltipY = this.height - tooltipHeight - 6;
        }
        if (tooltipY < 4) {
            tooltipY = 4;
        }

        StackTooltipRenderer.INSTANCE.drawTooltipImage(this.mc, this.fontRenderer, hoveredStack, tooltipX, tooltipY,
            tooltipHeight, tooltipLines);
    }

    public boolean isTooltipForHoveredSlot(ItemStack stack) {
        return this.hoveredSlot != null && !stack.isEmpty() && ItemStack.areItemStacksEqual(this.hoveredSlot.getStack(), stack);
    }

    private void prepareDisplayStacksForVanillaRender() {
    }

    private static List<String> formatTooltipLines(List<ITextComponent> tooltip) {
        List<String> lines = new ObjectArrayList<>(tooltip.size());
        for (ITextComponent component : tooltip) {
            lines.add(component.getFormattedText());
        }
        return lines;
    }

    private List<ITextComponent> getGenericStackTooltip(GenericStack stack) {
        List<ITextComponent> tooltip = new ObjectArrayList<>(AEKeyRendering.getTooltip(stack.what()));
        if (stack.amount() > 1 || Tooltips.shouldShowAmountTooltip(stack.what(), stack.amount())) {
            tooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, stack));
        }
        return tooltip;
    }

    private void drawTooltip(int mouseX, int mouseY, List<ITextComponent> tooltip) {
        drawTooltipLines(mouseX, mouseY, formatTooltipLines(tooltip));
    }

    protected final void drawKeyTooltipWithImages(int mouseX, int mouseY, AEKey what, List<ITextComponent> tooltip) {
        drawKeyTooltipLinesWithImages(mouseX, mouseY, what, formatTooltipLines(tooltip));
    }

    protected final void drawKeyTooltipLinesWithImages(int mouseX, int mouseY, AEKey what,
                                                       List<String> visibleTooltipLines) {
        ItemStack imageStack = getImageTooltipStack(what);
        if (imageStack.isEmpty()) {
            drawHoveringTextAtTopZ(visibleTooltipLines, mouseX, mouseY);
            return;
        }

        for (String line : visibleTooltipLines) {
            if (StackTooltipRenderer.isReservedTooltipLine(line)) {
                drawHoveringTextAtTopZ(visibleTooltipLines, mouseX, mouseY);
                renderStorageCellTooltipImage(mouseX, mouseY, imageStack, visibleTooltipLines);
                return;
            }
        }

        List<String> anchorTooltipLines = getItemToolTip(imageStack);
        List<String> imageTooltipLines = new ObjectArrayList<>(visibleTooltipLines);
        int insertionIndex = 0;
        boolean foundReservedLines = false;
        for (String line : anchorTooltipLines) {
            if (StackTooltipRenderer.isReservedTooltipLine(line)) {
                imageTooltipLines.add(Math.min(insertionIndex, imageTooltipLines.size()), line);
                insertionIndex++;
                foundReservedLines = true;
            } else if (!foundReservedLines) {
                insertionIndex++;
            }
        }

        if (imageTooltipLines.size() == visibleTooltipLines.size()) {
            drawHoveringTextAtTopZ(visibleTooltipLines, mouseX, mouseY);
            return;
        }

        drawHoveringTextAtTopZ(imageTooltipLines, mouseX, mouseY);
        renderStorageCellTooltipImage(mouseX, mouseY, imageStack, imageTooltipLines);
    }

    private ItemStack getImageTooltipStack(AEKey what) {
        if (!(what instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = itemKey.getReadOnlyStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof IStackTooltipDataProvider)) {
            return ItemStack.EMPTY;
        }

        return stack;
    }

    protected final void drawTooltipLines(int mouseX, int mouseY, List<String> tooltip) {
        drawHoveringTextAtTopZ(tooltip, mouseX, mouseY);
    }

    private void drawHoveringTextAtTopZ(List<String> tooltip, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.translate(0.0F, 0.0F, TOOLTIP_Z_LEVEL);
        this.zLevel = TOOLTIP_Z_LEVEL;
        this.itemRender.zLevel = TOOLTIP_Z_LEVEL;
        try {
            drawHoveringText(tooltip, mouseX, mouseY);
        } finally {
            this.itemRender.zLevel = 0.0F;
            this.zLevel = 0.0F;
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    public void drawTooltipWithHeader(int mouseX, int mouseY, List<ITextComponent> tooltip) {
        drawTooltip(mouseX, mouseY, tooltip);
    }

    @Override
    protected final void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawBG(guiLeft, guiTop, mouseX, mouseY, partialTicks);
        widgets.drawBackgroundLayer(getBounds(true), getMousePoint(mouseX, mouseY));

        for (Slot slot : this.getInventorySlots()) {
            if (slot instanceof IOptionalSlot optionalSlot) {
                drawOptionalSlotBackground(optionalSlot);
            }
            if (slot instanceof AppEngSlot appEngSlot) {
                drawAppEngSlotBackground(appEngSlot);
            }
        }
    }

    private void drawAppEngSlotBackground(AppEngSlot slot) {
        if (!slot.isEnabled()) {
            return;
        }

        boolean hasGenericDisplayStack = slot.hasGenericDisplayStack();
        ItemStack stack = hasGenericDisplayStack ? ItemStack.EMPTY : getRawStack(slot);
        var backgroundIcon = SlotBackgroundIconMapping.resolve(slot.getBackgroundIcon());
        if ((slot.renderIconWithItem() || !hasGenericDisplayStack && stack.isEmpty())
            && slot.isSlotEnabled()
            && backgroundIcon != null) {
            backgroundIcon.getBlitter()
                          .dest(guiLeft + slot.xPos, guiTop + slot.yPos)
                          .opacity(slot.getOpacityOfIcon())
                          .blit();
        }

        if (!slot.isValid()) {
            drawGradientRect(guiLeft + slot.xPos, guiTop + slot.yPos,
                guiLeft + slot.xPos + 16, guiTop + slot.yPos + 16,
                0x66ff6666, 0x66ff6666);
        }
    }

    private void drawOptionalSlotBackground(IOptionalSlot slot) {
        if (slot.isRenderDisabled()) {
            float alpha = slot.isSlotEnabled() ? 1.0f : 0.2f;

            Point pos = slot.getBackgroundPos();
            Icon.SLOT_BACKGROUND.getBlitter()
                                .dest(guiLeft + pos.x(), guiTop + pos.y())
                                .color(1, 1, 1, alpha)
                                .blit();
        }
    }

    private boolean renderTextFieldInsertionTooltip(int mouseX, int mouseY) {
        List<ITextComponent> tooltip = getTextFieldInsertionTooltip(mouseX, mouseY);
        if (tooltip == null) {
            return false;
        }

        drawTooltip(mouseX, mouseY, tooltip);
        return true;
    }

    private void drawText(Text text, @Nullable TextOverride override) {
        if (override != null && override.isHidden()) {
            return;
        }

        int color = requireStyle().getColor(text.getColor()).toARGB() & 0xFFFFFF;
        Point pos = text.getPosition().resolve(getBounds(false));
        float scale = text.getScale();

        ITextComponent content = text.getText();
        if (override != null && override.getContent() != null) {
            content = override.getContent();
        }

        List<String> lines;
        if (text.getMaxWidth() <= 0) {
            lines = Collections.singletonList(content.getFormattedText());
        } else {
            lines = this.fontRenderer.listFormattedStringToWidth(content.getFormattedText(), text.getMaxWidth());
        }

        int y = pos.y();
        for (String line : lines) {
            int lineWidth = this.fontRenderer.getStringWidth(line);
            int x = pos.x();
            if (text.getAlign() == TextAlignment.CENTER) {
                x -= Math.round(lineWidth * scale) / 2;
            } else if (text.getAlign() == TextAlignment.RIGHT) {
                x -= Math.round(lineWidth * scale);
            }

            if (scale == 1) {
                this.fontRenderer.drawString(line, x, y, color);
            } else {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, 1);
                GlStateManager.scale(scale, scale, 1);
                this.fontRenderer.drawString(line, 0, 0, color);
                GlStateManager.popMatrix();
            }
            y += Math.round(scale * this.fontRenderer.FONT_HEIGHT);
        }
    }

    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        if (style == null || !shouldDrawStyleBackground()) {
            return;
        }

        GeneratedBackground generatedBackground = style.getGeneratedBackground();
        if (generatedBackground != null) {
            BackgroundGenerator.draw(generatedBackground.getWidth(), generatedBackground.getHeight(), offsetX, offsetY);
        }

        Blitter background = style.getBackground();
        if (background != null) {
            background.dest(offsetX, offsetY).blit();
        }
    }

    protected boolean shouldDrawStyleBackground() {
        return true;
    }

    protected boolean shouldDrawStyleText() {
        return true;
    }

    @Override
    protected void drawSlot(Slot slot) {
        if (!(slot instanceof AppEngSlot appEngSlot)) {
            super.drawSlot(slot);
            return;
        }

        if (appEngSlot.hasGenericDisplayStack()) {
            drawGenericStackSlot(slot, appEngSlot);
            return;
        }

        ItemStack rawStack = getRawStack(appEngSlot);
        if (rawStack.isEmpty()) {
            super.drawSlot(slot);
            return;
        }

        appEngSlot.setDisplay(true);
        appEngSlot.setReturnAsSingleStack(true);

        boolean wasDragSplitting = this.dragSplitting;
        this.dragSplitting = false;
        super.drawSlot(slot);
        this.dragSplitting = wasDragSplitting;

        renderAppEngSlotAmount(slot, appEngSlot, rawStack);
    }

    private void drawGenericStackSlot(Slot slot, AppEngSlot appEngSlot) {
        AEKey what = appEngSlot.getGenericDisplayKey();
        if (what == null) {
            return;
        }

        AEKeyRendering.drawInGui(this.mc, slot.xPos, slot.yPos, what);
        restoreStateForVanillaItemRender();

        long amount = appEngSlot.getGenericDisplayAmount();
        if (amount > 1) {
            StackSizeRenderer.renderSizeLabel(this.fontRenderer, slot.xPos, slot.yPos,
                what.formatAmount(amount, AmountFormat.SLOT), false);
            restoreStateForVanillaItemRender();
        }
    }

    private static void restoreStateForVanillaItemRender() {
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        RenderHelper.enableGUIStandardItemLighting();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        suppressLockedOffhandSwapKey(keyCode);
        if (widgets.onKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void suppressLockedOffhandSwapKey(int keyCode) {
        KeyBinding swapHandsKey = this.mc.gameSettings.keyBindSwapHands;
        if (swapHandsKey.isActiveAndMatches(keyCode)
            && container.isPlayerInventorySlotLocked(getOffhandPlayerInventorySlot())) {
            KeyBinding.setKeyBindState(swapHandsKey.getKeyCode(), false);
            drainQueuedKeyPresses(swapHandsKey);
        }
    }

    private int getOffhandPlayerInventorySlot() {
        return AEBaseContainer.getOffhandPlayerInventorySlot(
            this.playerInventory.mainInventory.size(),
            this.playerInventory.armorInventory.size());
    }

    @Override
    protected boolean checkHotbarKeys(int keyCode) {
        final Slot theSlot = this.getSlotUnderMouse();

        if (this.playerInventory.getItemStack().isEmpty() && theSlot != null) {
            for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                if (this.mc.gameSettings.keyBindsHotbar[hotbarSlot].isActiveAndMatches(keyCode)) {
                    for (Slot inventorySlot : this.getInventorySlots()) {
                        if (inventorySlot != null
                            && inventorySlot.getSlotIndex() == hotbarSlot
                            && inventorySlot.inventory == this.playerInventory
                            && !inventorySlot.canTakeStack(this.mc.player)) {
                            return false;
                        }
                    }

                    if (theSlot.getSlotStackLimit() != 64) {
                        for (Slot inventorySlot : this.getInventorySlots()) {
                            if (inventorySlot != null
                                && inventorySlot.getSlotIndex() == hotbarSlot
                                && inventorySlot.inventory == this.playerInventory) {
                                InitNetwork.sendToServer(new SwapSlotsPacket(
                                    this.container.windowId,
                                    inventorySlot.slotNumber,
                                    theSlot.slotNumber));
                                return true;
                            }
                        }
                    }

                    break;
                }
            }
        }

        return super.checkHotbarKeys(keyCode);
    }

    private void renderAppEngSlotAmount(Slot slot, AppEngSlot appEngSlot, ItemStack rawStack) {
        ItemStack displayStack = appEngSlot.getDisplayStack();
        if (displayStack.isEmpty()) {
            return;
        }

        if (this.dragSplitting && this.dragSplittingSlots.contains(slot) && this.dragSplittingSlots.size() > 1) {
            return;
        }

        String amountText = getSlotAmountText(slot, appEngSlot, rawStack, displayStack);
        if (amountText == null) {
            return;
        }

        StackSizeRenderer.renderSizeLabel(this.fontRenderer, slot.xPos, slot.yPos, amountText, false);
    }

    @Nullable
    protected String getSlotAmountText(Slot slot, AppEngSlot appEngSlot, ItemStack rawStack, ItemStack displayStack) {
        GenericStack genericStack = GenericStack.unwrapItemStack(rawStack);
        if (genericStack != null) {
            if (genericStack.amount() <= 1) {
                return null;
            }
            return genericStack.what().formatAmount(genericStack.amount(), AmountFormat.SLOT);
        } else {
            int count = rawStack.getCount();
            if (count <= 1) {
                return null;
            }
            return Integer.toString(count);
        }
    }

    @Override
    protected final void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        widgets.drawForegroundLayer(getBounds(false), getMousePoint(mouseX, mouseY));
        drawFG(guiLeft, guiTop, mouseX, mouseY);

        if (style != null && shouldDrawStyleText()) {
            for (Map.Entry<String, Text> entry : style.getText().entrySet()) {
                TextOverride override = textOverrides.get(entry.getKey());
                drawText(entry.getValue(), override);
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-guiLeft, -guiTop, 0.0F);
        try {
            widgets.drawTextFields(getMousePoint(mouseX, mouseY));
        } finally {
            GlStateManager.popMatrix();
        }

        renderSelectionPopup(mouseX, mouseY);
        restoreStateForVanillaItemRender();
        if (!this.playerInventory.getItemStack().isEmpty()) {
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    @Nullable
    private List<ITextComponent> getTextFieldInsertionTooltip(int mouseX, int mouseY) {
        if (findTextFieldAt(mouseX, mouseY) == null) {
            return null;
        }

        ItemStack carried = this.playerInventory.getItemStack();
        if (!carried.isEmpty()) {
            return buildTextFieldInsertionTooltip(carried);
        }

        var hei = Integrations.hei();
        if (!hei.isEnabled()) {
            return null;
        }

        Object ingredient = hei.getCurrentGhostIngredient();
        if (!(ingredient instanceof ItemStack displayStack)) {
            var g = Integrations.hei().ingredientToStack(ingredient);
            return g == null ? null : buildTextFieldInsertionTooltip(g);
        }

        if (displayStack.isEmpty()) {
            return null;
        }

        return buildTextFieldInsertionTooltip(displayStack);
    }

    private List<ITextComponent> buildTextFieldInsertionTooltip(GenericStack stack) {
        return Collections.singletonList(buildTextFieldInsertionAction(0, stack.what().getDisplayName().getFormattedText()));
    }

    private List<ITextComponent> buildTextFieldInsertionTooltip(ItemStack stack) {
        var tooltip = new ObjectArrayList<ITextComponent>(2);
        String leftClickText = getTextFieldInsertionText(stack, 0);
        tooltip.add(buildTextFieldInsertionAction(0, leftClickText));

        String rightClickText = getTextFieldInsertionText(stack, 1);
        if (!Objects.equals(leftClickText, rightClickText)) {
            tooltip.add(buildTextFieldInsertionAction(1, rightClickText));
        }

        return tooltip;
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (renderTextFieldInsertionTooltip(mouseX, mouseY)) {
            return;
        } else if (renderEmptyingTooltip(mouseX, mouseY)) {
            return;
        } else if (renderHeiIngredientTooltip(mouseX, mouseY)) {
            return;
        } else if (this.hoveredSlot instanceof AppEngSlot appEngSlot) {
            List<ITextComponent> customTooltip = appEngSlot.getCustomTooltip(playerInventory.getItemStack());
            if (customTooltip != null) {
                drawTooltip(mouseX, mouseY, customTooltip);
                return;
            }
        }

        if (renderDisplayStackTooltip(mouseX, mouseY)) {
            return;
        }

        if (renderStorageCellTooltip(mouseX, mouseY)) {
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.drag_click.clear();
        this.drag_click_sent.clear();

        if (this.activeSelectionPopup != null) {
            Point mousePos = getMousePoint(mouseX, mouseY);
            GridSelectionPopup<?> popup = this.activeSelectionPopup;
            closeSelectionPopup();
            popup.mousePressed(mousePos.x(), mousePos.y());
            return;
        }

        if (widgets.onMouseDownBeforeButtons(getMousePoint(mouseX, mouseY), mouseButton)) {
            return;
        }

        if (mouseButton == 0 || mouseButton == 1) {
            writeCarriedItemNameToClickedTextField(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 1) {
            handlingRightClick = true;
            try {
                for (GuiButton widget : this.buttonList) {
                    if (widget.visible && widget.mousePressed(this.mc, mouseX, mouseY)) {
                        widget.playPressSound(this.mc.getSoundHandler());
                        this.actionPerformed(widget);
                        return;
                    }
                }
            } finally {
                handlingRightClick = false;
            }
        }

        if (widgets.onMouseDown(getMousePoint(mouseX, mouseY), mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void writeCarriedItemNameToClickedTextField(int mouseX, int mouseY, int mouseButton) {
        ItemStack carried = this.playerInventory.getItemStack();
        if (carried.isEmpty()) {
            return;
        }

        GuiTextField textField = findTextFieldAt(mouseX, mouseY);
        if (textField == null) {
            return;
        }

        String text = getTextFieldInsertionText(carried, mouseButton);
        if (textField instanceof AETextField aeTextField) {
            aeTextField.setTextFromClient(text);
        } else {
            textField.setText(text);
        }
    }

    @Nullable
    private GuiTextField findTextFieldAt(int mouseX, int mouseY) {
        for (GuiTextField textField : this.widgets.getTextFields()) {
            if (isClickedTextField(textField, mouseX, mouseY)) {
                return textField;
            }
        }

        if (this instanceof ITextFieldGui textFieldGui) {
            for (GuiTextField textField : textFieldGui.getTextFields()) {
                if (isClickedTextField(textField, mouseX, mouseY)) {
                    return textField;
                }
            }
        }

        return null;
    }

    public WidgetContainer getWidgets() {
        return widgets;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (widgets.onMouseUp(getMousePoint(mouseX, mouseY), state)) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        Slot slot = findSlot(mouseX, mouseY);
        if (widgets.onMouseDrag(getMousePoint(mouseX, mouseY), clickedMouseButton)) {
            return;
        }

        ItemStack carried = this.playerInventory.getItemStack();
        if (slot instanceof FakeSlot && !carried.isEmpty()) {
            this.drag_click.add(slot);
            if (this.drag_click.size() > 1) {
                for (Slot draggedSlot : this.drag_click) {
                    if (this.drag_click_sent.add(draggedSlot)) {
                        InventoryAction action = getDragFakeSlotAction(draggedSlot, carried, clickedMouseButton);
                        sendInventoryAction(action, draggedSlot.slotNumber);
                    }
                }
            }
            return;
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    public boolean handleAeMouseWheelInput() {
        int delta = Mouse.getEventDWheel();
        if (delta == 0) {
            return false;
        }

        Point mouse = getMousePoint(Mouse.getEventX() * this.width / this.mc.displayWidth,
            this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1);
        if (widgets.onMouseWheel(mouse, delta > 0 ? 1 : -1)) {
            return true;
        }

        return handleWirelessUniversalTerminalCycle(delta);
    }

    private boolean handleWirelessUniversalTerminalCycle(int delta) {
        if (this.mc.player == null
            || !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            return false;
        }
        if (!(this.container.getLocator() instanceof ItemGuiHostLocator itemLocator)) {
            return false;
        }

        ItemStack stack = itemLocator.locateItem(this.mc.player);
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem)) {
            return false;
        }

        InitNetwork.sendToServer(new CycleWirelessTerminalPacket(delta < 0));
        return true;
    }

    @Override
    protected void handleMouseClick(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (container.isClientSideSlot(slot)) {
            return;
        }

        if (slot instanceof AppEngSlot appEngSlot && !appEngSlot.isEnabled()) {
            return;
        }

        if (slot instanceof DisabledSlot) {
            return;
        }

        if (clickType == ClickType.CLONE && slot != null && GenericStack.isWrapped(slot.getStack())) {
            return;
        }

        if (slot instanceof CraftingTermSlot) {
            InventoryAction action;
            if (isShiftKeyDown()) {
                action = InventoryAction.CRAFT_SHIFT;
            } else if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
                action = InventoryAction.CRAFT_ALL;
            } else {
                action = mouseButton == 1 ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM;
            }

            sendInventoryAction(action, slotId);
            return;
        }

        if (this.drag_click.size() <= 1
            && mouseButton == 1
            && getEmptyingAction(slot, playerInventory.getItemStack()) != null) {
            sendInventoryAction(InventoryAction.EMPTY_ITEM, slotId);
            return;
        }

        if (slot instanceof FakeSlot) {
            if (this.drag_click.size() > 1) {
                return;
            }

            InventoryAction action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                : InventoryAction.PICKUP_OR_SET_DOWN;
            sendInventoryAction(action, slotId);
            this.drag_click.add(slot);
            this.drag_click_sent.add(slot);
            return;
        }

        if (slot != null && Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            sendInventoryAction(InventoryAction.MOVE_REGION, slot.slotNumber);
            return;
        }

        if (slot != null && !this.disableShiftClick && isShiftKeyDown() && mouseButton == 0) {
            this.disableShiftClick = true;

            if (this.dbl_whichItem.isEmpty() || this.bl_clicked != slot
                || this.dbl_clickTimer.elapsed(TimeUnit.MILLISECONDS) > 250) {
                this.bl_clicked = slot;
                this.dbl_clickTimer = Stopwatch.createStarted();
                this.dbl_whichItem = slot.getHasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
            } else if (!this.dbl_whichItem.isEmpty()) {
                for (Slot inventorySlot : this.getInventorySlots()) {
                    if (inventorySlot != null && inventorySlot.canTakeStack(this.mc.player)
                        && inventorySlot.getHasStack()
                        && isSameInventory(inventorySlot, slot)
                        && Container.canAddItemToSlot(inventorySlot, this.dbl_whichItem, true)) {
                        this.handleMouseClick(inventorySlot, inventorySlot.slotNumber, 0, ClickType.QUICK_MOVE);
                    }
                }
                this.dbl_whichItem = ItemStack.EMPTY;
            }

            this.disableShiftClick = false;
        }

        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int guiLeftIn, int guiTopIn) {
        Point mousePos = new Point(mouseX - guiLeftIn, mouseY - guiTopIn);
        if (widgets.hitTest(mousePos)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeftIn, guiTopIn);
    }

    @Nullable
    protected EmptyingAction getEmptyingAction(@Nullable Slot slot, ItemStack carried) {
        return FakeSlotFilterSupport.getEmptyingAction(slot, carried);
    }

    private InventoryAction getDragFakeSlotAction(Slot slot, ItemStack carried, int mouseButton) {
        if (mouseButton == 1 && getEmptyingAction(slot, carried) != null) {
            return InventoryAction.EMPTY_ITEM;
        }

        return mouseButton == 0 ? InventoryAction.PICKUP_OR_SET_DOWN : InventoryAction.PLACE_SINGLE;
    }

    private void sendInventoryAction(InventoryAction action, int slot) {
        InitNetwork.sendToServer(new InventoryActionPacket(this.container.windowId, action, slot, 0));
    }

    private Point getMousePoint(int mouseX, int mouseY) {
        return new Point(mouseX - guiLeft, mouseY - guiTop);
    }

    @Nullable
    protected Slot findSlot(int mouseX, int mouseY) {
        for (Slot slot : this.getInventorySlots()) {
            if (isSlotVisible(slot) && isHovering(slot, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private boolean isSlotVisible(Slot slot) {
        if (!slot.isEnabled()) {
            return false;
        }
        if (slot instanceof AppEngSlot appEngSlot) {
            if (!appEngSlot.isSlotEnabled()) {
                return false;
            }
        }
        return slot.xPos != HIDDEN_SLOT_POS.x() || slot.yPos != HIDDEN_SLOT_POS.y();
    }

    protected boolean isHovering(Slot slot, int mouseX, int mouseY) {
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        int width = 16;
        int height = 16;
        if (slot instanceof ResizableSlot resizableSlot) {
            width = resizableSlot.getWidth();
            height = resizableSlot.getHeight();
        }
        return x >= slot.xPos && x < slot.xPos + width && y >= slot.yPos && y < slot.yPos + height;
    }

    @Override
    protected boolean isPointInRegion(int left, int top, int width, int height, int pointX, int pointY) {
        if (this.suppressVanillaSlotHover) {
            return false;
        }

        return super.isPointInRegion(left, top, width, height, pointX, pointY);
    }

    private boolean isHoveringForHighlight(Slot slot, int mouseX, int mouseY) {
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        int width = 16;
        int height = 16;
        if (slot instanceof ResizableSlot resizableSlot) {
            width = resizableSlot.getWidth();
            height = resizableSlot.getHeight();
        }
        return x >= slot.xPos - 1 && x < slot.xPos + width + 1
            && y >= slot.yPos - 1 && y < slot.yPos + height + 1;
    }

    protected void renderSlotHighlight(Slot slot, int mouseX, int mouseY) {
        if (!isHoveringForHighlight(slot, mouseX, mouseY)) {
            return;
        }

        int width = 16;
        int height = 16;
        if (slot instanceof ResizableSlot resizableSlot) {
            width = resizableSlot.getWidth();
            height = resizableSlot.getHeight();
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.colorMask(true, true, true, false);
        this.drawGradientRect(guiLeft + slot.xPos - 1, guiTop + slot.yPos - 1,
            guiLeft + slot.xPos + width + 1, guiTop + slot.yPos + height + 1, 0x669cd3ff, 0x669cd3ff);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    protected ITextComponent getGuiDisplayName(ITextComponent in) {
        return container.getGuiTitle() != null ? container.getGuiTitle() : in;
    }

    public boolean isHandlingRightClick() {
        return handlingRightClick;
    }

    public final void openSelectionPopup(GridSelectionPopup<?> popup) {
        this.activeSelectionPopup = Objects.requireNonNull(popup, "popup");
    }

    public final void closeSelectionPopup() {
        this.activeSelectionPopup = null;
    }

    private void renderSelectionPopup(int mouseX, int mouseY) {
        if (this.activeSelectionPopup == null) {
            return;
        }

        this.activeSelectionPopup.render(this.mc, mouseX - this.guiLeft, mouseY - this.guiTop);
    }

    private boolean renderSelectionPopupTooltip(int mouseX, int mouseY) {
        if (this.activeSelectionPopup == null) {
            return false;
        }

        List<ITextComponent> tooltip = this.activeSelectionPopup.getTooltip(mouseX - this.guiLeft,
            mouseY - this.guiTop);
        if (tooltip.isEmpty()) {
            return true;
        }

        drawTooltip(mouseX, mouseY, tooltip);
        return true;
    }

    public List<Rectangle> getExclusionZones() {
        updateExclusionZones();
        return this.cachedExclusionZones;
    }

    public Rectangle[] getArrayExclusionZones() {
        updateExclusionZones();
        return this.arrayExclusionZones;
    }

    protected void updateExclusionZones() {
        long layoutVersion = this.widgets.getLayoutVersion();
        if (this.exclusionZonesDirty
            || this.cachedGuiLeft != this.guiLeft
            || this.cachedGuiTop != this.guiTop
            || this.cachedXSize != this.xSize
            || this.cachedYSize != this.ySize
            || this.cachedWidgetLayoutVersion != layoutVersion) {
            var l = new ObjectArrayList<Rectangle>();
            this.widgets.addExclusionZones(l, getBounds(true));
            this.cachedGuiLeft = this.guiLeft;
            this.cachedGuiTop = this.guiTop;
            this.cachedXSize = this.xSize;
            this.cachedYSize = this.ySize;
            this.cachedWidgetLayoutVersion = layoutVersion;
            this.exclusionZonesDirty = false;
            this.arrayExclusionZones = l.toArray(EmptyArrays.EMPTY_RECTANGLE_ARRAY);
            this.cachedExclusionZones = Arrays.asList(this.arrayExclusionZones);
        }
    }

    protected final void invalidateExclusionZonesCache() {
        this.exclusionZonesDirty = true;
    }

    private TextOverride getOrCreateTextOverride(String id) {
        return textOverrides.computeIfAbsent(id, ignored -> new TextOverride());
    }

    protected final void setTextHidden(String id, boolean hidden) {
        getOrCreateTextOverride(id).setHidden(hidden);
    }

    public final void setSlotsHidden(SlotSemantic semantic, boolean hidden) {
        if (hidden) {
            if (hiddenSlots.add(semantic)) {
                for (Slot slot : container.getSlots(semantic)) {
                    slot.xPos = HIDDEN_SLOT_POS.x();
                    slot.yPos = HIDDEN_SLOT_POS.y();
                }
            }
        } else if (hiddenSlots.remove(semantic) && style != null) {
            positionSlots();
        }
    }

    protected final void setTextContent(String id, ITextComponent content) {
        getOrCreateTextOverride(id).setContent(content);
    }

    @Nullable
    public GuiStyle getStyle() {
        return style;
    }

    @Nullable
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        Slot slot = findSlot((int) Math.round(mouseX), (int) Math.round(mouseY));
        if (slot != null) {
            return StackWithBounds.fromSlot(this, slot);
        }
        return null;
    }

    public final int getGuiLeft() {
        return this.guiLeft;
    }

    public final int getGuiTop() {
        return this.guiTop;
    }

    public final Minecraft getMinecraft() {
        return mc;
    }

    @Nullable
    public final Slot getSlotUnderMouse() {
        return hoveredSlot;
    }

    public final T getContainer() {
        return container;
    }

    @Override
    public boolean MT_isMouseTweaksDisabled() {
        return false;
    }

    @Override
    public boolean MT_isWheelTweakDisabled() {
        return true;
    }

    @Override
    public Container MT_getContainer() {
        return getContainer();
    }

    @Override
    public Slot MT_getSlotUnderMouse() {
        return getSlotUnderMouse();
    }

    @Override
    public boolean MT_isCraftingOutput(Slot slot) {
        return slot instanceof OutputSlot
            || slot instanceof AppEngCraftingSlot;
    }

    @Override
    public boolean MT_isIgnored(Slot slot) {
        return slot instanceof FakeSlot || slot instanceof DisabledSlot;
    }

    @Override
    public boolean MT_disableRMBDraggingFunctionality() {
        if (this.dragSplitting && this.dragSplittingButton == 1) {
            this.dragSplitting = false;

            Slot slot = getSlotUnderMouse();
            ItemStack carried = this.mc.player != null ? this.mc.player.inventory.getItemStack() : ItemStack.EMPTY;
            if (slot != null && !carried.isEmpty() && slot.isItemValid(carried)) {
                this.ignoreMouseUp = true;
            }

            return true;
        }

        return false;
    }

    public final void switchToScreen(AEBaseGui<?> screen) {
        beforeSwitchToScreen(screen);
        savedSlotInfos.clear();
        for (Slot slot : container.inventorySlots) {
            savedSlotInfos.add(new SavedSlotInfo(slot));
            slot.xPos = HIDDEN_SLOT_POS.x();
            slot.yPos = HIDDEN_SLOT_POS.y();
        }

        mc.displayGuiScreen(screen);

        if (!screen.savedSlotInfos.isEmpty()) {
            for (SavedSlotInfo savedSlotInfo : screen.savedSlotInfos) {
                savedSlotInfo.restore();
            }
            screen.savedSlotInfos.clear();
        }
    }

    protected void beforeSwitchToScreen(AEBaseGui<?> screen) {
        Objects.requireNonNull(screen, "screen");
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    public final void returnFromSubScreen(AEBaseGui<?> subScreen) {
        onReturnFromSubScreen(subScreen);
    }

    protected void onReturnFromSubScreen(AEBaseGui<?> subScreen) {
        Objects.requireNonNull(subScreen, "subScreen");
    }

    @SuppressWarnings("unused")
    public Collection<? extends Slot> getHEISlots(Object ingredient) {
        return container.inventorySlots;
    }

    private static final class SavedSlotInfo {
        private final Slot slot;
        private final boolean enabled;
        private final int x;
        private final int y;

        private SavedSlotInfo(Slot slot) {
            this.slot = slot;
            this.enabled = !(slot instanceof AppEngSlot appEngSlot) || appEngSlot.isSlotEnabled();
            this.x = slot.xPos;
            this.y = slot.yPos;
        }

        private void restore() {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(enabled);
            }
            slot.xPos = x;
            slot.yPos = y;
        }
    }

    private static final class TextOverride {
        private boolean hidden;
        @Nullable
        private ITextComponent content;

        private boolean isHidden() {
            return hidden;
        }

        private void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        @Nullable
        private ITextComponent getContent() {
            return content;
        }

        private void setContent(@Nullable ITextComponent content) {
            this.content = content;
        }
    }
}
