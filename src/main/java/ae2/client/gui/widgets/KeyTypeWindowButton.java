package ae2.client.gui.widgets;

import ae2.api.stacks.AEKeyType;
import ae2.client.gui.Icon;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Toolbar button for screens that expose a key type selection popup/window.
 */
public class KeyTypeWindowButton extends IconButton {
    private final ITextComponent title;
    private final Supplier<? extends Iterable<AEKeyType>> enabledTypesSupplier;
    private final Runnable openWindow;
    private final Runnable cycleSelection;

    public KeyTypeWindowButton(ITextComponent title, Supplier<? extends Iterable<AEKeyType>> enabledTypesSupplier,
                               Runnable openWindow, Runnable cycleSelection) {
        super(() -> {
        });
        this.title = Objects.requireNonNull(title);
        this.enabledTypesSupplier = Objects.requireNonNull(enabledTypesSupplier);
        this.openWindow = Objects.requireNonNull(openWindow);
        this.cycleSelection = Objects.requireNonNull(cycleSelection);
        setMessage(title);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        boolean releasedInside = this.enabled && this.visible
            && mouseX >= this.x
            && mouseY >= this.y
            && mouseX < this.x + this.width
            && mouseY < this.y + this.height;
        super.mouseReleased(mouseX, mouseY);
        if (!releasedInside) {
            return;
        }
        if (GuiScreen.isShiftKeyDown()) {
            this.cycleSelection.run();
        } else {
            this.openWindow.run();
        }
    }

    @Override
    public @NonNull List<ITextComponent> getTooltipMessage() {
        StringJoiner joiner = new StringJoiner(", ");
        for (AEKeyType keyType : this.enabledTypesSupplier.get()) {
            joiner.add(keyType.getDescription().getFormattedText());
        }
        return List.of(this.title, new TextComponentString(joiner.toString()));
    }

    @Override
    protected Icon getIcon() {
        return Icon.TYPE_FILTER_ALL;
    }
}
