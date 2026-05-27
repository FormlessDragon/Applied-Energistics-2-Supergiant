package appeng.client.gui.me.crafting;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.style.Blitter;
import appeng.container.implementations.ContainerCraftingTree;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.SwitchCraftingTreePacket;
import appeng.integration.data.LiteCraftTreeNode;
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
        this.screenshot = new CraftingTreeButton(0, 232,
            GuiText.CraftingTreeScreenshot.text(),
            this::saveScreenshot);
        widgets.add("screenshot", screenshot);
        this.missingOnly = new CraftingTreeButton(60, 160,
            GuiText.CraftingTreeMissingOnly.text(),
            () -> setMissingOnly(!tree.isMissingOnly()));
        widgets.add("missingOnly", missingOnly);
        this.back = new CraftingTreeButton(100, 232,
            GuiText.CraftingTreeBack.text(),
            () -> InitNetwork.sendToServer(new SwitchCraftingTreePacket()));
        widgets.add("back", back);
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
        Blitter.texture(background.texture(), background.width(), background.height())
                .src(0, 0, background.width(), background.height())
                .dest(offsetX, offsetY, background.width(), background.height())
                .blit();
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

    private void setMissingOnly(boolean missingOnly) {
        tree.setMissingOnly(missingOnly);
        boolean active = tree.isMissingOnly();
        this.missingOnly.setTooltip(active
            ? GuiText.CraftingTreeDefault.text()
            : GuiText.CraftingTreeMissingOnly.text());
        this.missingOnly.setActive(active);
        this.missingOnly.enabled = tree.hasMissingItems();
        this.screenshot.enabled = tree.hasTree();
    }

    private void layoutWidgets() {
        this.tree.setPosition(new appeng.client.Point(7, 25));
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
                button.width = 24;
                button.height = 20;
                return;
            }
        }
    }

    private static BackgroundSize getLargestBackground(int screenWidth, int screenHeight) {
        for (int i = BACKGROUNDS.length - 1; i >= 0; i--) {
            BackgroundSize background = BACKGROUNDS[i];
            if (screenWidth >= background.width() * 1.25 && screenHeight >= background.height()) {
                return background;
            }
        }
        return BACKGROUNDS[0];
    }

    private record BackgroundSize(int width, int height) {
        private String texture() {
            return "guis/ctl/guicraftingtree_" + width + "x" + height + ".png";
        }

        private int internalWidth() {
            return width - 14;
        }

        private int internalHeight() {
            return height - 34;
        }
    }
}
