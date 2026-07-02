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

package ae2.container;

import ae2.api.behaviors.ContainerItemContext;
import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.config.Actionable;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.container.guisync.DataSynchronization;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.CraftingMatrixSlot;
import ae2.container.slot.CraftingTermSlot;
import ae2.container.slot.DisabledSlot;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.FakeSlotFilterSupport;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.AELog;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.GuiDataSyncPacket;
import ae2.core.network.serverbound.GuiActionPacket;
import ae2.helpers.InventoryAction;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.me.helpers.PlayerSource;
import ae2.parts.AEBasePart;
import ae2.util.ConfigGuiInventory;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public abstract class AEBaseContainer extends Container {
    private static final int MAX_STRING_LENGTH = GuiActionPacket.MAX_JSON_PAYLOAD_LENGTH;
    private static final int MAX_CONTAINER_TRANSFER_ITERATIONS = 256;
    private static final String HIDE_SLOT = "HideSlot";

    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int PLAYER_HOTBAR_SIZE = 9;
    @Nullable
    protected final ItemGuiHost<?> itemGuiHost;
    private final InventoryPlayer playerInventory;
    private final IActionSource actionSource;
    private final DataSynchronization dataSync = new DataSynchronization(this);
    private final Reference2ObjectMap<Slot, SlotSemantic> semanticBySlot = new Reference2ObjectOpenHashMap<>();
    private final Object2ObjectMap<SlotSemantic, List<Slot>> slotsBySemantic = new Object2ObjectOpenHashMap<>();
    private final IntSet lockedPlayerInventorySlots = new IntOpenHashSet();
    private final ReferenceSet<Slot> clientSideSlots = new ReferenceOpenHashSet<>();
    private final Object2ObjectMap<String, ClientAction<?>> clientActions = new Object2ObjectOpenHashMap<>();
    @Nullable
    private final TileEntity tileEntityHost;
    @Nullable
    private final IPart partHost;
    private boolean containerValid = true;
    @Nullable
    private GuiHostLocator locator;
    private boolean returnedFromSubScreen;
    @Nullable
    private Container returnToContainerOverride;
    private boolean externalGuiReturn;
    @Nullable
    private ITextComponent guiTitle;

    protected AEBaseContainer(InventoryPlayer playerInventory, @Nullable Object host) {
        this.playerInventory = playerInventory;
        this.tileEntityHost = host instanceof TileEntity tileEntity ? tileEntity : null;
        this.partHost = host instanceof IPart part ? part : null;
        this.itemGuiHost = host instanceof ItemGuiHost<?> guiHost ? guiHost : null;

        if (host != null && this.tileEntityHost == null && this.partHost == null && this.itemGuiHost == null) {
            throw new IllegalArgumentException("Must have a valid host, instead " + host + " in " + playerInventory);
        }

        if (this.itemGuiHost != null && this.itemGuiHost.getPlayerInventorySlot() != null) {
            lockPlayerInventorySlot(this.itemGuiHost.getPlayerInventorySlot());
        }

        this.actionSource = new PlayerSource(playerInventory.player, getActionHost());
        registerClientAction(HIDE_SLOT, String.class, this::hideSlot);
    }

    public static int getOffhandPlayerInventorySlot(int mainInventorySize, int armorInventorySize) {
        return mainInventorySize + armorInventorySize;
    }

    static boolean isSwapClickOnPlayerInventorySlot(ClickType clickType, int swapTargetSlot, int playerInventorySlot) {
        return clickType == ClickType.SWAP && swapTargetSlot == playerInventorySlot;
    }

    public InventoryPlayer getPlayerInventory() {
        return this.playerInventory;
    }

    public EntityPlayer getPlayer() {
        return this.playerInventory.player;
    }

    public IActionSource getActionSource() {
        return this.actionSource;
    }

    @Nullable
    public TileEntity getTileEntity() {
        return this.tileEntityHost;
    }

    @Nullable
    public IPart getPart() {
        return this.partHost;
    }

    @Nullable
    public ItemGuiHost<?> getItemGuiHost() {
        return this.itemGuiHost;
    }

    public Object getTarget() {
        if (this.tileEntityHost != null) {
            return this.tileEntityHost;
        }
        if (this.partHost != null) {
            return this.partHost;
        }
        return this.itemGuiHost;
    }

    @Nullable
    protected final IActionHost getActionHost() {
        if (this.itemGuiHost instanceof IActionHost actionHost) {
            return actionHost;
        }

        if (this.tileEntityHost instanceof IActionHost actionHost) {
            return actionHost;
        }

        if (this.partHost instanceof IActionHost actionHost) {
            return actionHost;
        }

        return null;
    }

    public void lockPlayerInventorySlot(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= this.playerInventory.getSizeInventory()) {
            throw new IllegalArgumentException("cannot lock player inventory slot: " + inventorySlot);
        }
        this.lockedPlayerInventorySlots.add(inventorySlot);
    }

    public boolean isPlayerInventorySlotLocked(int inventorySlot) {
        return this.lockedPlayerInventorySlots.contains(inventorySlot);
    }

    public final boolean isPlayerInventorySlotLocked(@Nullable Slot slot) {
        return isLockedPlayerInventorySlot(slot);
    }

    private int getOffhandPlayerInventorySlot() {
        return getOffhandPlayerInventorySlot(this.playerInventory.mainInventory.size(), this.playerInventory.armorInventory.size());
    }

    protected void addPlayerInventorySlots(int left, int top) {
        for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
            for (int column = 0; column < PLAYER_INVENTORY_COLUMNS; column++) {
                int slotIndex = column + row * PLAYER_INVENTORY_COLUMNS + PLAYER_HOTBAR_SIZE;
                Slot slot = isPlayerInventorySlotLocked(slotIndex)
                    ? new DisabledSlot(playerInventory, slotIndex, left + column * 18, top + row * 18)
                    : new Slot(playerInventory, slotIndex, left + column * 18, top + row * 18);
                addSlotToContainer(slot, SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int column = 0; column < PLAYER_HOTBAR_SIZE; column++) {
            Slot slot = isPlayerInventorySlotLocked(column)
                ? new DisabledSlot(playerInventory, column, left + column * 18, top + 58)
                : new Slot(playerInventory, column, left + column * 18, top + 58);
            addSlotToContainer(slot, SlotSemantics.PLAYER_HOTBAR);
        }
    }

    protected Slot addSlotToContainer(Slot slot, SlotSemantic semantic) {
        Slot added = super.addSlotToContainer(slot);
        semanticBySlot.put(added, semantic);
        slotsBySemantic.computeIfAbsent(semantic, ignored -> new ObjectArrayList<>()).add(added);
        if (added instanceof AppEngSlot appEngSlot) {
            appEngSlot.setContainer(this);
        }
        return added;
    }

    public Slot addSlot(Slot slot, SlotSemantic semantic) {
        return addSlotToContainer(slot, semantic);
    }

    @Override
    protected Slot addSlotToContainer(Slot slot) {
        return addSlotToContainer(slot, SlotSemantics.STORAGE);
    }

    public List<Slot> getSlots(SlotSemantic semantic) {
        return slotsBySemantic.getOrDefault(semantic, Collections.emptyList());
    }

    @SuppressWarnings("UnusedReturnValue")
    public Slot addClientSideSlot(Slot slot, @Nullable SlotSemantic semantic) {
        Preconditions.checkState(isClientSide(), "Can only add client-side slots on the client");
        if (!this.clientSideSlots.add(slot)) {
            throw new IllegalStateException("Client-side slot already exists");
        }

        slot.slotNumber = this.inventorySlots.size();
        this.inventorySlots.add(slot);
        this.inventoryItemStacks.add(slot.getStack().copy());

        if (semantic != null) {
            this.semanticBySlot.put(slot, semantic);
            this.slotsBySemantic.computeIfAbsent(semantic, ignored -> new ObjectArrayList<>()).add(slot);
        }

        if (slot instanceof AppEngSlot appEngSlot) {
            appEngSlot.setContainer(this);
        }

        return slot;
    }

    public void removeClientSideSlot(Slot slot) {
        if (!this.clientSideSlots.remove(slot)) {
            throw new IllegalStateException("Trying to remove slot which isn't a client-side slot");
        }

        int slotIndex = this.inventorySlots.indexOf(slot);
        if (slotIndex < 0) {
            throw new IllegalStateException("Trying to remove slot which isn't currently in the container");
        }

        this.inventorySlots.remove(slotIndex);
        this.inventoryItemStacks.remove(slotIndex);

        SlotSemantic semantic = this.semanticBySlot.remove(slot);
        if (semantic != null) {
            List<Slot> slots = this.slotsBySemantic.get(semantic);
            if (slots != null) {
                slots.remove(slot);
                if (slots.isEmpty()) {
                    this.slotsBySemantic.remove(semantic);
                }
            }
        }

        for (int i = slotIndex; i < this.inventorySlots.size(); i++) {
            this.inventorySlots.get(i).slotNumber = i;
        }
    }

    public boolean isClientSideSlot(Slot slot) {
        return this.clientSideSlots.contains(slot);
    }

    @Nullable
    public SlotSemantic getSlotSemantic(Slot slot) {
        return semanticBySlot.get(slot);
    }

    public boolean isPlayerSideSlot(Slot slot) {
        if (slot.inventory == this.playerInventory) {
            return true;
        }
        SlotSemantic semantic = getSlotSemantic(slot);
        return semantic != null && semantic.playerSide();
    }

    protected int getQuickMovePriority(Slot slot) {
        SlotSemantic semantic = getSlotSemantic(slot);
        return semantic != null ? semantic.quickMovePriority() : 0;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (isClickOnLockedPlayerInventorySlot(slotId, clickTypeIn, dragType)) {
            return ItemStack.EMPTY;
        }

        int offhandInventorySlot = getOffhandPlayerInventorySlot();
        if (isPlayerInventorySlotLocked(offhandInventorySlot)
            && isSwapClickOnPlayerInventorySlot(clickTypeIn, dragType, offhandInventorySlot)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = super.slotClick(slotId, dragType, clickTypeIn, player);

        if (isServerSide() && player instanceof EntityPlayerMP serverPlayer && serverPlayer.openContainer == this) {
            syncInventoryActionState(serverPlayer);
        }

        return result;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (!isValidContainer()) {
            return false;
        }

        if (this.itemGuiHost != null && !this.itemGuiHost.isValid()) {
            setValidContainer(false);
            return false;
        }

        if (tileEntityHost == null) {
            if (this.partHost instanceof AEBasePart basePart) {
                IPartHost host = basePart.getHost();
                if (host == null || !host.isInWorld() || host.getPart(basePart.getSide()) != basePart) {
                    setValidContainer(false);
                    return false;
                }
            }
            return true;
        }
        if (tileEntityHost.isInvalid() || tileEntityHost.getWorld() != player.world) {
            setValidContainer(false);
            return false;
        }
        return canInteractiveDistance(player, tileEntityHost);
    }

    protected boolean canInteractiveDistance(@NotNull EntityPlayer player, @NotNull TileEntity tileEntityHost) {
        return player.getDistanceSq(tileEntityHost.getPos()) <= 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (isClientSide()) {
            return ItemStack.EMPTY;
        }

        if (index < 0 || index >= inventorySlots.size()) {
            return ItemStack.EMPTY;
        }

        Slot clickSlot = inventorySlots.get(index);
        if (!isValidQuickMoveSource(clickSlot, player)) {
            return ItemStack.EMPTY;
        }

        ItemStack stackToMove = clickSlot.getStack();
        if (stackToMove.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = stackToMove.copy();
        boolean changed = false;
        boolean fromPlayerSide = isPlayerSideSlot(clickSlot);
        if (fromPlayerSide) {
            ItemStack remainder = quickMoveToOtherSlots(stackToMove.copy(), true);
            int movedToSlots = originalStack.getCount() - remainder.getCount();
            if (movedToSlots > 0) {
                clickSlot.decrStackSize(movedToSlots);
                changed = true;
            }
        }

        stackToMove = clickSlot.getStack();
        if (stackToMove.isEmpty()) {
            if (changed) {
                clickSlot.onSlotChanged();
                detectAndSendChanges();
            }
            return ItemStack.EMPTY;
        }

        if (fromPlayerSide) {
            ItemStack remainder = transferStackToContainerWithRemainder(stackToMove.copy());
            if (!ItemStack.areItemStacksEqual(stackToMove, remainder)
                || !ItemStack.areItemStackTagsEqual(stackToMove, remainder)) {
                clickSlot.putStack(remainder);
                changed = true;
            }

            stackToMove = clickSlot.getStack();
            if (stackToMove.isEmpty()) {
                if (changed) {
                    clickSlot.onSlotChanged();
                    detectAndSendChanges();
                }
                return originalStack;
            }
        }

        ItemStack stackToMoveCopy = stackToMove.copy();
        ItemStack remainder = quickMoveToOtherSlots(stackToMoveCopy, fromPlayerSide);
        int moved = stackToMove.getCount() - remainder.getCount();
        if (moved <= 0) {
            if (changed) {
                clickSlot.onSlotChanged();
                detectAndSendChanges();
            }
            return ItemStack.EMPTY;
        }

        clickSlot.decrStackSize(moved);
        changed = true;
        clickSlot.onSlotChanged();
        if (changed) {
            detectAndSendChanges();
        }
        return originalStack;
    }

    private ItemStack quickMoveToOtherSlots(ItemStack stackToMove, boolean fromPlayerSide) {
        List<Slot> destinationSlots = getQuickMoveDestinationSlots(stackToMove, fromPlayerSide);

        for (Slot destination : destinationSlots) {
            if (destination.getHasStack()) {
                stackToMove = insertIntoSlot(destination, stackToMove);
                if (stackToMove.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        for (Slot destination : destinationSlots) {
            if (!destination.getHasStack()) {
                stackToMove = insertIntoSlot(destination, stackToMove);
                if (stackToMove.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        return stackToMove;
    }

    protected List<Slot> getQuickMoveDestinationSlots(ItemStack stackToMove, boolean fromPlayerSide) {
        List<Slot> destinationSlots = new ObjectArrayList<>();
        for (Slot candidateSlot : inventorySlots) {
            if (isValidQuickMoveDestination(candidateSlot, stackToMove, fromPlayerSide)) {
                destinationSlots.add(candidateSlot);
            }
        }
        destinationSlots.sort(Comparator.comparingInt(this::getQuickMovePriority).reversed());
        return destinationSlots;
    }

    protected boolean isValidQuickMoveDestination(Slot candidateSlot, ItemStack stackToMove, boolean fromPlayerSide) {
        return isPlayerSideSlot(candidateSlot) != fromPlayerSide
            && !(candidateSlot instanceof FakeSlot)
            && !(candidateSlot instanceof CraftingMatrixSlot)
            && !isLockedPlayerInventorySlot(candidateSlot)
            && candidateSlot.isItemValid(stackToMove);
    }

    private boolean isClickOnLockedPlayerInventorySlot(int slotId, ClickType clickType, int swapTargetSlot) {
        if (slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);
            if (isLockedPlayerInventorySlot(slot)) {
                return true;
            }
        }
        if (clickType == ClickType.SWAP) {
            return isPlayerInventorySlotLocked(swapTargetSlot);
        }
        return false;
    }

    protected final boolean isValidQuickMoveSource(@Nullable Slot slot, @Nullable EntityPlayer player) {
        return slot != null && slot.canTakeStack(player) && !isLockedPlayerInventorySlot(slot);
    }

    protected final boolean isLockedPlayerInventorySlot(@Nullable Slot slot) {
        return slot != null && slot.inventory == this.playerInventory && isPlayerInventorySlotLocked(slot.getSlotIndex());
    }

    private ItemStack insertIntoSlot(Slot destination, ItemStack stackToMove) {
        if (stackToMove.isEmpty() || !destination.isItemValid(stackToMove)) {
            return stackToMove;
        }

        ItemStack existing = destination.getStack();
        int maxStackSize = Math.min(destination.getSlotStackLimit(), stackToMove.getMaxStackSize());
        if (existing.isEmpty()) {
            int amount = Math.min(stackToMove.getCount(), maxStackSize);
            ItemStack inserted = stackToMove.copy();
            inserted.setCount(amount);
            destination.putStack(inserted);
            stackToMove.shrink(amount);
            destination.onSlotChanged();
            return stackToMove.isEmpty() ? ItemStack.EMPTY : stackToMove;
        }

        if (!ItemStack.areItemsEqual(existing, stackToMove) || !ItemStack.areItemStackTagsEqual(existing, stackToMove)) {
            return stackToMove;
        }

        int amount = Math.min(stackToMove.getCount(), maxStackSize - existing.getCount());
        if (amount <= 0) {
            return stackToMove;
        }
        existing.grow(amount);
        stackToMove.shrink(amount);
        destination.onSlotChanged();
        return stackToMove.isEmpty() ? ItemStack.EMPTY : stackToMove;
    }

    protected int transferStackToContainer(ItemStack input) {
        return 0;
    }

    protected ItemStack transferStackToContainerWithRemainder(ItemStack input) {
        int transferred = transferStackToContainer(input.copy());
        if (transferred <= 0) {
            return input;
        }

        ItemStack remainder = input.copy();
        remainder.shrink(transferred);
        return remainder;
    }

    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        return true;
    }

    public void setFilter(int slotIndex, ItemStack item) {
        setFilter(slotIndex, item, false);
    }

    public void setFilter(int slotIndex, ItemStack item, boolean preferEmptying) {
        if (slotIndex < 0 || slotIndex >= this.inventorySlots.size()) {
            return;
        }

        Slot slot = this.getSlot(slotIndex);
        if (!(slot instanceof AppEngSlot appEngSlot)) {
            return;
        }
        if (!appEngSlot.isSlotEnabled()) {
            return;
        }

        if (slot instanceof FakeSlot fakeSlot) {
            ItemStack filterItem = item;
            if (preferEmptying) {
                GenericStack emptyingFilter = FakeSlotFilterSupport.getEmptyingFilter(fakeSlot, item);
                if (emptyingFilter != null) {
                    filterItem = GenericStack.wrapInItemStack(emptyingFilter);
                }
            }

            if (!fakeSlot.canSetFilterTo(filterItem)) {
                ItemStack preferred = FakeSlotFilterSupport.getPreferredFilterStack(fakeSlot, item);
                if (!preferred.isEmpty()) {
                    filterItem = preferred;
                }
            }

            if (fakeSlot.canSetFilterTo(filterItem)) {
                fakeSlot.putStack(filterItem);
                fakeSlot.onSlotChanged();
            }
        }
    }

    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (slot < 0 || slot >= this.inventorySlots.size()) {
            return;
        }

        Slot targetSlot = this.getSlot(slot);

        if (targetSlot instanceof CraftingTermSlot craftingTermSlot) {
            switch (action) {
                case CRAFT_SHIFT, CRAFT_ALL, CRAFT_ITEM, CRAFT_STACK -> {
                    craftingTermSlot.doClick(action, player);
                    return;
                }
                default -> {
                }
            }
        }

        if (targetSlot instanceof FakeSlot fakeSlot) {
            handleFakeSlotAction(fakeSlot, action);
            return;
        }

        if (targetSlot instanceof AppEngSlot appEngSlot) {
            if (appEngSlot.getInventory() instanceof ConfigGuiInventory configInv) {
                if (configInv.getDelegate().getMode() == GenericStackInv.Mode.STORAGE) {
                    GenericStackInv realInv = configInv.getDelegate();
                    int realInvSlot = appEngSlot.getSlotIndex();

                    if (action == InventoryAction.FILL_ITEM || action == InventoryAction.FILL_ENTIRE_ITEM) {
                        AEKey what = realInv.getKey(realInvSlot);
                        if (what != null) {
                            handleFillingHeldItem(
                                new FillingSource() {
                                    @Override
                                    public long extract(long amount, Actionable mode) {
                                        return realInv.extract(realInvSlot, what, amount, mode);
                                    }

                                    @Override
                                    public long insert(AEKey what, long amount, Actionable mode) {
                                        return realInv.insert(realInvSlot, what, amount, mode);
                                    }
                                },
                                what,
                                action == InventoryAction.FILL_ENTIRE_ITEM);
                        }
                    } else if (action == InventoryAction.EMPTY_ITEM || action == InventoryAction.EMPTY_ENTIRE_ITEM) {
                        handleEmptyHeldItem(
                            (what, amount, mode) -> realInv.insert(realInvSlot, what, amount, mode),
                            action == InventoryAction.EMPTY_ENTIRE_ITEM);
                    }
                }
            }
        }

        if (action == InventoryAction.MOVE_REGION) {
            SlotSemantic slotSemantic = getSlotSemantic(targetSlot);
            if (slotSemantic != null) {
                List<Slot> slotsToMove = new ObjectArrayList<>(getSlots(slotSemantic));
                for (Slot slotToMove : slotsToMove) {
                    if (!isValidQuickMoveSource(slotToMove, player)) {
                        continue;
                    }
                    transferStackInSlot(player, slotToMove.slotNumber);
                }
            } else if (isValidQuickMoveSource(targetSlot, player)) {
                transferStackInSlot(player, targetSlot.slotNumber);
            }
        }
    }

    private void handleFakeSlotAction(FakeSlot fakeSlot, InventoryAction action) {
        ItemStack hand = getCarried();

        switch (action) {
            case PICKUP_OR_SET_DOWN -> fakeSlot.increase(hand);
            case PLACE_SINGLE -> {
                if (!hand.isEmpty()) {
                    ItemStack stack = hand.copy();
                    stack.setCount(1);
                    fakeSlot.increase(stack);
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                ItemStack stack = fakeSlot.getStack();
                if (!stack.isEmpty()) {
                    fakeSlot.decrease(hand);
                } else if (!hand.isEmpty()) {
                    stack = hand.copy();
                    stack.setCount(1);
                    fakeSlot.putStack(stack);
                }
            }
            case EMPTY_ITEM -> {
                EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(hand);
                if (emptyingAction != null) {
                    fakeSlot.setGenericFilter(new GenericStack(emptyingAction.what(), emptyingAction.maxAmount()));
                }
            }
            default -> {
            }
        }
    }

    protected int getPlaceableAmount(Slot slot, AEItemKey what) {
        if (!slot.isItemValid(what.toStack())) {
            return 0;
        }

        ItemStack currentItem = slot.getStack();
        if (currentItem.isEmpty()) {
            ItemStack readOnlyStack = what.getReadOnlyStack();
            if (readOnlyStack == null) {
                return 0;
            }
            return Math.min(slot.getSlotStackLimit(), readOnlyStack.getMaxStackSize());
        } else if (what.matches(currentItem)) {
            return Math.max(0, Math.min(slot.getSlotStackLimit(), currentItem.getMaxStackSize()) - currentItem.getCount());
        } else {
            return 0;
        }
    }

    protected final void handleFillingHeldItem(FillingSource source, AEKey what, boolean fillAll) {
        ContainerItemContext ctx = ContainerItemStrategies.findCarriedContextForKey(what, getPlayer(), this);
        if (ctx == null) {
            return;
        }

        long amount = fillAll ? Long.MAX_VALUE : what.getAmountPerUnit();
        boolean filled = false;
        int maxIterations = fillAll ? MAX_CONTAINER_TRANSFER_ITERATIONS : 1;

        while (maxIterations > 0) {
            long canPull = source.extract(amount, Actionable.SIMULATE);
            if (canPull <= 0) {
                break;
            }

            long amountAllowed = ctx.insert(what, canPull, Actionable.SIMULATE);
            if (amountAllowed == 0) {
                break;
            }

            long extracted = source.extract(amountAllowed, Actionable.MODULATE);
            if (extracted <= 0) {
                AELog.error("Unable to pull %s out of storage even though the simulation said yes", what);
                break;
            }

            long inserted = ctx.insert(what, extracted, Actionable.MODULATE);
            if (inserted <= 0) {
                AELog.error("Container item failed to accept previously extracted %s", what);
                rollBackFilledAmount(source, what, extracted);
                break;
            }

            if (inserted != extracted) {
                AELog.error("Container item accepted %d of %d previously extracted %s", inserted, extracted, what);
                rollBackFilledAmount(source, what, extracted - inserted);
                filled = true;
                break;
            }

            filled = true;
            maxIterations--;
        }

        if (filled) {
            ctx.playFillSound(getPlayer(), what);
        }
    }

    private void rollBackFilledAmount(FillingSource source, AEKey what, long amount) {
        if (amount <= 0) {
            return;
        }

        long restored = source.insert(what, amount, Actionable.MODULATE);
        if (restored != amount) {
            AELog.error("Failed to restore %d of %d %s after container item fill mismatch", amount - restored, amount,
                what);
        }
    }

    private void rollBackEmptiedAmount(ContainerItemContext ctx, AEKey what, long amount) {
        if (amount <= 0) {
            return;
        }

        long restored = ctx.insert(what, amount, Actionable.MODULATE);
        if (restored != amount) {
            AELog.error("Failed to restore %d of %d %s after storage insert mismatch", amount - restored, amount, what);
        }
    }

    protected final void handleEmptyHeldItem(EmptyingSink sink, boolean emptyAll) {
        ContainerItemContext ctx = ContainerItemStrategies.findCarriedContext(null, getPlayer(), this);
        if (ctx == null) {
            return;
        }

        GenericStack content = ctx.getExtractableContent();
        if (content == null || content.amount() == 0) {
            return;
        }

        AEKey what = content.what();
        long amount = emptyAll ? Long.MAX_VALUE : what.getAmountPerUnit();
        int maxIterations = emptyAll ? MAX_CONTAINER_TRANSFER_ITERATIONS : 1;
        boolean emptied = false;

        while (maxIterations > 0) {
            long canExtract = ctx.extract(what, amount, Actionable.SIMULATE);
            if (canExtract <= 0) {
                break;
            }

            long amountAllowed = sink.insert(what, canExtract, Actionable.SIMULATE);
            if (amountAllowed <= 0) {
                break;
            }

            long extracted = ctx.extract(what, amountAllowed, Actionable.MODULATE);
            if (extracted != amountAllowed) {
                AELog.error(
                    "Container item reported a different possible amount to drain than it actually provided: %s",
                    getCarried());
                break;
            }

            long inserted = sink.insert(what, extracted, Actionable.MODULATE);
            if (inserted != extracted) {
                AELog.error("Failed to insert previously simulated %s into storage", what);
                rollBackEmptiedAmount(ctx, what, extracted - inserted);
                break;
            }

            emptied = true;
            maxIterations--;
        }

        if (emptied) {
            ctx.playEmptySound(getPlayer(), what);
        }
    }

    public void onCraftMatrixChanged(IInventory inventory) {
        super.onCraftMatrixChanged(inventory);
    }

    public void onSlotChange(Slot slot) {
    }

    public void onClientDataSync(ShortSet ignored) {
    }

    public void onServerDataSync(ShortSet updatedFields) {
        this.onClientDataSync(updatedFields);
    }

    public void hideSlot(String semantic) {
        if (isClientSide()) {
            sendClientAction(HIDE_SLOT, semantic);
        }

        SlotSemantic slotSemantic = SlotSemantics.get(semantic);
        if (slotSemantic == null) {
            return;
        }

        if (canSlotsBeHidden(slotSemantic)) {
            for (Slot slot : getSlots(slotSemantic)) {
                if (slot instanceof AppEngSlot appEngSlot) {
                    appEngSlot.setSlotEnabled(false);
                }
            }
        }
    }

    protected boolean canSlotsBeHidden(SlotSemantic ignored) {
        return false;
    }

    public void broadcastChanges() {
        if (!isValidContainer()) {
            return;
        }

        if (this.itemGuiHost != null) {
            if (!this.itemGuiHost.isValid()) {
                setValidContainer(false);
                return;
            }
            this.itemGuiHost.tick();
        }

        if (isServerSide()) {
            if (this.partHost instanceof AEBasePart basePart) {
                IPartHost host = basePart.getHost();
                if (host == null || !host.isInWorld() || host.getPart(basePart.getSide()) != basePart) {
                    setValidContainer(false);
                    return;
                }
            }

            if (this.tileEntityHost != null && (this.tileEntityHost.isInvalid()
                || this.tileEntityHost.getWorld().getTileEntity(this.tileEntityHost.getPos()) != this.tileEntityHost)) {
                setValidContainer(false);
                return;
            }

            if (dataSync.hasFields() && dataSync.hasChanges()) {
                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP player) {
                        sendGuiData(player, false);
                    }
                }
            }
        }

        super.detectAndSendChanges();
    }

    public void receiveGuiData(byte[] payload) {
        receiveServerSyncData(Unpooled.wrappedBuffer(payload));
    }

    public final void receiveServerSyncData(ByteBuf data) {
        ShortSet updatedFields = dataSync.readUpdate(data);
        onServerDataSync(updatedFields);
    }

    @Override
    public void detectAndSendChanges() {
        this.broadcastChanges();
    }

    public final void syncInventoryActionState(EntityPlayerMP player) {
        if (isServerSide()) {
            this.detectAndSendChanges();
            player.connection.sendPacket(new SPacketSetSlot(-1, -1, player.inventory.getItemStack().copy()));
        }
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP player && dataSync.hasFields()) {
            sendGuiData(player, true);
        }
    }

    @Override
    public boolean canDragIntoSlot(Slot slotIn) {
        if (slotIn instanceof AppEngSlot appEngSlot) {
            return appEngSlot.isDraggable();
        }
        return super.canDragIntoSlot(slotIn);
    }

    public boolean isValidContainer() {
        return this.containerValid;
    }

    public void setValidContainer(boolean containerValid) {
        this.containerValid = containerValid;
    }

    public boolean isClientSide() {
        return this.getPlayer().world.isRemote;
    }

    protected boolean isServerSide() {
        return !this.isClientSide();
    }

    @Nullable
    public GuiHostLocator getLocator() {
        return this.locator;
    }

    public void setLocator(@Nullable GuiHostLocator locator) {
        this.locator = locator;
    }

    public boolean isReturnedFromSubScreen() {
        return this.returnedFromSubScreen;
    }

    public void setReturnedFromSubScreen(boolean returnedFromSubScreen) {
        this.returnedFromSubScreen = returnedFromSubScreen;
    }

    @Nullable
    public Container getReturnToContainerOverride() {
        return this.returnToContainerOverride;
    }

    public void setReturnToContainerOverride(@Nullable Container returnToContainerOverride) {
        this.returnToContainerOverride = returnToContainerOverride;
    }

    public boolean hasExternalGuiReturn() {
        return this.externalGuiReturn;
    }

    public void setExternalGuiReturn(boolean externalGuiReturn) {
        this.externalGuiReturn = externalGuiReturn;
    }

    protected ItemStack getCarried() {
        return this.getPlayerInventory().getItemStack();
    }

    protected void setCarried(ItemStack stack) {
        this.getPlayerInventory().setItemStack(stack);
    }

    protected final void sendPacketToClient(ClientboundPacket packet) {
        if (this.getPlayer() instanceof EntityPlayerMP player) {
            InitNetwork.CHANNEL.sendTo(packet, player);
        }
    }

    protected final <T> void registerClientAction(String name, Class<T> argClass, Consumer<T> handler) {
        this.registerClientAction(name, argClass, MAX_STRING_LENGTH, handler);
    }

    protected final <T> void registerClientAction(String name, Class<T> argClass, int maxPayloadLength,
                                                  Consumer<T> handler) {
        if (this.clientActions.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate client action registered: " + name);
        }
        if (maxPayloadLength < 0 || maxPayloadLength > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(
                "Client action " + name + " max payload length must be between 0 and " + MAX_STRING_LENGTH);
        }

        this.clientActions.put(name, new ClientAction<>(name, argClass, maxPayloadLength, handler));
    }

    protected final void registerClientAction(String name, Runnable callback) {
        this.registerClientAction(name, Void.class, ignored -> callback.run());
    }

    protected final <T> void sendClientAction(String action, T arg) {
        ClientAction<?> clientAction = this.clientActions.get(action);
        if (clientAction == null) {
            throw new IllegalArgumentException("Trying to send unregistered client action: " + action);
        }

        String jsonPayload;
        if (clientAction.argClass == Void.class) {
            if (arg != null) {
                throw new IllegalArgumentException(
                    "Client action " + action + " requires no argument, but it was given");
            }
            jsonPayload = null;
        } else {
            if (arg == null) {
                throw new IllegalArgumentException(
                    "Client action " + action + " requires an argument, but none was given");
            }
            if (clientAction.argClass != arg.getClass()) {
                throw new IllegalArgumentException(
                    "Trying to send client action " + action + " with wrong argument type " + arg.getClass()
                        + ", expected: " + clientAction.argClass);
            }
            jsonPayload = clientAction.gson.toJson(arg);
        }

        if (jsonPayload != null && jsonPayload.length() > clientAction.maxPayloadLength) {
            throw new IllegalArgumentException(
                "Cannot send client action " + action + " because serialized argument is longer than "
                    + clientAction.maxPayloadLength + " (" + jsonPayload.length() + ")");
        }

        InitNetwork.sendToServer(new GuiActionPacket(this.windowId, clientAction.name, jsonPayload));
    }

    protected final void sendClientAction(String action) {
        this.sendClientAction(action, (Void) null);
    }

    public final void receiveClientAction(String actionName, @Nullable String jsonPayload) {
        if (actionName == null || actionName.length() > GuiActionPacket.MAX_ACTION_NAME_LENGTH) {
            return;
        }
        if (jsonPayload != null && jsonPayload.length() > MAX_STRING_LENGTH) {
            return;
        }
        ClientAction<?> action = this.clientActions.get(actionName);
        if (action == null) {
            return;
        }

        action.handle(jsonPayload);
    }

    protected final void setupUpgrades(IUpgradeInventory upgrades) {
        for (int i = 0; i < upgrades.size(); i++) {
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades,
                i, 0, 0);
            slot.setNotDraggable();
            this.addSlot(slot, SlotSemantics.UPGRADE);
        }
    }

    public void swapSlotContents(int slotA, int slotB) {
        if (slotA < 0 || slotA >= this.inventorySlots.size() || slotB < 0 || slotB >= this.inventorySlots.size()) {
            return;
        }

        Slot firstSlot = this.getSlot(slotA);
        Slot secondSlot = this.getSlot(slotB);

        if (firstSlot == null || secondSlot == null) {
            return;
        }

        ItemStack firstStack = firstSlot.getStack();
        ItemStack secondStack = secondSlot.getStack();

        if (firstStack.isEmpty() && secondStack.isEmpty()) {
            return;
        }

        if (!firstStack.isEmpty() && !firstSlot.canTakeStack(this.getPlayerInventory().player)) {
            return;
        }

        if (!secondStack.isEmpty() && !secondSlot.canTakeStack(this.getPlayerInventory().player)) {
            return;
        }

        if (!secondStack.isEmpty() && !firstSlot.isItemValid(secondStack)) {
            return;
        }

        if (!firstStack.isEmpty() && !secondSlot.isItemValid(firstStack)) {
            return;
        }

        ItemStack newFirstStack = secondStack.isEmpty() ? ItemStack.EMPTY : secondStack.copy();
        ItemStack newSecondStack = firstStack.isEmpty() ? ItemStack.EMPTY : firstStack.copy();

        if (!newFirstStack.isEmpty() && newFirstStack.getCount() > firstSlot.getSlotStackLimit()) {
            if (!newSecondStack.isEmpty()) {
                return;
            }

            int totalFirst = newFirstStack.getCount();
            newFirstStack.setCount(firstSlot.getSlotStackLimit());
            newSecondStack = newFirstStack.copy();
            newSecondStack.setCount(totalFirst - newFirstStack.getCount());
        }

        if (!newSecondStack.isEmpty() && newSecondStack.getCount() > secondSlot.getSlotStackLimit()) {
            if (!newFirstStack.isEmpty()) {
                return;
            }

            int totalSecond = newSecondStack.getCount();
            newSecondStack.setCount(secondSlot.getSlotStackLimit());
            newFirstStack = newSecondStack.copy();
            newFirstStack.setCount(totalSecond - newSecondStack.getCount());
        }

        firstSlot.putStack(newFirstStack);
        secondSlot.putStack(newSecondStack);
    }

    @Nullable
    public ITextComponent getGuiTitle() {
        return this.guiTitle;
    }

    public void setGuiTitle(@Nullable ITextComponent guiTitle) {
        this.guiTitle = guiTitle;
    }

    private void sendGuiData(EntityPlayerMP player, boolean fullUpdate) {
        ByteBuf data = Unpooled.buffer();
        if (fullUpdate) {
            dataSync.writeFull(data);
        } else {
            dataSync.writeUpdate(data);
        }
        byte[] payload = new byte[data.readableBytes()];
        data.readBytes(payload);
        GuiDataSyncPacket packet = new GuiDataSyncPacket(windowId, payload);
        InitNetwork.CHANNEL.sendTo(packet, player);
    }

    protected interface FillingSource {
        long extract(long amount, Actionable mode);

        default long insert(AEKey what, long amount, Actionable mode) {
            return 0;
        }
    }

    protected interface EmptyingSink {
        long insert(AEKey what, long amount, Actionable mode);
    }

    private static final class ClientAction<T> {
        private final Gson gson = new GsonBuilder().create();
        private final String name;
        private final Class<T> argClass;
        private final int maxPayloadLength;
        private final Consumer<T> handler;

        private ClientAction(String name, Class<T> argClass, int maxPayloadLength, Consumer<T> handler) {
            this.name = name;
            this.argClass = argClass;
            this.maxPayloadLength = maxPayloadLength;
            this.handler = handler;
        }

        private void handle(@Nullable String jsonPayload) {
            T arg = null;
            if (jsonPayload != null && jsonPayload.length() > this.maxPayloadLength) {
                return;
            }
            if (argClass == Void.class) {
                if (jsonPayload != null) {
                    return;
                }
            } else {
                if (jsonPayload == null) {
                    return;
                }
                try {
                    arg = gson.fromJson(jsonPayload, argClass);
                } catch (JsonParseException e) {
                    return;
                }
                if (arg == null) {
                    return;
                }
            }

            this.handler.accept(arg);
        }
    }
}
