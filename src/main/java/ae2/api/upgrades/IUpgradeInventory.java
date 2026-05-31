/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
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

package ae2.api.upgrades;

import ae2.api.inventories.InternalInventory;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

/**
 * This specialized inventory can be used to insert and extract upgrade cards into AE2 machines. Only upgrades supported
 * by the machine can be inserted.
 */
public interface IUpgradeInventory extends InternalInventory {
    /**
     * Item representation of the upgradable object this inventory is managing upgrades for.
     */
    Item getUpgradableItem();

    /**
     * @return Checks if the given upgrade card is installed in this inventory.
     */
    default boolean isInstalled(Item upgradeCard) {
        return getInstalledUpgrades(upgradeCard) > 0;
    }

    /**
     * determine how many of an upgrade are installed.
     */
    int getInstalledUpgrades(Item u);

    /**
     * determine how many of an upgrade can be installed.
     */
    int getMaxInstalled(Item u);

    /**
     * Reads the contents of this upgrade inventory from a subtag of the given compound tag.
     */
    void readFromNBT(NBTTagCompound data, String subtag);

    /**
     * Reads the contents of this upgrade inventory from a subtag of the given compound tag.
     */
    void writeToNBT(NBTTagCompound data, String subtag);
}
