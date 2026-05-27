package appeng.client.gui;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.IStackTooltipDataProvider;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.core.localization.GuiText;
import appeng.integration.Integrations;
import appeng.items.storage.StorageCellTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

@SideOnly(Side.CLIENT)
public class StackTooltipRenderer {
    public static final StackTooltipRenderer INSTANCE = new StackTooltipRenderer();
    private static final float TOOLTIP_IMAGE_Z_LEVEL = 600.0F;
    private static final float TOOLTIP_CONTENT_TEXT_Z_LEVEL = 700.0F;

    private static final char SECTION_SIGN = 167;
    private static final String RESERVED_LINE_PREFIX = new String(
        new char[]{SECTION_SIGN, '0', SECTION_SIGN, 'r', SECTION_SIGN, '0', SECTION_SIGN, 'r'});
    private static final int ROW_HEIGHT = 17;
    private static final int SLOT_STEP = 17;
    private static final int ELLIPSIS_WIDTH = 10;
    private static final int UPGRADES_LABEL_GAP = 2;
    private ItemStack lastTooltipStack = ItemStack.EMPTY;
    private int lastReservedLineStart = -1;

    private StackTooltipRenderer() {
    }

    private static int getHeight(StorageCellTooltipComponent data) {
        return data.getRowCount() * ROW_HEIGHT;
    }

    private static int getWidth(StorageCellTooltipComponent data, FontRenderer font) {
        int width = 0;

        if (!data.content().isEmpty()) {
            int contentWidth = data.content().size() * SLOT_STEP;
            if (data.hasMoreContent()) {
                contentWidth += ELLIPSIS_WIDTH;
            }
            width = Math.max(width, contentWidth);
        }

        if (!data.upgrades().isEmpty()) {
            int upgradesWidth = font.getStringWidth(getUpgradesLabel())
                + UPGRADES_LABEL_GAP
                + data.upgrades().size() * SLOT_STEP;
            width = Math.max(width, upgradesWidth);
        }

        return width;
    }

    private static int getReservedLineCount(StorageCellTooltipComponent data) {
        return Math.max(data.getRowCount(), (getHeight(data) + 9) / 10);
    }

    private static String createSpacer(FontRenderer font, int width) {
        int spaceWidth = Math.max(1, font.getStringWidth(" "));
        int chars = Math.max(1, (width + spaceWidth - 1) / spaceWidth);
        return " ".repeat(chars);
    }

    private static String createReservedLine(FontRenderer font, int width) {
        return RESERVED_LINE_PREFIX + createSpacer(font, width);
    }

    private static int findReservedLineStart(List<String> tooltipLines) {
        for (int i = 0; i < tooltipLines.size(); i++) {
            if (tooltipLines.get(i).startsWith(RESERVED_LINE_PREFIX)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isReservedTooltipLine(String line) {
        return line != null && line.startsWith(RESERVED_LINE_PREFIX);
    }

    private static void renderContentRow(Minecraft minecraft, FontRenderer font, int x, int y,
                                         StorageCellTooltipComponent data) {
        int xOffset = 0;
        for (var stack : data.content()) {
            AEKeyRendering.drawInGui(minecraft, x + xOffset, y, stack.what());
            xOffset += SLOT_STEP;
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0F, 0.0F, TOOLTIP_CONTENT_TEXT_Z_LEVEL - TOOLTIP_IMAGE_Z_LEVEL);
            if (data.hasMoreContent()) {
                font.drawStringWithShadow("...", x + xOffset + 2, y + 2, 0xFFFFFF);
            }

            if (data.showAmounts()) {
                xOffset = 0;
                for (var stack : data.content()) {
                    var amount = stack.what().formatAmount(stack.amount(), AmountFormat.SLOT);
                    StackSizeRenderer.renderSizeLabel(font, x + xOffset, y, amount, false);
                    xOffset += SLOT_STEP;
                }
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void renderUpgradesRow(RenderItem renderItem, FontRenderer font, int x, int y,
                                          StorageCellTooltipComponent data) {
        var label = getUpgradesLabel();
        int labelY = y + (16 - font.FONT_HEIGHT) / 2;

        int xOffset = font.getStringWidth(label) + UPGRADES_LABEL_GAP;
        for (ItemStack upgrade : data.upgrades()) {
            renderItem.renderItemAndEffectIntoGUI(upgrade, x + xOffset, y);
            renderItem.renderItemOverlayIntoGUI(font, upgrade, x + xOffset, y, null);
            xOffset += SLOT_STEP;
        }

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0F, 0.0F, TOOLTIP_CONTENT_TEXT_Z_LEVEL - TOOLTIP_IMAGE_Z_LEVEL);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            font.drawString(label, x, labelY, 0x7E7E7E);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static String getUpgradesLabel() {
        return GuiText.StorageCellTooltipUpgrades.getLocal();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void appendTooltipRows(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        this.lastTooltipStack = stack.copy();
        this.lastReservedLineStart = -1;

        var unwrapped = GenericStack.unwrapItemStack(stack);
        if (unwrapped != null) {
            event.getToolTip().clear();
            for (var line : AEKeyRendering.getTooltip(unwrapped.what())) {
                event.getToolTip().add(line.getFormattedText());
            }
            return;
        }

        if (stack.isEmpty() || !(stack.getItem() instanceof IStackTooltipDataProvider provider)) {
            Integrations.hei().appendIngredientActionTooltip(event);
            return;
        }

        var data = provider.getTooltipImage(stack).orElse(null);
        if (data == null || data.getRowCount() == 0) {
            Integrations.hei().appendIngredientActionTooltip(event);
            return;
        }

        var font = Minecraft.getMinecraft().fontRenderer;
        String spacer = createReservedLine(font, getWidth(data, font));
        int lineCount = getReservedLineCount(data);
        this.lastReservedLineStart = event.getToolTip().size();
        for (int i = 0; i < lineCount; i++) {
            event.getToolTip().add(spacer);
        }
        Integrations.hei().appendIngredientActionTooltip(event);
    }

    @SubscribeEvent
    public void drawTooltipImage(RenderTooltipEvent.PostText event) {
        GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
        if (currentScreen instanceof AEBaseGui<?> gui) {
            if (gui.isTooltipForHoveredSlot(event.getStack())) {
                return;
            }
        }

        drawTooltipImage(Minecraft.getMinecraft(), event.getFontRenderer(), event.getStack(), event.getX(), event.getY(),
            event.getHeight(), event.getLines());
    }

    public void drawTooltipImage(Minecraft minecraft, FontRenderer font, ItemStack stack, int tooltipX, int tooltipY,
                                 int tooltipHeight, List<String> tooltipLines) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IStackTooltipDataProvider provider)) {
            return;
        }

        var data = provider.getTooltipImage(stack).orElse(null);
        if (data == null || data.getRowCount() == 0) {
            return;
        }

        int reservedLineStart = tooltipLines != null ? findReservedLineStart(tooltipLines) : -1;
        if (reservedLineStart < 0 && ItemStack.areItemStacksEqual(this.lastTooltipStack, stack)) {
            reservedLineStart = this.lastReservedLineStart;
        }
        int y = reservedLineStart >= 0
            ? tooltipY + reservedLineStart * 10 + 2
            : tooltipY + tooltipHeight - getReservedLineCount(data) * 10 + 2;
        var renderItem = minecraft.getRenderItem();
        float previousRenderItemZLevel = renderItem.zLevel;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(0.0F, 0.0F, TOOLTIP_IMAGE_Z_LEVEL);
            GlStateManager.enableBlend();
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            renderItem.zLevel = 0.0F;

            if (!data.content().isEmpty()) {
                renderContentRow(minecraft, font, tooltipX, y, data);
                y += ROW_HEIGHT;
            }
            if (!data.upgrades().isEmpty()) {
                renderUpgradesRow(renderItem, font, tooltipX, y, data);
            }
        } finally {
            renderItem.zLevel = previousRenderItemZLevel;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}
