package ae2.client.gui.widgets;

import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class TooltipButton extends AE2Button implements ITooltip {
    private Supplier<List<ITextComponent>> tooltipSupplier;

    public TooltipButton(int x, int y, int width, int height, ITextComponent component,
                         Supplier<List<ITextComponent>> tooltipSupplier, Runnable onPress) {
        super(x, y, width, height, component, onPress);
        this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier);
    }

    public TooltipButton(int x, int y, int width, int height, ITextComponent component,
                         List<ITextComponent> tooltip, Runnable onPress) {
        this(x, y, width, height, component, () -> tooltip, onPress);
    }

    public TooltipButton(int x, int y, int width, int height, ITextComponent component,
                         ITextComponent tooltip, Runnable onPress) {
        this(x, y, width, height, component, List.of(tooltip), onPress);
    }

    public TooltipButton(ITextComponent component, Supplier<List<ITextComponent>> tooltipSupplier,
                         Runnable onPress) {
        this(0, 0, 0, 0, component, tooltipSupplier, onPress);
    }

    public TooltipButton(ITextComponent component, List<ITextComponent> tooltip, Runnable onPress) {
        this(component, () -> tooltip, onPress);
    }

    public TooltipButton(ITextComponent component, ITextComponent tooltip, Runnable onPress) {
        this(component, List.of(tooltip), onPress);
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
