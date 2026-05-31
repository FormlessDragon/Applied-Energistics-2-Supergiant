package ae2.client.gui.me.common;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;
import java.util.List;

/**
 * A Minecraft toast for a finished crafting job.
 */
public class FinishedJobToast implements IToast {
    private static final long TIME_VISIBLE = 2500;
    private static final int TITLE_COLOR = Color.blue.getRGB();
    private static final int TEXT_COLOR = Color.green.getRGB();

    private final AEKey what;
    private final List<String> lines;

    public FinishedJobToast(AEKey what, long amount) {
        this.what = what;

        var minecraft = Minecraft.getMinecraft();
        var formattedAmount = what.getType().formatAmount(amount, AmountFormat.SLOT);
        var text = GuiText.ToastCraftingJobFinishedText.getLocal(formattedAmount,
            AEKeyRendering.getDisplayName(what).getFormattedText());
        this.lines = minecraft.fontRenderer.listFormattedStringToWidth(text, 125);
    }

    @Override
    public Visibility draw(GuiToast toastGui, long delta) {
        var minecraft = toastGui.getMinecraft();

        minecraft.getTextureManager().bindTexture(TEXTURE_TOASTS);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
        toastGui.drawTexturedModalRect(0, 0, 0, 0, 160, 32);
        minecraft.fontRenderer.drawString(GuiText.ToastCraftingJobFinishedTitle.getLocal(), 30, 7,
            TITLE_COLOR);

        var lineY = 18;
        for (String line : this.lines) {
            minecraft.fontRenderer.drawString(line, 30, lineY, TEXT_COLOR);
            lineY += minecraft.fontRenderer.FONT_HEIGHT;
        }

        AEKeyRendering.drawInGui(minecraft, 8, 8, this.what);

        return delta >= TIME_VISIBLE ? Visibility.HIDE : Visibility.SHOW;
    }
}
