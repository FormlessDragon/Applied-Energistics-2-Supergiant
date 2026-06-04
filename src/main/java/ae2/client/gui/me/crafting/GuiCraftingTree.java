package ae2.client.gui.me.crafting;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.Blitter;
import ae2.container.implementations.ContainerCraftingTree;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchCraftingTreePacket;
import ae2.integration.data.LiteCraftTreeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GuiCraftingTree extends AEBaseGui<ContainerCraftingTree> {
    private static final DateTimeFormatter SCREENSHOT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String BACKGROUND_TEXTURE = "guis/crafting_tree.png";
    private static final int BACKGROUND_TEXTURE_WIDTH = 256;
    private static final int BACKGROUND_TEXTURE_HEIGHT = 256;
    private static final int BACKGROUND_LEFT_BORDER = 7;
    private static final int BACKGROUND_RIGHT_BORDER = 7;
    private static final int BACKGROUND_TOP_BORDER = 25;
    private static final int BACKGROUND_BOTTOM_BORDER = 9;
    private static final double BACKGROUND_MIN_SCREEN_WIDTH_FACTOR = 1.5;
    private static final double BACKGROUND_MIN_SCREEN_HEIGHT_FACTOR = 1.5;

    private static final BackgroundSize[] BACKGROUNDS = {
        new BackgroundSize(256, 256),
        new BackgroundSize(320, 256),
        new BackgroundSize(384, 320),
        new BackgroundSize(512, 320),
        new BackgroundSize(640, 384)
    };

    private final CraftingTreeWidget tree = new CraftingTreeWidget();
    private final CraftingTreeButton screenshot;
    private final CraftingTreeButton missingOnly;
    private final CraftingTreeButton back;
    private BackgroundSize background;

    public GuiCraftingTree(ContainerCraftingTree container, InventoryPlayer playerInventory) {
        super(container, playerInventory);
        this.background = getLargestBackground(width, height);
        this.xSize = background.width();
        this.ySize = background.height();

        widgets.add("tree", tree);
        this.screenshot = new CraftingTreeButton(Icon.CRAFTING_TREE_SCREENSHOT,
            GuiText.CraftingTreeScreenshot.text(),
            this::saveScreenshot);
        widgets.add("screenshot", screenshot);
        this.missingOnly = new CraftingTreeButton(Icon.CRAFTING_TREE_BRANCHES_FAILED,
            GuiText.CraftingTreeMissingOnly.text(),
            () -> setMissingOnly(!tree.isMissingOnly()));
        widgets.add("missingOnly", missingOnly);
        this.back = new CraftingTreeButton(Icon.BACK,
            GuiText.CraftingTreeBack.text(),
            () -> InitNetwork.sendToServer(new SwitchCraftingTreePacket()));
        widgets.add("back", back);
    }

    private static ITextComponent savedMessage(Path file) {
        ITextComponent message = PlayerMessages.CraftingTreeScreenshotSaved.text();
        TextComponentString path = new TextComponentString(file.toString());
        path.getStyle()
            .setUnderlined(true)
            .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getParent().toString()));
        message.appendSibling(path);
        return message;
    }

    private static ITextComponent error(ITextComponent message) {
        ITextComponent copy = message.createCopy();
        copy.getStyle().setColor(TextFormatting.RED);
        return copy;
    }

    private static BackgroundSize getLargestBackground(int screenWidth, int screenHeight) {
        for (int i = BACKGROUNDS.length - 1; i >= 0; i--) {
            BackgroundSize background = BACKGROUNDS[i];
            if (screenWidth >= background.width() * BACKGROUND_MIN_SCREEN_WIDTH_FACTOR
                && screenHeight >= background.height() * BACKGROUND_MIN_SCREEN_HEIGHT_FACTOR) {
                return background;
            }
        }
        return BACKGROUNDS[0];
    }

    private static void drawBackgroundTexture(int x, int y, int width, int height) {
        int sourceCenterWidth = BACKGROUND_TEXTURE_WIDTH - BACKGROUND_LEFT_BORDER - BACKGROUND_RIGHT_BORDER;
        int sourceCenterHeight = BACKGROUND_TEXTURE_HEIGHT - BACKGROUND_TOP_BORDER - BACKGROUND_BOTTOM_BORDER;
        int targetCenterWidth = width - BACKGROUND_LEFT_BORDER - BACKGROUND_RIGHT_BORDER;
        int targetCenterHeight = height - BACKGROUND_TOP_BORDER - BACKGROUND_BOTTOM_BORDER;
        int sourceRightX = BACKGROUND_TEXTURE_WIDTH - BACKGROUND_RIGHT_BORDER;
        int sourceBottomY = BACKGROUND_TEXTURE_HEIGHT - BACKGROUND_BOTTOM_BORDER;
        int targetRightX = x + width - BACKGROUND_RIGHT_BORDER;
        int targetBottomY = y + height - BACKGROUND_BOTTOM_BORDER;

        drawBackgroundPart(0, 0, BACKGROUND_LEFT_BORDER, BACKGROUND_TOP_BORDER,
            x, y, BACKGROUND_LEFT_BORDER, BACKGROUND_TOP_BORDER);
        drawBackgroundPart(BACKGROUND_LEFT_BORDER, 0, sourceCenterWidth, BACKGROUND_TOP_BORDER,
            x + BACKGROUND_LEFT_BORDER, y, targetCenterWidth, BACKGROUND_TOP_BORDER);
        drawBackgroundPart(sourceRightX, 0, BACKGROUND_RIGHT_BORDER, BACKGROUND_TOP_BORDER,
            targetRightX, y, BACKGROUND_RIGHT_BORDER, BACKGROUND_TOP_BORDER);

        drawBackgroundPart(0, BACKGROUND_TOP_BORDER, BACKGROUND_LEFT_BORDER, sourceCenterHeight,
            x, y + BACKGROUND_TOP_BORDER, BACKGROUND_LEFT_BORDER, targetCenterHeight);
        drawBackgroundPart(BACKGROUND_LEFT_BORDER, BACKGROUND_TOP_BORDER, sourceCenterWidth, sourceCenterHeight,
            x + BACKGROUND_LEFT_BORDER, y + BACKGROUND_TOP_BORDER, targetCenterWidth, targetCenterHeight);
        drawBackgroundPart(sourceRightX, BACKGROUND_TOP_BORDER, BACKGROUND_RIGHT_BORDER, sourceCenterHeight,
            targetRightX, y + BACKGROUND_TOP_BORDER, BACKGROUND_RIGHT_BORDER, targetCenterHeight);

        drawBackgroundPart(0, sourceBottomY, BACKGROUND_LEFT_BORDER, BACKGROUND_BOTTOM_BORDER,
            x, targetBottomY, BACKGROUND_LEFT_BORDER, BACKGROUND_BOTTOM_BORDER);
        drawBackgroundPart(BACKGROUND_LEFT_BORDER, sourceBottomY, sourceCenterWidth, BACKGROUND_BOTTOM_BORDER,
            x + BACKGROUND_LEFT_BORDER, targetBottomY, targetCenterWidth, BACKGROUND_BOTTOM_BORDER);
        drawBackgroundPart(sourceRightX, sourceBottomY, BACKGROUND_RIGHT_BORDER, BACKGROUND_BOTTOM_BORDER,
            targetRightX, targetBottomY, BACKGROUND_RIGHT_BORDER, BACKGROUND_BOTTOM_BORDER);
    }

    private static void drawBackgroundPart(int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                           int targetX, int targetY, int targetWidth, int targetHeight) {
        for (int y = 0; y < targetHeight; y += sourceHeight) {
            int height = Math.min(sourceHeight, targetHeight - y);
            for (int x = 0; x < targetWidth; x += sourceWidth) {
                int width = Math.min(sourceWidth, targetWidth - x);
                Blitter.texture(BACKGROUND_TEXTURE, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT)
                       .src(sourceX, sourceY, width, height)
                       .dest(targetX + x, targetY + y, width, height)
                       .blit();
            }
        }
    }

    @Override
    public void initGui() {
        this.background = getLargestBackground(width, height);
        this.xSize = background.width();
        this.ySize = background.height();
        super.initGui();
        layoutWidgets();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawBackgroundTexture(offsetX, offsetY, background.width(), background.height());
        fontRenderer.drawString(GuiText.CraftingTreeTitle.getLocal(), offsetX + 6, offsetY + 9, 0x404040);
    }

    public void onDataUpdate(LiteCraftTreeNode root) {
        tree.setRoot(root);
        setMissingOnly(root != null && LiteCraftTreeNode.isMissing(root));
        screenshot.enabled = tree.hasTree();
    }

    private void saveScreenshot() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            return;
        }
        if (!tree.hasTree()) {
            minecraft.player.sendMessage(error(PlayerMessages.CraftingTreeScreenshotNoData.text()));
            return;
        }

        try {
            Path directory = minecraft.gameDir.toPath().toAbsolutePath().normalize()
                                              .resolve("ae2")
                                              .resolve("screenshots");
            Files.createDirectories(directory);

            Path file = findAvailableScreenshotPath(directory);
            ImageIO.write(tree.createNodeAreaScreenshot(), "png", file.toFile());
            minecraft.player.sendMessage(savedMessage(file));
        } catch (Exception e) {
            AELog.error(e, "Failed to save crafting tree screenshot");
            minecraft.player.sendMessage(error(PlayerMessages.CraftingTreeScreenshotFailed.text(e.getMessage())));
        }
    }

    private Path findAvailableScreenshotPath(Path directory) {
        String suffix = tree.isMissingOnly() ? "_missing" : "";
        String baseName = "crafting_tree_" + SCREENSHOT_TIMESTAMP_FORMATTER.format(LocalDateTime.now()) + suffix;
        Path file = directory.resolve(baseName + ".png");
        int counter = 1;
        while (Files.exists(file)) {
            file = directory.resolve(baseName + "_" + counter + ".png");
            counter++;
        }
        return file;
    }

    private void setMissingOnly(boolean missingOnly) {
        tree.setMissingOnly(missingOnly);
        boolean active = tree.isMissingOnly();
        this.missingOnly.setTooltip(active
            ? GuiText.CraftingTreeDefault.text()
            : GuiText.CraftingTreeMissingOnly.text());
        this.missingOnly.setIcon(active
            ? Icon.CRAFTING_TREE_BRANCHES_FAILED
            : Icon.CRAFTING_TREE_BRANCHES_ALL);
        this.missingOnly.enabled = tree.hasMissingItems();
        this.screenshot.enabled = tree.hasTree();
    }

    private void layoutWidgets() {
        this.tree.setPosition(new ae2.client.Point(7, 25));
        this.tree.setSize(background.internalWidth(), background.internalHeight());
        moveButton(screenshot, xSize - 86);
        moveButton(missingOnly, xSize - 58);
        moveButton(back, xSize - 30);
    }

    private void moveButton(GuiButton target, int x) {
        for (GuiButton button : buttonList) {
            if (button == target) {
                button.x = guiLeft + x;
                button.y = guiTop + 3;
                return;
            }
        }
    }

    private record BackgroundSize(int width, int height) {
        private int internalWidth() {
            return width - 14;
        }

        private int internalHeight() {
            return height - 34;
        }
    }
}
