package ae2.util;

import ae2.api.config.IncludeExclude;
import ae2.api.stacks.AEKey;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.core.definitions.AEItems;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.item.ItemStack;

public final class CellWorkbenchFilter {
    private CellWorkbenchFilter() {
    }

    public static boolean isInverted(ItemStack stack, ICellWorkbenchItem workbenchItem) {
        return workbenchItem.getUpgrades(stack).isInstalled(AEItems.INVERTER_CARD.item());
    }

    public static boolean isFuzzy(ItemStack stack, ICellWorkbenchItem workbenchItem) {
        return workbenchItem.getUpgrades(stack).isInstalled(AEItems.FUZZY_CARD.item());
    }

    public static boolean matches(ItemStack stack, ICellWorkbenchItem workbenchItem, AEKey key, boolean inverted,
                                  boolean fuzzy) {
        var config = workbenchItem.getConfigInventory(stack);
        if (config.isEmpty()) {
            return true;
        }

        var builder = IPartitionList.builder();
        builder.addAll(config.keySet());
        if (fuzzy) {
            builder.fuzzyMode(workbenchItem.getFuzzyMode(stack));
        }

        var includeExclude = inverted ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        return builder.build().matchesFilter(key, includeExclude);
    }
}
