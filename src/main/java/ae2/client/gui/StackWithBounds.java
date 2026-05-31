package ae2.client.gui;

import ae2.api.stacks.GenericStack;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;

public record StackWithBounds(GenericStack stack, Rectangle bounds) {
    @Nullable
    public static StackWithBounds fromSlot(AEBaseGui<?> screen, Slot slot) {
        ItemStack item = slot.getStack();
        GenericStack stack = GenericStack.unwrapItemStack(item);
        if (stack != null) {
            return new StackWithBounds(
                stack,
                new Rectangle(screen.getGuiLeft() + slot.xPos, screen.getGuiTop() + slot.yPos, 16, 16));
        }
        return null;
    }
}
