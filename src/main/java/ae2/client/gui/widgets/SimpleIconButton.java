package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Fixed icon button with a normal AE toolbar background and tooltip.
 */
public class SimpleIconButton extends DynamicIconButton {
    public SimpleIconButton(Icon icon, ITextComponent message, Runnable onPress) {
        super(() -> icon, message, onPress);
    }

    public SimpleIconButton(Icon icon, ITextComponent message, List<ITextComponent> tooltip, Runnable onPress) {
        super(() -> icon, message, () -> tooltip, onPress);
    }

    public SimpleIconButton(Icon icon, ITextComponent message, Supplier<List<ITextComponent>> tooltipSupplier,
                            Runnable onPress) {
        super(() -> icon, message, tooltipSupplier, onPress);
    }
}
