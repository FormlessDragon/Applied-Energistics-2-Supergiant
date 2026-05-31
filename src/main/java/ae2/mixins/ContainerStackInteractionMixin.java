package ae2.mixins;

import ae2.items.AEBaseItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Container.class)
public abstract class ContainerStackInteractionMixin {
    @Shadow
    public List<Slot> inventorySlots;

    @Shadow
    public abstract void detectAndSendChanges();

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void handleStackedContainerItems(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player,
                                             CallbackInfoReturnable<ItemStack> cir) {
        if (clickTypeIn != ClickType.PICKUP || dragType != 1 || slotId < 0 || slotId >= this.inventorySlots.size()) {
            return;
        }

        Slot slot = this.inventorySlots.get(slotId);
        if (slot == null) {
            return;
        }

        if (ae2_handleCarriedStack(player, slot) || ae2_handleSlotStack(player, slot)) {
            detectAndSendChanges();
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Unique
    private boolean ae2_handleCarriedStack(EntityPlayer player, Slot slot) {
        ItemStack carried = player.inventory.getItemStack();
        if (carried.isEmpty()) {
            return false;
        }

        var carriedItem = carried.getItem();
        if (carriedItem instanceof AEBaseItem aeBaseItem) {
            return aeBaseItem.onStackedOnOther(carried, slot, player);
        }
        return false;
    }

    @Unique
    private boolean ae2_handleSlotStack(EntityPlayer player, Slot slot) {
        ItemStack carried = player.inventory.getItemStack();
        ItemStack slotStack = slot.getStack();
        if (slotStack.isEmpty()) {
            return false;
        }

        var slotItem = slotStack.getItem();
        if (slotItem instanceof AEBaseItem aeBaseItem) {
            return aeBaseItem.onOtherStackedOnMe(slotStack, carried, slot, player);
        }
        return false;
    }
}
