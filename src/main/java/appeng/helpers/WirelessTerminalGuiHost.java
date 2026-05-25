/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 */
package appeng.helpers;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.Locatables;
import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.implementations.guiobjects.IPortableTerminal;
import appeng.api.implementations.guiobjects.ItemGuiHost;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.SupplierStorage;
import appeng.api.util.IConfigManager;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.container.ISubGui;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.core.gui.locator.ItemGuiHostLocator;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.items.contents.StackDependentSupplier;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.items.tools.powered.WirelessTerminals;
import appeng.me.cluster.implementations.QuantumCluster;
import appeng.me.helpers.PlayerSource;
import appeng.me.storage.NullInventory;
import appeng.tile.networking.TileWirelessAccessPoint;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.SupplierInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.text.ITextComponent;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class WirelessTerminalGuiHost<T extends WirelessTerminalItem> extends ItemGuiHost<T>
    implements IPortableTerminal, IActionHost, KeyTypeSelectionHost, IViewCellStorage, InternalInventoryHost {

    private static final String ENTANGLED_SINGULARITY_ID = "entangled_singularity_id";
    private static final double QUANTUM_BRIDGE_DRAIN_PER_TICK = 22.5;

    private final BiConsumer<EntityPlayer, ISubGui> returnToMainContainer;
    private final WirelessTerminalItem terminalItem;
    private final MEStorage storage;
    private final SupplierInternalInventory<InternalInventory> viewCellStorage;
    private final SupplierInternalInventory<InternalInventory> singularityStorage;
    private final StackDependentSupplier<WirelessTerminalMagnetHost> magnetHost;
    protected double currentDistanceFromGrid = Double.MAX_VALUE;
    protected double currentRemainingRange = Double.MIN_VALUE;
    @Nullable
    private IWirelessAccessPoint currentAccessPoint;
    @Nullable
    private IActionHost currentQuantumBridge;
    private boolean currentQuantumBridgeUnpowered;
    private ILinkStatus quantumLinkStatus = ILinkStatus.ofDisconnected();
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public WirelessTerminalGuiHost(T item, EntityPlayer player, ItemGuiHostLocator locator,
                                   BiConsumer<EntityPlayer, ISubGui> returnToMainContainer) {
        this(item, item, player, locator, returnToMainContainer);
    }

    public WirelessTerminalGuiHost(T stackItem, WirelessTerminalItem terminalItem, EntityPlayer player,
                                   ItemGuiHostLocator locator,
                                   BiConsumer<EntityPlayer, ISubGui> returnToMainContainer) {
        super(stackItem, player, locator);
        this.returnToMainContainer = returnToMainContainer;
        this.terminalItem = terminalItem;
        this.storage = new SupplierStorage(new StackDependentSupplier<>(this::getItemStack, this::getStorageFromStack));
        this.viewCellStorage = new SupplierInternalInventory<>(
            new StackDependentSupplier<>(this::getItemStack, stack -> createViewCellStorage(player, stack, terminalItem)));
        this.singularityStorage = new SupplierInternalInventory<>(
            new StackDependentSupplier<>(this::getItemStack, stack -> createSingularityStorage(player, stack)));
        this.magnetHost = new StackDependentSupplier<>(
            this::getItemStack, stack -> new WirelessTerminalMagnetHost(stack, terminalItem));
        updateConnectedAccessPoint();
        updateLinkStatus();
    }

    private static InternalInventory createViewCellStorage(EntityPlayer player, ItemStack stack,
                                                           WirelessTerminalItem terminal) {
        var viewCellStorage = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inv) {
                inv.writeToNBT(WirelessTerminals.getTerminalData(stack, terminal), WirelessTerminals.TAG_VIEW_CELLS);
            }

            @Override
            public boolean isClientSide() {
                return player.world.isRemote;
            }
        }, 5);
        NBTTagCompound tag = WirelessTerminals.getExistingTerminalData(stack, terminal);
        if (tag != null) {
            viewCellStorage.readFromNBT(tag, WirelessTerminals.TAG_VIEW_CELLS);
        }
        return viewCellStorage;
    }

    private static InternalInventory createSingularityStorage(EntityPlayer player, ItemStack stack) {
        var singularityStorage = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inv) {
                NBTTagCompound tag = WirelessTerminals.getOrCreateTag(stack);
                ItemStack singularity = inv.getStackInSlot(0);
                if (singularity.isEmpty()) {
                    tag.removeTag(WirelessTerminals.TAG_SINGULARITY);
                } else {
                    tag.setTag(WirelessTerminals.TAG_SINGULARITY, singularity.serializeNBT());
                }
            }

            @Override
            public boolean isClientSide() {
                return player.world.isRemote;
            }
        }, 1, 1);

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(WirelessTerminals.TAG_SINGULARITY, 10)) {
            singularityStorage.setItemDirect(0,
                new ItemStack(tag.getCompoundTag(WirelessTerminals.TAG_SINGULARITY)));
        }
        return singularityStorage;
    }

    private static long getEntangledSingularityFrequency(ItemStack singularity) {
        if (!singularity.hasTagCompound()) {
            return 0;
        }
        NBTTagCompound compound = singularity.getTagCompound();
        if (compound == null) {
            return 0;
        }
        NBTBase tag = compound.getTag(ENTANGLED_SINGULARITY_ID);
        return tag instanceof NBTTagLong frequency ? frequency.getLong() : 0;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return linkStatus;
    }

    public final WirelessTerminalItem getTerminalItem() {
        return this.terminalItem;
    }

    @Nullable
    private MEStorage getStorageFromStack(ItemStack stack) {
        updateConnectedAccessPoint();
        IGridNode node = getActionableNode();
        if (node != null && node.isActive()) {
            return node.grid().getStorageService().getInventory();
        }
        return NullInventory.of();
    }

    @Override
    public MEStorage getInventory() {
        return this.storage;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        final double extracted = Math.min(amt, getItem().getAECurrentPower(getItemStack()));
        if (mode == Actionable.SIMULATE) {
            return extracted;
        }
        return getItem().usePower(getPlayer(), extracted, getItemStack()) ? extracted : 0;
    }

    @Override
    public IConfigManager getConfigManager() {
        return getItem().getConfigManager(this::getItemStack);
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return KeyTypeSelection.forStack(getItemStack(), ignored -> true);
    }

    @Override
    public InternalInventory getViewCellStorage() {
        return this.viewCellStorage;
    }

    @Nullable
    private IGrid getLinkedGrid(ItemStack stack) {
        return getItem().getLinkedGrid(stack, this.terminalItem, getPlayer().world, null);
    }

    public InternalInventory getSingularityStorage() {
        return this.singularityStorage;
    }

    public WirelessTerminalMagnetHost getMagnetHost() {
        return this.magnetHost.get();
    }

    @Override
    public IGridNode getActionableNode() {
        if (this.currentAccessPoint != null) {
            return this.currentAccessPoint.getActionableNode();
        }
        if (this.currentQuantumBridge != null
            && (this.quantumLinkStatus.connected() || this.currentQuantumBridgeUnpowered)) {
            return this.currentQuantumBridge.getActionableNode();
        }
        return null;
    }

    protected AccessPointSignal getAccessPointSignal(IWirelessAccessPoint wap) {
        double rangeLimit = wap.getRange();
        rangeLimit *= rangeLimit;

        var dc = wap.getLocation();
        if (dc.getLevel() == this.getPlayer().world) {
            var offX = dc.getPos().getX() - this.getPlayer().posX;
            var offY = dc.getPos().getY() - this.getPlayer().posY;
            var offZ = dc.getPos().getZ() - this.getPlayer().posZ;
            double r = offX * offX + offY * offY + offZ * offZ;
            if (r < rangeLimit && wap.isActive()) {
                return new AccessPointSignal(r, rangeLimit - r);
            }
        }

        return new AccessPointSignal(Double.MAX_VALUE, Double.MIN_VALUE);
    }

    @Override
    public void tick() {
        updateConnectedAccessPoint();
        consumeIdlePower(Actionable.MODULATE);
        updateLinkStatus();
    }

    protected void updateConnectedAccessPoint() {
        this.currentAccessPoint = null;
        this.currentQuantumBridge = null;
        this.currentQuantumBridgeUnpowered = false;
        this.currentDistanceFromGrid = Double.MAX_VALUE;
        this.currentRemainingRange = Double.MIN_VALUE;

        var targetGrid = getLinkedGrid(getItemStack());
        if (targetGrid != null) {
            IWirelessAccessPoint bestWap = null;
            double bestSqDistance = Double.MAX_VALUE;
            double bestSqRemainingRange = Double.MIN_VALUE;

            for (var wap : targetGrid.getMachines(TileWirelessAccessPoint.class)) {
                var signal = getAccessPointSignal(wap);
                if (signal.distanceSquared < bestSqDistance) {
                    bestSqDistance = signal.distanceSquared;
                    bestWap = wap;
                }
                if (signal.remainingRangeSquared > bestSqRemainingRange) {
                    bestSqRemainingRange = signal.remainingRangeSquared;
                }
            }

            this.currentAccessPoint = bestWap;
            this.currentDistanceFromGrid = Math.sqrt(bestSqDistance);
            this.currentRemainingRange = Math.sqrt(bestSqRemainingRange);
        }

        this.quantumLinkStatus = updateQuantumBridge(targetGrid);
    }

    protected void updateLinkStatus() {
        if (!consumeIdlePower(Actionable.SIMULATE)) {
            this.linkStatus = ILinkStatus.ofDisconnected(GuiText.OutOfPower.text());
        } else if (currentAccessPoint != null) {
            this.linkStatus = ILinkStatus.ofConnected();
        } else if (this.quantumLinkStatus.connected()
            || this.quantumLinkStatus.statusDescription() != null) {
            this.linkStatus = this.quantumLinkStatus;
        } else {
            MutableObject<ITextComponent> errorHolder = new MutableObject<>();
            if (getItem().getLinkedGrid(getItemStack(), this.terminalItem, getPlayer().world, errorHolder::setValue) == null) {
                this.linkStatus = ILinkStatus.ofDisconnected(errorHolder.get());
            } else {
                this.linkStatus = ILinkStatus.ofDisconnected(PlayerMessages.OutOfRange.text());
            }
        }
    }

    @Override
    protected double getPowerDrainPerTick() {
        if (currentAccessPoint != null && currentDistanceFromGrid < Double.MAX_VALUE) {
            return AEConfig.instance().wireless_getDrainRate(currentDistanceFromGrid);
        }
        if (this.quantumLinkStatus.connected() || this.currentQuantumBridgeUnpowered) {
            return QUANTUM_BRIDGE_DRAIN_PER_TICK;
        }
        return 0.0;
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        returnToMainContainer.accept(player, subGui);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getItemStack();
    }

    @Override
    public boolean consumeIdlePower(Actionable action) {
        if (action == Actionable.SIMULATE) {
            rechargeFromQuantumBridge();
        }
        boolean success = super.consumeIdlePower(action);
        if (action == Actionable.SIMULATE) {
            rechargeFromQuantumBridge();
        }
        return success;
    }

    @Override
    public long insert(EntityPlayer player, AEKey what, long amount, Actionable mode) {
        if (isClientSide()) {
            return 0;
        }
        if (getLinkStatus().connected()) {
            return StorageHelper.poweredInsert(this, getInventory(), what, amount, new PlayerSource(player), mode);
        } else {
            var statusText = getLinkStatus().statusDescription();
            if (statusText != null && !mode.isSimulate()) {
                player.sendStatusMessage(statusText, false);
            }
            return 0;
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
    }

    @Override
    public String getCloseHotkey() {
        return this.terminalItem.getWirelessTerminalDefinition().hotkeyName();
    }

    private ILinkStatus updateQuantumBridge(@Nullable IGrid targetGrid) {
        if (!getUpgrades().isInstalled(AEItems.QUANTUM_BRIDGE_CARD.item())) {
            return ILinkStatus.ofDisconnected();
        }

        ItemStack singularity = this.singularityStorage.getStackInSlot(0);
        if (singularity.isEmpty() || !TileQuantumBridge.isValidEntangledSingularity(singularity)) {
            return ILinkStatus.ofDisconnected(GuiText.WirelessTerminalNoSingularity.text());
        }

        long frequency = getEntangledSingularityFrequency(singularity);
        if (frequency == 0) {
            return ILinkStatus.ofDisconnected(GuiText.WirelessTerminalNoSingularity.text());
        }

        IActionHost quantumBridge = findQuantumBridge(frequency);
        if (quantumBridge == null) {
            return ILinkStatus.ofDisconnected(GuiText.WirelessTerminalBridgeMissing.text());
        }

        IGridNode node = quantumBridge.getActionableNode();
        if (node == null || !node.isActive()) {
            return ILinkStatus.ofDisconnected(GuiText.WirelessTerminalBridgeMissing.text());
        }

        IGrid quantumGrid = node.grid();
        if (targetGrid != null && quantumGrid != targetGrid) {
            return ILinkStatus.ofDisconnected(GuiText.WirelessTerminalDifferentNetwork.text());
        }
        if (!quantumGrid.getEnergyService().isNetworkPowered()) {
            this.currentQuantumBridge = quantumBridge;
            this.currentQuantumBridgeUnpowered = true;
            return ILinkStatus.ofDisconnected(GuiText.WirelessTerminalBridgeUnpowered.text());
        }

        this.currentQuantumBridge = quantumBridge;
        this.currentQuantumBridgeUnpowered = false;
        return ILinkStatus.ofConnected();
    }

    @Nullable
    private IActionHost findQuantumBridge(long frequency) {
        if (isCurrentQuantumBridgeValid(frequency)) {
            return this.currentQuantumBridge;
        }

        IActionHost quantumBridge = Locatables.quantumNetworkBridges().get(getPlayer().world, frequency);
        if (quantumBridge == null) {
            quantumBridge = Locatables.quantumNetworkBridges().get(getPlayer().world, -frequency);
        }
        return quantumBridge;
    }

    private boolean isCurrentQuantumBridgeValid(long frequency) {
        if (this.currentQuantumBridge == null) {
            return false;
        }
        if (this.currentQuantumBridge instanceof QuantumCluster quantumCluster) {
            TileQuantumBridge center = quantumCluster.getCenter();
            return center != null
                && (center.getQEFrequency() == frequency || center.getQEFrequency() == -frequency);
        }
        IGridNode node = this.currentQuantumBridge.getActionableNode();
        return node != null && node.isActive();
    }

    private void rechargeFromQuantumBridge() {
        if (!this.quantumLinkStatus.connected() && !this.currentQuantumBridgeUnpowered) {
            return;
        }

        IGridNode node = getActionableNode();
        if (node == null || !node.isActive()) {
            return;
        }

        ItemStack stack = getItemStack();
        double missing = getItem().getAEMaxPower(stack) - getItem().getAECurrentPower(stack);
        if (missing <= 0) {
            return;
        }

        var energyService = node.grid().getEnergyService();
        double safePower = energyService.getStoredPower() - energyService.getMaxStoredPower() / 2;
        if (safePower <= 0) {
            return;
        }

        double extracted = energyService.extractAEPower(Math.min(missing, safePower), Actionable.MODULATE,
            PowerMultiplier.ONE);
        getItem().injectAEPower(stack, extracted, Actionable.MODULATE);
    }

    protected static final class AccessPointSignal {
        final double distanceSquared;
        final double remainingRangeSquared;

        AccessPointSignal(double distanceSquared, double remainingRangeSquared) {
            this.distanceSquared = distanceSquared;
            this.remainingRangeSquared = remainingRangeSquared;
        }
    }
}
