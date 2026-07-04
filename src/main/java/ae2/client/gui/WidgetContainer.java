/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

import ae2.client.Point;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.TooltipArea;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AECheckbox;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.BackgroundPanel;
import ae2.client.gui.widgets.IResizableWidget;
import ae2.client.gui.widgets.ITickingWidget;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.NumberEntryWidget;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.TabButton;
import ae2.client.gui.widgets.TooltipButton;
import ae2.container.GuiIds;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This utility class helps with positioning commonly used Minecraft {@link GuiButton} instances on a screen
 * without having to recreate them everytime the screen resizes in the <code>init</code> method.
 * <p/>
 * This class sources the positioning and sizing for widgets from the {@link GuiStyle}, and correlates between the
 * screen's JSON file and the widget using a string id.
 */
public class WidgetContainer {
    private static final Point OFFSCREEN_MOUSE = new Point(Integer.MIN_VALUE / 4, Integer.MIN_VALUE / 4);
    @Nullable
    private final GuiStyle style;
    private final Object2ObjectLinkedOpenHashMap<String, GuiButton> widgets = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<String, AETextField> textFields = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<String, ICompositeWidget> compositeWidgets = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectList<ICompositeWidget> compositeWidgetOrder = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<String, ResolvedTooltipArea> tooltips = new Object2ObjectLinkedOpenHashMap<>();
    private Rectangle currentBounds = Rects.ZERO;
    private long layoutVersion;

    @SuppressWarnings("unused")
    public WidgetContainer() {
        this(null);
    }

    public WidgetContainer(@Nullable GuiStyle style) {
        this.style = style;
    }

    private static boolean contains(Rectangle area, int mouseX, int mouseY) {
        if (mouseX < area.x) return false;
        if (mouseX >= area.x + area.width
            || mouseY < area.y) return false;
        return mouseY < area.y + area.height;
    }

    public void add(String id, GuiButton widget) {
        Preconditions.checkState(!compositeWidgets.containsKey(id), "%s already used for composite widget", id);
        Preconditions.checkState(!textFields.containsKey(id), "%s already used for text field", id);

        if (this.style != null) {
            WidgetStyle widgetStyle = this.style.getWidget(id);
            int width = widgetStyle.getWidth() != 0 ? widgetStyle.getWidth() : widget.width;
            int height = widgetStyle.getHeight() != 0 ? widgetStyle.getHeight() : widget.height;
            if (widget instanceof IResizableWidget resizableWidget) {
                resizableWidget.resize(width, height);
            } else {
                widget.width = width;
                widget.height = height;
            }

            if (widget instanceof TabButton tabButton) {
                if (widgetStyle.isHideEdge()) {
                    tabButton.setStyle(TabButton.Style.CORNER);
                }
            }
        }

        if (widgets.put(id, widget) != null) {
            throw new IllegalStateException("Duplicate id: " + id);
        }
        this.layoutVersion++;
    }

    public void add(String id, AETextField widget) {
        Preconditions.checkState(!widgets.containsKey(id), "%s already used for widget", id);
        Preconditions.checkState(!compositeWidgets.containsKey(id), "%s already used for composite widget", id);

        WidgetStyle widgetStyle = requireStyle().getWidget(id);
        int width;
        if (widgetStyle.getWidth() != 0) {
            width = widgetStyle.getWidth();
        } else {
            width = widget.getTooltipArea().width;
        }
        int height;
        if (widgetStyle.getHeight() != 0) {
            height = widgetStyle.getHeight();
        } else {
            height = widget.getTooltipArea().height;
        }
        widget.resize(width, height);

        if (textFields.put(id, widget) != null) {
            throw new IllegalStateException("Duplicate id: " + id);
        }
        this.layoutVersion++;
    }

    public void add(String id, ICompositeWidget widget) {
        Preconditions.checkState(!widgets.containsKey(id), "%s already used for widget", id);
        Preconditions.checkState(!textFields.containsKey(id), "%s already used for text field", id);

        if (this.style != null) {
            WidgetStyle widgetStyle = this.style.getWidget(id);
            widget.setSize(widgetStyle.getWidth(), widgetStyle.getHeight());
        }

        if (compositeWidgets.put(id, widget) != null) {
            throw new IllegalStateException("Duplicate id: " + id);
        }
        this.compositeWidgetOrder.add(widget);
        this.layoutVersion++;
    }

    public long getLayoutVersion() {
        return this.layoutVersion;
    }

    public GuiButton get(String id) {
        return widgets.get(id);
    }

    @SuppressWarnings("unused")
    public AETextField getTextField(String id) {
        return textFields.get(id);
    }

    @SuppressWarnings("unused")
    public ICompositeWidget getComposite(String id) {
        return compositeWidgets.get(id);
    }

    /**
     * Convenient way to add Vanilla buttons without having to specify x,y,width and height. The actual
     * position/rectangle is instead sourced from the screen style.
     */
    public AE2Button addButton(String id, ITextComponent text, Runnable action) {
        AE2Button button = new AE2Button(text, action);
        add(id, button);
        return button;
    }

    public TooltipButton addTooltipButton(String id, ITextComponent text,
                                          Supplier<List<ITextComponent>> tooltipSupplier, Runnable action) {
        TooltipButton button = new TooltipButton(text, tooltipSupplier, action);
        add(id, button);
        return button;
    }

    public AECheckbox addCheckbox(String id, ITextComponent text, Runnable changeListener) {
        AECheckbox checkbox = new AECheckbox(0, 0, 0, AECheckbox.SIZE, requireStyle(), text);
        add(id, checkbox);
        checkbox.setChangeListener(changeListener);
        return checkbox;
    }

    public NumberEntryWidget addNumberEntryWidget(String id, NumberEntryType type) {
        NumberEntryWidget numberEntry = new NumberEntryWidget(requireStyle(), type);
        add(id, numberEntry);
        return numberEntry;
    }

    /**
     * Adds a {@link Scrollbar} to the screen.
     */
    @SuppressWarnings("unused")
    public Scrollbar addScrollBar(String id) {
        return addScrollBar(id, Scrollbar.DEFAULT);
    }

    /**
     * Adds a {@link Scrollbar} to the screen.
     */
    public Scrollbar addScrollBar(String id, Scrollbar.Style style) {
        Scrollbar scrollbar = new Scrollbar(style);
        add(id, scrollbar);
        return scrollbar;
    }

    /**
     * Adds a panel to the screen, which takes its background from the style's "images" section, and it's position from
     * the widget section.
     *
     * @param id The id used to look up the background image and bounds in the style.
     */
    public void addBackgroundPanel(String id) {
        Blitter background = requireStyle().getImage(id).copy();
        add(id, new BackgroundPanel(background));
    }

    /**
     * Adds a button named "openPriority" that opens the priority GUI for the current container host.
     */
    public void addOpenPriorityButton() {
        add("openPriority", new TabButton(Icon.PRIORITY, GuiText.Priority.text(), this::openPriorityGui));
    }

    private void openPriorityGui() {
        ServerboundPacket message = SwitchGuisPacket.openSubGui(GuiIds.GuiKey.PRIORITY);
        InitNetwork.sendToServer(message);
    }

    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        this.currentBounds = bounds;

        for (Map.Entry<String, GuiButton> entry : widgets.entrySet()) {
            GuiButton widget = entry.getValue();
            if (widget instanceof TabButton tabButton && tabButton.isFocused()) {
                tabButton.setFocused(false);
            } else if (widget instanceof AECheckbox checkBox && checkBox.isFocused()) {
                checkBox.setFocused(false);
            }

            if (this.style != null) {
                WidgetStyle widgetStyle = this.style.getWidget(entry.getKey());
                Point pos = widgetStyle.resolve(bounds);
                if (widget instanceof IResizableWidget resizableWidget) {
                    resizableWidget.move(pos);
                } else {
                    widget.x = pos.x();
                    widget.y = pos.y();
                }
            }

            addWidget.accept(widget);
        }

        for (Map.Entry<String, AETextField> entry : textFields.entrySet()) {
            AETextField widget = entry.getValue();
            if (widget.isFocused()) {
                widget.setFocused(false);
            }

            if (this.style != null) {
                WidgetStyle widgetStyle = this.style.getWidget(entry.getKey());
                widget.move(widgetStyle.resolve(bounds));
            }
        }

        Rectangle relativeBounds = new Rectangle(0, 0, bounds.width, bounds.height);
        for (Map.Entry<String, ICompositeWidget> entry : compositeWidgets.entrySet()) {
            ICompositeWidget widget = entry.getValue();
            if (this.style != null) {
                WidgetStyle widgetStyle = this.style.getWidget(entry.getKey());
                widget.setPosition(widgetStyle.resolve(relativeBounds));
            }

            widget.populateScreen(addWidget, bounds, screen);
        }

        tooltips.clear();
        if (this.style != null) {
            for (Map.Entry<String, TooltipArea> entry : this.style.getTooltips().entrySet()) {
                Point pos = entry.getValue().resolve(relativeBounds);
                Rectangle area = new Rectangle(
                    pos.x(), pos.y(),
                    entry.getValue().getWidth(),
                    entry.getValue().getHeight());
                tooltips.put(entry.getKey(), new ResolvedTooltipArea(
                    area, new Tooltip(entry.getValue().getTooltip())));
            }
        }
    }

    public Iterable<GuiButton> values() {
        return widgets.values();
    }

    /**
     * Tick {@link ICompositeWidget} instances that are not automatically ticked as part of being a normal widget.
     */
    public void tick() {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()) {
                widget.tick();
            }
        }

        for (GuiButton widget : widgets.values()) {
            if (widget instanceof ITickingWidget tickingWidget) {
                tickingWidget.tick();
            }
        }
        for (AETextField textField : textFields.values()) {
            if (textField.getVisible()) {
                textField.updateCursorCounter();
                textField.tickKeyRepeat();
            }
        }
    }

    /**
     * @see ICompositeWidget#updateBeforeRender()
     */
    public void updateBeforeRender() {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()) {
                widget.updateBeforeRender();
            }
        }
    }

    /**
     * @see ICompositeWidget#drawBackgroundLayer(Rectangle, Point)
     */
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()) {
                widget.drawBackgroundLayer(bounds, mouse);
            }
        }
    }

    /**
     * @see ICompositeWidget#drawForegroundLayer(Rectangle, Point)
     */
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()) {
                widget.drawForegroundLayer(bounds, mouse);
            }
        }
    }

    public void drawTextFields(Point mouse, @Nullable ICompositeWidget mouseInteractionBlocker) {
        for (AETextField textField : textFields.values()) {
            if (textField.getVisible()) {
                textField.drawTextBox();
            }
        }

        boolean blockedByLowerWidget = mouseInteractionBlocker != null;
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()) {
                if (widget == mouseInteractionBlocker) {
                    blockedByLowerWidget = false;
                }

                Point widgetMouse = blockedByLowerWidget ? OFFSCREEN_MOUSE : mouse;
                widget.drawAbsoluteLayer(currentBounds, new Point(
                    currentBounds.x + widgetMouse.x(),
                    currentBounds.y + widgetMouse.y()));
            }
        }
    }

    /**
     * @see ICompositeWidget#onMouseDown(Point, int)
     */
    public boolean onMouseDown(Point mousePos, int btn) {
        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            ICompositeWidget widget = this.compositeWidgetOrder.get(i);
            if (widget.isVisible()
                && (widget.wantsAllMouseDownEvents() || mousePos.isIn(widget.getBounds()))
                && widget.onMouseDown(mousePos, btn)) {
                return true;
            }
        }

        for (AETextField textField : textFields.values()) {

            if (textField.mouseClicked(currentBounds.x + mousePos.x(), currentBounds.y + mousePos.y(),
                btn)) {
                return true;
            }
        }

        return false;
    }

    public boolean onMouseDownBeforeButtons(Point mousePos, int btn) {
        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            ICompositeWidget widget = this.compositeWidgetOrder.get(i);
            if (widget.isVisible()
                && widget.wantsAllMouseDownEvents()
                && widget.onMouseDown(mousePos, btn)) {
                return true;
            }
        }

        return false;
    }

    public boolean blocksMouseInteraction(Point mousePos) {
        return getMouseInteractionBlocker(mousePos) != null;
    }

    @Nullable
    public ICompositeWidget getMouseInteractionBlocker(Point mousePos) {
        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            ICompositeWidget widget = this.compositeWidgetOrder.get(i);
            if (widget.isVisible() && widget.blocksMouseInteraction(mousePos.x(), mousePos.y())) {
                return widget;
            }
        }
        return null;
    }

    /**
     * @see ICompositeWidget#onMouseUp(Point, int)
     */
    public boolean onMouseUp(Point mousePos, int btn) {
        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            ICompositeWidget widget = this.compositeWidgetOrder.get(i);
            if (widget.isVisible()
                && (widget.wantsAllMouseUpEvents() || mousePos.isIn(widget.getBounds()))
                && widget.onMouseUp(mousePos, btn)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @see ICompositeWidget#onMouseDrag(Point, int)
     */
    public boolean onMouseDrag(Point mousePos, int btn) {
        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            ICompositeWidget widget = this.compositeWidgetOrder.get(i);
            if (widget.isVisible() && widget.onMouseDrag(mousePos, btn)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @see ICompositeWidget#onMouseWheel(Point, double)
     */
    public boolean onMouseWheel(Point mousePos, double wheelDelta) {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()
                && mousePos.isIn(widget.getBounds())
                && widget.onMouseWheel(mousePos, wheelDelta)) {
                return true;
            }
        }

        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            var widget = this.compositeWidgetOrder.get(i);
            if (widget.isVisible()
                && widget.wantsAllMouseWheelEvents()
                && widget.onMouseWheel(mousePos, wheelDelta)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @see ICompositeWidget#addExclusionZones(List, Rectangle)
     */
    public void addExclusionZones(List<Rectangle> exclusionZones, Rectangle bounds) {
        for (GuiButton widget : widgets.values()) {
            if (!widget.visible) {
                continue;
            }

            if (widget.x < bounds.x
                || widget.y < bounds.y
                || widget.x + widget.width > bounds.x + bounds.width
                || widget.y + widget.height > bounds.y + bounds.height) {
                exclusionZones.add(new Rectangle(widget.x, widget.y, widget.width, widget.height));
            }
        }

        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible()) {
                widget.addExclusionZones(exclusionZones, bounds);
            }
        }
    }

    public boolean onKeyTyped(char typedChar, int keyCode) {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible() && widget.onKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }

        for (AETextField textField : textFields.values()) {
            if (textField.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Enables or disables a tooltip area that is defined in the widget styles.
     */
    public void setTooltipAreaEnabled(String id, boolean enabled) {
        ResolvedTooltipArea tooltip = tooltips.get(id);
        Preconditions.checkArgument(tooltip != null, "No tooltip with id '%s' is defined", id);
        tooltip.enabled = enabled;
    }

    @Nullable
    public Tooltip getTooltip(int mouseX, int mouseY) {
        for (int i = this.compositeWidgetOrder.size() - 1; i >= 0; i--) {
            ICompositeWidget widget = this.compositeWidgetOrder.get(i);
            if (!widget.isVisible()) {
                continue;
            }

            Rectangle bounds = widget.getBounds();
            if (widget.wantsAllMouseDownEvents() || contains(bounds, mouseX, mouseY)) {
                Tooltip tooltip = widget.getTooltip(mouseX, mouseY);
                if (tooltip != null) {
                    return tooltip;
                }
                if (widget.blocksTooltips(mouseX, mouseY)) {
                    return null;
                }
            }
        }

        for (GuiButton widget : widgets.values()) {
            if (widget instanceof ITooltip tooltip) {
                if (tooltip.isTooltipAreaVisible()) {
                    Rectangle area = toRelative(tooltip.getTooltipArea());
                    if (contains(area, mouseX, mouseY)) {
                        return new Tooltip(tooltip.getTooltipMessage());
                    }
                }
            }
        }

        for (AETextField widget : textFields.values()) {
            if (widget.isTooltipAreaVisible()) {
                Rectangle area = toRelative(widget.getTooltipArea());
                if (contains(area, mouseX, mouseY)) {
                    return new Tooltip(widget.getTooltipMessage());
                }
            }
        }

        for (ResolvedTooltipArea tooltipArea : tooltips.values()) {
            if (tooltipArea.enabled && contains(tooltipArea.area, mouseX, mouseY)) {
                return tooltipArea.tooltip;
            }
        }

        return null;
    }

    /**
     * Check if there's any content or compound widget at the given screen-relative mouse position.
     */
    public boolean hitTest(Point mousePos) {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible() && widget.hitTest(mousePos)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInCompositeWidgetBounds(Point mousePos) {
        for (ICompositeWidget widget : compositeWidgets.values()) {
            if (widget.isVisible() && contains(widget.getBounds(), mousePos.x(), mousePos.y())) {
                return true;
            }
        }
        return false;
    }

    public AETextField addTextField(String id) {
        AETextField searchField = new AETextField(requireStyle(), Minecraft.getMinecraft().fontRenderer,
            0, 0, 0, 0);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(25);
        searchField.setTextColor(0xFFFFFF);
        searchField.setVisible(true);
        add(id, searchField);
        return searchField;
    }

    private Rectangle toRelative(Rectangle area) {

        return new Rectangle(
            area.x - currentBounds.x,
            area.y - currentBounds.y,
            area.width,
            area.height);
    }

    private GuiStyle requireStyle() {
        if (this.style == null) {
            throw new IllegalStateException("WidgetContainer requires a GuiStyle for styled widgets");
        }
        return this.style;
    }

    public Collection<? extends GuiTextField> getTextFields() {
        return textFields.values();
    }

    private static class ResolvedTooltipArea {
        private final Rectangle area;
        private final Tooltip tooltip;
        private boolean enabled = true;

        public ResolvedTooltipArea(Rectangle area, Tooltip tooltip) {
            this.area = area;
            this.tooltip = tooltip;
        }
    }
}
