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

import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.IPatternAccessTermContainerHost;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.guisync.ILinkStatusAwareContainer;
import appeng.container.slot.RestrictedInputSlot;
import appeng.core.AELog;
import appeng.core.definitions.AEBlocks;
import appeng.core.network.clientbound.ClearPatternAccessTerminalPacket;
import appeng.core.network.clientbound.PatternAccessTerminalPacket;
import appeng.core.network.clientbound.SetLinkStatusPacket;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiHost;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.tile.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class ContainerPatternAccessTerm extends AEBaseContainer implements ILinkStatusAwareContainer {

    private static long inventorySerial = Long.MIN_VALUE;

    private final IPatternAccessTermContainerHost host;
    private final Reference2ObjectMap<PatternContainer, ContainerTracker> diList = new Reference2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ContainerTracker> byId = new Long2ObjectOpenHashMap<>();
    private final ReferenceSet<PatternContainer> pinnedHosts = new ReferenceOpenHashSet<>();
    @GuiSync(1)
    public ShowPatternProviders showPatternProviders = ShowPatternProviders.VISIBLE;
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public ContainerPatternAccessTerm(InventoryPlayer playerInventory, IPatternAccessTermContainerHost host) {
        super(playerInventory, host);
        this.host = host;
        if (host instanceof WirelessTerminalGuiHost<?> wirelessHost) {
            setupUpgrades(wirelessHost.getUpgrades());
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                wirelessHost.getSingularityStorage(), 0, 0, 0);
            slot.setStackLimit(1);
            this.addSlot(slot, SlotSemantics.WIRELESS_SINGULARITY);
        }
        this.addPlayerInventorySlots(0, 0);
    }

    @Nullable
    private static Class<? extends PatternContainer> tryCastMachineToContainer(Class<?> machineClass) {
        if (PatternContainer.class.isAssignableFrom(machineClass)) {
            return machineClass.asSubclass(PatternContainer.class);
        }
        return null;
    }

    public ShowPatternProviders getShownProviders() {
        return this.showPatternProviders;
    }

    public ILinkStatus getLinkStatus() {
        return this.linkStatus;
    }

    @Override
    public void setLinkStatus(ILinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @Override
    public void broadcastChanges() {
        if (isClientSide()) {
            return;
        }

        this.showPatternProviders = this.host.getConfigManager().getSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS);

        super.broadcastChanges();

        updateLinkStatus();

        if (this.showPatternProviders != ShowPatternProviders.NOT_FULL) {
            this.pinnedHosts.clear();
        }

        IGrid grid = getGrid();
        VisitorState state = new VisitorState();
        if (grid != null) {
            for (Class<?> machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    visitPatternProviderHosts(grid, machineClass.asSubclass(PatternContainer.class), state);
                }
            }

            this.pinnedHosts.removeIf(container -> container.getGrid() != grid);
        } else {
            this.pinnedHosts.clear();
        }

        if (!hasSameVisibleContainers(state.visibleContainers)) {
            state.forceFullUpdate = true;
        }

        if (state.total != this.diList.size() || state.forceFullUpdate) {
            sendFullUpdate(grid);
        } else {
            sendIncrementalUpdate();
        }
    }

    @Nullable
    private IGrid getGrid() {
        appeng.api.networking.IGridNode node = this.host.getGridNode();
        if (node != null && node.isActive()) {
            return node.grid();
        }
        return null;
    }

    private boolean isFull(PatternContainer container) {
        for (int i = 0; i < container.getTerminalPatternInventory().size(); i++) {
            if (container.getTerminalPatternInventory().getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isVisible(PatternContainer container) {
        boolean visible = container.isVisibleInTerminal();
        return switch (getShownProviders()) {
            case VISIBLE -> visible;
            case NOT_FULL -> visible && (this.pinnedHosts.contains(container) || !isFull(container));
            case ALL -> true;
        };
    }

    private boolean hasSameVisibleContainers(ReferenceSet<PatternContainer> visibleContainers) {
        if (visibleContainers.size() != this.diList.size()) {
            return false;
        }

        for (PatternContainer container : this.diList.keySet()) {
            if (!visibleContainers.contains(container)) {
                return false;
            }
        }

        return true;
    }

    private <T extends PatternContainer> void visitPatternProviderHosts(IGrid grid, Class<T> machineClass,
                                                                        VisitorState state) {
        for (T container : grid.getActiveMachines(machineClass)) {
            if (!isVisible(container)) {
                continue;
            }

            state.visibleContainers.add(container);

            if (getShownProviders() == ShowPatternProviders.NOT_FULL) {
                this.pinnedHosts.add(container);
            }

            ContainerTracker tracker = this.diList.get(container);
            if (tracker == null || !tracker.group.equals(container.getTerminalGroup())) {
                state.forceFullUpdate = true;
            }

            state.total++;
        }
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        final ContainerTracker inv = this.byId.get(id);
        if (inv == null) {
            return;
        }
        if (slot < 0 || slot >= inv.server.size()) {
            AELog.warn("Client refers to invalid slot %d of inventory %s", slot, inv.container);
            return;
        }

        final ItemStack stackInSlot = inv.server.getStackInSlot(slot);
        FilteredInternalInventory patternSlot = new FilteredInternalInventory(inv.server.getSlotInv(slot), new PatternSlotFilter());
        ItemStack carried = getCarried();

        switch (action) {
            case PICKUP_OR_SET_DOWN -> {
                if (!carried.isEmpty()) {
                    ItemStack inSlot = patternSlot.getStackInSlot(0);
                    if (inSlot.isEmpty()) {
                        setCarried(patternSlot.addItems(carried));
                    } else {
                        inSlot = inSlot.copy();
                        final ItemStack inHand = carried.copy();

                        patternSlot.setItemDirect(0, ItemStack.EMPTY);
                        setCarried(ItemStack.EMPTY);

                        setCarried(patternSlot.addItems(inHand.copy()));

                        if (getCarried().isEmpty()) {
                            setCarried(inSlot);
                        } else {
                            setCarried(inHand);
                            patternSlot.setItemDirect(0, inSlot);
                        }
                    }
                } else {
                    setCarried(patternSlot.getStackInSlot(0));
                    patternSlot.setItemDirect(0, ItemStack.EMPTY);
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                if (!carried.isEmpty()) {
                    ItemStack extra = carried.splitStack(1);
                    if (!extra.isEmpty()) {
                        extra = patternSlot.addItems(extra);
                    }
                    if (!extra.isEmpty()) {
                        carried.grow(extra.getCount());
                    }
                } else if (!stackInSlot.isEmpty()) {
                    setCarried(patternSlot.extractItem(0, (stackInSlot.getCount() + 1) / 2, false));
                }
            }
            case SHIFT_CLICK -> {
                ItemStack stack = patternSlot.getStackInSlot(0).copy();
                if (!player.inventory.addItemStackToInventory(stack)) {
                    patternSlot.setItemDirect(0, stack);
                } else {
                    patternSlot.setItemDirect(0, ItemStack.EMPTY);
                }
            }
            case MOVE_REGION -> {
                for (int x = 0; x < inv.server.size(); x++) {
                    FilteredInternalInventory slotInventory = new FilteredInternalInventory(inv.server.getSlotInv(x), new PatternSlotFilter());
                    ItemStack slotStack = slotInventory.getStackInSlot(0);
                    if (!player.inventory.addItemStackToInventory(slotStack)) {
                        slotInventory.setItemDirect(0, slotStack);
                    } else {
                        slotInventory.setItemDirect(0, ItemStack.EMPTY);
                    }
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.capabilities.isCreativeMode && carried.isEmpty()) {
                    setCarried(stackInSlot.isEmpty() ? ItemStack.EMPTY : stackInSlot.copy());
                }
            }
            default -> {
            }
        }
    }

    public void quickMovePattern(EntityPlayerMP player, int clickedSlot, LongCollection allowedPatternContainers) {
        if (clickedSlot < 0 || clickedSlot >= this.inventorySlots.size()) {
            return;
        }

        Slot sourceSlot = getSlot(clickedSlot);
        if (!isPlayerSideSlot(sourceSlot)) {
            return;
        }

        ItemStack sourceStack = sourceSlot.getStack();
        if (sourceStack.getCount() != 1) {
            return;
        }

        Object pattern = PatternDetailsHelper.decodePattern(sourceStack, player.world);
        if (pattern == null) {
            return;
        }
        boolean molecularAssemblerPattern = pattern instanceof IMolecularAssemblerSupportedPattern;

        List<ContainerTracker> targets = new ObjectArrayList<>();
        LongIterator allowedIds = allowedPatternContainers.iterator();
        while (allowedIds.hasNext()) {
            long allowedId = allowedIds.nextLong();
            ContainerTracker targetInventory = this.byId.get(allowedId);
            if (targetInventory != null && isVisible(targetInventory.container)) {
                AEItemKey icon = targetInventory.group.icon();
                boolean molecularAssembler = icon != null && icon.is(AEBlocks.MOLECULAR_ASSEMBLER.item());
                if (molecularAssemblerPattern == molecularAssembler) {
                    targets.add(targetInventory);
                }
            }
        }

        if (targets.stream().map(target -> target.group).distinct().count() != 1) {
            return;
        }

        for (ContainerTracker target : targets) {
            FilteredInternalInventory targetContainer = new FilteredInternalInventory(target.server, new PatternSlotFilter());
            if (targetContainer.addItems(sourceStack).isEmpty()) {
                sourceSlot.putStack(ItemStack.EMPTY);
                return;
            }
        }
    }

    private void sendFullUpdate(@Nullable IGrid grid) {
        this.byId.clear();
        this.diList.clear();

        sendPacketToClient(new ClearPatternAccessTerminalPacket());

        if (grid == null) {
            return;
        }

        for (Class<?> machineClass : grid.getMachineClasses()) {
            Class<? extends PatternContainer> containerClass = tryCastMachineToContainer(machineClass);
            if (containerClass == null) {
                continue;
            }

            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (isVisible(container)) {
                    this.diList.put(container, new ContainerTracker(container, container.getTerminalPatternInventory(),
                        container.getTerminalGroup()));
                }
            }
        }

        for (ContainerTracker inv : this.diList.values()) {
            this.byId.put(inv.serverId, inv);
            sendPacketToClient(inv.createFullPacket());
        }
    }

    private void sendIncrementalUpdate() {
        for (ContainerTracker inv : this.diList.values()) {
            PatternAccessTerminalPacket packet = inv.createUpdatePacket();
            if (packet != null) {
                sendPacketToClient(packet);
            }
        }
    }

    protected void updateLinkStatus() {
        ILinkStatus linkStatus = this.host.getLinkStatus();
        if (!Objects.equals(this.linkStatus, linkStatus)) {
            this.linkStatus = linkStatus;
            sendPacketToClient(new SetLinkStatusPacket(linkStatus));
        }
    }

    private static final class VisitorState {
        private final ReferenceSet<PatternContainer> visibleContainers = new ReferenceOpenHashSet<>();
        private int total;
        private boolean forceFullUpdate;
    }

    private static final class ContainerTracker {
        private final PatternContainer container;
        private final long sortBy;
        private final long serverId = inventorySerial++;
        private final PatternContainerGroup group;
        private final InternalInventory client;
        private final InternalInventory server;

        private ContainerTracker(PatternContainer container, InternalInventory patterns, PatternContainerGroup group) {
            this.container = container;
            this.server = patterns;
            this.client = new AppEngInternalInventory(this.server.size());
            this.group = group;
            this.sortBy = container.getTerminalSortOrder();
        }

        private static boolean isDifferent(ItemStack a, ItemStack b) {
            if (a.isEmpty() && b.isEmpty()) {
                return false;
            }

            if (a.isEmpty() || b.isEmpty()) {
                return true;
            }

            return !ItemStack.areItemsEqual(a, b) || !ItemStack.areItemStackTagsEqual(a, b);
        }

        private PatternAccessTerminalPacket createFullPacket() {
            Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(this.server.size());
            for (int i = 0; i < this.server.size(); i++) {
                ItemStack stack = this.server.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    slots.put(i, stack);
                }
            }

            return PatternAccessTerminalPacket.fullUpdate(this.serverId, this.server.size(), this.sortBy, this.group,
                slots);
        }

        @Nullable
        private PatternAccessTerminalPacket createUpdatePacket() {
            IntList changedSlots = detectChangedSlots();
            if (changedSlots == null) {
                return null;
            }

            Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(changedSlots.size());
            for (int i = 0; i < changedSlots.size(); i++) {
                int slot = changedSlots.getInt(i);
                ItemStack stack = this.server.getStackInSlot(slot);
                this.client.setItemDirect(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                slots.put(slot, stack);
            }

            return PatternAccessTerminalPacket.incrementalUpdate(this.serverId, slots);
        }

        @Nullable
        private IntList detectChangedSlots() {
            IntList changedSlots = null;
            for (int i = 0; i < this.server.size(); i++) {
                if (isDifferent(this.server.getStackInSlot(i), this.client.getStackInSlot(i))) {
                    if (changedSlots == null) {
                        changedSlots = new IntArrayList();
                    }
                    changedSlots.add(i);
                }
            }
            return changedSlots;
        }
    }

    private static final class PatternSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }
    }
}
