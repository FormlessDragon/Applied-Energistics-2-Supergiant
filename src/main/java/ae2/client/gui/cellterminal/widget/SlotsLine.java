/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package ae2.client.gui.cellterminal.widget;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.Icon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class SlotsLine extends AbstractLine {
    private static final int SIZE = CellTerminalLayout.MINI_SLOT_SIZE;
    protected final int slotsPerRow;
    protected final int slotsXOffset;
    protected final SlotMode mode;
    protected final FontRenderer fontRenderer;
    protected final Minecraft mc;
    protected final List<PartitionSlotTarget> partitionTargets = new ObjectArrayList<>();
    protected final int startIndex;
    protected Supplier<List<GenericStack>> itemsSupplier;
    protected Supplier<List<GenericStack>> partitionSupplier;
    protected int maxSlots = Integer.MAX_VALUE;
    protected int guiLeft;
    protected int guiTop;
    protected int hoveredSlotIndex = -1;
    protected GenericStack hoveredStack;
    protected SlotClickCallback slotClickCallback;
    protected BooleanSupplier selectedSupplier;
    protected boolean drawTopSeparator = false;
    protected boolean suppressTopSeparator = false;
    protected boolean cutTreeLineAboveAtJunction = false;

    public SlotsLine(int y, int slotsPerRow, int slotsXOffset, SlotMode mode, int startIndex, FontRenderer fontRenderer) {
        super(0, y, CellTerminalLayout.CONTENT_RIGHT_EDGE);
        this.slotsPerRow = slotsPerRow;
        this.slotsXOffset = slotsXOffset;
        this.mode = mode;
        this.startIndex = startIndex;
        this.fontRenderer = fontRenderer;
        this.mc = Minecraft.getMinecraft();
    }

    private static boolean isInPartition(GenericStack stack, List<GenericStack> partition) {
        AEKey key = stack.what();
        for (GenericStack p : partition) {
            if (p != null && key.equals(p.what())) {
                return true;
            }
        }
        return false;
    }

    public void setItemsSupplier(Supplier<List<GenericStack>> supplier) {
        this.itemsSupplier = supplier;
    }

    public void setPartitionSupplier(Supplier<List<GenericStack>> supplier) {
        this.partitionSupplier = supplier;
    }

    public void setSlotClickCallback(SlotClickCallback callback) {
        this.slotClickCallback = callback;
    }

    public void setSelectedSupplier(BooleanSupplier supplier) {
        this.selectedSupplier = supplier;
    }

    public void setDrawTopSeparator(boolean drawTopSeparator) {
        this.drawTopSeparator = drawTopSeparator;
    }

    public void setSuppressTopSeparator(boolean suppressTopSeparator) {
        this.suppressTopSeparator = suppressTopSeparator;
    }

    public void setCutTreeLineAboveAtJunction(boolean cutTreeLineAboveAtJunction) {
        this.cutTreeLineAboveAtJunction = cutTreeLineAboveAtJunction;
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    public void setGuiOffsets(int guiLeft, int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
    }

    public List<PartitionSlotTarget> getPartitionTargets() {
        return Collections.unmodifiableList(partitionTargets);
    }

    public int getHoveredSlotIndex() {
        return hoveredSlotIndex;
    }

    public boolean isMouseOverSlotGrid(int mouseX, int mouseY) {
        return mouseX >= slotsXOffset
            && mouseX < slotsXOffset + slotsPerRow * SIZE
            && mouseY >= y
            && mouseY < y + SIZE;
    }

    @Override
    protected void drawTreeLines(int mouseX, int mouseY) {
        if (!drawTreeLine) {
            return;
        }
        int branchY = y + 8;
        int verticalEndY = (treeButton != null) ? y + 5 : branchY;
        int verticalStartY = cutTreeLineAboveAtJunction ? Math.max(lineAboveCutY, verticalEndY) : lineAboveCutY;
        if (verticalStartY < verticalEndY) {
            Gui.drawRect(TREE_LINE_X, verticalStartY, TREE_LINE_X + 1, verticalEndY,
                CellTerminalLayout.COLOR_TREE_LINE);
        }
        Gui.drawRect(TREE_LINE_X, branchY, TREE_LINE_X + TREE_BRANCH_WIDTH, branchY + 1,
            CellTerminalLayout.COLOR_TREE_LINE);
        if (extendTreeLineToBottom) {
            int bottomStartY = getTreeLineCutY();
            int bottomEndY = y + CellTerminalLayout.ROW_HEIGHT;
            if (bottomStartY < bottomEndY) {
                Gui.drawRect(TREE_LINE_X, bottomStartY, TREE_LINE_X + 1, bottomEndY,
                    CellTerminalLayout.COLOR_TREE_LINE);
            }
        }
        if (treeButton != null) {
            int buttonX = TREE_LINE_X + treeButtonXOffset;
            int buttonY = y + 5;
            treeButton.setPosition(buttonX, buttonY);
            treeButton.draw(mouseX, mouseY);
        }
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        if (drawTopSeparator && !suppressTopSeparator) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y - 1, CellTerminalLayout.CONTENT_RIGHT_EDGE, y,
                CellTerminalLayout.COLOR_SEPARATOR);
        }
        boolean isSelected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        if (isSelected) {
            Gui.drawRect(CellTerminalLayout.GUI_INDENT, y, CellTerminalLayout.CONTENT_RIGHT_EDGE,
                y + CellTerminalLayout.ROW_HEIGHT, CellTerminalLayout.COLOR_SELECTION);
        }
        drawTreeLines(mouseX, mouseY);
        hoveredSlotIndex = -1;
        hoveredStack = null;
        partitionTargets.clear();
        if (mode == SlotMode.CONTENT) {
            drawContentSlots(mouseX, mouseY);
        } else {
            drawPartitionSlots(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (super.handleClick(mouseX, mouseY, button)) {
            return true;
        }
        if (!visible || hoveredSlotIndex < 0 || slotClickCallback == null || (button != 0 && button != 2)) {
            return false;
        }
        slotClickCallback.onSlotClicked(hoveredSlotIndex, button);
        return true;
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY) || hoveredStack == null) {
            return ItemStack.EMPTY;
        }
        return hoveredStack.what().wrapForDisplayOrFilter();
    }

    protected void drawContentSlots(int mouseX, int mouseY) {
        List<GenericStack> items = itemsSupplier != null ? itemsSupplier.get() : Collections.emptyList();
        List<GenericStack> partition = partitionSupplier != null ? partitionSupplier.get() : Collections.emptyList();
        for (int x = slotsXOffset; x < slotsXOffset + (slotsPerRow * SIZE); x += SIZE) {
            drawSlotBackground(x, y, false);
        }
        int slots = Math.min(startIndex + slotsPerRow, items.size()) - startIndex;
        for (int i = 0; i < slots; i++) {
            int absIndex = startIndex + i;
            int slotX = slotsXOffset + (i * SIZE);
            GenericStack stack = items.get(absIndex);
            if (stack == null) {
                continue;
            }
            renderStack(stack, slotX, y);
            if (isInPartition(stack, partition)) {
                drawPartitionIndicator(slotX, y);
            }
            drawItemCount(stack.amount(), slotX, y);
            if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
                drawSlotHoverHighlight(slotX, y);
                hoveredSlotIndex = absIndex;
                hoveredStack = stack;
            }
        }
    }

    protected void drawPartitionSlots(int mouseX, int mouseY) {
        List<GenericStack> partition = itemsSupplier != null ? itemsSupplier.get() : Collections.emptyList();
        for (int i = 0; i < slotsPerRow; i++) {
            int absIndex = startIndex + i;
            if (absIndex >= maxSlots) {
                break;
            }
            int slotBgX = slotsXOffset + (i * SIZE);
            drawSlotBackground(slotBgX, y, true);
        }
        for (int i = 0; i < slotsPerRow; i++) {
            int absIndex = startIndex + i;
            if (absIndex >= maxSlots) {
                break;
            }
            int slotX = slotsXOffset + (i * SIZE);
            partitionTargets.add(new PartitionSlotTarget(absIndex,
                new Rectangle(guiLeft + slotX, guiTop + y, SIZE, SIZE)));
            GenericStack partItem = absIndex < partition.size() ? partition.get(absIndex) : null;
            if (partItem != null) {
                renderStack(partItem, slotX, y);
            }
            if (mouseX >= slotX && mouseX < slotX + SIZE && mouseY >= y && mouseY < y + SIZE) {
                drawSlotHoverHighlight(slotX, y);
                hoveredSlotIndex = absIndex;
                if (partItem != null) {
                    hoveredStack = partItem;
                }
            }
        }
    }

    protected void drawSlotBackground(int slotX, int slotY, boolean partition) {
        Icon icon = partition ? Icon.CELL_TERMINAL_MINI_SLOT_PARTITION : Icon.CELL_TERMINAL_MINI_SLOT;
        icon.getBlitter().copy().dest(slotX, slotY, SIZE, SIZE).blit();
    }

    protected void drawSlotHoverHighlight(int slotX, int slotY) {
        Gui.drawRect(slotX + 1, slotY + 1, slotX + SIZE - 1, slotY + SIZE - 1, CellTerminalLayout.COLOR_HOVER_HIGHLIGHT);
    }

    protected void renderStack(GenericStack stack, int renderX, int renderY) {
        AEKeyRendering.drawInGui(this.mc, renderX, renderY, stack.what());
    }

    protected void drawItemCount(long count, int slotX, int slotY) {
        if (count <= 0) {
            return;
        }
        String countStr = formatCount(count);
        int countWidth = fontRenderer.getStringWidth(countStr);
        int textX = slotX + SIZE - 1;
        int textY = slotY + SIZE - 5;
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        fontRenderer.drawStringWithShadow(countStr, textX * 2 - countWidth, textY * 2, 0xFFFFFF);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    protected void drawPartitionIndicator(int slotX, int slotY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.75f, 0.75f, 0.75f);
        fontRenderer.drawStringWithShadow("P", Math.round((slotX + 1) / 0.75f), Math.round(slotY / 0.75f),
            CellTerminalLayout.COLOR_PARTITION_INDICATOR);
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
    }

    private String formatCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 1_000_000) {
            return (count / 1000) + "K";
        }
        if (count < 1_000_000_000) {
            return (count / 1_000_000) + "M";
        }
        return (count / 1_000_000_000) + "B";
    }

    public enum SlotMode {
        CONTENT,
        PARTITION
    }

    @FunctionalInterface
    public interface SlotClickCallback {
        void onSlotClicked(int slotIndex, int mouseButton);
    }

    public record PartitionSlotTarget(int absoluteIndex, Rectangle area) {
    }
}
