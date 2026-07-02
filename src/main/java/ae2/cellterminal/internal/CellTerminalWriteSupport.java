package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalCellSlotMutation;
import ae2.api.cellterminal.CellTerminalPartitionSnapshot;
import ae2.api.cellterminal.CellTerminalUpgradeSnapshot;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.StorageCells;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.core.AELog;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CellTerminalWriteSupport {
    private CellTerminalWriteSupport() {
    }

    static CellTerminalCellSlotMutation insertCell(InternalInventory inventory, int slotIndex, ItemStack stack) {
        validateCellStack(stack);
        ItemStack remainder = inventory.insertItem(slotIndex, stack.copy(), true);
        if (!remainder.isEmpty()) {
            return new CellTerminalCellSlotMutation(ItemStack.EMPTY, remainder.copy());
        }

        ItemStack actualRemainder = inventory.insertItem(slotIndex, stack.copy(), false);
        if (!actualRemainder.isEmpty()) {
            fail("Cell Terminal cell insert simulation changed during modulation. slot=%d, remainder=%s",
                slotIndex, actualRemainder);
        }
        return new CellTerminalCellSlotMutation(ItemStack.EMPTY, ItemStack.EMPTY);
    }

    static CellTerminalCellSlotMutation ejectCell(InternalInventory inventory, int slotIndex) {
        ItemStack current = inventory.getStackInSlot(slotIndex);
        if (current.isEmpty()) {
            return new CellTerminalCellSlotMutation(ItemStack.EMPTY, ItemStack.EMPTY);
        }

        ItemStack simulated = inventory.extractItem(slotIndex, current.getCount(), true);
        if (simulated.isEmpty() || !ItemStack.areItemStacksEqual(current, simulated)) {
            fail("Cell Terminal cell eject simulation mismatch. slot=%d, current=%s, simulated=%s",
                slotIndex, current, simulated);
        }

        ItemStack extracted = inventory.extractItem(slotIndex, current.getCount(), false);
        if (!ItemStack.areItemStacksEqual(current, extracted)) {
            fail("Cell Terminal cell eject modulation mismatch. slot=%d, expected=%s, extracted=%s",
                slotIndex, current, extracted);
        }
        return new CellTerminalCellSlotMutation(extracted.copy(), ItemStack.EMPTY);
    }

    static CellTerminalCellSlotMutation replaceCell(InternalInventory inventory, int slotIndex, ItemStack stack) {
        validateCellStack(stack);
        ItemStack current = inventory.getStackInSlot(slotIndex);
        if (current.isEmpty()) {
            return insertCell(inventory, slotIndex, stack);
        }

        ItemStack simulatedExtract = inventory.extractItem(slotIndex, current.getCount(), true);
        if (!ItemStack.areItemStacksEqual(current, simulatedExtract)) {
            fail("Cell Terminal cell replace extract simulation mismatch. slot=%d, current=%s, simulated=%s",
                slotIndex, current, simulatedExtract);
        }

        ItemStack extracted = inventory.extractItem(slotIndex, current.getCount(), false);
        if (!ItemStack.areItemStacksEqual(current, extracted)) {
            fail("Cell Terminal cell replace extract modulation mismatch. slot=%d, expected=%s, extracted=%s",
                slotIndex, current, extracted);
        }

        ItemStack remainder = inventory.insertItem(slotIndex, stack.copy(), false);
        if (!remainder.isEmpty()) {
            ItemStack restoreRemainder = inventory.insertItem(slotIndex, extracted.copy(), false);
            if (!restoreRemainder.isEmpty()) {
                fail("Cell Terminal cell replace rollback failed. slot=%d, extracted=%s, remainder=%s",
                    slotIndex, extracted, restoreRemainder);
            }
            return new CellTerminalCellSlotMutation(ItemStack.EMPTY, remainder.copy());
        }

        return new CellTerminalCellSlotMutation(extracted.copy(), ItemStack.EMPTY);
    }

    static CellTerminalPartitionSnapshot snapshotPartition(ConfigInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        var entries = new ObjectArrayList<@Nullable GenericStack>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            entries.add(inventory.getStack(slot));
        }
        return new CellTerminalPartitionSnapshot(entries);
    }

    static void setPartition(ConfigInventory inventory, List<? extends @Nullable GenericStack> slots) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(slots, "slots");
        if (slots.size() > inventory.size()) {
            fail("Cell Terminal partition write exceeds native capacity. size=%d, capacity=%d",
                slots.size(), inventory.size());
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            GenericStack stack = slot < slots.size() ? slots.get(slot) : null;
            if (stack != null && !inventory.isAllowedIn(slot, stack.what())) {
                fail("Cell Terminal partition slot rejected key. slot=%d, key=%s", slot, stack.what());
            }
        }

        inventory.beginBatch();
        try {
            for (int slot = 0; slot < inventory.size(); slot++) {
                GenericStack stack = slot < slots.size() ? slots.get(slot) : null;
                inventory.setStack(slot, stack);
            }
        } finally {
            inventory.endBatch();
        }
    }

    static CellTerminalUpgradeSnapshot snapshotUpgrades(IUpgradeInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        var entries = new ObjectArrayList<ItemStack>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            entries.add(inventory.getStackInSlot(slot));
        }
        return new CellTerminalUpgradeSnapshot(entries);
    }

    static void setUpgrades(IUpgradeInventory inventory, List<ItemStack> slots) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(slots, "slots");
        List<ItemStack> normalizedSlots = normalizeUpgradeSlots(inventory, slots);
        validateUpgradeSnapshot(inventory, normalizedSlots);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = normalizedSlots.get(slot);
            ItemStack previous = inventory.getStackInSlot(slot);
            inventory.setItemDirect(slot, stack);
            if (!ItemStack.areItemStacksEqual(previous, stack)) {
                inventory.sendChangeNotification(slot);
            }
        }
    }

    private static List<ItemStack> normalizeUpgradeSlots(IUpgradeInventory inventory, List<ItemStack> slots) {
        if (slots.size() > inventory.size()) {
            fail("Cell Terminal upgrade write exceeds native capacity. size=%d, capacity=%d",
                slots.size(), inventory.size());
        }

        var normalized = new ObjectArrayList<ItemStack>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (slot >= slots.size()) {
                normalized.add(ItemStack.EMPTY);
                continue;
            }

            ItemStack stack = slots.get(slot);
            if (stack == null) {
                fail("Cell Terminal upgrade write received null stack. slot=%d", slot);
            } else normalized.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return normalized;
    }

    private static void validateUpgradeSnapshot(IUpgradeInventory inventory, List<ItemStack> slots) {
        Map<Item, Integer> installed = new Reference2ObjectOpenHashMap<>();

        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() != 1) {
                fail("Cell Terminal upgrade write requires single-card stacks. slot=%d, stack=%s", slot, stack);
            }
            if (!Upgrades.isUpgradeCardItem(stack)) {
                fail("Cell Terminal upgrade write rejected non-upgrade stack. slot=%d, stack=%s", slot, stack);
            }

            Item item = stack.getItem();
            int maxInstalled = inventory.getMaxInstalled(item);
            if (maxInstalled <= 0) {
                fail("Cell Terminal upgrade write rejected unsupported card. slot=%d, stack=%s", slot, stack);
            }

            int updatedCount = installed.merge(item, 1, Integer::sum);
            if (updatedCount > maxInstalled) {
                fail("Cell Terminal upgrade write exceeds card limit. slot=%d, stack=%s, count=%d, max=%d",
                    slot, stack, updatedCount, maxInstalled);
            }
        }
    }

    public static void fail(String format, Object... params) {
        AELog.error(format, params);
        throw new IllegalStateException(String.format(format, params));
    }

    private static void validateCellStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            fail("Cell Terminal cell write requires a non-empty cell stack");
        }
        if (stack.getCount() != 1) {
            fail("Cell Terminal cell write requires a single cell stack. stack=%s", stack);
        }
        if (!StorageCells.isCellHandled(stack)) {
            fail("Cell Terminal cell write rejected non-cell stack. stack=%s", stack);
        }
    }
}
