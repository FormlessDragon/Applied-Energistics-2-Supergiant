package ae2.hotkeys;

import ae2.api.features.HotkeyAction;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

public record InventoryHotkeyAction(Predicate<ItemStack> locatable, Opener opener) implements HotkeyAction {

    public InventoryHotkeyAction(Item item, Opener opener) {
        this(stack -> stack.getItem() == item, opener);
    }

    @Override
    public boolean run(EntityPlayerMP player) {
        List<ItemStack> items = player.inventory.mainInventory;
        for (int i = 0; i < items.size(); i++) {
            if (this.locatable.test(items.get(i))) {
                if (this.opener.open(player, GuiHostLocators.forInventorySlot(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface Opener {
        boolean open(EntityPlayerMP player, ItemGuiHostLocator locator);
    }
}
