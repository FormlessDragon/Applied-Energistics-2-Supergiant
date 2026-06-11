package ae2.client.gui.implementations;

import ae2.api.client.AEKeyRendering;
import ae2.api.config.LockCraftingMode;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.client.Point;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Tooltip;
import ae2.container.implementations.ContainerPatternProvider;
import ae2.core.localization.GuiText;
import ae2.core.localization.InGameTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;

public class PatternProviderLockReason implements ICompositeWidget {
    private final GuiPatternProvider screen;
    private boolean visible;
    private int x;
    private int y;

    public PatternProviderLockReason(GuiPatternProvider screen) {
        this.screen = screen;
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.x();
        this.y = position.y();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this.x, this.y, 126, 16);
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
        ContainerPatternProvider container = this.screen.getContainer();
        Icon icon;
        String text;
        int color;

        if (container.getCraftingLockedReason() == LockCraftingMode.NONE) {
            icon = Icon.UNLOCKED;
            text = GuiText.CraftingLockIsUnlocked.getLocal();
            color = 0x7DA9D2;
        } else {
            icon = Icon.LOCKED;
            text = GuiText.CraftingLockIsLocked.getLocal();
            color = 0xC1424B;
        }

        icon.getBlitter().dest(this.x, this.y).blit();
        Minecraft.getMinecraft().fontRenderer.drawString(text, this.x + 15, this.y + 5, color);
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        ContainerPatternProvider container = this.screen.getContainer();
        LockCraftingMode reason = container.getCraftingLockedReason();
        return switch (reason) {
            case LOCK_UNTIL_PULSE -> new Tooltip(InGameTooltip.CraftingLockedUntilPulse.text());
            case LOCK_WHILE_HIGH -> new Tooltip(InGameTooltip.CraftingLockedByRedstoneSignal.text());
            case LOCK_WHILE_LOW -> new Tooltip(InGameTooltip.CraftingLockedByLackOfRedstoneSignal.text());
            case LOCK_UNTIL_RESULT -> {
                GenericStack unlockStack = container.getUnlockStack();
                ITextComponent stackName = unlockStack != null
                    ? AEKeyRendering.getDisplayName(unlockStack.what())
                    : GuiText.Error.text();
                ITextComponent stackAmount = unlockStack != null
                    ? new TextComponentString(unlockStack.what().formatAmount(unlockStack.amount(), AmountFormat.FULL))
                    : GuiText.Error.text();
                yield new Tooltip(InGameTooltip.CraftingLockedUntilResult.text(stackName, stackAmount));
            }
            case NONE -> null;
        };
    }
}

