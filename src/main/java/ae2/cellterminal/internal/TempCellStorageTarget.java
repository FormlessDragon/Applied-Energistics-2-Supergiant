package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalCapability;
import ae2.api.cellterminal.CellTerminalCellSlotMutation;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalContentSnapshot;
import ae2.api.cellterminal.CellTerminalPartitionSnapshot;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.cellterminal.CellTerminalUpgradeSnapshot;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.StorageCell;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.me.cells.BasicCellInventory;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Temporary cell storage exposed by the Cell Terminal container while it owns the backing inventory.
 */
public final class TempCellStorageTarget implements CellTerminalStorageTarget {
    private final InternalInventory inventory;
    private final String stableTargetId;
    private final CellTerminalTargetLocator locator;
    private final List<CellTerminalCellSlotTarget> cellSlots;

    public TempCellStorageTarget(InternalInventory inventory,
                                 String stableTargetId,
                                 CellTerminalTargetLocator locator) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.stableTargetId = Objects.requireNonNull(stableTargetId, "stableTargetId");
        this.locator = Objects.requireNonNull(locator, "locator");
        var slots = new ObjectArrayList<CellTerminalCellSlotTarget>(inventory.size());
        for (int slotIndex = 0; slotIndex < inventory.size(); slotIndex++) {
            slots.add(new TempCellSlotTarget(this, slotIndex));
        }
        this.cellSlots = List.copyOf(slots);
    }

    @Override
    public String stableTargetId() {
        return this.stableTargetId;
    }

    @Override
    public ITextComponent displayName() {
        return new TextComponentTranslation("gui.ae2.CellTerminal.tempCells");
    }

    @Override
    public CellTerminalTargetLocator locator() {
        return this.locator;
    }

    @Override
    public boolean supportsCapability(CellTerminalCapability capability) {
        return switch (capability) {
            case CONTENT_PREVIEW, CELL_SLOT_WRITE, PARTITION_WRITE, UPGRADE_WRITE -> true;
            case AUTO_PARTITION_FROM_CONTENT, TEXT_PARTITION_WRITE, PRECISE_PARTITION_WRITE,
                 SAFE_UNIQUE_TYPE_REALLOCATION, PRIORITY_WRITE,
                 IO_FILTER_MODE_WRITE, SUBNET_RESOLVE -> false;
        };
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void setPriority(int priority) {
        CellTerminalWriteSupport.fail("Temporary cell storage has no priority");
    }

    @Override
    public int getCellSlotCount() {
        return this.inventory.size();
    }

    @Override
    public int getMountedCellCount() {
        int mounted = 0;
        for (var slot : getCellSlots()) {
            if (slot.isMounted()) {
                mounted++;
            }
        }
        return mounted;
    }

    @Override
    public List<? extends CellTerminalCellSlotTarget> getCellSlots() {
        return this.cellSlots;
    }

    @Override
    public CellTerminalContentSnapshot previewContent() {
        KeyCounter counter = new KeyCounter();
        for (var slot : getCellSlots()) {
            StorageCell cell = slot.getCellInventory();
            if (cell != null) {
                cell.getAvailableStacks(counter);
            }
        }
        return CellTerminalContentSnapshot.fromCounter(counter);
    }

    private record TempCellSlotTarget(TempCellStorageTarget storageTarget,
                                      int slotIndex) implements CellTerminalCellSlotTarget {
        private TempCellSlotTarget(TempCellStorageTarget storageTarget, int slotIndex) {
            this.storageTarget = Objects.requireNonNull(storageTarget, "storageTarget");
            if (slotIndex < 0 || slotIndex >= storageTarget.inventory.size()) {
                throw new IllegalArgumentException("Temporary cell slot out of range: " + slotIndex);
            }
            this.slotIndex = slotIndex;
        }

        @Override
        public CellTerminalStorageTarget getStorageTarget() {
            return this.storageTarget;
        }

        @Override
        public ItemStack getCellStack() {
            return this.storageTarget.inventory.getStackInSlot(this.slotIndex);
        }

        @Override
        public boolean isMounted() {
            return getCellInventory() != null;
        }

        @Override
        public CellState getCellState() {
            StorageCell cell = getCellInventory();
            return cell == null ? CellState.ABSENT : cell.getStatus();
        }

        @Override
        public @Nullable StorageCell getCellInventory() {
            return StorageCells.getCellInventory(getCellStack(), null);
        }

        @Override
        public boolean supportsCapability(CellTerminalCapability capability) {
            if (capability == CellTerminalCapability.AUTO_PARTITION_FROM_CONTENT) {
                ItemStack stack = getCellStack();
                return !stack.isEmpty()
                    && stack.getItem() instanceof ICellWorkbenchItem workbenchItem
                    && workbenchItem.supportsAutoPartition(stack);
            }
            if (capability == CellTerminalCapability.SAFE_UNIQUE_TYPE_REALLOCATION) {
                return getCellInventory() instanceof BasicCellInventory;
            }
            return this.storageTarget.supportsCapability(capability);
        }

        @Override
        public @Nullable ConfigInventory getConfigInventory() {
            ItemStack stack = getCellStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof ICellWorkbenchItem workbenchItem)) {
                return null;
            }
            return workbenchItem.getConfigInventory(stack);
        }

        @Override
        public @Nullable IUpgradeInventory getUpgradeInventory() {
            ItemStack stack = getCellStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof ICellWorkbenchItem workbenchItem)) {
                return null;
            }
            return workbenchItem.getUpgrades(stack);
        }

        @Override
        public CellTerminalContentSnapshot previewContent() {
            StorageCell cell = getCellInventory();
            return cell == null ? CellTerminalContentSnapshot.fromCounter(new KeyCounter())
                : CellTerminalContentSnapshot.fromCounter(cell.getAvailableStacks());
        }

        @Override
        public CellTerminalPartitionSnapshot getPartitionSnapshot() {
            ConfigInventory config = getConfigInventory();
            return config == null ? new CellTerminalPartitionSnapshot(List.of())
                : CellTerminalWriteSupport.snapshotPartition(config);
        }

        @Override
        public void setPartition(List<? extends @Nullable GenericStack> partitionSlots) {
            ConfigInventory config = getConfigInventory();
            if (config == null) {
                CellTerminalWriteSupport.fail("Temporary cell has no partition inventory: %d", this.slotIndex);
            }
            CellTerminalWriteSupport.setPartition(config, partitionSlots);
            persist();
        }

        @Override
        public <T> T simulateWithPartition(List<? extends @Nullable GenericStack> partitionSlots,
                                           PartitionSimulation<T> simulation) {
            Objects.requireNonNull(simulation, "simulation");
            ConfigInventory config = getConfigInventory();
            if (config == null) {
                CellTerminalWriteSupport.fail("Temporary cell has no partition inventory: %d", this.slotIndex);
            }
            List<@Nullable GenericStack> baseline = CellTerminalWriteSupport.snapshotPartition(config).slots();
            try {
                CellTerminalWriteSupport.setPartition(config, partitionSlots);
                StorageCell cell = getCellInventory();
                if (cell == null) {
                    CellTerminalWriteSupport.fail("Temporary cell has no storage inventory: %d", this.slotIndex);
                }
                return simulation.run(cell);
            } finally {
                CellTerminalWriteSupport.setPartition(config, baseline);
                persist();
            }
        }

        @Override
        public CellTerminalUpgradeSnapshot getUpgradeSnapshot() {
            IUpgradeInventory upgrades = getUpgradeInventory();
            return upgrades == null ? new CellTerminalUpgradeSnapshot(List.of())
                : CellTerminalWriteSupport.snapshotUpgrades(upgrades);
        }

        @Override
        public void setUpgrades(List<ItemStack> upgradeStacks) {
            IUpgradeInventory upgrades = getUpgradeInventory();
            if (upgrades == null) {
                CellTerminalWriteSupport.fail("Temporary cell has no upgrade inventory: %d", this.slotIndex);
            }
            CellTerminalWriteSupport.setUpgrades(upgrades, upgradeStacks);
            persist();
        }

        @Override
        public CellTerminalCellSlotMutation insertCell(ItemStack stack) {
            return CellTerminalWriteSupport.insertCell(
                this.storageTarget.inventory,
                this.slotIndex,
                stack);
        }

        @Override
        public CellTerminalCellSlotMutation ejectCell() {
            return CellTerminalWriteSupport.ejectCell(this.storageTarget.inventory, this.slotIndex);
        }

        @Override
        public CellTerminalCellSlotMutation replaceCell(ItemStack stack) {
            return CellTerminalWriteSupport.replaceCell(
                this.storageTarget.inventory,
                this.slotIndex,
                stack);
        }

        private void persist() {
            StorageCell cell = getCellInventory();
            if (cell != null) {
                cell.persist();
            }
            this.storageTarget.inventory.sendChangeNotification(this.slotIndex);
        }
    }
}
