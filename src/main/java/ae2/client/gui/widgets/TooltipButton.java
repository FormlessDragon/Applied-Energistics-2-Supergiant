package ae2.client.gui.widgets;

import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class TooltipButton extends AE2Button implements ITooltip {
    private Supplier<List<ITextComponent>> tooltipSupplier;

    public TooltipButton(ITextComponent component, Supplier<List<ITextComponent>> tooltipSupplier,
                         Runnable onPress) {
        super(component, onPress);
        this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier);
    }

    public TooltipButton(ITextComponent component, List<ITextComponent> tooltip, Runnable onPress) {
        this(component, () -> tooltip, onPress);
    }

    public void setTooltipSupplier(Supplier<List<ITextComponent>> tooltipSupplier) {
        this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier);
    }

    public void setTooltip(List<ITextComponent> tooltip) {
        this.tooltipSupplier = () -> tooltip;
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
}
