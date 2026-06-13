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

import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.Upgrades;
import ae2.client.Point;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Rects;
import ae2.client.gui.Tooltip;
import ae2.client.gui.WidgetContainer;
import ae2.client.gui.style.Blitter;
import ae2.container.slot.AppEngSlot;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class UpgradesPanel implements ICompositeWidget {
    private static final int SCROLLING_THRESHOLD = 5;

    private static final int SLOT_SIZE = 18;
    private static final int LEFT_PADDING = 5;
    private static final int RIGHT_PADDING = 6;
    private static final int TOP_PADDING = 5;
    private static final int BOTTOM_PADDING = 7;
    private static final int DEFAULT_MAX_VISIBLE_SLOTS = 8;
    private static final int OFFSET_X = 2;
    private static final int SLOT_X = 4;
    private static final int SLOT_Y = TOP_PADDING + 1;

    private final List<Slot> slots;
    private final List<Slot> leadingSlots;
    private final BooleanSupplier hideLeadingSlots;
    private final Supplier<List<ITextComponent>> tooltipSupplier;
    private final Blitter background;
    private int x;
    private int y;
    private int maxVisibleSlots = DEFAULT_MAX_VISIBLE_SLOTS;

    @SuppressWarnings("unused")
    public UpgradesPanel(List<Slot> slots, IUpgradeableObject upgradeableObject) {
        this(slots, () -> getCompatibleUpgrades(upgradeableObject));
    }

    public UpgradesPanel(List<Slot> slots, Supplier<List<ITextComponent>> tooltipSupplier) {
        this(slots, List.of(), () -> false, tooltipSupplier,
            Blitter.texture("guis/extra_panels.png", 128, 128));
    }

    public UpgradesPanel(List<Slot> slots, List<Slot> leadingSlots, BooleanSupplier hideLeadingSlots,
                         Supplier<List<ITextComponent>> tooltipSupplier) {
        this(slots, leadingSlots, hideLeadingSlots, tooltipSupplier,
            Blitter.texture("guis/extra_panels.png", 128, 128));
    }

    protected UpgradesPanel(List<Slot> slots, List<Slot> leadingSlots, BooleanSupplier hideLeadingSlots,
                            Supplier<List<ITextComponent>> tooltipSupplier, Blitter background) {
        this.slots = slots;
        this.leadingSlots = leadingSlots;
        this.hideLeadingSlots = hideLeadingSlots;
        this.tooltipSupplier = tooltipSupplier;
        this.background = background;
    }

    public static ICompositeWidget create(WidgetContainer widgets, List<Slot> slots,
                                          Supplier<List<ITextComponent>> tooltipSupplier) {
        return create(widgets, slots, List.of(), () -> false, tooltipSupplier);
    }

    public static ICompositeWidget create(WidgetContainer widgets, List<Slot> slots, IUpgradeableObject upgradeableObject) {
        return create(widgets, slots, () -> getCompatibleUpgrades(upgradeableObject));
    }

    public static ICompositeWidget create(WidgetContainer widgets, List<Slot> slots, List<Slot> leadingSlots,
                                          IUpgradeableObject upgradeableObject) {
        return create(
            widgets,
            slots,
            leadingSlots,
            () -> shouldHideWirelessSingularitySlot(leadingSlots, upgradeableObject.getUpgrades()),
            () -> getCompatibleUpgrades(upgradeableObject));
    }

    public static ICompositeWidget create(WidgetContainer widgets, List<Slot> slots, List<Slot> leadingSlots,
                                          BooleanSupplier hideLeadingSlots,
                                          Supplier<List<ITextComponent>> tooltipSupplier) {
        int visibleSlots = getPanelSlotCount(slots, leadingSlots, hideLeadingSlots);
        if (visibleSlots <= SCROLLING_THRESHOLD) {
            return new UpgradesPanel(slots, leadingSlots, hideLeadingSlots, tooltipSupplier);
        }

        Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
        return new ScrollingUpgradesPanel(slots, leadingSlots, hideLeadingSlots, tooltipSupplier, scrollbar);
    }

    private static void disableSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(false);
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

    private static int getEnabledSlotCount(List<Slot> slots) {
        int count = 0;
        for (Slot slot : slots) {
            if (isSlotEnabled(slot)) {
                count++;
            }
        }
        return count;
    }

    private static int getPanelSlotCount(List<Slot> slots, List<Slot> leadingSlots, BooleanSupplier hideLeadingSlots) {
        int visibleSlots = getEnabledSlotCount(slots);
        if (!hideLeadingSlots.getAsBoolean()) {
            visibleSlots += getEnabledSlotCount(leadingSlots);
        }
        return visibleSlots;
    }

    private static boolean shouldHideWirelessSingularitySlot(List<Slot> slots, IUpgradeInventory upgrades) {
        if (slots.isEmpty() || upgrades.isInstalled(AEItems.QUANTUM_BRIDGE_CARD.item())) {
            return false;
        }
        for (Slot slot : slots) {
            boolean enabled = true;
            if (slot instanceof AppEngSlot appEngSlot) {
                enabled = appEngSlot.isSlotEnabled();
                appEngSlot.setSlotEnabled(true);
            }
            boolean empty = slot.getStack().isEmpty();
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(enabled);
            }
            if (!empty) {
                return false;
            }
        }
        return true;
    }

    private static List<ITextComponent> getCompatibleUpgrades(IUpgradeableObject upgradeableObject) {
        var tooltip = new ObjectArrayList<ITextComponent>();
        tooltip.add(GuiText.CompatibleUpgrades.text());
        tooltip.addAll(Upgrades.getTooltipLinesForInventory(upgradeableObject.getUpgrades()));
        return tooltip;
    }

    private void drawSlot(int x, int y, boolean borderTop, boolean borderBottom) {
        int srcX = 0;
        int srcY = TOP_PADDING;
        int srcWidth = LEFT_PADDING + SLOT_SIZE + RIGHT_PADDING;
        int srcHeight = SLOT_SIZE;

        x -= LEFT_PADDING;
        if (borderTop) {
            y -= TOP_PADDING;
            srcY = 0;
            srcHeight += TOP_PADDING;
        }
        if (borderBottom) {
            srcHeight += BOTTOM_PADDING;
        }

        this.background.copy().src(srcX, srcY, srcWidth, srcHeight).dest(x, y).blit();
    }

    @Override
    public void setPosition(Point position) {
        x = position.x() + OFFSET_X;
        y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
        if (height >= SLOT_SIZE) {
            this.maxVisibleSlots = Math.max(1, height / SLOT_SIZE);
        }
    }

    @Override
    public Rectangle getBounds() {
        int visibleSlots = getVisibleSlotCount();
        if (visibleSlots <= 0) {
            return new Rectangle(x, y, 0, 0);
        }
        int height = TOP_PADDING + BOTTOM_PADDING + visibleSlots * SLOT_SIZE;
        int width = LEFT_PADDING + RIGHT_PADDING + SLOT_SIZE;
        return new Rectangle(x, y, width, height);
    }

    @Override
    public boolean hitTest(Point mousePos) {
        return mousePos.isIn(getBounds()) && !isHoveringPanelSlot(mousePos.x(), mousePos.y());
    }

    @Override
    public void updateBeforeRender() {
        int slotOriginX = this.x + SLOT_X;
        int slotOriginY = this.y + SLOT_Y;
        int firstSlot = 0;
        int index = 0;

        for (Slot slot : getPanelSlots()) {
            if (index < firstSlot || index >= firstSlot + getVisibleSlotCount()) {
                index++;
                continue;
            }

            slot.xPos = slotOriginX;
            slot.yPos = slotOriginY;
            slotOriginY += SLOT_SIZE;
            index++;
        }
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        int visibleSlotCount = getVisibleSlotCount();
        if (visibleSlotCount <= 0) {
            return;
        }

        int slotOriginX = bounds.x + this.x + LEFT_PADDING;
        int slotOriginY = bounds.y + this.y + TOP_PADDING;

        for (int i = 0; i < visibleSlotCount; i++) {
            drawSlot(slotOriginX, slotOriginY + i * SLOT_SIZE, i == 0, i + 1 == visibleSlotCount);
        }
    }

    @Override
    public void addExclusionZones(List<Rectangle> exclusionZones, Rectangle screenBounds) {
        Rectangle bounds = getBounds();
        if (bounds.width > 0 && bounds.height > 0) {
            exclusionZones.add(Rects.expand(new Rectangle(
                screenBounds.x + bounds.x,
                screenBounds.y + bounds.y,
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
        if (tooltip.isEmpty()) {
            return null;
        }

        return new Tooltip(tooltip);
    }

    private int getUpgradeSlotCount() {
        return getPanelSlots().size();
    }

    private int getVisibleSlotCount() {
        return Math.min(this.maxVisibleSlots, getUpgradeSlotCount());
    }

    private boolean isHoveringEmptyUpgradeSlot(int mouseX, int mouseY) {
        for (Slot slot : getPanelSlots()) {
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
        for (Slot slot : getPanelSlots()) {
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

    private List<Slot> getPanelSlots() {
        var visibleSlots = new ObjectArrayList<Slot>(this.leadingSlots.size() + this.slots.size());
        if (!this.hideLeadingSlots.getAsBoolean()) {
            enableSlots(this.leadingSlots);
            for (Slot slot : this.leadingSlots) {
                if (isSlotEnabled(slot)) {
                    visibleSlots.add(slot);
                }
            }
        } else {
            disableSlots(this.leadingSlots);
        }
        for (Slot slot : this.slots) {
            if (isSlotEnabled(slot)) {
                visibleSlots.add(slot);
            }
        }
        return visibleSlots;
    }

}
