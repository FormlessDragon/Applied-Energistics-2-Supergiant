package ae2.core.gui.locator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public final class StackItemLocator implements ItemGuiHostLocator {
    private final ItemStack stack;

    public StackItemLocator(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public ItemStack locateItem(EntityPlayer player) {
        return stack;
    }

}
