package appeng.client.ctl.gui.widget.impl.craftingtree;

import appeng.client.ctl.gui.util.MousePos;
import appeng.client.ctl.gui.util.RenderFunction;
import appeng.client.ctl.gui.util.RenderPos;
import appeng.client.ctl.gui.util.RenderSize;
import appeng.client.ctl.gui.widget.SizedColumn;
import appeng.client.ctl.gui.widget.base.DynamicWidget;
import appeng.client.ctl.gui.widget.base.WidgetGui;
import appeng.client.ctl.gui.widget.container.Row;
import appeng.client.ctl.gui.widget.event.GuiEvent;
import appeng.client.ctl.gui.widget.impl.craftingtree.event.CraftingTreeDataUpdateEvent;
import appeng.client.ctl.gui.widget.impl.craftingtree.event.TreeNodeSelectEvent;
import appeng.integration.data.LiteCraftTreeNode;
import appeng.integration.data.LiteCraftTreeProc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CraftingTree extends SizedColumn {

    public static final AtomicInteger DEBUG_RENDERED_NODES = new AtomicInteger(0);

    protected int offsetX = 0;
    protected int offsetY = 0;

    protected boolean mouseDown = false;
    protected int mouseClickX = 0;
    protected int mouseClickY = 0;
    protected int prevMouseX = 0;
    protected int prevMouseY = 0;

    protected float scale = 1.0F;

    protected int nodes = 0;
    protected LiteCraftTreeNode root = null;
    protected LiteCraftTreeNode missingOnlyRoot = null;

    protected TreeNode selected = null;
    protected boolean missingOnly = false;

    protected boolean darkMode = true;

    public CraftingTree() {
    }

    @Override
    protected void preRenderInternal(final WidgetGui gui, final RenderSize renderSize, final RenderPos renderPos, final MousePos mousePos) {
        int totalWidth = Math.round(getTotalWidth() * scale);
        int totalHeight = Math.round(getTotalHeight() * scale);

        if (mouseDown) {
            int mouseX = mousePos.mouseX();
            int mouseY = mousePos.mouseY();
            int offsetX = Math.round((mouseX - prevMouseX) / scale);
            int offsetY = Math.round((mouseY - prevMouseY) / scale);
            this.offsetX = Math.min(this.offsetX + offsetX, 0);
            this.offsetY = Math.min(this.offsetY + offsetY, 0);
            this.prevMouseX = mouseX;
            this.prevMouseY = mouseY;
        }

        if (totalWidth > width) {
            if (width + (offsetX * scale) > totalWidth) {
                this.offsetX = totalWidth - width;
            } else if (width + Math.abs(offsetX * scale) > totalWidth) {
                this.offsetX = Math.round(-(totalWidth - width) / scale);
            }
        } else {
            this.offsetX = 0;
        }
        if (totalHeight > height) {
            if (height + (offsetY * scale) > totalHeight) {
                this.offsetY = totalHeight - height;
            } else if (height + Math.abs(offsetY * scale) > totalHeight) {
                this.offsetY = Math.round(-(totalHeight - height) / scale);
            }
        } else {
            this.offsetY = 0;
        }

        super.preRenderInternal(gui, renderSize, renderPos, mousePos);
    }

    @Override
    protected void postRenderInternal(final WidgetGui gui, final RenderSize renderSize, final RenderPos renderPos, final MousePos mousePos) {
        super.postRenderInternal(gui, renderSize, renderPos, mousePos);
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (selected == null) {
            fr.drawStringWithShadow(
                    I18n.format("gui.crafting_tree.rendered_nodes", DEBUG_RENDERED_NODES.getAndSet(0), nodes),
                    renderPos.posX() + 2, renderPos.posY() + renderSize.height() - 18, 0x80FFFFFF
            );
            fr.drawStringWithShadow(
                    I18n.format("gui.crafting_tree.tip.0"),
                    renderPos.posX() + 2, renderPos.posY() + renderSize.height() - 9, 0x80FFFFFF
            );
        } else {
            fr.drawStringWithShadow(
                    I18n.format("gui.crafting_tree.rendered_nodes", DEBUG_RENDERED_NODES.getAndSet(0), nodes),
                    renderPos.posX() + 2, renderPos.posY() + renderSize.height() - 27, 0x80FFFFFF
            );
            fr.drawStringWithShadow(
                    I18n.format("gui.crafting_tree.tip.1"),
                    renderPos.posX() + 2, renderPos.posY() + renderSize.height() - 18, 0x80FFFFFF
            );
            fr.drawStringWithShadow(
                    I18n.format("gui.crafting_tree.tip.2"),
                    renderPos.posX() + 2, renderPos.posY() + renderSize.height() - 9, 0x80FFFFFF
            );
        }
    }

    @Override
    protected void doRender(final WidgetGui gui, final RenderSize renderSize, final RenderPos renderPos, final MousePos mousePos,
                            final RenderFunction renderFunction) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(renderPos.posX(), renderPos.posY(), 0);
        GlStateManager.scale(scale, scale, scale);

        int width = (int) (this.width / scale);
        final RenderPos newRenderPos = new RenderPos(0, 0);
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        final RenderSize newRenderSize = new RenderSize((int) (renderSize.width() / scale) + Math.abs(offsetX), (int) (renderSize.height() / scale));

        int y = this.offsetY;

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }

            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }

            if (widget.isVisible()) {
                int offsetY = widgetRenderPos.posY();
                if (offsetY + widget.getHeight() >= 0) {
                    RenderPos absRenderPos = widgetRenderPos.add(newRenderPos);
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(absRenderPos.posX(), absRenderPos.posY(), 0);

                    renderFunction.doRender(widget, gui, new RenderSize(widget.getWidth(), widget.getHeight()).smaller(newRenderSize), new RenderPos(offsetX, 0), newMousePos.relativeTo(widgetRenderPos));

                    GlStateManager.popMatrix();
                }
            }

            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
            if (newRenderSize.isHeightLimited() && y > newRenderSize.height()) {
                break;
            }
        }

        GlStateManager.popMatrix();
    }

    // Implementations

    @Override
    public boolean onGuiEvent(final GuiEvent event) {
        if (event instanceof CraftingTreeDataUpdateEvent) {
            root = ((CraftingTreeDataUpdateEvent) event).getRoot();
            root.sort();
            // Missing only
            if (LiteCraftTreeNode.isMissing(root)) {
                missingOnly = true;
                missingOnlyRoot = root.withMissingOnly();
            } else {
                missingOnlyRoot = null;
            }
            rebuildTree();
            return true;
        }
        if (event instanceof TreeNodeSelectEvent selectEvent) {
            selected = selectEvent.getSelectedNode();
        }
        return super.onGuiEvent(event);
    }

    public void rebuildTree() {
        selected = null;
        nodes = 0;
        getWidgets().clear();
        recursiveAddNode(missingOnly ? missingOnlyRoot : root, 0);
    }

    protected void recursiveAddNode(final LiteCraftTreeNode node, final int depth) {
        TreeNode nodeWidget = new TreeNode(this).setNode(node);
        if (depth == 0) {
            nodeWidget.setRoot(true).setMarginUp(TreeNode.ROOT_MARGIN_TOP).setMarginRight(6);
        }
        nodeWidget.setWidth(TreeNode.WIDTH);
        addNode(nodeWidget, depth);

        for (final LiteCraftTreeProc proc : node.inputs()) {
            for (final LiteCraftTreeNode input : proc.inputs()) {
                recursiveAddNode(input, depth + 1);
            }
        }

        int extraRenderNodes = node.getRenderExpandNodes();
        fillEmpty(depth, extraRenderNodes);

        if (extraRenderNodes == 0) {
            List<LiteCraftTreeProc> inputs = node.inputs();
            if (inputs.size() > 1) {
                extraRenderNodes += inputs.size() - 1;
            }
        }
        nodeWidget.setLinkedSubNodes(extraRenderNodes - node.getLastNodeRenderExpandNodes());
    }

    protected void fillEmpty(final int depth, final int fillNodes) {
        int nodeWidth = TreeNode.WIDTH + TreeNode.MARGIN_LEFT;
        Row row = (Row) widgets.get(depth);
        int totalWidth = row.getWidth() + (fillNodes * nodeWidth);
        for (int i = depth; i < widgets.size(); i++) {
            Row nextRow = (Row) widgets.get(i);
            int nextRowWidth = nextRow.getWidth();
            if (nextRowWidth >= totalWidth) {
                continue;
            }
            while (nextRowWidth < totalWidth) {
                nextRow.addWidget(new PlaceHolder().setWidth(TreeNode.WIDTH).setMarginLeft(TreeNode.MARGIN_LEFT));
                nextRowWidth += nodeWidth;
            }
        }
    }

    protected void addNode(final TreeNode node, final int depth) {
        while (widgets.size() <= depth) {
            widgets.add(new TreeRow());
        }
        TreeRow row = (TreeRow) widgets.get(depth);
        row.addWidget(node.setParentRow(row));
        nodes++;

        // Link prev and next nodes
        List<DynamicWidget> rowWidgets = row.getWidgets();
        if (rowWidgets.isEmpty()) {
            return;
        }

        // -2, Skip the last node
        for (int i = rowWidgets.size() - 2; i >= 0; i--) {
            DynamicWidget prev = rowWidgets.get(i);
            // Filter placeholders
            if (prev instanceof TreeNode prevNode) {
                prevNode.setNext(node);
                node.setPrev(prevNode);
                break;
            }
        }
    }

    public CraftingTree setMissingOnly(final boolean missingOnly) {
        if (this.root == null) {
            return this;
        }
        if (!LiteCraftTreeNode.isMissing(this.root)) {
            return this;
        }
        if (missingOnly == this.missingOnly) {
            return this;
        }
        if (this.missingOnlyRoot == null) {
            this.missingOnlyRoot = this.root.withMissingOnly();
            if (missingOnlyRoot == null) {
                throw new NullPointerException("Cannot get missing only node because `this.root.withMissingOnly()` returns null (" + this.root.toString() + ").");
            }
            this.missingOnlyRoot.sort();
        }
        this.missingOnly = missingOnly;
        rebuildTree();
        return this;
    }

    // Mouse events

    @Override
    public boolean onMouseClick(final MousePos mousePos, final RenderPos renderPos, final int mouseButton) {
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        int y = this.offsetY;
        int width = (int) (this.width / scale);

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }
            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }

            MousePos relativeMousePos = newMousePos.relativeTo(widgetRenderPos).relativeTo(new RenderPos(offsetX, 0));
            if (widget.isMouseOver(relativeMousePos)) {
                RenderPos absRenderPos = widgetRenderPos.add(renderPos).add(new RenderPos(offsetX, 0));
                if (widget.onMouseClick(relativeMousePos, absRenderPos, mouseButton)) {
                    return true;
                }
            }
            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }

        mouseDown = true;
        prevMouseX = mousePos.mouseX();
        prevMouseY = mousePos.mouseY();
        mouseClickX = mousePos.mouseX();
        mouseClickY = mousePos.mouseY();
        return true;
    }

    @Override
    public boolean onMouseReleased(final MousePos mousePos, final RenderPos renderPos) {
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        int y = this.offsetY;
        int width = (int) (this.width / scale);

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }
            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }
            RenderPos absRenderPos = widgetRenderPos.add(renderPos);
            if (widget.onMouseReleased(newMousePos.relativeTo(widgetRenderPos).add(new RenderPos(offsetX, 0)), absRenderPos.add(new RenderPos(offsetX, 0)))) {
                return true;
            }
            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }

        if (mouseDown) {
            if (Math.abs(mousePos.mouseX() - mouseClickX) <= 2 && Math.abs(mousePos.mouseY() - mouseClickY) <= 2) {
                onGuiEvent(new TreeNodeSelectEvent(null));
            }
        }
        mouseDown = false;
        return false;
    }

    @Override
    public boolean onMouseDWheel(final MousePos mousePos, final RenderPos renderPos, final int wheel) {
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        int y = this.offsetY;
        int width = (int) (this.width / scale);

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }
            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }
            RenderPos absRenderPos = widgetRenderPos.add(renderPos);
            if (widget.onMouseDWheel(newMousePos.relativeTo(widgetRenderPos).add(new RenderPos(offsetX, 0)), absRenderPos.add(new RenderPos(offsetX, 0)), wheel)) {
                return true;
            }
            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }

        if (isMouseOver(mousePos)) {
            this.scale = MathHelper.clamp(scale + (wheel < 0 ? -0.05f : 0.05f), 0.25F, 1.0F);
            return true;
        }
        return false;
    }

    @Override
    public void onMouseClickGlobal(final MousePos mousePos, final RenderPos renderPos, final int mouseButton) {
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        int y = this.offsetY;
        int width = (int) (this.width / scale);

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }
            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }
            RenderPos absRenderPos = widgetRenderPos.add(renderPos);
            widget.onMouseClickGlobal(newMousePos.relativeTo(widgetRenderPos).add(new RenderPos(offsetX, 0)), absRenderPos.add(new RenderPos(offsetX, 0)), mouseButton);
            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }
    }

    @Override
    public boolean onMouseClickMove(final MousePos mousePos, final RenderPos renderPos, final int mouseButton) {
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        int y = this.offsetY;
        int width = (int) (this.width / scale);

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }
            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }
            RenderPos absRenderPos = widgetRenderPos.add(renderPos);
            if (widget.onMouseClickMove(newMousePos.relativeTo(widgetRenderPos).add(new RenderPos(offsetX, 0)), absRenderPos.add(new RenderPos(offsetX, 0)), mouseButton)) {
                return true;
            }
            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }
        return false;
    }

    // Keyboard Events

    @Override
    public boolean onKeyTyped(final char typedChar, final int keyCode) {
        if (super.onKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (selected == null) {
            return false;
        }

        boolean ctrlPressed = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean selectChanged = false;
        if (keyCode == Keyboard.KEY_LEFT) {
            selectChanged = findLeftNode(selected, ctrlPressed);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            selectChanged = findRightNode(selected, ctrlPressed);
        } else if (keyCode == Keyboard.KEY_UP) {
            selectChanged = findUpNode();
        } else if (keyCode == Keyboard.KEY_DOWN) {
            selectChanged = findDownNode();
        }

        if (!selectChanged) {
            return false;
        }

        TreeRow treeRow = selected.getParentRow();
        RenderPos rowRenderPos = getRelativeRenderPos(treeRow);
        RenderPos renderPos = treeRow.getRelativeRenderPos(selected);
        if (rowRenderPos == null || renderPos == null) {
            return false;
        }

        RenderPos absRenderPos = renderPos.add(rowRenderPos);

        int newOffsetX = (-absRenderPos.posX()) + TreeNode.MARGIN_LEFT;
        int newOffsetY = (-absRenderPos.posY()) + TreeNode.ROOT_MARGIN_TOP;
        if (offsetX < newOffsetX || Math.abs(offsetX) + width < Math.abs(newOffsetX) + TreeNode.WIDTH) {
            offsetX = newOffsetX;
        }
        if (offsetY < newOffsetY || Math.abs(offsetY) + height < Math.abs(newOffsetY) + TreeNode.HEIGHT) {
            offsetY = newOffsetY;
        }

        return true;
    }

    protected static boolean findLeftNode(final TreeNode node, final boolean missingOnly) {
        if (node == null) {
            return false;
        }
        TreeNode prev = node.getPrev();
        while (prev != null) {
            if (!missingOnly || LiteCraftTreeNode.isMissing(prev.node)) {
                prev.select();
                return true;
            }
            prev = prev.getPrev();
        }
        return false;
    }

    protected static boolean findRightNode(final TreeNode node, final boolean missingOnly) {
        if (node == null) {
            return false;
        }
        TreeNode next = node.getNext();
        while (next != null) {
            if (!missingOnly || LiteCraftTreeNode.isMissing(next.node)) {
                next.select();
                return true;
            }
            next = next.getNext();
        }
        return false;
    }

    protected boolean findUpNode() {
        if (selected == null) {
            return false;
        }
        TreeRow treeRow = selected.getParentRow();
        int rowIdx = treeRow.getWidgets().indexOf(selected);
        int idx = widgets.indexOf(treeRow);
        if (idx > 0) {
            TreeRow prevRow = (TreeRow) widgets.get(idx - 1);
            List<DynamicWidget> prevRowWidgets = prevRow.getWidgets();
            while (rowIdx >= 0 && rowIdx < prevRowWidgets.size()) {
                DynamicWidget up = prevRowWidgets.get(rowIdx);
                if (up instanceof TreeNode upNode) {
                    upNode.select();
                    return true;
                }
                rowIdx--;
            }
        }
        return false;
    }

    protected boolean findDownNode() {
        if (selected == null) {
            return false;
        }
        TreeRow treeRow = selected.getParentRow();
        int rowIdx = treeRow.getWidgets().indexOf(selected);
        int idx = widgets.indexOf(treeRow);
        if (idx < widgets.size() - 1) {
            TreeRow nextRow = (TreeRow) widgets.get(idx + 1);
            List<DynamicWidget> nextRowWidgets = nextRow.getWidgets();
            while (rowIdx >= 0 && rowIdx < nextRowWidgets.size()) {
                DynamicWidget down = nextRowWidgets.get(rowIdx);
                if (down instanceof TreeNode downNode) {
                    downNode.select();
                    return true;
                }
                rowIdx--;
            }
        }
        return false;
    }

    public RenderPos getRelativeRenderPos(final DynamicWidget target) {
        if (!widgets.contains(target)) {
            return null;
        }
        int width = (int) (this.width / scale);
        int y = this.offsetY;

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }

            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }

            if (widget.isVisible()) {
                int offsetY = widgetRenderPos.posY();
                if (offsetY + widget.getHeight() >= 0) {
                    if (widget == target) {
                        return widgetRenderPos;
                    }
                }
            }

            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }

        return null;
    }

    // Dark Mode

    public boolean isDarkMode() {
        return darkMode;
    }

    public CraftingTree setDarkMode(final boolean darkMode) {
        this.darkMode = darkMode;
        return this;
    }

    // Tooltips

    @Override
    public List<String> getHoverTooltips(final WidgetGui widgetGui, final MousePos mousePos) {
        final MousePos newMousePos = new MousePos((int) (mousePos.mouseX() / scale), (int) (mousePos.mouseY() / scale));
        int y = this.offsetY;
        int width = (int) (this.width / scale);

        List<String> tooltips = null;

        for (final DynamicWidget widget : widgets) {
            if (widget.isDisabled()) {
                continue;
            }

            RenderPos widgetRenderPos = getWidgetRenderOffset(widget, width, y);
            if (widgetRenderPos == null) {
                continue;
            }

            MousePos relativeMousePos = newMousePos.relativeTo(widgetRenderPos.add(new RenderPos(offsetX, 0)));
            if (widget.isMouseOver(relativeMousePos)) {
                List<String> hoverTooltips = widget.getHoverTooltips(widgetGui, relativeMousePos);
                if (!hoverTooltips.isEmpty()) {
                    tooltips = hoverTooltips;
                    break;
                }
            }

            y += widget.getMarginUp() + widget.getHeight() + widget.getMarginDown();
        }

        return tooltips != null ? tooltips : Collections.emptyList();
    }

}