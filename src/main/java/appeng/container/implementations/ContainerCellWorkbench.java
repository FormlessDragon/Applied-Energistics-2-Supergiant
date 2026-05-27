/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package appeng.container.implementations;

import appeng.api.config.CopyMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.IConfigManager;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.CellPartitionSlot;
import appeng.container.slot.OptionalRestrictedInputSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.tile.misc.TileCellWorkbench;
import appeng.util.EnumCycler;
import appeng.util.inv.SupplierInternalInventory;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class ContainerCellWorkbench extends UpgradeableContainer<TileCellWorkbench> implements appeng.container.slot.IPartitionSlotHost {
    public static final String ACTION_NEXT_COPYMODE = "nextCopyMode";
    public static final String ACTION_PARTITION = "partition";
    public static final String ACTION_CLEAR = "clear";
    public static final String ACTION_SET_FUZZY_MODE = "setFuzzyMode";

    @GuiSync(2)
    public CopyMode copyMode = CopyMode.CLEAR_ON_REMOVE;

    public ContainerCellWorkbench(InventoryPlayer ip, TileCellWorkbench host) {
        super(ip, host);

        registerClientAction(ACTION_NEXT_COPYMODE, this::nextWorkBenchCopyMode);
        registerClientAction(ACTION_PARTITION, this::partition);
        registerClientAction(ACTION_CLEAR, this::clear);
        registerClientAction(ACTION_SET_FUZZY_MODE, FuzzyMode.class, this::setCellFuzzyMode);
    }

    @Override
    protected void setupInventorySlots() {
        appeng.api.inventories.InternalInventory cell = this.getHost().getSubInventory(ISegmentedInventory.CELLS);
        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.WORKBENCH_CELL, cell, 0),
            SlotSemantics.STORAGE_CELL);
    }

    @Override
    protected void setupConfig() {
        appeng.util.ConfigGuiInventory inv = getConfigInventory().createGuiWrapper();
        for (int slot = 0; slot < 63; slot++) {
            this.addSlot(new CellPartitionSlot(inv, this, slot), SlotSemantics.CONFIG);
        }
    }

    @Override
    protected void setupUpgrades() {
        SupplierInternalInventory<IUpgradeInventory> upgradeInventory = new SupplierInternalInventory<>(this::getUpgrades);
        for (int i = 0; i < 8; i++) {
            this.addSlot(new OptionalRestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES,
                upgradeInventory, this, i, 0, 0, i), SlotSemantics.UPGRADE);
        }
    }

    public void setCellFuzzyMode(FuzzyMode fuzzyMode) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_FUZZY_MODE, fuzzyMode);
            return;
        }

        ICellWorkbenchItem cell = getHost().getCell();
        if (cell != null) {
            cell.setFuzzyMode(getWorkbenchItem(), fuzzyMode);
        }
    }

    public void nextWorkBenchCopyMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_NEXT_COPYMODE);
        } else {
            getHost().getConfigManager().putSetting(Settings.COPY_MODE, EnumCycler.next(getCopyMode()));
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.copyMode = cm.getSetting(Settings.COPY_MODE);
        this.setFuzzyMode(getWorkbenchFuzzyMode());
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return idx < getUpgrades().size();
    }

    @Override
    public boolean isPartitionSlotEnabled(int idx) {
        ICellWorkbenchItem cwi = getHost().getCell();
        if (cwi != null && getCopyMode() == CopyMode.CLEAR_ON_REMOVE) {
            return idx < cwi.getConfigInventory(getWorkbenchItem()).size();
        }
        return getCopyMode() == CopyMode.KEEP_ON_REMOVE;
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        getHost().getConfigManager().putSetting(Settings.COPY_MODE, getCopyMode());
    }

    public void clear() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
        } else {
            getConfigInventory().clear();
            this.broadcastChanges();
        }
    }

    public void partition() {
        if (isClientSide()) {
            sendClientAction(ACTION_PARTITION);
            return;
        }

        GenericStackInv inv = getConfigInventory();
        Iterator<? extends AEKey> it = iterateCellStacks(getWorkbenchItem());
        for (int i = 0; i < inv.size(); i++) {
            inv.setStack(i, it.hasNext() ? new GenericStack(it.next(), 0) : null);
        }
        this.broadcastChanges();
    }

    public ItemStack getWorkbenchItem() {
        appeng.api.inventories.InternalInventory cells = Objects.requireNonNull(getHost().getSubInventory(ISegmentedInventory.CELLS));
        return cells.getStackInSlot(0);
    }

    public CopyMode getCopyMode() {
        return this.copyMode;
    }

    private GenericStackInv getConfigInventory() {
        return Objects.requireNonNull(this.getHost().getConfig());
    }

    private FuzzyMode getWorkbenchFuzzyMode() {
        ICellWorkbenchItem cell = getHost().getCell();
        return cell != null ? cell.getFuzzyMode(getWorkbenchItem()) : FuzzyMode.IGNORE_ALL;
    }

    @NotNull
    private Iterator<? extends AEKey> iterateCellStacks(ItemStack stack) {
        StorageCell cellInv = StorageCells.getCellInventory(stack, null);
        if (cellInv != null) {
            return Iterators.transform(cellInv.getAvailableStacks().iterator(), Map.Entry::getKey);
        }
        return Collections.emptyIterator();
    }
}
