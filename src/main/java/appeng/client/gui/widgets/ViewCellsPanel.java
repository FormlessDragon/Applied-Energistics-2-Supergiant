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

package appeng.client.gui.widgets;

import appeng.client.Point;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Rects;
import appeng.client.gui.Tooltip;
import appeng.client.gui.style.Blitter;
import appeng.container.slot.AppEngSlot;
import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ViewCellsPanel implements ICompositeWidget {
    private static final int SLOT_SIZE = 18;
    private static final int BORDER_COLOR = 0xFFF2F2F2;
    private static final int PANEL_WIDTH = 24;
    private static final int PANEL_TOP_HEIGHT = 6;
    private static final int PANEL_ROW_HEIGHT = 18;
    private static final int PANEL_BOTTOM_HEIGHT = 6;
    private static final int SLOT_INSET_X = 4;
    private static final int SLOT_INSET_Y = 6;

    private static final Blitter BACKGROUND = Blitter.texture("guis/view_cells_panel.png", 24, 30);

    private final List<Slot> slots;
    private final Supplier<List<ITextComponent>> tooltipSupplier;

    private Point screenOrigin = Point.ZERO;
    private int x;
    private int y;

    public ViewCellsPanel(List<Slot> slots, Supplier<List<ITextComponent>> tooltipSupplier) {
        this.slots = slots;
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.x();
        this.y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rectangle getBounds() {
        int slotCount = getSlotCount();
        return new Rectangle(this.x, this.y, PANEL_WIDTH,
            PANEL_TOP_HEIGHT + slotCount * PANEL_ROW_HEIGHT + PANEL_BOTTOM_HEIGHT);
    }

    @Override
    public boolean hitTest(Point mousePos) {
        return mousePos.isIn(getBounds()) && !isHoveringPanelSlot(mousePos.x(), mousePos.y());
    }

    @Override
    public void populateScreen(Consumer<net.minecraft.client.gui.GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        this.screenOrigin = Point.fromTopLeft(bounds);
    }

    @Override
    public void updateBeforeRender() {
        int slotX = this.x + SLOT_INSET_X;
        int slotY = this.y + SLOT_INSET_Y;

        for (Slot slot : this.slots) {
            if (slot instanceof AppEngSlot appEngSlot && !appEngSlot.isSlotEnabled()) {
                continue;
            }

            slot.xPos = slotX;
            slot.yPos = slotY;
            slotY += SLOT_SIZE;
        }
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        int slotCount = getSlotCount();
        if (slotCount <= 0) {
            return;
        }

        int drawX = this.screenOrigin.x() + this.x;
        int drawY = this.screenOrigin.y() + this.y;
        BACKGROUND.copy().src(0, 0, PANEL_WIDTH, PANEL_TOP_HEIGHT).dest(drawX, drawY).blit();

        int bodyY = drawY + PANEL_TOP_HEIGHT;
        for (int i = 0; i < slotCount; i++) {
            BACKGROUND.copy().src(0, PANEL_TOP_HEIGHT, PANEL_WIDTH, PANEL_ROW_HEIGHT).dest(drawX, bodyY).blit();
            bodyY += PANEL_ROW_HEIGHT;
        }

        BACKGROUND.copy().src(0, PANEL_TOP_HEIGHT + PANEL_ROW_HEIGHT, PANEL_WIDTH, PANEL_BOTTOM_HEIGHT)
                  .dest(drawX, bodyY)
                  .blit();

        int slotLeft = drawX + SLOT_INSET_X - 1;
        int slotTop = drawY + SLOT_INSET_Y - 1;
        int slotRight = slotLeft + 18;
        int slotBottom = slotTop + slotCount * SLOT_SIZE;
        Gui.drawRect(slotLeft + 1, slotTop, slotRight - 1, slotTop + 1, BORDER_COLOR);
        Gui.drawRect(slotLeft + 1, slotBottom, slotRight - 1, slotBottom + 1, BORDER_COLOR);
        Gui.drawRect(slotLeft, slotTop, slotLeft + 1, slotBottom + 1, BORDER_COLOR);
        Gui.drawRect(slotRight - 1, slotTop, slotRight, slotBottom + 1, BORDER_COLOR);
    }

    @Override
    public void addExclusionZones(List<Rectangle> exclusionZones, Rectangle screenBounds) {
        exclusionZones.add(Rects.expand(new Rectangle(
            screenBounds.x + this.x,
            screenBounds.y + this.y,
            PANEL_WIDTH,
            PANEL_TOP_HEIGHT + getSlotCount() * PANEL_ROW_HEIGHT + PANEL_BOTTOM_HEIGHT), 2));
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (getSlotCount() == 0 || !isHoveringEmptyPanelSlot(mouseX, mouseY)) {
            return null;
        }

        List<ITextComponent> tooltip = this.tooltipSupplier.get();
        if (tooltip.isEmpty()) {
            return null;
        }

        return new Tooltip(tooltip);
    }

    private int getSlotCount() {
        int count = 0;
        for (Slot slot : this.slots) {
            if (slot instanceof AppEngSlot appEngSlot && appEngSlot.isSlotEnabled()) {
                count++;
            }
        }
        return count;
    }

    private boolean isHoveringPanelSlot(int mouseX, int mouseY) {
        for (Slot slot : this.slots) {
            if (!(slot instanceof AppEngSlot appEngSlot) || !appEngSlot.isSlotEnabled()) {
                continue;
            }

            if (mouseX >= slot.xPos && mouseX < slot.xPos + SLOT_SIZE
                && mouseY >= slot.yPos && mouseY < slot.yPos + SLOT_SIZE) {
                return true;
            }
        }

        return false;
    }

    private boolean isHoveringEmptyPanelSlot(int mouseX, int mouseY) {
        for (Slot slot : this.slots) {
            if (!(slot instanceof AppEngSlot appEngSlot) || !appEngSlot.isSlotEnabled()) {
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
}
