package ae2.items.tools.powered;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.config.Actionable;
import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class PoweredContainerItem extends AEBasePoweredItem implements IGuiItem {
    protected PoweredContainerItem(double powerCapacity) {
        super(powerCapacity);
    }

    protected long insert(EntityPlayer player, ItemStack stack, AEKey what, @Nullable AEKeyType allowed, long amount,
                          Actionable mode) {
        if (allowed != null && what.getType() != allowed) {
            return 0;
        }

        var host = getGuiHost(player, GuiHostLocators.forStack(stack), null);
        if (host == null) {
            return 0;
        }

        return host.insert(player, what, amount, mode);
    }

    @Override
    public boolean onStackedOnOther(ItemStack containerStack, Slot slot, EntityPlayer player) {
        if (!slot.canTakeStack(player)) {
            return false;
        }

        var other = slot.getStack();
        if (other.isEmpty()) {
            return true;
        }

        tryInsertFromPlayerOwnedItem(player, containerStack, other);
        return true;
    }

    @Override
    public boolean onOtherStackedOnMe(ItemStack containerStack, ItemStack otherStack, Slot slot,
                                      EntityPlayer player) {
        if (!slot.canTakeStack(player)) {
            return false;
        }

        if (otherStack.isEmpty()) {
            return false;
        }

        tryInsertFromPlayerOwnedItem(player, containerStack, otherStack);
        return true;
    }

    protected void tryInsertFromPlayerOwnedItem(EntityPlayer player, ItemStack cellStack, ItemStack otherStack) {
        for (var keyType : ContainerItemStrategies.getSupportedKeyTypes()) {
            if (tryInsertFromPlayerOwnedItem(player, cellStack, otherStack, keyType)) {
                return;
            }
        }

        var key = AEItemKey.of(otherStack);
        if (key == null) {
            return;
        }
        var inserted = (int) insert(player, cellStack, key, AEKeyType.items(), otherStack.getCount(),
            Actionable.MODULATE);
        if (inserted > 0) {
            otherStack.shrink(inserted);
        }
    }

    protected boolean tryInsertFromPlayerOwnedItem(EntityPlayer player, ItemStack cellStack, ItemStack otherStack,
                                                   AEKeyType keyType) {
        var context = ContainerItemStrategies.findOwnedItemContext(keyType, player, otherStack);
        if (context != null) {
            var containedStack = context.getExtractableContent();
            if (containedStack != null) {
                if (insert(player, cellStack, containedStack.what(), keyType, containedStack.amount(),
                    Actionable.SIMULATE) == containedStack.amount()) {
                    var extracted = context.extract(containedStack.what(), containedStack.amount(),
                        Actionable.MODULATE);
                    if (extracted > 0) {
                        insert(player, cellStack, containedStack.what(), keyType, extracted, Actionable.MODULATE);
                        context.playEmptySound(player, containedStack.what());
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
