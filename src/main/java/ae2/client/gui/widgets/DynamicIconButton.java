package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Icon button whose icon and tooltip are supplied by the hosting GUI state.
 */
public class DynamicIconButton extends IconButton {
    private final Supplier<Icon> iconSupplier;
    private Supplier<List<ITextComponent>> tooltipSupplier;

    public DynamicIconButton(Supplier<Icon> iconSupplier, ITextComponent message, Runnable onPress) {
        this(iconSupplier, message, () -> List.of(message), onPress);
    }

    public DynamicIconButton(Supplier<Icon> iconSupplier, ITextComponent message,
                             Supplier<List<ITextComponent>> tooltipSupplier, Runnable onPress) {
        super(onPress);
        this.iconSupplier = Objects.requireNonNull(iconSupplier);
        this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier);
        setMessage(message);
    }

    public void setTooltipSupplier(Supplier<List<ITextComponent>> tooltipSupplier) {
        this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier);
    }

    @Override
    protected Icon getIcon() {
        return this.iconSupplier.get();
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        return this.tooltipSupplier.get();
    }

    @Override
    public Rectangle getTooltipArea() {
        return new Rectangle(this.x, this.y, this.width, this.height);
    }
}
