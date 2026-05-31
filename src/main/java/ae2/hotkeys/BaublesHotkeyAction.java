package ae2.hotkeys;

import ae2.api.features.HotkeyAction;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.integration.modules.baubles.BaublesIntegration;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public record BaublesHotkeyAction(Predicate<ItemStack> locatable, InventoryHotkeyAction.Opener opener)
    implements HotkeyAction {

    public BaublesHotkeyAction(Item item, InventoryHotkeyAction.Opener opener) {
        this(stack -> stack.getItem() == item, opener);
    }

    @Override
    public boolean run(EntityPlayerMP player) {
        for (int i = 0; i < BaublesIntegration.getSlots(player); i++) {
            if (this.locatable.test(BaublesIntegration.getStackInSlot(player, i))) {
                if (this.opener.open(player, GuiHostLocators.forBaubleSlot(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
