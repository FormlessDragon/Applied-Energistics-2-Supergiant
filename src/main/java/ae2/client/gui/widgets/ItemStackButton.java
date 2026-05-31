package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ItemStackButton extends IconButton {

    private final Supplier<ItemStack> itemSupplier;

    public ItemStackButton(ItemStack item, ITextComponent message, Runnable onPress) {
        this(() -> item, message, onPress);
    }

    public ItemStackButton(Supplier<ItemStack> itemSupplier, ITextComponent message, Runnable onPress) {
        super(onPress);
        this.itemSupplier = itemSupplier;
        setMessage(message);
    }

    @Nullable
    @Override
    protected Icon getIcon() {
        return null;
    }

    @Override
    protected ItemStack getItemStackOverlay() {
        ItemStack stack = this.itemSupplier.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
