package ae2.integration.modules.hei.target;

import ae2.api.stacks.GenericStack;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.me.common.RepoSlot;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;

public record ManualPinTarget<T>(GuiMEStorage<?> gui, RepoSlot slot, int fallbackMouseButton,
                                 @Nullable GenericStack leftClickStack,
                                 @Nullable GenericStack rightClickStack) implements Target<T> {
    @Override
    public Rectangle getArea() {
        return new Rectangle(this.gui.getGuiLeft() + this.slot.xPos, this.gui.getGuiTop() + this.slot.yPos, 16, 16);
    }

    @Override
    public void accept(@NotNull T ingredient) {
        int mouseButton = HeiGhostTargetSupport.getActiveMouseButton();
        if (mouseButton < 0) {
            mouseButton = this.fallbackMouseButton;
        }
        GenericStack stack = stackForMouseButton(mouseButton);
        if (stack == null && mouseButton >= 0) {
            stack = HeiGhostTargetSupport.toManualPinStack(ingredient, mouseButton);
        }
        if (stack != null) {
            this.gui.acceptManualPinGhost(stack.what(), this.slot);
        }
    }

    @Nullable
    private GenericStack stackForMouseButton(int mouseButton) {
        if (mouseButton == 1) {
            return this.rightClickStack;
        }
        if (mouseButton == 0) {
            return this.leftClickStack;
        }
        return this.leftClickStack != null ? this.leftClickStack : this.rightClickStack;
    }
}
