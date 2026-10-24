package ae2.client.gui.me.crafting;

import ae2.api.client.AEKeyRendering;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.client.Point;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Tooltip;
import ae2.client.gui.me.common.StackSizeRenderer;
import ae2.client.render.overlay.CraftingSupplierHighlightHandler;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.integration.Integrations;
import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import ae2.integration.modules.hei.CraftingTreeUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.config.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.NotNull;
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
import java.util.Map;
import java.util.Set;

public class CraftingTreeWidget implements ICompositeWidget {
    private static final int SCREENSHOT_TILE_SIZE = 2048;
    private static final long MAX_SCREENSHOT_IMAGE_BYTES = 64L * 1024 * 1024;

    private static final int LINE_WIDTH = 1;
    private static final int LINE_HEIGHT = 2;
    private static final int LINE_TOTAL_HEIGHT = 6;
    private static final int NODE_WIDTH = 20;
    private static final int NODE_HEIGHT = 20;
    private static final int NODE_TOTAL_HEIGHT = NODE_HEIGHT + LINE_TOTAL_HEIGHT;
    private static final int ROOT_MARGIN_TOP = 4;
    private static final int NODE_MARGIN_LEFT = 6;
    private static final int NODE_TOTAL_WIDTH = NODE_WIDTH + NODE_MARGIN_LEFT;
    private static final int LINE_RENDER_OFFSET = (NODE_WIDTH - (LINE_WIDTH * 2)) / 2;
    private static final int ITEM_RENDER_OFFSET = 2;

    private static final int LINE_COLOR = 0xFFF2F2F2;
    private static final int LINE_SHADOW_COLOR = 0xFF4D4D67;
    private static final int MISSING_LINE_COLOR = 0xFFEE6363;
    private static final int MISSING_LINE_SHADOW_COLOR = 0xFF8B3A3A;
    private static final int FEEDBACK_LINE_COLOR = 0xFFF2B33D;
    private static final int FEEDBACK_LINE_SHADOW_COLOR = 0xFF7A4E12;
    // Between the lines a ring's inputs are fed back along, left of its first column
    private static final int FEEDBACK_LANE = 4;

    private final List<TreeRow> rows = new ObjectArrayList<>();

    private final Rectangle bounds = new Rectangle();
    private int offsetX;
    private int offsetY;
    private int totalWidth;
    private int totalHeight;
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
    @Nullable
    private CraftingTreeCycleView.Contracted contracted;
    @Nullable
    private CraftingTreeCycleView.Contracted contractedMissingOnly;
    // The rings open, each opened from the one before
    private final List<RingState> rings = new ObjectArrayList<>();
    private final Map<LiteCraftTreeNode, TreeNode> nodeWidgets = new Reference2ObjectOpenHashMap<>();
    // Per craft, the node a line to it ends at: its machine, or the node it makes
    private final Map<LiteCraftTreeProc, TreeNode> procAnchors = new Reference2ObjectOpenHashMap<>();
    private int lanePadding;

    private static boolean hasSameOutput(@Nullable GenericStack left, GenericStack right) {
        return left != null && left.what().equals(right.what()) && left.amount() == right.amount();
    }

    private static boolean findLeftNode(TreeNode node, boolean missingOnly) {
        TreeNode previous = node.previous;
        while (previous != null) {
            if (previous.isSelectable() && (!missingOnly || previous.isMissing())) {
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
            if (next.isSelectable() && (!missingOnly || next.isMissing())) {
                next.select();
                return true;
            }
            next = next.next;
        }
        return false;
    }

    private static void drawIcon(int x, int y, Icon icon) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        icon.getBlitter().dest(x, y).blit();
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
        this.rings.clear();
        this.contracted = CraftingTreeCycleView.contract(root);
        if (LiteCraftTreeNode.isMissing(root)) {
            this.missingOnly = true;
            this.missingOnlyRoot = root.withMissingOnly();
            this.contractedMissingOnly = CraftingTreeCycleView.contract(this.missingOnlyRoot);
        } else {
            this.missingOnly = false;
            this.missingOnlyRoot = null;
            this.contractedMissingOnly = null;
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
            this.contractedMissingOnly = CraftingTreeCycleView.contract(this.missingOnlyRoot);
        }

        this.missingOnly = missingOnly;
        this.rings.clear();
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
                    row.render(new Point(offsetX + lanePadding, y), scaledWidth, scaledMouse);
                }
                y += row.getHeight();
                if (y > scaledHeight) {
                    break;
                }
            }
            renderFeedbackLines();
        });

        GlStateManager.popMatrix();
    }

    @Override
    public void drawForegroundLayer(Rectangle screenBounds, Point mouse) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int x = bounds.x + 2;
        var ring = currentRing();
        if (ring != null) {
            var region = ring.region();
            font.drawStringWithShadow(GuiText.CraftingTreeRing.getLocal(region.crafts(), region.feedbacks()),
                x, bounds.y + bounds.height - 18, 0x80FFFFFF);
            font.drawStringWithShadow(GuiText.CraftingTreeRingTip.getLocal(),
                x, bounds.y + bounds.height - 9, 0x80FFFFFF);
        } else if (selected == null) {
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

    private static boolean isMissing(LiteCraftTreeProc proc) {
        for (LiteCraftTreeNode input : proc.inputs()) {
            if (LiteCraftTreeNode.isMissing(input)) {
                return true;
            }
        }
        return false;
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

    private static int getNodeRenderDepth(LiteCraftTreeNode node) {
        int maxDepth = 0;
        for (LiteCraftTreeProc proc : node.inputs()) {
            maxDepth = Math.max(maxDepth, 1 + getProcessRenderDepth(proc));
        }
        return maxDepth;
    }

    /**
     * Only machine nodes use the generic widget tooltip. Ingredient nodes are drawn by the screen through
     * {@link #getHoveredKeyTooltip}, see there.
     */
    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        Point relativeMouse = toScaledTreeMouse(new Point(mouseX, mouseY));
        TreeNode node = findNodeAt(relativeMouse);
        return node == null ? null : node.getMachineTooltip();
    }

    /**
     * Returns the tooltip of the hovered ingredient node. Like every other AE2 screen, it must be drawn together with
     * the key's stack: mods such as Thaumcraft reserve tooltip lines for an item and then draw into them during
     * rendering, which fails when the generic widget tooltip renders the lines without a stack.
     *
     * @param mouseX mouse position relative to the screen's origin
     * @param mouseY mouse position relative to the screen's origin
     */
    @Nullable
    public KeyTooltip getHoveredKeyTooltip(int mouseX, int mouseY) {
        if (!isVisible() || !bounds.contains(mouseX, mouseY)) {
            return null;
        }
        Point relativeMouse = toScaledTreeMouse(new Point(mouseX, mouseY));
        TreeNode node = findNodeAt(relativeMouse);
        return node == null ? null : node.getKeyTooltip();
    }

    public record KeyTooltip(AEKey what, List<ITextComponent> lines) {
    }

    public BufferedImage createNodeAreaScreenshot() {
        if (!hasTree()) {
            throw new IllegalStateException("No crafting tree data available");
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        int renderScale = new ScaledResolution(minecraft).getScaleFactor();
        long logicalWidth = (long) getTotalWidth() + NODE_MARGIN_LEFT;
        long logicalHeight = getTotalHeight();
        long imageWidth = Math.max(1L, logicalWidth) * renderScale;
        long imageHeight = Math.max(1L, logicalHeight) * renderScale;
        long imageBytes;
        try {
            imageBytes = Math.multiplyExact(Math.multiplyExact(imageWidth, imageHeight), Integer.BYTES);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Crafting tree screenshot dimensions overflow", e);
        }
        if (imageWidth > Integer.MAX_VALUE || imageHeight > Integer.MAX_VALUE
            || imageBytes > MAX_SCREENSHOT_IMAGE_BYTES) {
            throw new IllegalArgumentException(String.format(
                "Crafting tree screenshot is too large: %dx%d pixels (%d bytes, max %d)",
                imageWidth, imageHeight, imageBytes, MAX_SCREENSHOT_IMAGE_BYTES));
        }
        int width = (int) Math.max(1L, logicalWidth);
        int height = (int) Math.max(1L, logicalHeight);
        BufferedImage image = new BufferedImage((int) imageWidth, (int) imageHeight, BufferedImage.TYPE_INT_ARGB);
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
                    BufferedImage tile = null;
                    try {
                        renderScreenshotTile(framebuffer, tileX, tileY, tileWidth, tileHeight, renderScale);
                        tile = readFramebuffer(framebuffer, framebufferWidth, framebufferHeight);
                        graphics.drawImage(tile, tileX * renderScale, tileY * renderScale, null);
                    } finally {
                        if (tile != null) {
                            tile.flush();
                        }
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

    private static int getProcessRenderDepth(LiteCraftTreeProc proc) {
        int inputDepth = getProcessInputDepth(proc);
        int maxDepth = inputDepth;
        for (LiteCraftTreeNode input : proc.inputs()) {
            maxDepth = Math.max(maxDepth, inputDepth + getNodeRenderDepth(input));
        }
        return maxDepth;
    }

    private void clearTree() {
        root = null;
        missingOnlyRoot = null;
        contracted = null;
        contractedMissingOnly = null;
        rings.clear();
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
        if (!rings.isEmpty()) {
            return rings.getLast().ring.root();
        }
        var tree = currentContracted();
        return tree == null ? null : tree.root();
    }

    @Nullable
    private CraftingTreeCycleView.Contracted currentContracted() {
        return missingOnly ? contractedMissingOnly : contracted;
    }

    @Nullable
    private CraftingTreeCycleView.Ring currentRing() {
        return rings.isEmpty() ? null : rings.getLast().ring;
    }

    public boolean isInRing() {
        return !rings.isEmpty();
    }

    private void openRing(CraftingTreeCycleView.Region region) {
        var tree = currentContracted();
        if (tree == null) {
            return;
        }
        var state = new RingState(region, offsetX, offsetY);
        state.ring = CraftingTreeCycleView.ring(region, state.unfolded, tree.entries());
        rings.add(state);
        offsetX = 0;
        offsetY = 0;
        select(null);
        rebuildTree();
    }

    private void unfold(CraftingTreeCycleView.Fold fold) {
        var state = rings.getLast();
        var tree = currentContracted();
        if (tree == null) {
            return;
        }
        state.unfolded.add(fold.node());
        state.ring = CraftingTreeCycleView.ring(state.region, state.unfolded, tree.entries());
        rebuildTree();
    }

    /**
     * Back to the ring or the tree it was opened from.
     */
    public void leaveRing() {
        if (rings.isEmpty()) {
            return;
        }
        var state = rings.removeLast();
        select(null);
        rebuildTree();
        offsetX = state.savedOffsetX;
        offsetY = state.savedOffsetY;
        clampOffsets();
    }

    private static int getProcessInputDepth(LiteCraftTreeProc proc) {
        if (proc.machines().isEmpty()) {
            return 0;
        }
        return proc.machines().size() > 1 ? 2 : 1;
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        Point relativeMouse = toScaledTreeMouse(mousePos);
        TreeNode node = findNodeAt(relativeMouse);
        if (node != null) {
            if (button == 0 && GuiScreen.isShiftKeyDown() && node.highlightMachinesAndClose()) {
                return true;
            }
            if (button == 0 && node.fold != null) {
                unfold(node.fold);
                return true;
            }
            if (button == 0 && node.cycle != null) {
                openRing(node.cycle);
                return true;
            }
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
    public boolean onKeyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && !rings.isEmpty()) {
            leaveRing();
            return true;
        }
        GenericStack hoveredOutput = hovered == null ? null : hovered.getOutput();
        if (Integrations.hei().isEnabled() && hoveredOutput != null) {
            int showRecipeKeyCode = KeyBindings.showRecipe.getKeyCode();
            int showUsesKeyCode = KeyBindings.showUses.getKeyCode();
            int bookmarkKeyCode = KeyBindings.bookmark.getKeyCode();

            if (showRecipeKeyCode > 0 && showRecipeKeyCode <= 255 && showRecipeKeyCode == keyCode) {
                return CraftingTreeUtils.showStackFocus(hoveredOutput, IFocus.Mode.OUTPUT);
            }
            if (showUsesKeyCode > 0 && showUsesKeyCode <= 255 && showUsesKeyCode == keyCode) {
                return CraftingTreeUtils.showStackFocus(hoveredOutput, IFocus.Mode.INPUT);
            }
            if (bookmarkKeyCode > 0 && bookmarkKeyCode <= 255 && bookmarkKeyCode == keyCode) {
                return CraftingTreeUtils.addIngredientToBookmarkList(hoveredOutput);
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

    private void rebuildTree() {
        GenericStack selectedOutput = selected == null ? null : selected.getOutput();
        selected = null;
        nodes = 0;
        rows.clear();
        nodeWidgets.clear();
        procAnchors.clear();
        var ring = currentRing();
        lanePadding = ring == null ? 0 : FEEDBACK_LANE * ring.feedback().size();

        LiteCraftTreeNode treeRoot = getCurrentRoot();
        if (treeRoot != null) {
            addNodeRecursive(treeRoot, 0);
        }
        alignJunctionsToRowBottom();
        computeTotalSize();

        TreeNode restoredSelection = findNodeByOutput(selectedOutput);
        if (restoredSelection != null) {
            restoredSelection.select();
        }
        clampOffsets();
    }

    private void computeTotalSize() {
        int width = 0;
        int height = 0;
        for (TreeRow row : rows) {
            width = Math.max(width, row.getWidth());
            height += row.getHeight();
        }
        this.totalWidth = width + lanePadding;
        this.totalHeight = height;
    }

    private TreeNode addNodeRecursive(LiteCraftTreeNode node, int depth) {
        TreeNode nodeWidget = new TreeNode(node);
        nodeWidgets.put(node, nodeWidget);
        if (depth == 0) {
            nodeWidget.root = true;
            nodeWidget.marginTop = ROOT_MARGIN_TOP;
            nodeWidget.marginRight = 6;
        }

        addNode(nodeWidget, depth);

        List<TreeNode> directChildren = new ObjectArrayList<>();
        for (LiteCraftTreeProc proc : node.inputs()) {
            int firstProcessDepth = depth + 1;
            int lastProcessDepth = firstProcessDepth + getProcessRenderDepth(proc);
            alignRows(firstProcessDepth, lastProcessDepth);
            directChildren.addAll(addProcessRecursive(proc, depth + 1, nodeWidget));
            alignRows(firstProcessDepth, lastProcessDepth);
        }

        configureChildLink(nodeWidget, directChildren);
        alignRows(depth, depth + getNodeRenderDepth(node));
        return nodeWidget;
    }

    private List<TreeNode> addProcessRecursive(LiteCraftTreeProc proc, int depth, TreeNode parent) {
        if (proc.machines().isEmpty()) {
            procAnchors.put(proc, parent);
            return addProcessInputs(proc, depth);
        }

        if (proc.machines().size() > 1) {
            alignRows(depth, depth + 2);
        }

        List<TreeNode> machineNodes = new ObjectArrayList<>();
        boolean missing = isMissing(proc);
        for (PatternContainerGroup machine : proc.machines()) {
            TreeNode machineNode = new TreeNode(machine, proc.machineLocations(machine), missing);
            addNode(machineNode, depth);
            machineNodes.add(machineNode);
            procAnchors.putIfAbsent(proc, machineNode);
        }

        if (machineNodes.size() > 1) {
            addPlaceholders(depth + 1, (machineNodes.size() - 1) / 2, LINE_TOTAL_HEIGHT);
            TreeNode junction = new TreeNode(missing);
            addNode(junction, depth + 1);
            alignJunctionToRowBottom(junction);

            List<TreeNode> materialNodes = addProcessInputs(proc, depth + 2);
            configureChildLink(junction, materialNodes);
            for (TreeNode machineNode : machineNodes) {
                configureChildLink(machineNode, List.of(junction));
            }
        } else if (!machineNodes.isEmpty()) {
            TreeNode machineNode = machineNodes.getFirst();
            List<TreeNode> materialNodes = addProcessInputs(proc, depth + 1);
            configureChildLink(machineNode, materialNodes);
        }

        return machineNodes;
    }

    private List<TreeNode> addProcessInputs(LiteCraftTreeProc proc, int depth) {
        List<TreeNode> materialNodes = new ObjectArrayList<>();
        for (LiteCraftTreeNode input : proc.inputs()) {
            materialNodes.add(addNodeRecursive(input, depth));
        }
        return materialNodes;
    }

    private void configureChildLink(TreeNode parent, List<TreeNode> directChildren) {
        parent.hasChildLink = !directChildren.isEmpty();
        parent.childLinkStartOffset = LINE_RENDER_OFFSET;
        parent.childLinkWidth = 0;
        parent.childLinkVerticalHeight = LINE_HEIGHT;
        if (directChildren.isEmpty()) {
            return;
        }

        Point parentPos = parent.row.getRelativePosition(parent);
        if (parentPos == null) {
            return;
        }

        int minAnchorX = parentPos.x() + LINE_RENDER_OFFSET;
        int maxAnchorX = minAnchorX;
        for (TreeNode child : directChildren) {
            Point childPos = child.row.getRelativePosition(child);
            if (childPos == null) {
                continue;
            }
            int childAnchorX = childPos.x() + LINE_RENDER_OFFSET;
            minAnchorX = Math.min(minAnchorX, childAnchorX);
            maxAnchorX = Math.max(maxAnchorX, childAnchorX);
            parent.childLinkVerticalHeight = Math.max(parent.childLinkVerticalHeight, childPos.y() + 1);
        }

        parent.childLinkStartOffset = minAnchorX - parentPos.x();
        parent.childLinkWidth = maxAnchorX == minAnchorX ? 0 : maxAnchorX - minAnchorX + LINE_WIDTH;
    }

    private void alignRows(int firstDepth, int lastDepth) {
        int maxWidth = 0;
        for (int depth = firstDepth; depth <= lastDepth; depth++) {
            while (rows.size() <= depth) {
                rows.add(new TreeRow());
            }
            maxWidth = Math.max(maxWidth, rows.get(depth).getWidth());
        }

        for (int depth = firstDepth; depth <= lastDepth; depth++) {
            TreeRow row = rows.get(depth);
            int width = row.getWidth();
            while (width < maxWidth) {
                row.addWidget(row.createPlaceholder());
                width += NODE_TOTAL_WIDTH;
            }
        }
    }

    private void addPlaceholders(int depth, int count, int height) {
        while (rows.size() <= depth) {
            rows.add(new TreeRow());
        }
        TreeRow row = rows.get(depth);
        for (int i = 0; i < count; i++) {
            row.addWidget(new Placeholder(height));
        }
    }

    private void addNode(TreeNode node, int depth) {
        while (rows.size() <= depth) {
            rows.add(new TreeRow());
        }

        TreeRow row = rows.get(depth);
        node.row = row;
        row.addWidget(node);
        if (node.countsAsNode()) {
            nodes++;
        }

        for (int i = row.widgets.size() - 2; i >= 0; i--) {
            RowElement previous = row.widgets.get(i);
            if (previous instanceof TreeNode previousNode) {
                previousNode.next = node;
                node.previous = previousNode;
                break;
            }
        }
    }

    private void alignJunctionsToRowBottom() {
        for (TreeRow row : rows) {
            for (RowElement element : row.widgets) {
                if (element instanceof TreeNode node && node.isJunction()) {
                    alignJunctionToRowBottom(node);
                }
            }
        }
    }

    private void alignJunctionToRowBottom(TreeNode junction) {
        junction.marginTop = Math.max(0, junction.row.getHeight() - LINE_TOTAL_HEIGHT);
        junction.row.invalidateHeight();
    }

    @Nullable
    private TreeNode findNodeAt(Point treeMouse) {
        int scaledHeight = (int) (bounds.height / scale);
        int y = offsetY;
        for (TreeRow row : rows) {
            int rowHeight = row.getHeight();
            if (y + rowHeight >= 0 && y <= scaledHeight) {
                TreeNode node = row.findNodeAt(treeMouse, offsetX + lanePadding, y);
                if (node != null) {
                    return node;
                }
            }
            y += rowHeight;
            if (y > scaledHeight) {
                break;
            }
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
                if (element instanceof TreeNode node && node.getOutput() != null && hasSameOutput(node.getOutput(), output)) {
                    return node;
                }
            }
        }
        return null;
    }

    private void renderFeedbackLines() {
        var ring = currentRing();
        if (ring == null || ring.feedback().isEmpty()) {
            return;
        }
        var rowTops = new Reference2IntOpenHashMap<TreeRow>();
        int y = offsetY;
        for (TreeRow row : rows) {
            rowTops.put(row, y);
            y += row.getHeight();
        }
        int rowX = offsetX + lanePadding;
        int lane = 0;
        for (var entry : ring.feedback().entrySet()) {
            TreeNode target = nodeWidgets.get(entry.getKey());
            TreeNode maker = procAnchors.get(entry.getValue());
            if (target == null || maker == null) {
                continue;
            }
            int laneX = rowX + NODE_MARGIN_LEFT - 3 - FEEDBACK_LANE * lane++;
            int makerX = rowX + maker.getPos() + NODE_MARGIN_LEFT;
            int makerY = rowTops.getInt(maker.row) + maker.marginTop + LINE_HEIGHT;
            int targetX = rowX + target.getPos() + NODE_MARGIN_LEFT;
            int targetY = rowTops.getInt(target.row) + target.marginTop + LINE_HEIGHT;
            var points = new ObjectArrayList<Point>();
            // Out of the side when nothing is left of it in its row, else from under it
            if (maker.isLeftmost()) {
                points.add(new Point(makerX - 2, makerY + NODE_HEIGHT / 2));
                points.add(new Point(laneX, makerY + NODE_HEIGHT / 2));
            } else {
                points.add(new Point(makerX + 3, makerY + NODE_HEIGHT));
                points.add(new Point(makerX + 3, makerY + NODE_HEIGHT + 1));
                points.add(new Point(laneX, makerY + NODE_HEIGHT + 1));
            }
            // Into the side the same way, or up into it from under it as what it takes comes in
            if (target.isLeftmost()) {
                points.add(new Point(laneX, targetY + NODE_HEIGHT / 2));
                points.add(new Point(targetX - 2, targetY + NODE_HEIGHT / 2));
            } else {
                points.add(new Point(laneX, targetY + NODE_HEIGHT + 1));
                points.add(new Point(targetX + 3, targetY + NODE_HEIGHT + 1));
                points.add(new Point(targetX + 3, targetY + NODE_HEIGHT));
            }
            renderFeedbackLine(points);
        }
    }

    private static void renderFeedbackLine(List<Point> points) {
        GlStateManager.disableDepth();
        for (int pass = 0; pass < 2; pass++) {
            int color = pass == 0 ? FEEDBACK_LINE_SHADOW_COLOR : FEEDBACK_LINE_COLOR;
            int shift = pass == 0 ? 1 : 0;
            for (int i = 0; i + 1 < points.size(); i++) {
                Point from = points.get(i);
                Point to = points.get(i + 1);
                GuiContainer.drawRect(Math.min(from.x(), to.x()) + shift, Math.min(from.y(), to.y()) + shift,
                    Math.max(from.x(), to.x()) + 1 + shift, Math.max(from.y(), to.y()) + 1 + shift, color);
            }
            // A head where the item arrives
            Point tip = points.getLast();
            Point before = points.get(points.size() - 2);
            int dx = Integer.signum(tip.x() - before.x());
            int dy = Integer.signum(tip.y() - before.y());
            for (int k = 1; k <= 2; k++) {
                int x = tip.x() - dx * k + shift;
                int y = tip.y() - dy * k + shift;
                if (dx != 0) {
                    GuiContainer.drawRect(x, y - k, x + 1, y + k + 1, color);
                } else {
                    GuiContainer.drawRect(x - k, y, x + k + 1, y + 1, color);
                }
            }
        }
        GlStateManager.enableDepth();
    }

    private Point toScaledTreeMouse(Point mouse) {
        return new Point((int) ((mouse.x() - bounds.x) / scale), (int) ((mouse.y() - bounds.y) / scale));
    }

    private void clampOffsets() {
        int visibleWidth = Math.max(1, (int) (bounds.width / scale));
        int visibleHeight = Math.max(1, (int) (bounds.height / scale));
        int minOffsetX = Math.min(0, visibleWidth - this.totalWidth);
        int minOffsetY = Math.min(0, visibleHeight - this.totalHeight);
        this.offsetX = MathHelper.clamp(offsetX, minOffsetX, 0);
        this.offsetY = MathHelper.clamp(offsetY, minOffsetY, 0);
    }

    private int getTotalWidth() {
        return this.totalWidth;
    }

    private int getTotalHeight() {
        return this.totalHeight;
    }

    private void select(@Nullable TreeNode node) {
        if (node != null && !node.isSelectable()) {
            return;
        }
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
            if (element instanceof TreeNode node && node.isSelectable()) {
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
                    row.renderForScreenshot(new Point(lanePadding - tileX, y), tileWidth);
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

        int getFullHeight();

        boolean hasPos();

        int getPos();

        void setPos(int pos);
    }

    private static class Placeholder implements RowElement {

        private final int height;
        private int pos;
        private boolean hasPos;

        private Placeholder(int height) {
            this.height = height;
        }

        @Override
        public int getFullWidth() {
            return NODE_MARGIN_LEFT + NODE_WIDTH;
        }

        @Override
        public int getFullHeight() {
            return height;
        }

        @Override
        public boolean hasPos() {
            return hasPos;
        }

        @Override
        public int getPos() {
            return pos;
        }

        @Override
        public void setPos(int pos) {
            hasPos = true;
            this.pos = pos;
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
        private int width;
        private int height;

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
            if (node.hasPos()) {
                int x = node.getPos();
                return new Point(x + NODE_MARGIN_LEFT, node.marginTop);
            }
            return null;
        }

        private int getWidth() {
            return width;
        }

        private int getHeight() {
            return height;
        }

        private void addWidget(RowElement element) {
            element.setPos(width);
            width += element.getFullWidth();
            height = Math.max(height, element.getFullHeight());
            widgets.add(element);
        }

        private void invalidateHeight() {
            height = 0;
            for (RowElement element : widgets) {
                height = Math.max(height, element.getFullHeight());
            }
        }

        @Nullable
        private TreeNode findNodeAt(Point mouse, int rowX, int rowY) {
            if (widgets.isEmpty()) {
                return null;
            }

            int localX = mouse.x() - rowX;
            int threshold = localX - NODE_MARGIN_LEFT;
            int low = 0;
            int high = widgets.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                RowElement element = widgets.get(middle);
                int start = element.getPos();
                if (start + element.getFullWidth() <= threshold) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }

            for (int i = low; i < widgets.size(); i++) {
                RowElement element = widgets.get(i);
                int start = element.getPos();
                if (start > localX + NODE_WIDTH) {
                    break;
                }
                if (element instanceof TreeNode node) {
                    node.currentPosition = new Point(rowX + start + NODE_MARGIN_LEFT, rowY + node.marginTop);
                    if (node.isMouseOver(mouse)) {
                        return node;
                    }
                }
            }
            return null;
        }

        private Placeholder createPlaceholder() {
            return new Placeholder(isJunctionOnly() ? LINE_TOTAL_HEIGHT : NODE_TOTAL_HEIGHT);
        }

        private boolean isJunctionOnly() {
            for (RowElement element : widgets) {
                if (element instanceof TreeNode node && !node.isJunction()) {
                    return false;
                }
            }
            return !widgets.isEmpty();
        }
    }

    private static final class RingState {
        private final CraftingTreeCycleView.Region region;
        private final Set<LiteCraftTreeNode> unfolded = new ReferenceOpenHashSet<>();
        private final int savedOffsetX;
        private final int savedOffsetY;
        private CraftingTreeCycleView.Ring ring;

        private RingState(CraftingTreeCycleView.Region region, int savedOffsetX, int savedOffsetY) {
            this.region = region;
            this.savedOffsetX = savedOffsetX;
            this.savedOffsetY = savedOffsetY;
        }
    }

    private final class TreeNode implements RowElement {
        @Nullable
        private final LiteCraftTreeNode node;
        // The cycle it stands for or enters, the craft it is fed back from, and what is folded into it
        @Nullable
        private final CraftingTreeCycleView.Region cycle;
        private final boolean feedback;
        @Nullable
        private final CraftingTreeCycleView.Fold fold;
        @Nullable
        private final PatternContainerGroup machine;
        private final List<CraftingSupplierLocation> machineLocations;
        private final boolean junction;
        private final boolean missing;
        private TreeRow row;
        private boolean root;
        private boolean selected;
        private int marginTop;
        private int marginRight;
        private int childLinkStartOffset = LINE_RENDER_OFFSET;
        private int childLinkWidth;
        private int childLinkVerticalHeight = LINE_HEIGHT;
        private boolean hasChildLink;
        private TreeNode previous;
        private TreeNode next;
        private Point currentPosition = Point.ZERO;
        private int pos;
        private boolean hasPos;

        private TreeNode(@NotNull LiteCraftTreeNode node) {
            this.node = node;
            this.machine = null;
            this.machineLocations = List.of();
            this.junction = false;
            this.missing = LiteCraftTreeNode.isMissing(node);
            var ring = currentRing();
            if (ring != null) {
                this.cycle = ring.nested().get(node);
                this.feedback = ring.feedback().containsKey(node);
                this.fold = ring.folds().get(node);
            } else {
                var tree = currentContracted();
                this.cycle = tree == null ? null : tree.cycles().get(node);
                this.feedback = false;
                this.fold = null;
            }
        }

        private TreeNode(@Nullable PatternContainerGroup machine, List<CraftingSupplierLocation> machineLocations,
                         boolean missing) {
            this.node = null;
            this.machine = machine;
            this.machineLocations = machineLocations;
            this.junction = false;
            this.missing = missing;
            this.cycle = null;
            this.feedback = false;
            this.fold = null;
        }

        private TreeNode(boolean missing) {
            this.node = null;
            this.machine = null;
            this.machineLocations = List.of();
            this.junction = true;
            this.missing = missing;
            this.cycle = null;
            this.feedback = false;
            this.fold = null;
        }

        @Override
        public int getFullWidth() {
            return NODE_MARGIN_LEFT + NODE_WIDTH + marginRight;
        }

        @Override
        public int getFullHeight() {
            return marginTop + (junction ? LINE_TOTAL_HEIGHT : NODE_TOTAL_HEIGHT);
        }

        @Override
        public boolean hasPos() {
            return hasPos;
        }

        @Override
        public int getPos() {
            return pos;
        }

        @Override
        public void setPos(int pos) {
            hasPos = true;
            this.pos = pos;
        }

        private int getChildLinkRenderWidth() {
            if (childLinkWidth == 0) {
                return NODE_WIDTH;
            }
            return Math.max(NODE_WIDTH, Math.max(LINE_RENDER_OFFSET, childLinkStartOffset) + childLinkWidth);
        }

        private void render(Point position, Point mouse, boolean updateHover) {
            if (updateHover && isSelectable() && isMouseOver(mouse)) {
                hovered = this;
            }
            if (!junction) {
                renderBackground(position);
                renderIcon(position.move(ITEM_RENDER_OFFSET, LINE_HEIGHT + ITEM_RENDER_OFFSET));
                renderMissingOverlay(position);
                renderCycleOverlay(position);
            }
            renderLinks(position);
            if (updateHover && countsAsNode()) {
                renderedNodes++;
            }
        }

        private void renderBackground(Point position) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            Icon background = selected ? Icon.CRAFTING_TREE_NODE_SELECTED :
                missing ? Icon.CRAFTING_TREE_NODE_MISSING :
                    cycle != null ? Icon.CRAFTING_TREE_NODE_CYCLE : Icon.CRAFTING_TREE_NODE_NORMAL;
            drawIcon(position.x(), position.y() + LINE_HEIGHT, background);
        }

        private void renderMissingOverlay(Point position) {
            if (node != null && missing && node.missing() > 0) {
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                drawIcon(position.x(), position.y() + LINE_HEIGHT, Icon.CRAFTING_TREE_MISSING_OVERLAY);
                GlStateManager.enableDepth();
            }
        }

        private void renderCycleOverlay(Point position) {
            Icon overlay = cycle != null ? Icon.CRAFTING_TREE_CYCLE
                : feedback ? Icon.CRAFTING_TREE_FEEDBACK
                : fold != null ? Icon.CRAFTING_TREE_FOLDED : null;
            if (overlay == null) {
                return;
            }
            GlStateManager.disableDepth();
            drawIcon(position.x(), position.y() + LINE_HEIGHT, overlay);
            GlStateManager.enableDepth();
        }

        private boolean isLeftmost() {
            for (RowElement element : row.widgets) {
                if (element instanceof TreeNode other && other != this && !other.junction && other.getPos() < getPos()) {
                    return false;
                }
            }
            return true;
        }

        private void renderIcon(Point position) {
            if (machine != null) {
                renderMachine(machine, position);
                return;
            }
            renderItem(position);
        }

        private void renderMachine(@NotNull PatternContainerGroup machine, Point position) {
            if (machine.icon() == null) {
                return;
            }

            GlStateManager.pushMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            AEKeyRendering.drawInGui(Minecraft.getMinecraft(), position.x(), position.y(), machine.icon());
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawIcon(position.x() - LINE_HEIGHT, position.y() - LINE_HEIGHT, Icon.CRAFTING_TREE_MACHINE);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }

        private void renderItem(Point position) {
            if (node == null) {
                return;
            }
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
            renderChildLink(position.move(0, junction ? LINE_HEIGHT : NODE_HEIGHT + LINE_HEIGHT));
        }

        private void renderParentLink(Point position) {
            if (root) {
                return;
            }

            int lineColor = missing ? MISSING_LINE_COLOR : LINE_COLOR;
            int shadowColor = missing ? MISSING_LINE_SHADOW_COLOR : LINE_SHADOW_COLOR;
            renderLine(position.move(LINE_RENDER_OFFSET, -1), LINE_WIDTH, LINE_HEIGHT + 1, lineColor);
            renderLine(position.move(LINE_RENDER_OFFSET + 1, 0), LINE_WIDTH, LINE_HEIGHT, shadowColor);
        }

        private void renderChildLink(Point position) {
            if (!hasChildLink) {
                return;
            }

            int lineColor = missing ? MISSING_LINE_COLOR : LINE_COLOR;
            int shadowColor = missing ? MISSING_LINE_SHADOW_COLOR : LINE_SHADOW_COLOR;
            if (childLinkWidth > 0) {
                renderLine(position.move(childLinkStartOffset, LINE_HEIGHT), childLinkWidth, 1, lineColor);
                renderLine(position.move(childLinkStartOffset + 1, LINE_HEIGHT + 1), childLinkWidth, 1, shadowColor);
                renderLine(position.move(LINE_RENDER_OFFSET, 0), LINE_WIDTH, childLinkVerticalHeight, lineColor);
                renderLine(position.move(LINE_RENDER_OFFSET + 1, 0), LINE_WIDTH, childLinkVerticalHeight, shadowColor);
            } else {
                renderLine(position.move(LINE_RENDER_OFFSET, 0), LINE_WIDTH, childLinkVerticalHeight + 1, lineColor);
                renderLine(position.move(LINE_RENDER_OFFSET + 1, 0), LINE_WIDTH, childLinkVerticalHeight + 2, shadowColor);
            }
        }

        private void select() {
            CraftingTreeWidget.this.select(this);
        }

        private boolean highlightMachinesAndClose() {
            if (machineLocations.isEmpty()) {
                return false;
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            CraftingSupplierHighlightHandler.INSTANCE.showLocations(minecraft, machineLocations);
            minecraft.displayGuiScreen(null);
            return true;
        }

        private boolean isSelectable() {
            return !junction;
        }

        private boolean countsAsNode() {
            return !junction;
        }

        private boolean isJunction() {
            return junction;
        }

        @Nullable
        private GenericStack getOutput() {
            return node == null ? null : node.output();
        }

        private boolean isMissing() {
            return missing;
        }

        private boolean isMouseOver(Point mouse) {
            if (junction) {
                return false;
            }
            return mouse.x() >= currentPosition.x()
                && mouse.x() < currentPosition.x() + NODE_WIDTH
                && mouse.y() >= currentPosition.y() + LINE_HEIGHT
                && mouse.y() < currentPosition.y() + LINE_HEIGHT + NODE_HEIGHT;
        }

        @Nullable
        private Tooltip getMachineTooltip() {
            if (machine == null) {
                return null;
            }
            int extraLines = machineLocations.isEmpty() ? 0 :
                (GuiScreen.isShiftKeyDown() ? machineLocations.size() : 1);
            List<ITextComponent> lines = new ObjectArrayList<>(machine.tooltip().size() + 1 + extraLines);
            lines.add(machine.name());
            lines.addAll(machine.tooltip());
            if (!machineLocations.isEmpty()) {
                if (GuiScreen.isShiftKeyDown()) {
                    for (CraftingSupplierLocation location : machineLocations) {
                        lines.add(formatMachineLocation(location));
                    }
                } else {
                    lines.add(GuiText.CraftingTreeHighlightProvider.text());
                }
            }
            return new Tooltip(lines);
        }

        @Nullable
        private KeyTooltip getKeyTooltip() {
            if (machine != null || node == null) {
                return null;
            }
            GenericStack output = node.output();
            if (output == null) {
                return null;
            }

            List<ITextComponent> lines = AEKeyRendering.getTooltip(output.what());
            int keyLines = lines.size();
            lines.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, output));
            if (missing) {
                lines.add(new TextComponentString(node.missing() > 0
                    ? GuiText.CraftingTreeMissing.getLocal(CraftingTreeNumberFormat.formatDecimal(node.missing()))
                    : GuiText.CraftingTreeSubNodeMissing.getLocal()));
            }
            if (output.amount() >= 10000) {
                lines.add(new TextComponentString(CraftingTreeNumberFormat.formatDecimal(output.amount())));
            }
            if (cycle != null) {
                lines.add(new TextComponentString(isInRing()
                    ? GuiText.CraftingTreeCycleNested.getLocal()
                    : GuiText.CraftingTreeCycle.getLocal(cycle.crafts(), cycle.feedbacks())));
                lines.add(GuiText.CraftingTreeCycleOpen.text());
            }
            if (feedback) {
                lines.add(GuiText.CraftingTreeFeedback.text());
            }
            if (fold != null) {
                lines.add(new TextComponentString(GuiText.CraftingTreeFolded.getLocal(fold.crafts())));
            }

            for (int i = keyLines; i < lines.size(); i++) {
                ITextComponent line = lines.get(i);
                if (line.getStyle().getColor() == null) {
                    line.getStyle().setColor(TextFormatting.GRAY);
                }
            }
            return new KeyTooltip(output.what(), lines);
        }

        private ITextComponent formatMachineLocation(CraftingSupplierLocation location) {
            String dimensionName = CraftingSupplierLocator.getDimensionName(location.dimensionId());
            return GuiText.CraftingTreeLocationInDimension.text(
                location.x(), location.y(), location.z(), dimensionName);
        }
    }

}
