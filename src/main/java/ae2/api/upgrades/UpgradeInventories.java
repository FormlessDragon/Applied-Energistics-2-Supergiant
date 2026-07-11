package ae2.api.upgrades;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Utilities for creating {@link IUpgradeInventory upgrade inventories}.
 */
public final class UpgradeInventories {
    private UpgradeInventories() {
    }

    /**
     * Returns an empty, read-only upgrade inventory.
     */
    public static IUpgradeInventory empty() {
        return EmptyUpgradeInventory.INSTANCE;
    }

    /**
     * Creates an upgrade inventory that manages the upgrades inserted into an upgradable item stack such as portable
     * cells or wireless terminals. Ensure to save your machine to disk when the upgrades change using the given
     * callback.
     */
    public static IUpgradeInventory forMachine(Item machineType, int maxUpgrades,
                                               MachineUpgradesChanged changeCallback) {
        return new MachineUpgradeInventory(machineType, getMachineUpgradeSlots(machineType, maxUpgrades),
            changeCallback);
    }

    /**
     * Resolves a machine's physical upgrade inventory size from its base slots and registered slot contributions.
     * Calling this freezes further slot registrations for the machine.
     */
    public static int getMachineUpgradeSlots(Item machineType, int baseSlots) {
        if (baseSlots < 0) {
            throw new IllegalArgumentException("baseSlots must not be negative");
        }
        return Math.addExact(baseSlots, Upgrades.getAdditionalUpgradeSlots(machineType));
    }

    /**
     * Creates an upgrade inventory that manages the upgrades inserted into an upgradable item stack such as portable
     * cells or wireless terminals. Changes to the upgrades are immediately written into the given stack's NBT.
     */
    public static IUpgradeInventory forItem(ItemStack stack, int maxUpgrades) {
        return new ItemUpgradeInventory(stack, maxUpgrades, null);
    }

    /**
     * Same as {@link #forItem(ItemStack, int)}, but with change notifications.
     */
    public static IUpgradeInventory forItem(ItemStack stack, int maxUpgrades, ItemUpgradesChanged changeCallback) {
        return new ItemUpgradeInventory(stack, maxUpgrades, changeCallback);
    }
}
