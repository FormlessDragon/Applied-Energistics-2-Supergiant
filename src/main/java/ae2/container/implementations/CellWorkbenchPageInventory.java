package ae2.container.implementations;

import ae2.api.behaviors.GenericStackDisplayInventory;
import ae2.api.inventories.BaseInternalInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.AEKey;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.tile.misc.TileCellWorkbench;
import ae2.util.ConfigGuiInventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class CellWorkbenchPageInventory extends BaseInternalInventory implements GenericStackDisplayInventory {
    private final TileCellWorkbench host;
    private final int slotsPerPage;
    private int page;
    private GenericStackInv cachedConfig;
    private ConfigGuiInventory cachedGuiWrapper;

    CellWorkbenchPageInventory(TileCellWorkbench host, int slotsPerPage) {
        this.host = host;
        this.slotsPerPage = slotsPerPage;
    }

    void setPage(int page) {
        this.page = page;
    }

    private InternalInventory getDelegate() {
        GenericStackInv config = this.host.getConfig();
        if (this.cachedConfig != config || this.cachedGuiWrapper == null) {
            this.cachedConfig = config;
            this.cachedGuiWrapper = config.createGuiWrapper();
        }
        return this.cachedGuiWrapper;
    }

    private int translateSlot(int slot) {
        return this.page * this.slotsPerPage + slot;
    }

    private boolean isValidTranslatedSlot(int slot) {
        int translatedSlot = translateSlot(slot);
        return slot >= 0 && slot < this.slotsPerPage && translatedSlot >= 0 && translatedSlot < getDelegate().size();
    }

    private boolean isValidTranslatedSlot(InternalInventory delegate, int slot) {
        int translatedSlot = translateSlot(slot);
        return slot >= 0 && slot < this.slotsPerPage && translatedSlot >= 0 && translatedSlot < delegate.size();
    }

    @Override
    public int size() {
        return this.slotsPerPage;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (!isValidTranslatedSlot(slot)) {
            return 0;
        }
        return getDelegate().getSlotLimit(translateSlot(slot));
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        if (!isValidTranslatedSlot(slotIndex)) {
            return ItemStack.EMPTY;
        }
        return getDelegate().getStackInSlot(translateSlot(slotIndex));
    }

    @Override
    public boolean hasGenericDisplayStack(int slot) {
        InternalInventory delegate = getDelegate();
        return isValidTranslatedSlot(delegate, slot)
            && delegate instanceof GenericStackDisplayInventory displayInventory
            && displayInventory.hasGenericDisplayStack(translateSlot(slot));
    }

    @Override
    @Nullable
    public AEKey getDisplayKey(int slot) {
        InternalInventory delegate = getDelegate();
        if (!isValidTranslatedSlot(delegate, slot) || !(delegate instanceof GenericStackDisplayInventory displayInventory)) {
            return null;
        }
        return displayInventory.getDisplayKey(translateSlot(slot));
    }

    @Override
    public long getDisplayAmount(int slot) {
        InternalInventory delegate = getDelegate();
        if (!isValidTranslatedSlot(delegate, slot) || !(delegate instanceof GenericStackDisplayInventory displayInventory)) {
            return 0;
        }
        return displayInventory.getDisplayAmount(translateSlot(slot));
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        if (isValidTranslatedSlot(slotIndex)) {
            getDelegate().setItemDirect(translateSlot(slotIndex), stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isValidTranslatedSlot(slot) && getDelegate().isItemValid(translateSlot(slot), stack);
    }

    @Override
    public InternalInventory getSlotInv(int slotIndex) {
        if (!isValidTranslatedSlot(slotIndex)) {
            return this;
        }
        return getDelegate().getSlotInv(translateSlot(slotIndex));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isValidTranslatedSlot(slot)) {
            return stack;
        }
        return getDelegate().insertItem(translateSlot(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isValidTranslatedSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return getDelegate().extractItem(translateSlot(slot), amount, simulate);
    }
}
