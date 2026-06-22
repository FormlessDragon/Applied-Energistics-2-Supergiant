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

package ae2.client.gui.widgets;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Rects;
import ae2.client.gui.Tooltip;
import ae2.container.slot.AppEngSlot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ScrollingUpgradesPanel implements ICompositeWidget {
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int DEFAULT_MAX_ROWS = 5;
    private static final int MIN_SCROLLING_ROWS = 5;
    private static final int PANEL_X_OFFSET = 2;
    private static final int SLOT_X_OFFSET = 5;
    private static final int SLOT_Y_OFFSET = PADDING + 1;
    private static final int SCROLLBAR_X_OFFSET = 23;
    private static final int SCROLLBAR_Y_OFFSET = 6;
    private static final int HIDDEN_SLOT_POS = -9999;

    private final List<Slot> slots;
    private final List<Slot> leadingSlots;
    private final BooleanSupplier hideLeadingSlots;
    private final Supplier<List<ITextComponent>> tooltipSupplier;
    private final Scrollbar scrollbar;
    private final ObjectArrayList<Slot> panelSlots;
    private final ObjectArrayList<Slot> visiblePanelSlots;

    private Point screenOrigin = Point.ZERO;
    private int x;
    private int y;
    private int maxRows = DEFAULT_MAX_ROWS;

    ScrollingUpgradesPanel(List<Slot> slots, List<Slot> leadingSlots, BooleanSupplier hideLeadingSlots,
                           Supplier<List<ITextComponent>> tooltipSupplier, Scrollbar scrollbar) {
        this.slots = slots;
        this.leadingSlots = leadingSlots;
        this.hideLeadingSlots = hideLeadingSlots;
        this.tooltipSupplier = tooltipSupplier;
        this.scrollbar = scrollbar;
        this.scrollbar.setCaptureMouseWheel(false);
        int totalSlots = slots.size() + leadingSlots.size();
        this.panelSlots = new ObjectArrayList<>(totalSlots);
        this.visiblePanelSlots = new ObjectArrayList<>(Math.min(DEFAULT_MAX_ROWS, totalSlots));
    }

    private static void disableSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(false);
                appEngSlot.setActive(false);
            }
        }
    }

    private static void enableSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(true);
            }
        }
    }

    private static boolean isSlotEnabled(Slot slot) {
        return !(slot instanceof AppEngSlot appEngSlot) || appEngSlot.isSlotEnabled();
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.x() + PANEL_X_OFFSET;
        this.y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
        if (height >= SLOT_SIZE) {
            this.maxRows = Math.max(MIN_SCROLLING_ROWS, height / SLOT_SIZE);
        }
    }

    @Override
    public Rectangle getBounds() {
        rebuildPanelSlots();
        int visibleSlots = getVisibleSlotCount();
        if (visibleSlots <= 0) {
            return new Rectangle(this.x, this.y, 0, 0);
        }
        UpgradeBackground background = UpgradeBackground.get(scrolling());
        return new Rectangle(this.x, this.y, background.width(), 2 * PADDING + visibleSlots * SLOT_SIZE);
    }

    @Override
    public boolean hitTest(Point mousePos) {
        return mousePos.isIn(getBounds()) && !isHoveringPanelSlot(mousePos.x(), mousePos.y());
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        this.screenOrigin = Point.fromTopLeft(bounds);
        rebuildPanelSlots();
        updateScrollbar();
    }

    @Override
    public void updateBeforeRender() {
        rebuildPanelSlots();
        updateScrollbar();
        deactivatePanelSlots();
        int visibleSlotCount = getVisibleSlotCount();
        this.visiblePanelSlots.clear();
        this.visiblePanelSlots.ensureCapacity(visibleSlotCount);
        int slotOriginX = this.x + SLOT_X_OFFSET;
        int slotOriginY = this.y + SLOT_Y_OFFSET;
        int firstSlot = this.scrollbar.getCurrentScroll();
        int index = 0;

        for (Slot slot : this.panelSlots) {
            boolean slotVisible = index >= firstSlot && index < firstSlot + visibleSlotCount;
            index++;

            if (!slotVisible) {
                continue;
            }

            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(true);
            }
            slot.xPos = slotOriginX;
            slot.yPos = slotOriginY;
            this.visiblePanelSlots.add(slot);
            slotOriginY += SLOT_SIZE;
        }
    }

    @Override
    public void tick() {
        this.scrollbar.tick();
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        int visibleSlots = getVisibleSlotCount();
        if (visibleSlots <= 0) {
            return;
        }

        int slotOriginX = this.screenOrigin.x() + this.x;
        int slotOriginY = this.screenOrigin.y() + this.y + PADDING;
        UpgradeBackground background = UpgradeBackground.get(scrolling());

        background.top().copy().dest(slotOriginX, slotOriginY - PADDING).blit();
        for (int i = 1; i < visibleSlots - 1; i++) {
            background.middle().copy().dest(slotOriginX, slotOriginY + i * SLOT_SIZE).blit();
        }
        background.bottom().copy().dest(slotOriginX, slotOriginY + (visibleSlots - 1) * SLOT_SIZE).blit();
    }

    @Override
    public void addExclusionZones(List<Rectangle> exclusionZones, Rectangle screenBounds) {
        Rectangle bounds = getBounds();
        if (bounds.width > 0 && bounds.height > 0) {
            exclusionZones.add(Rects.expand(new Rectangle(
                screenBounds.x + this.x,
                screenBounds.y + this.y,
                bounds.width,
                bounds.height), 2));
        }
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (getUpgradeSlotCount() == 0 || !isHoveringEmptyUpgradeSlot(mouseX, mouseY)) {
            return null;
        }

        List<ITextComponent> tooltip = this.tooltipSupplier.get();
        return tooltip.isEmpty() ? null : new Tooltip(tooltip);
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (!this.scrollbar.isVisible() || !isMouseOverScrollableArea(mousePos)) {
            return false;
        }

        return this.scrollbar.onMouseWheel(mousePos, delta);
    }

    @Override
    public boolean wantsAllMouseWheelEvents() {
        return this.scrollbar.isVisible();
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        return this.scrollbar.isVisible()
            && mousePos.isIn(this.scrollbar.getBounds())
            && this.scrollbar.onMouseDown(mousePos, button);
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        return this.scrollbar.onMouseUp(mousePos, button);
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return true;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        return this.scrollbar.onMouseDrag(mousePos, button);
    }

    private void rebuildPanelSlots() {
        this.panelSlots.ensureCapacity(this.slots.size()
            + (this.hideLeadingSlots.getAsBoolean() ? 0 : this.leadingSlots.size()));
        this.panelSlots.clear();
        if (!this.hideLeadingSlots.getAsBoolean()) {
            enableSlots(this.leadingSlots);
            for (Slot slot : this.leadingSlots) {
                if (isSlotEnabled(slot)) {
                    this.panelSlots.add(slot);
                }
            }
        } else {
            disableSlots(this.leadingSlots);
        }
        for (Slot slot : this.slots) {
            if (isSlotEnabled(slot)) {
                this.panelSlots.add(slot);
            }
        }
    }

    @Override
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
        if (this.scrollbar.isVisible()) {
            this.scrollbar.drawForegroundLayer(bounds, mouse);
        }
    }

    private void deactivatePanelSlots() {
        for (Slot slot : this.leadingSlots) {
            hideSlot(slot);
        }
        for (Slot slot : this.slots) {
            hideSlot(slot);
        }
    }

    private void hideSlot(Slot slot) {
        if (slot instanceof AppEngSlot appEngSlot) {
            appEngSlot.setActive(false);
        }
        slot.xPos = HIDDEN_SLOT_POS;
        slot.yPos = HIDDEN_SLOT_POS;
    }

    private int getUpgradeSlotCount() {
        return this.panelSlots.size();
    }

    private int getVisibleSlotCount() {
        return Math.min(this.maxRows, getUpgradeSlotCount());
    }

    private boolean scrolling() {
        return getUpgradeSlotCount() > this.maxRows;
    }

    private boolean isMouseOverScrollableArea(Point mousePos) {
        if (mousePos.isIn(getBounds())) {
            return true;
        }

        return mousePos.isIn(this.scrollbar.getBounds());
    }

    private void updateScrollbar() {
        this.scrollbar.setRange(0, getUpgradeSlotCount() - getVisibleSlotCount(), 1);
        this.scrollbar.setVisible(scrolling());
        this.scrollbar.setHeight(Math.max(0, getVisibleSlotCount() * SLOT_SIZE - 2));
        this.scrollbar.setPosition(new Point(
            this.x + SCROLLBAR_X_OFFSET,
            this.y + SCROLLBAR_Y_OFFSET));
    }

    private boolean isHoveringEmptyUpgradeSlot(int mouseX, int mouseY) {
        for (Slot slot : this.visiblePanelSlots) {
            if (slot instanceof AppEngSlot appEngSlot && !appEngSlot.isSlotEnabled()) {
                continue;
            }

            if (!slot.getStack().isEmpty()) {
                continue;
            }

            if (mouseX >= slot.xPos && mouseX < slot.xPos + SLOT_SIZE
                && mouseY >= slot.yPos && mouseY < slot.yPos + SLOT_SIZE) {
                return true;
            }
        }

        return false;
    }

    private boolean isHoveringPanelSlot(int mouseX, int mouseY) {
        for (Slot slot : this.visiblePanelSlots) {
            if (slot instanceof AppEngSlot appEngSlot && !appEngSlot.isSlotEnabled()) {
                continue;
            }

            if (mouseX >= slot.xPos && mouseX < slot.xPos + SLOT_SIZE
                && mouseY >= slot.yPos && mouseY < slot.yPos + SLOT_SIZE) {
                return true;
            }
        }

        return false;
    }
}
