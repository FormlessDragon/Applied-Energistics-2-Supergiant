package ae2.client.gui.widgets;

import ae2.api.orientation.RelativeSide;
import ae2.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class OutputSideSelectionButton extends IconButton {
    private final RelativeSide side;
    private final Supplier<ItemStack> stackSupplier;
    private List<ITextComponent> tooltip = Collections.emptyList();
    private boolean allowed = true;

    public OutputSideSelectionButton(RelativeSide side, Supplier<ItemStack> stackSupplier, Runnable onPress) {
        super(onPress);
        this.side = side;
        this.stackSupplier = stackSupplier;
    }

    public RelativeSide getSide() {
        return this.side;
    }

    public boolean isAllowed() {
        return this.allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    protected ItemStack getItemStackOverlay() {
        var stack = this.stackSupplier.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return this.tooltip;
    }

    public void setTooltipMessage(List<ITextComponent> tooltip) {
        this.tooltip = tooltip;
        if (!tooltip.isEmpty()) {
            setMessage(tooltip.getFirst());
        }
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        return this.allowed && super.mousePressed(minecraft, mouseX, mouseY);
    }
}
