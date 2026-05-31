package ae2.api.implementations.guiobjects;

import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableItem;
import ae2.api.upgrades.UpgradeInventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.function.Supplier;

public final class DelegateItemUpgradeInventory implements IUpgradeInventory {
    private final Supplier<ItemStack> stackSupplier;

    public DelegateItemUpgradeInventory(Supplier<ItemStack> stackSupplier) {
        this.stackSupplier = stackSupplier;
    }

    private static IUpgradeInventory inventoryFromStack(ItemStack stack) {
        if (stack.getItem() instanceof IUpgradeableItem upgradeableItem) {
            return upgradeableItem.getUpgrades(stack);
        }
        return UpgradeInventories.empty();
    }

    @Override
    public Item getUpgradableItem() {
        return getDelegate().getUpgradableItem();
    }

    @Override
    public int getInstalledUpgrades(Item u) {
        return getDelegate().getInstalledUpgrades(u);
    }

    @Override
    public int getMaxInstalled(Item u) {
        return getDelegate().getMaxInstalled(u);
    }

    @Override
    public void readFromNBT(NBTTagCompound data, String subtag) {
        getDelegate().readFromNBT(data, subtag);
    }

    @Override
    public void writeToNBT(NBTTagCompound data, String subtag) {
        getDelegate().writeToNBT(data, subtag);
    }

    @Override
    public int size() {
        return getDelegate().size();
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return getDelegate().getStackInSlot(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        getDelegate().setItemDirect(slotIndex, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return getDelegate().getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return getDelegate().isItemValid(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return getDelegate().insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return getDelegate().extractItem(slot, amount, simulate);
    }

    private IUpgradeInventory getDelegate() {
        return inventoryFromStack(this.stackSupplier.get());
    }
}
