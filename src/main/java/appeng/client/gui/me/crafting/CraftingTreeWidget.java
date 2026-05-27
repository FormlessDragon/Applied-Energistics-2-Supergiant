package appeng.client.gui.me.crafting;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.core.AppEng;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.integration.data.LiteCraftTreeNode;
import appeng.integration.data.LiteCraftTreeProc;
import appeng.integration.modules.hei.CraftingTreeUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.config.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

public class CraftingTreeWidget implements ICompositeWidget {
    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/ctl/guicraftingtree.png");
    private static final int SCREENSHOT_TILE_SIZE = 2048;

    private static final int LINE_WIDTH = 1;
    private static final int LINE_HEIGHT = 2;
    private static final int LINE_TOTAL_HEIGHT = 6;
    private static final int NODE_WIDTH = 20;
    private static final int NODE_HEIGHT = 20;
    private static final int NODE_TOTAL_HEIGHT = NODE_HEIGHT + LINE_TOTAL_HEIGHT;
    private static final int ROOT_MARGIN_TOP = 4;
    private static final int NODE_MARGIN_LEFT = 6;
    private static final int LINE_RENDER_OFFSET = (NODE_WIDTH - (LINE_WIDTH * 2)) / 2;
    private static final int ITEM_RENDER_OFFSET = 2;

    private static final int LINE_COLOR = 0xFFF2F2F2;
    private static final int LINE_SHADOW_COLOR = 0xFF4D4D67;
    private static final int MISSING_LINE_COLOR = 0xFFEE6363;
    private static final int MISSING_LINE_SHADOW_COLOR = 0xFF8B3A3A;

    private final List<TreeRow> rows = new ObjectArrayList<>();

    private final Rectangle bounds = new Rectangle();
    private int offsetX;
    private int offsetY;
    private boolean mouseDown;
    private int mouseClickX;
    private int mouseClickY;
    private int prevMouseX;
    private int prevMouseY;
    private float scale = 1.0F;
    private int nodes;
    private LiteCraftTreeNode root;
    private LiteCraftTreeNode missingOnlyRoot;
    private TreeNode selected;
    private TreeNode hovered;
    private boolean missingOnly;
    private int renderedNodes;

    private static boolean hasSameOutput(@Nullable GenericStack left, GenericStack right) {
        return left != null && left.what().equals(right.what()) && left.amount() == right.amount();
    }

    private static boolean findLeftNode(TreeNode node, boolean missingOnly) {
        TreeNode previous = node.previous;
        while (previous != null) {
            if (!missingOnly || LiteCraftTreeNode.isMissing(previous.node)) {
                previous.select();
                return true;
            }
            previous = previous.previous;
        }
        return false;
    }

    private static boolean findRightNode(TreeNode node, boolean missingOnly) {
        TreeNode next = node.next;
        while (next != null) {
            if (!missingOnly || LiteCraftTreeNode.isMissing(next.node)) {
                next.select();
                return true;
            }
            next = next.next;
        }
        return false;
    }

    private static void drawTexturedRect(int x, int y, int textureX, int textureY) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(x, y, textureX, textureY, NODE_WIDTH, NODE_HEIGHT, 256, 256);
    }

    private static void renderLine(Point pos, int width, int height, int color) {
        GuiContainer.drawRect(pos.x(), pos.y(), pos.x() + width, pos.y() + height, color);
    }

    private static BufferedImage readFramebuffer(ScreenshotFramebuffer framebuffer, int width, int height) {
        ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
        int[] pixels = new int[width * height];

        framebuffer.bind(false);
        GlStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GlStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        pixelBuffer.clear();

        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);

        for (int y = 0; y < height; y++) {
            int sourceRow = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int sourceIndex = (sourceRow * width + x) * 4;
                int red = pixelBuffer.get(sourceIndex) & 0xFF;
                int green = pixelBuffer.get(sourceIndex + 1) & 0xFF;
                int blue = pixelBuffer.get(sourceIndex + 2) & 0xFF;
                int alpha = pixelBuffer.get(sourceIndex + 3) & 0xFF;
                pixels[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static int getScreenshotTileSize() {
        int maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize <= 0) {
            return SCREENSHOT_TILE_SIZE;
        }
        return MathHelper.clamp(maxTextureSize, 256, SCREENSHOT_TILE_SIZE);
    }

    private static void restoreMainFramebuffer(Minecraft minecraft) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            minecraft.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        }
        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
    }

    @Override
    public void setPosition(Point position) {
        this.bounds.x = position.x();
        this.bounds.y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
        this.bounds.width = width;
        this.bounds.height = height;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    public void setRoot(LiteCraftTreeNode root) {
        if (root == null) {
            clearTree();
            return;
        }

        this.root = root;
        this.root.sort();
        if (LiteCraftTreeNode.isMissing(root)) {
            this.missingOnly = true;
            this.missingOnlyRoot = root.withMissingOnly();
        } else {
            this.missingOnly = false;
            this.missingOnlyRoot = null;
        }
        rebuildTree();
    }

    public boolean isMissingOnly() {
        return missingOnly;
    }

    public void setMissingOnly(boolean missingOnly) {
        if (!hasMissingItems() || missingOnly == this.missingOnly) {
            return;
        }

        if (this.missingOnlyRoot == null) {
            this.missingOnlyRoot = this.root.withMissingOnly();
            if (this.missingOnlyRoot == null) {
                throw new IllegalStateException("Missing-only tree is unavailable for " + this.root);
            }
            this.missingOnlyRoot.sort();
        }

        this.missingOnly = missingOnly;
        rebuildTree();
    }

    public boolean hasMissingItems() {
        return root != null && LiteCraftTreeNode.isMissing(root);
    }

    public boolean hasTree() {
        return getCurrentRoot() != null && !rows.isEmpty();
    }

    @Override
    public void updateBeforeRender() {
        clampOffsets();
    }

    @Override
    public void drawBackgroundLayer(Rectangle screenBounds, Point mouse) {
        renderedNodes = 0;
        GlStateManager.pushMatrix();
        GlStateManager.translate(screenBounds.x + bounds.x, screenBounds.y + bounds.y, 0);
        GlStateManager.scale(scale, scale, scale);

        int scaledWidth = (int) (bounds.width / scale);
        int scaledHeight = (int) (bounds.height / scale);
        Point scaledMouse = new Point((int) ((mouse.x() - bounds.x) / scale), (int) ((mouse.y() - bounds.y) / scale));

        hovered = null;
        withScissor(screenBounds, () -> {
            int y = offsetY;
            for (TreeRow row : rows) {
                if (y + row.getHeight() >= 0) {
                    row.render(new Point(offsetX, y), scaledWidth, scaledMouse);
                }
                y += row.getHeight();
                if (y > scaledHeight) {
                    break;
                }
            }
        });

        GlStateManager.popMatrix();
    }

    @Override
    public void drawForegroundLayer(Rectangle screenBounds, Point mouse) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int x = bounds.x + 2;
        if (selected == null) {
            font.drawStringWithShadow(GuiText.CraftingTreeRenderedNodes.getLocal(renderedNodes, nodes),
                x, bounds.y + bounds.height - 18, 0x80FFFFFF);
            font.drawStringWithShadow(GuiText.CraftingTreeTip0.getLocal(),
                x, bounds.y + bounds.height - 9, 0x80FFFFFF);
        } else {
            font.drawStringWithShadow(GuiText.CraftingTreeRenderedNodes.getLocal(renderedNodes, nodes),
                x, bounds.y + bounds.height - 27, 0x80FFFFFF);
            font.drawStringWithShadow(GuiText.CraftingTreeTip1.getLocal(),
                x, bounds.y + bounds.height - 18, 0x80FFFFFF);
            font.drawStringWithShadow(GuiText.CraftingTreeTip2.getLocal(),
                x, bounds.y + bounds.height - 9, 0x80FFFFFF);
        }
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        Point relativeMouse = toScaledTreeMouse(mousePos);
        TreeNode node = findNodeAt(relativeMouse);
        if (node != null) {
            node.select();
            return true;
        }

        mouseDown = true;
        prevMouseX = mousePos.x();
        prevMouseY = mousePos.y();
        mouseClickX = mousePos.x();
        mouseClickY = mousePos.y();
        return true;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return mouseDown;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (mouseDown && Math.abs(mousePos.x() - mouseClickX) <= 2 && Math.abs(mousePos.y() - mouseClickY) <= 2) {
            select(null);
        }
        mouseDown = false;
        return false;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (mouseDown) {
            int dx = Math.round((mousePos.x() - prevMouseX) / scale);
            int dy = Math.round((mousePos.y() - prevMouseY) / scale);
            this.offsetX = Math.min(this.offsetX + dx, 0);
            this.offsetY = Math.min(this.offsetY + dy, 0);
            clampOffsets();
            this.prevMouseX = mousePos.x();
            this.prevMouseY = mousePos.y();
        }
        return mouseDown;
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        float oldScale = scale;
        float newScale = MathHelper.clamp(scale + (delta < 0 ? -0.05F : 0.05F), 0.25F, 1.0F);
        if (newScale == oldScale) {
            return true;
        }

        int mouseTreeX = Math.round((mousePos.x() - bounds.x) / oldScale) - offsetX;
        int mouseTreeY = Math.round((mousePos.y() - bounds.y) / oldScale) - offsetY;
        this.scale = newScale;
        this.offsetX = Math.round((mousePos.x() - bounds.x) / newScale) - mouseTreeX;
        this.offsetY = Math.round((mousePos.y() - bounds.y) / newScale) - mouseTreeY;
        clampOffsets();
        return true;
    }

    @Override
    public boolean onKeyTyped(char typedChar, int keyCode) {
        if (hovered != null) {
            int showRecipeKeyCode = KeyBindings.showRecipe.getKeyCode();
            int showUsesKeyCode = KeyBindings.showUses.getKeyCode();
            int bookmarkKeyCode = KeyBindings.bookmark.getKeyCode();

            if (showRecipeKeyCode > 0 && showRecipeKeyCode <= 255 && showRecipeKeyCode == keyCode) {
                return CraftingTreeUtils.showStackFocus(hovered.node.output(), IFocus.Mode.OUTPUT);
            }
            if (showUsesKeyCode > 0 && showUsesKeyCode <= 255 && showUsesKeyCode == keyCode) {
                return CraftingTreeUtils.showStackFocus(hovered.node.output(), IFocus.Mode.INPUT);
            }
            if (bookmarkKeyCode > 0 && bookmarkKeyCode <= 255 && bookmarkKeyCode == keyCode) {
                return CraftingTreeUtils.addIngredientToBookmarkList(hovered.node.output());
            }
        }

        if (selected == null) {
            return false;
        }

        boolean ctrlPressed = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean selectChanged = switch (keyCode) {
            case Keyboard.KEY_LEFT -> findLeftNode(selected, ctrlPressed);
            case Keyboard.KEY_RIGHT -> findRightNode(selected, ctrlPressed);
            case Keyboard.KEY_UP -> findVerticalNode(-1);
            case Keyboard.KEY_DOWN -> findVerticalNode(1);
            default -> false;
        };

        if (!selectChanged) {
            return false;
        }

        scrollSelectedIntoView();
        return true;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        Point relativeMouse = toScaledTreeMouse(new Point(mouseX, mouseY));
        TreeNode node = findNodeAt(relativeMouse);
        return node == null ? null : node.getTooltip();
    }

    public BufferedImage createNodeAreaScreenshot() {
        if (!hasTree()) {
            throw new IllegalStateException("No crafting tree data available");
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        int renderScale = new ScaledResolution(minecraft).getScaleFactor();
        int width = Math.max(1, getTotalWidth() + NODE_MARGIN_LEFT);
        int height = Math.max(1, getTotalHeight());
        BufferedImage image = new BufferedImage(width * renderScale, height * renderScale, BufferedImage.TYPE_INT_ARGB);
        int tileSize = Math.max(1, getScreenshotTileSize() / renderScale);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int tileY = 0; tileY < height; tileY += tileSize) {
                int tileHeight = Math.min(tileSize, height - tileY);
                for (int tileX = 0; tileX < width; tileX += tileSize) {
                    int tileWidth = Math.min(tileSize, width - tileX);
                    int framebufferWidth = tileWidth * renderScale;
                    int framebufferHeight = tileHeight * renderScale;
                    ScreenshotFramebuffer framebuffer = new ScreenshotFramebuffer(framebufferWidth, framebufferHeight);
                    try {
                        renderScreenshotTile(framebuffer, tileX, tileY, tileWidth, tileHeight, renderScale);
                        BufferedImage tile = readFramebuffer(framebuffer, framebufferWidth, framebufferHeight);
                        graphics.drawImage(tile, tileX * renderScale, tileY * renderScale, null);
                    } finally {
                        framebuffer.delete();
                        restoreMainFramebuffer(minecraft);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }

        return image;
    }

    private void rebuildTree() {
        GenericStack selectedOutput = selected == null ? null : selected.node.output();
        selected = null;
        nodes = 0;
        rows.clear();

        LiteCraftTreeNode treeRoot = getCurrentRoot();
        if (treeRoot != null) {
            addNodeRecursive(treeRoot, 0);
        }

        TreeNode restoredSelection = findNodeByOutput(selectedOutput);
        if (restoredSelection != null) {
            restoredSelection.select();
        }
        clampOffsets();
    }

    private void clearTree() {
        root = null;
        missingOnlyRoot = null;
        selected = null;
        hovered = null;
        missingOnly = false;
        nodes = 0;
        renderedNodes = 0;
        offsetX = 0;
        offsetY = 0;
        scale = 1.0F;
        rows.clear();
    }

    @Nullable
    private LiteCraftTreeNode getCurrentRoot() {
        return missingOnly ? missingOnlyRoot : root;
    }

    private TreeNode addNodeRecursive(LiteCraftTreeNode node, int depth) {
        TreeNode nodeWidget = new TreeNode(node);
        if (depth == 0) {
            nodeWidget.root = true;
            nodeWidget.marginTop = ROOT_MARGIN_TOP;
            nodeWidget.marginRight = 6;
        }

        addNode(nodeWidget, depth);

        List<TreeNode> directChildren = new ObjectArrayList<>();
        for (LiteCraftTreeProc proc : node.inputs()) {
            for (LiteCraftTreeNode input : proc.inputs()) {
                directChildren.add(addNodeRecursive(input, depth + 1));
            }
        }

        fillEmpty(depth, node.getRenderExpandNodes());
        nodeWidget.childLinkWidth = getChildLinkWidth(nodeWidget, directChildren);
        return nodeWidget;
    }

    private int getChildLinkWidth(TreeNode parent, List<TreeNode> directChildren) {
        if (directChildren.size() <= 1) {
            return 0;
        }

        Point parentPos = parent.row.getRelativePosition(parent);
        TreeNode lastChild = directChildren.getLast();
        Point lastChildPos = lastChild.row.getRelativePosition(lastChild);
        if (parentPos == null || lastChildPos == null) {
            return 0;
        }

        return Math.max(0, lastChildPos.x() - parentPos.x()) + LINE_WIDTH;
    }

    private void fillEmpty(int depth, int fillNodes) {
        int nodeWidth = NODE_WIDTH + NODE_MARGIN_LEFT;
        TreeRow row = rows.get(depth);
        int totalWidth = row.getWidth() + (fillNodes * nodeWidth);
        for (int i = depth; i < rows.size(); i++) {
            TreeRow nextRow = rows.get(i);
            int nextRowWidth = nextRow.getWidth();
            while (nextRowWidth < totalWidth) {
                nextRow.widgets.add(new Placeholder());
                nextRowWidth += nodeWidth;
            }
        }
    }

    private void addNode(TreeNode node, int depth) {
        while (rows.size() <= depth) {
            rows.add(new TreeRow());
        }

        TreeRow row = rows.get(depth);
        node.row = row;
        row.widgets.add(node);
        nodes++;

        for (int i = row.widgets.size() - 2; i >= 0; i--) {
            RowElement previous = row.widgets.get(i);
            if (previous instanceof TreeNode previousNode) {
                previousNode.next = node;
                node.previous = previousNode;
                break;
            }
        }
    }

    @Nullable
    private TreeNode findNodeAt(Point treeMouse) {
        int y = offsetY;
        for (TreeRow row : rows) {
            int x = offsetX;
            for (RowElement element : row.widgets) {
                if (element instanceof TreeNode node) {
                    node.currentPosition = new Point(x + NODE_MARGIN_LEFT, y + node.marginTop);
                    if (node.isMouseOver(treeMouse)) {
                        return node;
                    }
                }
                x += element.getFullWidth();
            }
            y += row.getHeight();
        }
        return null;
    }

    @Nullable
    private TreeNode findNodeByOutput(@Nullable GenericStack output) {
        if (output == null) {
            return null;
        }

        for (TreeRow row : rows) {
            for (RowElement element : row.widgets) {
                if (element instanceof TreeNode node && hasSameOutput(node.node.output(), output)) {
                    return node;
                }
            }
        }
        return null;
    }

    private Point toScaledTreeMouse(Point mouse) {
        return new Point((int) ((mouse.x() - bounds.x) / scale), (int) ((mouse.y() - bounds.y) / scale));
    }

    private void clampOffsets() {
        int visibleWidth = Math.max(1, (int) (bounds.width / scale));
        int visibleHeight = Math.max(1, (int) (bounds.height / scale));
        int minOffsetX = Math.min(0, visibleWidth - getTotalWidth());
        int minOffsetY = Math.min(0, visibleHeight - getTotalHeight());
        this.offsetX = MathHelper.clamp(offsetX, minOffsetX, 0);
        this.offsetY = MathHelper.clamp(offsetY, minOffsetY, 0);
    }

    private int getTotalWidth() {
        int width = 0;
        for (TreeRow row : rows) {
            width = Math.max(width, row.getWidth());
        }
        return width;
    }

    private int getTotalHeight() {
        int height = 0;
        for (TreeRow row : rows) {
            height += row.getHeight();
        }
        return height;
    }

    private void select(@Nullable TreeNode node) {
        if (selected != null) {
            selected.selected = false;
        }
        selected = node;
        if (selected != null) {
            selected.selected = true;
        }
    }

    private boolean findVerticalNode(int rowOffset) {
        int rowIndex = rows.indexOf(selected.row);
        int targetRowIndex = rowIndex + rowOffset;
        if (targetRowIndex < 0 || targetRowIndex >= rows.size()) {
            return false;
        }

        TreeRow targetRow = rows.get(targetRowIndex);
        int columnIndex = selected.row.widgets.indexOf(selected);
        while (columnIndex >= 0 && columnIndex < targetRow.widgets.size()) {
            RowElement element = targetRow.widgets.get(columnIndex);
            if (element instanceof TreeNode node) {
                node.select();
                return true;
            }
            columnIndex--;
        }
        return false;
    }

    private void scrollSelectedIntoView() {
        Point rowPos = new Point(0, offsetY);
        for (TreeRow row : rows) {
            if (row == selected.row) {
                Point nodePos = selected.row.getRelativePosition(selected);
                if (nodePos == null) {
                    return;
                }
                Point absPos = nodePos.move(rowPos.x(), rowPos.y());
                int newOffsetX = -absPos.x() + NODE_MARGIN_LEFT;
                int newOffsetY = -absPos.y() + ROOT_MARGIN_TOP;
                if (offsetX < newOffsetX || Math.abs(offsetX) + bounds.width < Math.abs(newOffsetX) + NODE_WIDTH) {
                    offsetX = newOffsetX;
                }
                if (offsetY < newOffsetY || Math.abs(offsetY) + bounds.height < Math.abs(newOffsetY) + NODE_HEIGHT) {
                    offsetY = newOffsetY;
                }
                return;
            }
            rowPos = rowPos.move(0, row.getHeight());
        }
    }

    private void withScissor(Rectangle screenBounds, Runnable action) {
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previousBox = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previousBox);

        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int scaleFactor = resolution.getScaleFactor();
        int scissorX = (screenBounds.x + bounds.x) * scaleFactor;
        int scissorY = minecraft.displayHeight - (screenBounds.y + bounds.y + bounds.height) * scaleFactor;
        int scissorWidth = bounds.width * scaleFactor;
        int scissorHeight = bounds.height * scaleFactor;
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            action.run();
        } finally {
            GL11.glScissor(previousBox.get(0), previousBox.get(1), previousBox.get(2), previousBox.get(3));
            if (wasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void renderScreenshotTile(ScreenshotFramebuffer framebuffer, int tileX, int tileY, int tileWidth, int tileHeight,
                                      int renderScale) {
        framebuffer.bind(true);
        framebuffer.clear();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.viewport(0, 0, tileWidth * renderScale, tileHeight * renderScale);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, tileWidth, tileHeight, 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);

            int y = -tileY;
            for (TreeRow row : rows) {
                if (y + row.getHeight() >= 0) {
                    framebuffer.bind(false);
                    row.renderForScreenshot(new Point(-tileX, y), tileWidth);
                }
                y += row.getHeight();
                if (y > tileHeight) {
                    break;
                }
            }
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        framebuffer.bind(false);
    }

    private interface RowElement {
        int getFullWidth();
    }

    private static final class Placeholder implements RowElement {
        @Override
        public int getFullWidth() {
            return NODE_MARGIN_LEFT + NODE_WIDTH;
        }
    }

    private static final class ScreenshotFramebuffer {
        private final int width;
        private final int height;
        private final int framebuffer;
        private final int colorTexture;
        private final int depthBuffer;

        private ScreenshotFramebuffer(int width, int height) {
            if (!OpenGlHelper.framebufferSupported) {
                throw new IllegalStateException("Framebuffer objects are not supported");
            }

            this.width = width;
            this.height = height;
            this.framebuffer = OpenGlHelper.glGenFramebuffers();
            this.colorTexture = GL11.glGenTextures();
            this.depthBuffer = OpenGlHelper.glGenRenderbuffers();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorTexture, 0);
            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, depthBuffer);
            OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, width, height);
            OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT,
                OpenGlHelper.GL_RENDERBUFFER, depthBuffer);

            int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
            if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                delete();
                throw new IllegalStateException("Crafting tree screenshot framebuffer is incomplete: " + status);
            }

            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        }

        private void bind(boolean setViewport) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            if (setViewport) {
                GlStateManager.viewport(0, 0, width, height);
            }
        }

        private void clear() {
            bind(true);
            GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GlStateManager.clearDepth(1.0D);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }

        private void delete() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, 0);
            OpenGlHelper.glDeleteRenderbuffers(depthBuffer);
            OpenGlHelper.glDeleteFramebuffers(framebuffer);
            GL11.glDeleteTextures(colorTexture);
        }
    }

    private final class TreeRow {
        private final List<RowElement> widgets = new ObjectArrayList<>();

        private void render(Point rowPosition, int renderWidth, Point mouse) {
            render(rowPosition, renderWidth, mouse, true);
        }

        private void renderForScreenshot(Point rowPosition, int renderWidth) {
            render(rowPosition, renderWidth, Point.ZERO, false);
        }

        private void render(Point rowPosition, int renderWidth, Point mouse, boolean updateHover) {
            int x = 0;
            for (int i = 0; i < widgets.size(); i++) {
                RowElement element = widgets.get(i);
                if (element instanceof TreeNode node) {
                    Point nodePosition = rowPosition.move(x + NODE_MARGIN_LEFT, node.marginTop);
                    node.currentPosition = nodePosition;
                    int renderWidthWithLinks = node.getChildLinkRenderWidth() + getPlaceholderWidth(i + 1);
                    boolean insideLeftEdge = nodePosition.x() + renderWidthWithLinks > 0;
                    boolean insideRightEdge = nodePosition.x() < renderWidth;
                    if (insideLeftEdge && insideRightEdge) {
                        node.render(nodePosition, mouse, updateHover);
                    }
                }
                x += element.getFullWidth();
            }
        }

        private int getPlaceholderWidth(int startIndex) {
            int width = 0;
            for (int i = startIndex; i < widgets.size(); i++) {
                if (widgets.get(i) instanceof Placeholder placeholder) {
                    width += placeholder.getFullWidth();
                } else {
                    break;
                }
            }
            return width;
        }

        private Point getRelativePosition(TreeNode node) {
            int x = 0;
            for (RowElement element : widgets) {
                if (element == node) {
                    return new Point(x + NODE_MARGIN_LEFT, node.marginTop);
                }
                x += element.getFullWidth();
            }
            return null;
        }

        private int getWidth() {
            int width = 0;
            for (RowElement element : widgets) {
                width += element.getFullWidth();
            }
            return width;
        }

        private int getHeight() {
            int height = 0;
            for (RowElement element : widgets) {
                if (element instanceof TreeNode node) {
                    height = Math.max(height, node.marginTop + NODE_TOTAL_HEIGHT);
                } else {
                    height = Math.max(height, NODE_TOTAL_HEIGHT);
                }
            }
            return height;
        }
    }

    private final class TreeNode implements RowElement {
        private final LiteCraftTreeNode node;
        private TreeRow row;
        private boolean root;
        private boolean selected;
        private int marginTop;
        private int marginRight;
        private int childLinkWidth;
        private TreeNode previous;
        private TreeNode next;
        private Point currentPosition = Point.ZERO;

        private TreeNode(LiteCraftTreeNode node) {
            this.node = node;
        }

        @Override
        public int getFullWidth() {
            return NODE_MARGIN_LEFT + NODE_WIDTH + marginRight;
        }

        private int getChildLinkRenderWidth() {
            return childLinkWidth == 0 ? NODE_WIDTH : LINE_RENDER_OFFSET + childLinkWidth;
        }

        private void render(Point position, Point mouse, boolean updateHover) {
            if (updateHover && isMouseOver(mouse)) {
                hovered = this;
            }
            renderBackground(position);
            renderItem(position.move(ITEM_RENDER_OFFSET, LINE_HEIGHT + ITEM_RENDER_OFFSET));
            renderMissingOverlay(position);
            renderLinks(position);
            if (updateHover) {
                renderedNodes++;
            }
        }

        private void renderBackground(Point position) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            int textureY = LiteCraftTreeNode.isMissing(node) ? 236 : 216;
            drawTexturedRect(position.x(), position.y() + LINE_HEIGHT, 0, textureY);
            if (selected) {
                drawTexturedRect(position.x(), position.y() + LINE_HEIGHT, 0, 196);
            }
        }

        private void renderMissingOverlay(Point position) {
            if (LiteCraftTreeNode.isMissing(node) && node.missing() > 0) {
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                drawTexturedRect(position.x(), position.y() + LINE_HEIGHT, 40, 216);
                GlStateManager.enableDepth();
            }
        }

        private void renderItem(Point position) {
            GenericStack output = node.output();
            if (output == null) {
                return;
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            FontRenderer font = minecraft.fontRenderer;
            ItemStack stack = GenericStack.wrapInItemStack(output.what(), output.amount());
            GlStateManager.pushMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            minecraft.getRenderItem().renderItemAndEffectIntoGUI(null, stack, position.x(), position.y());
            minecraft.getRenderItem().renderItemOverlayIntoGUI(font, stack, position.x(), position.y(), "");
            StackSizeRenderer.renderSizeLabel(font, position.x(), position.y(),
                output.what().formatAmount(output.amount(), AmountFormat.SLOT));
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }

        private void renderLinks(Point position) {
            renderParentLink(position);
            renderChildLink(position.move(0, NODE_HEIGHT + LINE_HEIGHT));
        }

        private void renderParentLink(Point position) {
            if (root) {
                return;
            }

            int lineColor = LiteCraftTreeNode.isMissing(node) ? MISSING_LINE_COLOR : LINE_COLOR;
            int shadowColor = LiteCraftTreeNode.isMissing(node) ? MISSING_LINE_SHADOW_COLOR : LINE_SHADOW_COLOR;
            renderLine(position.move(LINE_RENDER_OFFSET, -1), LINE_WIDTH, LINE_HEIGHT + 1, lineColor);
            renderLine(position.move(LINE_RENDER_OFFSET + 1, 0), LINE_WIDTH, LINE_HEIGHT, shadowColor);
        }

        private void renderChildLink(Point position) {
            if (node.inputs().isEmpty()) {
                return;
            }

            int lineColor = LiteCraftTreeNode.isMissing(node) ? MISSING_LINE_COLOR : LINE_COLOR;
            int shadowColor = LiteCraftTreeNode.isMissing(node) ? MISSING_LINE_SHADOW_COLOR : LINE_SHADOW_COLOR;
            if (childLinkWidth > 0) {
                renderLine(position.move(LINE_RENDER_OFFSET, LINE_HEIGHT), childLinkWidth, 1, lineColor);
                renderLine(position.move(LINE_RENDER_OFFSET + 1, LINE_HEIGHT + 1), childLinkWidth, 1, shadowColor);
                renderLine(position.move(LINE_RENDER_OFFSET, 0), LINE_WIDTH, LINE_HEIGHT, lineColor);
                renderLine(position.move(LINE_RENDER_OFFSET + 1, 0), LINE_WIDTH, LINE_HEIGHT, shadowColor);
            } else {
                renderLine(position.move(LINE_RENDER_OFFSET, 0), LINE_WIDTH, LINE_HEIGHT + 1, lineColor);
                renderLine(position.move(LINE_RENDER_OFFSET + 1, 0), LINE_WIDTH, LINE_HEIGHT + 2, shadowColor);
            }
        }

        private void select() {
            CraftingTreeWidget.this.select(this);
        }

        private boolean isMouseOver(Point mouse) {
            return mouse.x() >= currentPosition.x()
                && mouse.x() < currentPosition.x() + NODE_WIDTH
                && mouse.y() >= currentPosition.y() + LINE_HEIGHT
                && mouse.y() < currentPosition.y() + LINE_HEIGHT + NODE_HEIGHT;
        }

        private Tooltip getTooltip() {
            GenericStack output = node.output();
            if (output == null) {
                return new Tooltip(List.of());
            }

            ItemStack definition = output.what().wrapForDisplayOrFilter();
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen == null) {
                return new Tooltip(List.of());
            }
            GuiUtils.preItemToolTip(definition);

            List<String> itemTooltip = screen.getItemToolTip(definition);
            List<ITextComponent> lines = new ObjectArrayList<>(itemTooltip.size() + 4);
            for (String line : itemTooltip) {
                lines.add(new TextComponentString(line));
            }

            lines.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, output));
            if (LiteCraftTreeNode.isMissing(node)) {
                lines.add(new TextComponentString(node.missing() > 0
                    ? GuiText.CraftingTreeMissing.getLocal(CraftingTreeNumberFormat.formatDecimal(node.missing()))
                    : GuiText.CraftingTreeSubNodeMissing.getLocal()));
            }
            if (output.amount() >= 10000) {
                lines.add(new TextComponentString(CraftingTreeNumberFormat.formatDecimal(output.amount())));
            }

            return new Tooltip(lines);
        }
    }

}
