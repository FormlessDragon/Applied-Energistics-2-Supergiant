package ae2.api.upgrades;

import net.minecraft.item.ItemStack;

/**
 * Callback for upgrade inventories created through {@link UpgradeInventories#forItem}.
 */
@FunctionalInterface
public interface ItemUpgradesChanged {
    /**
     * Called when the upgrades inserted into <code>stack</code> have changed. The inventory of upgrades is passed to
     * avoid having to deserialize it from NBT again to inspect installed upgrades.
     */
    void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades);
}
