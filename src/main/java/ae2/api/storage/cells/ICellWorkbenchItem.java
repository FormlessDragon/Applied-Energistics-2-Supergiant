/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.storage.cells;

import ae2.api.config.FuzzyMode;
import ae2.api.upgrades.IUpgradeableItem;
import ae2.util.ConfigInventory;
import net.minecraft.item.ItemStack;

public interface ICellWorkbenchItem extends IUpgradeableItem {
    /**
     * Determines whether or not the item should be treated as a cell and allow for configuration via a cell workbench.
     * By default, any such item with either a filtering or upgrade inventory is thus assumed to be editable.
     *
     * @param is item
     * @return true if the item should be editable in the cell workbench.
     */
    default boolean isEditable(ItemStack is) {
        return getConfigInventory(is).size() > 0 || getUpgrades(is).size() > 0;
    }

    /**
     * Used to extract, or mirror the contents of the work bench onto the cell.
     * <p>
     * This should not exceed 63 slots. Any more than that might cause issues.
     * <p>
     * onInventoryChange will be called when saving is needed.
     */
    default ConfigInventory getConfigInventory(ItemStack is) {
        return ConfigInventory.emptyTypes();
    }

    /**
     * Determines whether automatic "partition from content" actions are allowed for this item.
     * <p>
     * This only controls automatic population of the partition inventory. Manual partition editing still depends on
     * the exposed config inventory itself.
     *
     * @param is item
     * @return true when automatic partitioning from existing content is supported.
     */
    default boolean supportsAutoPartition(ItemStack is) {
        return true;
    }

    /**
     * @return the current fuzzy status.
     */
    FuzzyMode getFuzzyMode(ItemStack is);

    /**
     * sets the setting on the cell.
     */
    void setFuzzyMode(ItemStack is, FuzzyMode fzMode);
}
