/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 */
package ae2.items.contents;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.features.HotkeyAction;
import ae2.api.implementations.blockentities.IViewCellStorage;
import ae2.api.implementations.guiobjects.IPortableTerminal;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageCells;
import ae2.api.storage.StorageHelper;
import ae2.api.storage.SupplierStorage;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.util.IConfigManager;
import ae2.container.ISubGui;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.items.tools.powered.AbstractPortableCell;
import ae2.me.helpers.PlayerSource;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.SupplierInternalInventory;
import com.google.common.base.Preconditions;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class PortableCellGuiHost<T extends AbstractPortableCell> extends ItemGuiHost<T>
    implements IPortableTerminal, IViewCellStorage {
    private static final String VIEW_CELL_TAG = "viewCell";

    private final BiConsumer<EntityPlayer, ISubGui> returnMainContainer;
    private final MEStorage cellStorage;
    private final AbstractPortableCell item;
    private final IConfigManager configManager;
    private final SupplierInternalInventory<InternalInventory> viewCellStorage;
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public PortableCellGuiHost(T item, EntityPlayer player, ItemGuiHostLocator locator,
                               BiConsumer<EntityPlayer, ISubGui> returnMainContainer) {
        super(item, player, locator);
        Preconditions.checkArgument(getItemStack().getItem() == item, "Stack doesn't match item");
        this.returnMainContainer = returnMainContainer;
        this.cellStorage = new SupplierStorage(new CellStorageSupplier());
        Objects.requireNonNull(cellStorage, "Portable cell doesn't expose a cell inventory.");
        this.item = item;
        this.viewCellStorage = new SupplierInternalInventory<>(
            new StackDependentSupplier<>(this::getItemStack, stack -> createViewCellStorage(player, stack)));
        this.updateLinkStatus();
        this.configManager = IConfigManager.builder(this::getItemStack)
                                           .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                                           .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                                           .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
                                           .build();
    }

    private static InternalInventory createViewCellStorage(EntityPlayer player, ItemStack stack) {
        var viewCellStorage = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inv) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    stack.setTagCompound(tag);
                }
                inv.writeToNBT(tag, VIEW_CELL_TAG);
            }

            @Override
            public boolean isClientSide() {
                return player.world.isRemote;
            }
        }, 5);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            viewCellStorage.readFromNBT(tag, VIEW_CELL_TAG);
        }
        return viewCellStorage;
    }

    @Override
    public void tick() {
        super.tick();
        consumeIdlePower(Actionable.MODULATE);
        updateLinkStatus();
    }

    @Override
    public long insert(EntityPlayer player, AEKey what, long amount, Actionable mode) {
        if (getLinkStatus().connected()) {
            var inv = getInventory();
            return inv == null ? 0 : StorageHelper.poweredInsert(this, inv, what, amount, new PlayerSource(player), mode);
        } else {
            var statusText = getLinkStatus().statusDescription();
            if (isClientSide() && statusText != null && !mode.isSimulate()) {
                player.sendStatusMessage(statusText, false);
            }
            return 0;
        }
    }

    private void updateLinkStatus() {
        if (!consumeIdlePower(Actionable.SIMULATE)) {
            this.linkStatus = ILinkStatus.ofDisconnected(GuiText.OutOfPower.text());
        } else {
            this.linkStatus = ILinkStatus.ofConnected();
        }
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return linkStatus;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        amt = usePowerMultiplier.multiply(amt);
        if (mode == Actionable.SIMULATE) {
            return usePowerMultiplier.divide(Math.min(amt, this.item.getAECurrentPower(getItemStack())));
        }
        return usePowerMultiplier.divide(this.item.extractAEPower(getItemStack(), amt, Actionable.MODULATE));
    }

    @Override
    public MEStorage getInventory() {
        return cellStorage;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public InternalInventory getViewCellStorage() {
        return this.viewCellStorage;
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        returnMainContainer.accept(player, subGui);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getItemStack();
    }

    @Override
    public String getCloseHotkey() {
        if (item instanceof IBasicCellItem cellItem) {
            if (cellItem.getKeyType().equals(AEKeyType.items())) {
                return HotkeyAction.PORTABLE_ITEM_CELL;
            } else if (cellItem.getKeyType().equals(AEKeyType.fluids())) {
                return HotkeyAction.PORTABLE_FLUID_CELL;
            }
        }
        return null;
    }

    private class CellStorageSupplier implements Supplier<MEStorage> {
        private MEStorage currentStorage;
        private ItemStack currentStack = ItemStack.EMPTY;

        @Override
        public MEStorage get() {
            var stack = getItemStack();
            if (stack != currentStack) {
                currentStorage = StorageCells.getCellInventory(stack, null);
                currentStack = stack;
            }
            return currentStorage;
        }
    }
}





