package ae2.client.gui.me.common;

import ae2.core.localization.GeneralText;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ClientReadOnlySlot extends Slot {
    private static final InventoryBasic EMPTY_INVENTORY = new InventoryBasic(
        GeneralText.ClientReadOnly.getTranslationKey(), false, 0);

    public ClientReadOnlySlot(int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, 0, xPosition, yPosition);
    }

    public ClientReadOnlySlot() {
        this(0, 0);
    }

    @Override
    public final boolean isItemValid(ItemStack stack) {
        return false;
    }

    @Override
    public final void putStack(ItemStack stack) {
    }

    @Override
    public final int getSlotStackLimit() {
        return 0;
    }

    @Override
    public final ItemStack decrStackSize(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public final boolean canTakeStack(EntityPlayer player) {
        return false;
    }
}

