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
package ae2.tile.storage;

import ae2.api.AECapabilities;
import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.implementations.blockentities.IColorableBlockEntity;
import ae2.api.implementations.blockentities.IMEChest;
import ae2.api.implementations.blockentities.IViewCellStorage;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.events.GridPowerStorageStateChanged;
import ae2.api.networking.events.GridPowerStorageStateChanged.PowerEventType;
import ae2.api.networking.security.IActionSource;
import ae2.api.orientation.RelativeSide;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.ITerminalHost;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageCells;
import ae2.api.storage.StorageHelper;
import ae2.api.storage.SupplierStorage;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.StorageCell;
import ae2.api.util.AEColor;
import ae2.api.util.IConfigManager;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.definitions.AEBlocks;
import ae2.core.gui.GuiOpener;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.helpers.IPriorityHost;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.items.misc.PackageInsertResult;
import ae2.me.helpers.MachineSource;
import ae2.me.storage.DelegatingMEInventory;
import ae2.tile.ServerTickingTile;
import ae2.tile.grid.AENetworkedPoweredTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.CombinedInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.jetbrains.annotations.Nullable;

public class TileMEChest extends AENetworkedPoweredTile
    implements IMEChest, ITerminalHost, IPriorityHost, IColorableBlockEntity,
    IStorageProvider, ServerTickingTile, KeyTypeSelectionHost, IViewCellStorage {

    private static final CellState[] CELL_STATES = CellState.values();
    private static final AEColor[] COLORS = AEColor.values();

    private final AppEngInternalInventory inputInventory = new AppEngInternalInventory(this, 1, 64,
        new InputInventoryFilter());
    private final AppEngInternalInventory cellInventory = new AppEngInternalInventory(this, 1, 1,
        new CellInventoryFilter());
    private final AppEngInternalInventory viewCellInventory = new AppEngInternalInventory(this, 5);
    private final InternalInventory internalInventory = new CombinedInternalInventory(this.inputInventory,
        this.cellInventory);
    private final MachineSource mySrc = new MachineSource(this);

    private final IConfigManager config = IConfigManager.builder(this::saveChanges)
                                                        .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                                                        .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                                                        .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
                                                        .build();
    private final KeyTypeSelection keyTypeSelection = new KeyTypeSelection(this::saveChanges, TileMEChest::isVisibleKeyType);

    private int priority;
    private CellState clientCellState = CellState.ABSENT;
    private boolean clientPowered;
    private Item clientCellItem = Items.AIR;
    private boolean wasOnline;
    private AEColor paintedColor = AEColor.TRANSPARENT;
    private boolean isCached;
    private ChestMonitorHandler cellHandler;
    private IFluidHandler fluidHandler;
    private double idlePowerUsage;

    public TileMEChest() {
        setInternalMaxPower(PowerMultiplier.CONFIG.multiply(500));
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
        setInternalPublicPowerStorage(true);
        setInternalPowerFlow(AccessRestriction.WRITE);
    }

    private static boolean isVisibleKeyType(AEKeyType keyType) {
        return true;
    }

    private static CellState readCellState(int ordinal) {
        return ordinal >= 0 && ordinal < CELL_STATES.length ? CELL_STATES[ordinal] : CellState.ABSENT;
    }

    private static AEColor readColor(int ordinal) {
        return ordinal >= 0 && ordinal < COLORS.length ? COLORS[ordinal] : AEColor.TRANSPARENT;
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                    .addService(IStorageProvider.class, this);
    }

    public ItemStack getCell() {
        return cellInventory.getStackInSlot(0);
    }

    public void setCell(ItemStack stack) {
        cellInventory.setItemDirect(0, stack);
    }

    @Override
    protected void emitPowerStateEvent(PowerEventType x) {
        if (x == PowerEventType.RECEIVE_POWER) {
            ifGridPresent(grid -> grid.postEvent(new GridPowerStorageStateChanged(this, x)));
        } else {
            recalculateDisplay();
        }
    }

    private void recalculateDisplay() {
        var cellState = getCellStatus(0);
        var powered = isPowered();
        var cellItem = getResolvedCellItem();

        if (this.clientCellState != cellState
            || this.clientPowered != powered
            || this.clientCellItem != cellItem) {
            this.clientCellState = cellState;
            this.clientPowered = powered;
            this.clientCellItem = cellItem;
            markForUpdate();
        }
    }

    @Override
    public int getCellCount() {
        return 1;
    }

    private void updateHandler() {
        if (!this.isCached) {
            this.cellHandler = null;
            this.fluidHandler = null;
            this.idlePowerUsage = 0;
            this.getMainNode().setIdlePowerUsage(0);

            var cell = this.getCell();
            if (!cell.isEmpty()) {
                this.isCached = true;
                var newCell = StorageCells.getCellInventory(cell, this::onCellContentChanged);
                if (newCell != null) {
                    this.idlePowerUsage = 1.0 + newCell.getIdleDrain();
                    this.cellHandler = this.wrap(newCell);
                    this.getMainNode().setIdlePowerUsage(this.idlePowerUsage);
                    if (this.cellHandler != null) {
                        this.fluidHandler = new FluidHandler();
                    }
                }
            }
        }
    }

    @Nullable
    private ChestMonitorHandler wrap(StorageCell cellInventory) {
        return cellInventory != null ? new ChestMonitorHandler(this, cellInventory) : null;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        updateHandler();
        if (this.cellHandler == null) {
            return ILinkStatus.ofDisconnected(PlayerMessages.ChestCannotReadStorageCell.text());
        }
        if (!isPowered()) {
            return ILinkStatus.ofDisconnected(GuiText.OutOfPower.text());
        }
        return ILinkStatus.ofConnected();
    }

    @Override
    public CellState getCellStatus(int slot) {
        if (isClientSide()) {
            return clientCellState;
        }

        this.updateHandler();

        var cell = this.getCell();
        var handler = StorageCells.getHandler(cell);
        if (slot == 0 && this.cellHandler != null && handler != null) {
            return this.cellHandler.cellInventory.getStatus();
        }

        return CellState.ABSENT;
    }

    @Nullable
    @Override
    public Item getCellItem(int slot) {
        if (slot != 0) {
            return null;
        }
        if (isClientSide()) {
            return this.clientCellItem == Items.AIR ? null : this.clientCellItem;
        }
        var cellItem = getResolvedCellItem();
        return cellItem == Items.AIR ? null : cellItem;
    }

    @Nullable
    @Override
    public MEStorage getCellInventory(int slot) {
        if (slot == 0 && cellHandler != null) {
            return cellHandler;
        }
        return null;
    }

    @Nullable
    @Override
    public StorageCell getOriginalCellInventory(int slot) {
        if (slot == 0 && cellHandler != null) {
            return cellHandler.cellInventory;
        }
        return null;
    }

    @Override
    public boolean isPowered() {
        if (isClientSide()) {
            return this.clientPowered;
        }
        if (getMainNode().isPowered()) {
            return true;
        }
        return getAECurrentPower() > 1;
    }

    @Override
    public boolean isCellBlinking(int slot) {
        return false;
    }

    @Override
    protected double extractAEPower(double amt, Actionable mode) {
        double stash = 0.0;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            var energy = grid.getEnergyService();
            stash = energy.extractAEPower(amt, mode, PowerMultiplier.ONE);
            if (stash >= amt) {
                return stash;
            }
        }
        return super.extractAEPower(amt - stash, mode) + stash;
    }

    @Override
    public void serverTick() {
        var grid = getMainNode().getGrid();
        if (grid == null || !grid.getEnergyService().isNetworkPowered()) {
            this.extractAEPower(idlePowerUsage, Actionable.MODULATE, PowerMultiplier.CONFIG);
            this.recalculateDisplay();
        }

        if (!this.inputInventory.isEmpty()) {
            this.tryToStoreContents();
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        this.clientCellState = getCellStatus(0);
        this.clientPowered = isPowered();
        this.clientCellItem = getResolvedCellItem();
        data.writeByte(this.clientCellState.ordinal());
        data.writeBoolean(this.clientPowered);
        data.writeByte(this.paintedColor.ordinal());
        data.writeInt(Item.getIdFromItem(this.clientCellItem));
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        final boolean changed = super.readFromStream(data);
        var oldCellState = clientCellState;
        var oldPowered = clientPowered;
        var oldColor = paintedColor;
        var oldCellItem = clientCellItem;

        int stateOrdinal = data.readUnsignedByte();
        clientCellState = readCellState(stateOrdinal);
        clientPowered = data.readBoolean();
        int colorOrdinal = data.readUnsignedByte();
        paintedColor = readColor(colorOrdinal);
        clientCellItem = Item.getItemById(data.readInt());
        if (clientCellItem == null) {
            clientCellItem = Items.AIR;
        }

        return changed || oldCellState != clientCellState || oldPowered != clientPowered
            || oldColor != paintedColor || oldCellItem != clientCellItem;
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);
        data.setBoolean("powered", isPowered());
        data.setByte("cellState", (byte) getCellStatus(0).ordinal());
        data.setInteger("cellItem", Item.getIdFromItem(getResolvedCellItem()));
        data.setByte("paintedColor", (byte) this.paintedColor.ordinal());
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);
        int stateOrdinal = data.getByte("cellState") & 0xFF;
        this.clientCellState = readCellState(stateOrdinal);
        this.clientPowered = data.getBoolean("powered");
        var item = Item.getItemById(data.getInteger("cellItem"));
        this.clientCellItem = item == null ? Items.AIR : item;
        if (data.hasKey("paintedColor", Constants.NBT.TAG_ANY_NUMERIC)) {
            int colorOrdinal = data.getByte("paintedColor") & 0xFF;
            this.paintedColor = readColor(colorOrdinal);
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.config.readFromNBT(data);
        this.keyTypeSelection.readFromNBT(data);
        inputInventory.readFromNBT(data, "inputInventory");
        cellInventory.readFromNBT(data, "cellInventory");
        viewCellInventory.readFromNBT(data, "viewCellInventory");
        priority = data.getInteger("priority");
        if (data.hasKey("paintedColor", Constants.NBT.TAG_ANY_NUMERIC)) {
            int colorOrdinal = data.getByte("paintedColor") & 0xFF;
            this.paintedColor = readColor(colorOrdinal);
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.config.writeToNBT(data);
        this.keyTypeSelection.writeToNBT(data);
        inputInventory.writeToNBT(data, "inputInventory");
        cellInventory.writeToNBT(data, "cellInventory");
        viewCellInventory.writeToNBT(data, "viewCellInventory");
        data.setInteger("priority", priority);
        data.setByte("paintedColor", (byte) this.paintedColor.ordinal());
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        var currentOnline = getMainNode().isOnline();
        if (wasOnline != currentOnline) {
            wasOnline = currentOnline;
            IStorageProvider.requestUpdate(getMainNode());
            recalculateDisplay();
        }
    }

    public MEStorage getInventory() {
        return new SupplierStorage(() -> {
            updateHandler();
            return TileMEChest.this.cellHandler;
        });
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.config;
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public InternalInventory getViewCellStorage() {
        return this.viewCellInventory;
    }

    @Override
    public ItemStack getItemFromTile() {
        return new ItemStack(AEBlocks.ME_CHEST.block());
    }

    public InternalInventory getInternalInventory() {
        return this.internalInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return side == this.getOrientation().getSide(RelativeSide.FRONT) ? this.cellInventory : this.inputInventory;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.cellInventory) {
            this.isCached = false;
            this.cellHandler = null;
            this.fluidHandler = null;
            this.idlePowerUsage = 0;
            this.getMainNode().setIdlePowerUsage(0);
            IStorageProvider.requestUpdate(getMainNode());
            this.markForUpdate();
        }
        if (inv == this.inputInventory && !inv.getStackInSlot(slot).isEmpty()) {
            this.tryToStoreContents();
        }
    }

    private void tryToStoreContents() {
        if (!this.inputInventory.isEmpty()) {
            this.updateHandler();
            if (this.cellHandler != null) {
                var stack = this.inputInventory.getStackInSlot(0);
                if (stack.isEmpty()) {
                    return;
                }

                if (GenericResourcePackageItem.isPackage(stack)) {
                    PackageInsertResult result = GenericResourcePackageItem.tryInsertPackage(stack, this,
                        this.cellHandler, this.mySrc, Actionable.MODULATE);
                    this.inputInventory.setItemDirect(0, result.remainder());
                    return;
                }

                var what = AEItemKey.of(stack);
                if (what == null) {
                    return;
                }

                var inserted = StorageHelper.poweredInsert(this, this.cellHandler, what, stack.getCount(), this.mySrc);
                if (inserted >= stack.getCount()) {
                    this.inputInventory.setItemDirect(0, ItemStack.EMPTY);
                } else {
                    stack.shrink((int) inserted);
                    this.inputInventory.setItemDirect(0, stack);
                }
            }
        }
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (getMainNode().isOnline()) {
            updateHandler();
            if (cellHandler != null) {
                storageMounts.mount(cellHandler, priority);
            }
        }
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.cellHandler = null;
        this.isCached = false;
        IStorageProvider.requestUpdate(getMainNode());
        saveChanges();
    }

    private void blinkCell() {
        this.recalculateDisplay();
    }

    public boolean openGui(EntityPlayer player) {
        this.updateHandler();
        if (this.cellHandler != null && StorageCells.getHandler(this.getCell()) != null) {
            GuiOpener.openGui(player, GuiIds.GuiKey.BASIC_CELL_CHEST, this);
            return true;
        }
        return false;
    }

    public void openCellInventoryGui(EntityPlayer player) {
        GuiOpener.openGui(player, GuiIds.GuiKey.ME_CHEST, this);
    }

    @Override
    public AEColor getColor() {
        return this.paintedColor;
    }

    @Override
    public boolean recolourBlock(EnumFacing side, AEColor newPaintedColor, EntityPlayer who) {
        if (this.paintedColor == newPaintedColor) {
            return false;
        }

        this.paintedColor = newPaintedColor;
        this.saveChanges();
        this.markForUpdate();
        return true;
    }

    private void onCellContentChanged() {
        if (this.cellHandler != null) {
            this.cellHandler.cellInventory.persist();
        }
        this.saveChanges();
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable EnumFacing side) {
        this.updateHandler();
        return side != this.getOrientation().getSide(RelativeSide.FRONT) ? this.fluidHandler : null;
    }

    @Nullable
    public MEStorage getMEStorage(@Nullable EnumFacing side) {
        return side != this.getOrientation().getSide(RelativeSide.FRONT) ? this.getInventory() : null;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.ME_STORAGE && this.getMEStorage(facing) != null) {
            return true;
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && this.getFluidHandler(facing) != null) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.ME_STORAGE) {
            return (T) this.getMEStorage(facing);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.getFluidHandler(facing);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return new ItemStack(AEBlocks.ME_CHEST.block());
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openGui(player, GuiIds.GuiKey.ME_CHEST, this, true);
    }

    private Item getResolvedCellItem() {
        var cell = this.getCell();
        return cell.isEmpty() ? Items.AIR : cell.getItem();
    }

    private static class ChestMonitorHandler extends DelegatingMEInventory {
        private final TileMEChest owner;
        private final StorageCell cellInventory;

        ChestMonitorHandler(TileMEChest owner, StorageCell cellInventory) {
            super(cellInventory);
            this.owner = owner;
            this.cellInventory = cellInventory;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            var inserted = super.insert(what, amount, mode, source);
            if (inserted > 0 && mode == Actionable.MODULATE) {
                owner.blinkCell();
            }
            return inserted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var extracted = super.extract(what, amount, mode, source);
            if (extracted > 0 && mode == Actionable.MODULATE) {
                owner.blinkCell();
            }
            return extracted;
        }
    }

    private static class CellInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return StorageCells.getHandler(stack) != null;
        }
    }

    private class FluidHandler implements IFluidHandler {
        private static final IFluidTankProperties[] EMPTY_TANKS = new IFluidTankProperties[0];

        private boolean canAcceptLiquids() {
            return TileMEChest.this.cellHandler != null;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            if (!canAcceptLiquids()) {
                return EMPTY_TANKS;
            }
            return new IFluidTankProperties[]{
                new FluidTankProperties(null, Fluid.BUCKET_VOLUME, true, false)
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            TileMEChest.this.updateHandler();
            if (resource == null || resource.amount <= 0 || !canAcceptLiquids()) {
                return 0;
            }

            var what = AEFluidKey.of(resource);
            if (what == null) {
                return 0;
            }

            return (int) StorageHelper.poweredInsert(TileMEChest.this,
                TileMEChest.this.cellHandler,
                what,
                resource.amount,
                TileMEChest.this.mySrc,
                Actionable.ofSimulate(!doFill));
        }

        @Override
        public @Nullable FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }

    private class InputInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return false;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (isPowered()) {
                updateHandler();
                if (cellHandler == null) {
                    return false;
                }

                var what = AEItemKey.of(stack);
                if (what != null && cellHandler.insert(what, stack.getCount(), Actionable.SIMULATE, mySrc) > 0) {
                    return true;
                }

                return GenericResourcePackageItem.tryInsertPackage(stack, TileMEChest.this, cellHandler, mySrc,
                    Actionable.SIMULATE).changed();
            }
            return false;
        }
    }
}
