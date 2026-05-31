package ae2.api.upgrades;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/**
 * Counterpart for {@link IUpgradeableObject}, but for {@link Item}.
 */
@ApiStatus.NonExtendable
public interface IUpgradeableItem {
    /**
     * Used to edit the upgrades on your item, should have a capacity of 0-8 slots. You are also responsible for
     * implementing the valid checks, and any storage/usage of them.
     * <p>
     * onInventoryChange will be called when saving is needed.
     */
    default IUpgradeInventory getUpgrades(ItemStack stack) {
        return EmptyUpgradeInventory.INSTANCE;
    }
}
