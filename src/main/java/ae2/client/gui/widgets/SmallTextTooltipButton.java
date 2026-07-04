package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Compact square text button used by dense pattern editing controls.
 */
public class SmallTextTooltipButton extends AE2Button implements ITooltip {
    private static final float TEXT_SCALE = 0.5F;

    private final Supplier<String> labelSupplier;
    private final Supplier<List<ITextComponent>> tooltipSupplier;
    private final BackgroundStyle backgroundStyle;

    public SmallTextTooltipButton(int width, int height, Supplier<String> labelSupplier,
                                  Supplier<List<ITextComponent>> tooltipSupplier, Runnable onPress) {
        this(width, height, labelSupplier, tooltipSupplier, onPress, BackgroundStyle.AE2_BUTTON);
    }

    public SmallTextTooltipButton(int width, int height, Supplier<String> labelSupplier,
                                  Supplier<List<ITextComponent>> tooltipSupplier, Runnable onPress,
                                  BackgroundStyle backgroundStyle) {
        super(0, 0, width, height, new TextComponentString(labelSupplier.get()), onPress);
        this.labelSupplier = Objects.requireNonNull(labelSupplier);
        this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier);
        this.backgroundStyle = Objects.requireNonNull(backgroundStyle);
    }

    public SmallTextTooltipButton(int width, int height, String label,
                                  Supplier<List<ITextComponent>> tooltipSupplier, Runnable onPress) {
        this(width, height, () -> label, tooltipSupplier, onPress);
    }

    public SmallTextTooltipButton(int width, int height, String label,
                                  Supplier<List<ITextComponent>> tooltipSupplier, Runnable onPress,
                                  BackgroundStyle backgroundStyle) {
        this(width, height, () -> label, tooltipSupplier, onPress, backgroundStyle);
    }

    @Override
    public ITextComponent getMessageComponent() {
        return new TextComponentString(this.labelSupplier.get());
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (this.backgroundStyle == BackgroundStyle.AE2_BUTTON) {
            try {
                super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            } finally {
                GlStateManager.disableDepth();
            }
            return;
        }

        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        Icon background = this.hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter()
                  .dest(this.x, this.y, this.width, this.height)
                  .zOffset(2)
                  .blit();

        int color = this.enabled ? 0xFFFFFFFF : 0xFF413F54;
        renderScaledText(minecraft.fontRenderer, this.labelSupplier.get(), color, this.hovered ? 1 : 0);
    }

    private void renderScaledText(FontRenderer fontRenderer, String text, int color, int yOffset) {
        float textWidth = fontRenderer.getStringWidth(text) * TEXT_SCALE;
        float textHeight = 8.0F * TEXT_SCALE;
        float textX = this.x + (this.width - textWidth) / 2.0F;
        float textY = this.y + (this.height - textHeight) / 2.0F - yOffset * 0.5F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(textX, textY, 0);
        GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        fontRenderer.drawString(text, 0, 0, color, false);
        GlStateManager.popMatrix();
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        return this.tooltipSupplier.get();
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(this.x, this.y, this.width, this.height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    public enum BackgroundStyle {
        AE2_BUTTON,
        TOOLBAR
    }
}
