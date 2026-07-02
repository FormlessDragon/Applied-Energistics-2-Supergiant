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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

public class CellTerminalRowList {
    private final List<IWidget> visibleRows = new ObjectArrayList<>();
    private final Map<IWidget, Object> widgetDataMap = new Object2ObjectOpenHashMap<>();
    private final int rowHeight;
    private final ToIntFunction<Object> rowHeightFunction;
    private final RowFactory rowFactory;
    private final ContentLinePredicate contentPredicate;
    private int rowsVisible;

    public CellTerminalRowList(int rowsVisible, int rowHeight, RowFactory rowFactory,
                               ContentLinePredicate contentPredicate) {
        this(rowsVisible, rowHeight, ignored -> rowHeight, rowFactory, contentPredicate);
    }

    public CellTerminalRowList(int rowsVisible, int rowHeight, ToIntFunction<Object> rowHeightFunction,
                               RowFactory rowFactory, ContentLinePredicate contentPredicate) {
        this.rowsVisible = rowsVisible;
        this.rowHeight = rowHeight;
        this.rowHeightFunction = rowHeightFunction;
        this.rowFactory = rowFactory;
        this.contentPredicate = contentPredicate;
    }

    private static boolean isSourceBranchAbove(TreeLineInfo previousInfo, TreeLineInfo info) {
        BranchKind sourceBranchKindAbove = info.sourceBranchKindAbove();
        return sourceBranchKindAbove != null
            && previousInfo.branchKind() == sourceBranchKindAbove
            && previousInfo.sectionKey().equals(info.sectionKey());
    }

    private static boolean sameBranch(TreeLineInfo a, TreeLineInfo b) {
        return a.branchKind() == b.branchKind() && a.sectionKey().equals(b.sectionKey());
    }

    private static BranchKey branchKey(TreeLineInfo info) {
        return new BranchKey(info.sectionKey(), info.branchKind());
    }

    public void setRowsVisible(int rowsVisible) {
        this.rowsVisible = rowsVisible;
    }

    public List<IWidget> getVisibleRows() {
        return visibleRows;
    }

    public void buildVisibleRows(List<?> lines, int scrollOffset) {
        visibleRows.clear();
        widgetDataMap.clear();
        int bottomY = CellTerminalLayout.CONTENT_START_Y + rowsVisible * rowHeight;
        int scrollPixels = scrollOffset * rowHeight;
        int accumulatedHeight = 0;
        int rowY = CellTerminalLayout.CONTENT_START_Y - scrollPixels;
        int end = lines.size();
        int firstVisibleIndex = 0;
        for (int i = 0; i < end; i++) {
            Object lineData = lines.get(i);
            int actualHeight = Math.max(1, rowHeightFunction.applyAsInt(lineData));
            if (accumulatedHeight + actualHeight <= scrollPixels) {
                accumulatedHeight += actualHeight;
                rowY += actualHeight;
                firstVisibleIndex = i + 1;
                continue;
            }
            if (rowY >= bottomY) {
                break;
            }
            IWidget widget = rowFactory.createRow(lineData, rowY, lines, i);
            if (widget != null) {
                visibleRows.add(widget);
                widgetDataMap.put(widget, lineData);
            }
            rowY += actualHeight;
        }
        propagateTreeLines(lines, firstVisibleIndex);
    }

    private void propagateTreeLines(List<?> allLines, int firstVisibleIndex) {
        Map<BranchKey, Integer> branchCutYs = new Object2ObjectOpenHashMap<>();
        Map<BranchKey, Integer> previousRowCutYs = new Object2ObjectOpenHashMap<>();
        for (int i = 0; i < visibleRows.size(); i++) {
            IWidget widget = visibleRows.get(i);
            int currentIndex = firstVisibleIndex + i;
            if (widget instanceof AbstractHeader header) {
                previousRowCutYs.clear();
                header.setSuppressTopSeparator(i == 0);
                TreeLineInfo connectorInfo = contentPredicate.headerConnectorInfo(allLines, currentIndex);
                header.setDrawConnector(connectorInfo != null);
                if (connectorInfo != null) {
                    branchCutYs.put(branchKey(connectorInfo), header.getConnectorY());
                }
            } else if (widget instanceof AbstractLine line) {
                if (line instanceof SlotsLine slotsLine) {
                    slotsLine.setSuppressTopSeparator(i == 0);
                    slotsLine.setCutTreeLineAboveAtJunction(false);
                }
                TreeLineInfo info = contentPredicate.treeLineInfo(allLines, currentIndex);
                if (info == null) {
                    line.setTreeLineParams(false, line.getTreeLineCutY());
                    line.setExtendTreeLineToBottom(false);
                    previousRowCutYs.clear();
                    continue;
                }
                if (line instanceof ContinuationLine continuationLine) {
                    continuationLine.setDrawHorizontalBranch(info.drawsHorizontalJunction());
                }
                if (line instanceof SlotsLine slotsLine) {
                    slotsLine.setCutTreeLineAboveAtJunction(info.cutsTreeLineAboveAtJunction());
                }

                BranchKey branchKey = branchKey(info);
                boolean isLastVisibleRow = i == visibleRows.size() - 1;
                boolean drawTreeLine = info.drawsHorizontalJunction() || info.carriesTrunk();
                boolean extendToBottom = shouldExtendToBottom(allLines, currentIndex, info, isLastVisibleRow);
                int sourceY = treeLineSourceY(allLines, currentIndex, info, line, branchCutYs, previousRowCutYs);
                line.setTreeLineParams(drawTreeLine, sourceY);
                line.setExtendTreeLineToBottom(extendToBottom);
                if (info.continuesTrunkBelow()) {
                    branchCutYs.put(branchKey, line.getTreeLineCutY());
                } else {
                    branchCutYs.remove(branchKey);
                }
                previousRowCutYs.clear();
                previousRowCutYs.put(branchKey, line.getTreeLineCutY());
            } else {
                previousRowCutYs.clear();
            }
        }
    }

    private boolean shouldExtendToBottom(List<?> allLines, int currentIndex, TreeLineInfo info,
                                         boolean isLastVisibleRow) {
        if (!isLastVisibleRow || currentIndex + 1 >= allLines.size()) {
            return false;
        }
        TreeLineInfo nextInfo = contentPredicate.treeLineInfo(allLines, currentIndex + 1);
        return nextInfo != null && sameBranch(info, nextInfo) && nextInfo.carriesTrunk();
    }

    private int treeLineSourceY(List<?> allLines, int currentIndex, TreeLineInfo info, AbstractLine line,
                                Map<BranchKey, Integer> branchCutYs,
                                Map<BranchKey, Integer> previousRowCutYs) {
        Integer cutY = branchCutYs.get(branchKey(info));
        if (cutY != null) {
            return cutY;
        }
        Integer sourceCutY = previousSourceBranchCutY(info, previousRowCutYs);
        if (sourceCutY != null) {
            return sourceCutY;
        }
        if (hasBranchSourceAbove(allLines, currentIndex, info)) {
            return CellTerminalLayout.CONTENT_START_Y;
        }
        return line.getTreeLineCutY();
    }

    private Integer previousSourceBranchCutY(TreeLineInfo info, Map<BranchKey, Integer> previousRowCutYs) {
        BranchKind sourceBranchKindAbove = info.sourceBranchKindAbove();
        if (sourceBranchKindAbove == null) {
            return null;
        }
        return previousRowCutYs.get(new BranchKey(info.sectionKey(), sourceBranchKindAbove));
    }

    private boolean hasBranchSourceAbove(List<?> allLines, int currentIndex, TreeLineInfo info) {
        if (currentIndex <= 0) {
            return false;
        }
        TreeLineInfo previousInfo = contentPredicate.treeLineInfo(allLines, currentIndex - 1);
        if (previousInfo != null) {
            return (sameBranch(previousInfo, info) && previousInfo.continuesTrunkBelow())
                || isSourceBranchAbove(previousInfo, info);
        }
        TreeLineInfo connectorInfo = contentPredicate.headerConnectorInfo(allLines, currentIndex - 1);
        return connectorInfo != null && sameBranch(connectorInfo, info);
    }

    public void draw(int mouseX, int mouseY) {
        for (IWidget widget : visibleRows) {
            widget.draw(mouseX, mouseY);
        }
    }

    public boolean handleClick(int mouseX, int mouseY, int button) {
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (!widget.isHovered(mouseX, mouseY)) {
                continue;
            }
            if (widget.handleClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getTooltip(int mouseX, int mouseY) {
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (!widget.isHovered(mouseX, mouseY)) {
                continue;
            }
            List<String> tooltip = widget.getTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) {
                return tooltip;
            }
        }
        return Collections.emptyList();
    }

    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        for (int i = visibleRows.size() - 1; i >= 0; i--) {
            IWidget widget = visibleRows.get(i);
            if (!widget.isHovered(mouseX, mouseY)) {
                continue;
            }
            ItemStack stack = widget.getHoveredItemStack(mouseX, mouseY);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public Map<IWidget, Object> getWidgetDataMap() {
        return Collections.unmodifiableMap(widgetDataMap);
    }

    public enum BranchKind {
        CONTENT,
        PARTITION,
        SINGLE
    }

    public interface RowFactory {
        IWidget createRow(Object lineData, int y, List<?> allLines, int lineIndex);
    }

    public interface ContentLinePredicate {
        boolean isContentLine(List<?> allLines, int index);

        default boolean drawsHorizontalJunction(List<?> allLines, int index) {
            return isContentLine(allLines, index);
        }

        default TreeLineInfo treeLineInfo(List<?> allLines, int index) {
            if (!isContentLine(allLines, index)) {
                return null;
            }
            return new TreeLineInfo(
                "default",
                BranchKind.SINGLE,
                drawsHorizontalJunction(allLines, index),
                true,
                true,
                false);
        }

        default TreeLineInfo headerConnectorInfo(List<?> allLines, int index) {
            if (index + 1 >= allLines.size()) {
                return null;
            }
            return treeLineInfo(allLines, index + 1);
        }
    }

    public record TreeLineInfo(String sectionKey, BranchKind branchKind, boolean drawsHorizontalJunction,
                               boolean carriesTrunk, boolean continuesTrunkBelow,
                               boolean cutsTreeLineAboveAtJunction, BranchKind sourceBranchKindAbove) {
        public TreeLineInfo(String sectionKey, BranchKind branchKind, boolean drawsHorizontalJunction,
                            boolean carriesTrunk, boolean continuesTrunkBelow,
                            boolean cutsTreeLineAboveAtJunction) {
            this(sectionKey, branchKind, drawsHorizontalJunction, carriesTrunk, continuesTrunkBelow,
                cutsTreeLineAboveAtJunction, null);
        }

        public TreeLineInfo {
            Objects.requireNonNull(sectionKey, "sectionKey");
            Objects.requireNonNull(branchKind, "branchKind");
        }
    }

    private record BranchKey(String sectionKey, BranchKind branchKind) {
    }
}
