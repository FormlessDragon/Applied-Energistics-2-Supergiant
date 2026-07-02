package ae2.integration.modules.hei.target;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.widgets.AETextField;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import net.minecraft.client.gui.GuiTextField;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;

public record TextFieldTarget<T>(AEBaseGui<?> gui, GuiTextField field) implements Target<T> {
    @Override
    public Rectangle getArea() {
        if (this.field instanceof AETextField aeTextField) {
            return aeTextField.getTooltipArea();
        }

        return new Rectangle(this.field.x, this.field.y, this.field.width, this.field.height);
    }

    @Override
    public void accept(@NotNull T ingredient) {
        String text = HeiGhostTargetSupport.getTextFieldInsertionText(ingredient, Mouse.getEventButton());
        if (text == null) {
            return;
        }
        if (this.field instanceof AETextField aeTextField) {
            aeTextField.setTextFromClient(text);
        } else {
            this.field.setText(text);
        }
    }
}
