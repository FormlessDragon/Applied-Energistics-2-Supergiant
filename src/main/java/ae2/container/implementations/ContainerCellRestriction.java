package ae2.container.implementations;

import ae2.api.inventories.ISegmentedInventory;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.container.guisync.GuiSync;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.tile.misc.TileCellWorkbench;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public class ContainerCellRestriction extends AEBaseContainer implements ISubGui {

    private static final String ACTION_SET_RESTRICTION = "setRestriction";
    private static final String ACTION_RELEASE_RESTRICTION = "releaseRestriction";
    private final TileCellWorkbench host;
    @GuiSync(2)
    public long maxBytes;
    @GuiSync(3)
    public int maxTypes;
    @GuiSync(4)
    public int amountPerByte;
    @GuiSync(5)
    public int typePerByte;
    @GuiSync(6)
    public long restrictionAmount = -1;
    @GuiSync(7)
    public int restrictionTypes = -1;
    private ItemStack cachedCellStack = ItemStack.EMPTY;

    public ContainerCellRestriction(InventoryPlayer ip, TileCellWorkbench host) {
        super(ip, host);
        this.host = host;

        registerClientAction(ACTION_SET_RESTRICTION, RestrictionRequest.class, this::handleSetRestriction);
        registerClientAction(ACTION_RELEASE_RESTRICTION, this::releaseRestriction);
    }

    @Override
    public void broadcastChanges() {
        refreshRestrictionDataIfNeeded();
        super.broadcastChanges();
    }

    public void setRestriction(long amount, int types) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_RESTRICTION, new RestrictionRequest(amount, types));
            return;
        }

        IBasicCellItem cell = getBasicCellItem();
        ItemStack stack = getCellStack();
        if (cell == null || stack.isEmpty() || !isValidRestriction(cell, stack, amount, types)) {
            return;
        }

        cell.setCellRestriction(stack, amount, types);
        this.host.saveChanges();
        refreshRestrictionData();
        detectAndSendChanges();
    }

    private void handleSetRestriction(RestrictionRequest request) {
        if (request != null) {
            setRestriction(request.amount(), request.types());
        }
    }

    public void releaseRestriction() {
        if (isClientSide()) {
            sendClientAction(ACTION_RELEASE_RESTRICTION);
            return;
        }

        IBasicCellItem cell = getBasicCellItem();
        ItemStack stack = getCellStack();
        if (cell == null || stack.isEmpty() || !cell.hasCellRestriction(stack)) {
            return;
        }

        cell.clearCellRestriction(stack);
        this.host.saveChanges();
        refreshRestrictionData();
        detectAndSendChanges();
    }

    public boolean hasRestriction() {
        return this.restrictionAmount >= 0 && this.restrictionTypes >= 0;
    }

    public long getCurrentAmountLimit() {
        return hasRestriction() ? this.restrictionAmount : getNativeAmountLimit();
    }

    public int getCurrentTypeLimit() {
        return hasRestriction() ? this.restrictionTypes : this.maxTypes;
    }

    public long getAllocatedBytes(long amount, int types) {
        return IBasicCellItem.getAllocatedBytesForRestriction(amount, types, this.amountPerByte, this.typePerByte);
    }

    public boolean isValidRestriction(long amount, int types) {
        return amount >= 0
            && amount <= getNativeAmountLimit()
            && types >= 0
            && types <= this.maxTypes
            && getAllocatedBytes(amount, types) <= this.maxBytes;
    }

    public boolean isSameAsCurrent(long amount, int types) {
        return getCurrentAmountLimit() == amount && getCurrentTypeLimit() == types;
    }

    private boolean isValidRestriction(IBasicCellItem cell, ItemStack stack, long amount, int types) {
        return amount >= 0
            && amount <= cell.getNativeAmountLimit(stack)
            && types >= 0
            && types <= cell.getTotalTypes(stack)
            && IBasicCellItem.getAllocatedBytesForRestriction(amount, types, cell.getKeyType().getAmountPerByte(),
            cell.getBytesPerType(stack)) <= cell.getBytes(stack);
    }

    private void refreshRestrictionDataIfNeeded() {
        ItemStack stack = getCellStack();
        if (!ItemStack.areItemStacksEqual(stack, this.cachedCellStack)) {
            refreshRestrictionData(stack);
        }
    }

    private void refreshRestrictionData() {
        refreshRestrictionData(getCellStack());
    }

    private void refreshRestrictionData(ItemStack stack) {
        this.cachedCellStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        IBasicCellItem cell = getBasicCellItem(stack);
        if (cell == null || stack.isEmpty()) {
            this.maxBytes = 0;
            this.maxTypes = 0;
            this.amountPerByte = 1;
            this.typePerByte = 0;
            this.restrictionAmount = -1;
            this.restrictionTypes = -1;
            return;
        }

        this.maxBytes = cell.getBytes(stack);
        this.maxTypes = cell.getTotalTypes(stack);
        this.amountPerByte = cell.getKeyType().getAmountPerByte();
        this.typePerByte = cell.getBytesPerType(stack);

        IBasicCellItem.CellRestriction restriction = cell.getCellRestrictionOrNull(stack);
        if (restriction != null) {
            this.restrictionAmount = restriction.amount();
            this.restrictionTypes = restriction.types();
        } else {
            this.restrictionAmount = -1;
            this.restrictionTypes = -1;
        }
    }

    private ItemStack getCellStack() {
        return Objects.requireNonNull(this.host.getSubInventory(ISegmentedInventory.CELLS))
                      .getStackInSlot(0);
    }

    private IBasicCellItem getBasicCellItem() {
        return getBasicCellItem(getCellStack());
    }

    private IBasicCellItem getBasicCellItem(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IBasicCellItem basicCellItem
            && basicCellItem.isStorageCell(stack)) {
            return basicCellItem;
        }
        return null;
    }

    @Override
    public GuiHostLocator getLocator() {
        return super.getLocator();
    }

    @Override
    public ISubGuiHost getHost() {
        return this.host;
    }

    private long getNativeAmountLimit() {
        return this.maxBytes * this.amountPerByte;
    }

    public record RestrictionRequest(long amount, int types) {
    }
}
