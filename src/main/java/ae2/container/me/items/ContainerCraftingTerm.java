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

package ae2.container.me.items;

import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ITerminalHost;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.slot.CraftingMatrixSlot;
import ae2.container.slot.CraftingTermSlot;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.crafting.TemporaryPseudoCraftingProvider;
import ae2.helpers.InventoryAction;
import ae2.me.storage.LinkStatusRespectingInventory;
import ae2.parts.reporting.CraftingTerminalPart;
import ae2.util.inv.PlayerInternalInventory;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Can only be used with a host that implements {@link ISegmentedInventory} and exposes an inventory named "crafting" to
 * store the crafting grid.
 */
public class ContainerCraftingTerm extends ContainerMEStorage implements ICraftingGridContainer {

    private static final String ACTION_CLEAR_TO_PLAYER = "clearToPlayer";
    private static final String ACTION_SET_CLEAR_ON_CLOSE = "setClearOnClose";
    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;
    private static final int GRID_LEFT = 30;
    private static final int GRID_TOP = 17;
    private static final int OUTPUT_X = 124;
    private static final int OUTPUT_Y = 35;
    private static final Container RECIPE_FINDING_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    };

    private final InternalInventory craftingGrid;
    private final CraftingMatrixSlot[] craftingSlots = new CraftingMatrixSlot[9];
    private final CraftingTermSlot outputSlot;
    @Nullable
    private NonNullList<ItemStack> lastTestedInput;
    @Nullable
    private IRecipe currentRecipe;
    private boolean clearGridOnClose;

    public ContainerCraftingTerm(InventoryPlayer ip, ITerminalHost host) {
        this(GuiIds.GuiKey.CRAFTING_TERMINAL, ip, host, true);
    }

    protected ContainerCraftingTerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, ITerminalHost host,
                                    boolean bindInventory) {
        super(guiKey, ip, host, bindInventory);
        Preconditions.checkArgument(host instanceof ISegmentedInventory,
            "Crafting terminal host must implement ISegmentedInventory");

        this.craftingGrid = ((ISegmentedInventory) host).getSubInventory(CraftingTerminalPart.INV_CRAFTING);

        for (int i = 0; i < this.craftingSlots.length; i++) {
            int x = GRID_LEFT + i % GRID_WIDTH * 18;
            int y = GRID_TOP + i / GRID_WIDTH * 18;
            this.addSlot(this.craftingSlots[i] = new CraftingMatrixSlot(this, this.craftingGrid, i, x, y),
                SlotSemantics.CRAFTING_GRID);
        }

        LinkStatusRespectingInventory linkStatusInventory = new LinkStatusRespectingInventory(host.getInventory(), this::getLinkStatus);
        this.addSlot(this.outputSlot = new CraftingTermSlot(this.getPlayerInventory().player, this.getActionSource(),
                this.energySource, linkStatusInventory, this.craftingGrid, this.craftingGrid, this, OUTPUT_X, OUTPUT_Y),
            SlotSemantics.CRAFTING_RESULT);

        updateCurrentRecipeAndOutput(true);

        registerClientAction(ACTION_CLEAR_TO_PLAYER, this::clearToPlayerInventory);
        registerClientAction(ACTION_SET_CLEAR_ON_CLOSE, Boolean.class, this::setClearGridOnClose);
    }

    @Override
    public IEnergySource getEnergySource() {
        return this.energySource;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        super.onCraftMatrixChanged(inventory);
        updateCurrentRecipeAndOutput(false);
    }

    private void updateCurrentRecipeAndOutput(boolean forceUpdate) {
        NonNullList<ItemStack> testInput = NonNullList.withSize(this.craftingSlots.length, ItemStack.EMPTY);
        for (int i = 0; i < this.craftingSlots.length; i++) {
            testInput.set(i, this.craftingSlots[i].getStack().copy());
        }

        if (!forceUpdate && isSameMatrix(testInput)) {
            return;
        }

        InventoryCrafting craftingInventory = new InventoryCrafting(RECIPE_FINDING_CONTAINER, GRID_WIDTH, GRID_HEIGHT);
        for (int i = 0; i < testInput.size(); i++) {
            craftingInventory.setInventorySlotContents(i, testInput.get(i));
        }

        this.currentRecipe = CraftingManager.findMatchingRecipe(craftingInventory, this.getPlayer().world);
        this.lastTestedInput = testInput;
        this.outputSlot.setRecipeUsed(this.currentRecipe);

        if (this.currentRecipe == null) {
            this.outputSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
        } else {
            this.outputSlot.setDisplayedCraftingOutput(this.currentRecipe.getCraftingResult(craftingInventory));
        }
    }

    private boolean isSameMatrix(NonNullList<ItemStack> testInput) {
        if (this.lastTestedInput == null || this.lastTestedInput.size() != testInput.size()) {
            return false;
        }

        for (int i = 0; i < testInput.size(); i++) {
            if (!ItemStack.areItemStacksEqual(this.lastTestedInput.get(i), testInput.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public InternalInventory getCraftingMatrix() {
        return this.craftingGrid;
    }

    @Override
    public void startAutoCrafting(List<AutoCraftEntry> toCraft) {
        if (getPlayer() instanceof EntityPlayerMP player) {
            ContainerCraftConfirm.openWithCraftingList(getActionHost(), player, getLocator(), toCraft);
        }
    }

    @Override
    public void startTemporaryPseudoCrafting(List<GenericStack> inputs, List<GenericStack> outputs) {
        if (!(getPlayer() instanceof EntityPlayerMP player) || outputs.isEmpty()) {
            return;
        }

        IGridNode node = getGridNode();
        if (node == null || !getLinkStatus().connected()) {
            return;
        }

        var provider = new TemporaryPseudoCraftingProvider(inputs, outputs);
        ContainerCraftConfirm.openWithTemporaryPseudoPattern(getActionHost(), player, getLocator(), provider);
    }

    @Nullable
    public IRecipe getCurrentRecipe() {
        return this.currentRecipe;
    }

    public void clearCraftingGrid() {
        Preconditions.checkState(isClientSide());
        CraftingMatrixSlot slot = this.craftingSlots[0];
        InitNetwork.sendToServer(new InventoryActionPacket(this.windowId, InventoryAction.MOVE_REGION, slot.slotNumber, 0));
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (isServerSide() && this.clearGridOnClose) {
            moveCraftingGridToNetwork();
        }
        super.onContainerClosed(player);
    }

    @Override
    public boolean hasIngredient(Ingredient ingredient, Object2IntMap<Object> reservedAmounts) {
        for (CraftingMatrixSlot slot : this.craftingSlots) {
            ItemStack stackInSlot = slot.getStack();
            if (!stackInSlot.isEmpty() && ingredient.apply(stackInSlot)) {
                int reservedAmount = reservedAmounts.getInt(slot);
                if (stackInSlot.getCount() > reservedAmount) {
                    reservedAmounts.put(slot, reservedAmount + 1);
                    return true;
                }
            }
        }

        return super.hasIngredient(ingredient, reservedAmounts);
    }

    public MissingIngredientSlots findMissingIngredients(Int2ObjectMap<Ingredient> ingredients) {
        IntSet missingSlots = new IntOpenHashSet();
        IntSet craftableSlots = new IntOpenHashSet();

        List<ItemStack> playerItems = getPlayerInventory().mainInventory;
        int[] reservedPlayerItems = new int[playerItems.size()];
        Object2IntMap<Object> reservedGridAmounts = new Object2IntOpenHashMap<>();

        for (Int2ObjectMap.Entry<Ingredient> entry : ingredients.int2ObjectEntrySet()) {
            Ingredient ingredient = entry.getValue();
            boolean found = false;

            for (int i = 0; i < playerItems.size(); i++) {
                if (isPlayerInventorySlotLocked(i)) {
                    continue;
                }

                ItemStack stack = playerItems.get(i);
                if (stack.getCount() - reservedPlayerItems[i] > 0 && ingredient.apply(stack)) {
                    reservedPlayerItems[i]++;
                    found = true;
                    break;
                }
            }

            if (!found) {
                if (hasIngredient(ingredient, reservedGridAmounts)) {
                    reservedGridAmounts.put(ingredient, reservedGridAmounts.getInt(ingredient) + 1);
                    found = true;
                }
            }

            if (!found) {
                for (ItemStack stack : ingredient.getMatchingStacks()) {
                    if (isCraftable(stack)) {
                        craftableSlots.add(entry.getIntKey());
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                missingSlots.add(entry.getIntKey());
            }
        }

        return new MissingIngredientSlots(missingSlots, craftableSlots);
    }

    protected boolean isCraftable(ItemStack itemStack) {
        IClientRepo clientRepo = getClientRepo();
        if (clientRepo != null) {
            for (GridInventoryEntry stack : clientRepo.getAllEntries()) {
                if (AEItemKey.matches(stack.what(), itemStack) && stack.craftable()) {
                    return true;
                }
            }
        }

        return false;
    }

    public void clearToPlayerInventory() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_TO_PLAYER);
            return;
        }

        moveCraftingGridToPlayerInventory();
    }

    public void setClearGridOnClose(boolean clearGridOnClose) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_CLEAR_ON_CLOSE, clearGridOnClose);
            return;
        }

        this.clearGridOnClose = clearGridOnClose;
    }

    private void moveCraftingGridToPlayerInventory() {
        PlayerInternalInventory playerInv = new PlayerInternalInventory(getPlayerInventory());

        for (int i = 0; i < this.craftingGrid.size(); ++i) {
            for (int emptyLoop = 0; emptyLoop < 2; ++emptyLoop) {
                boolean allowEmpty = emptyLoop == 1;

                for (int j = 9; j-- > 0; ) {
                    if (playerInv.getStackInSlot(j).isEmpty() == allowEmpty) {
                        this.craftingGrid.setItemDirect(i,
                            playerInv.getSlotInv(j).addItems(this.craftingGrid.getStackInSlot(i)));
                    }
                }

                for (int j = 9; j < playerInv.size(); ++j) {
                    if (playerInv.getStackInSlot(j).isEmpty() == allowEmpty) {
                        this.craftingGrid.setItemDirect(i,
                            playerInv.getSlotInv(j).addItems(this.craftingGrid.getStackInSlot(i)));
                    }
                }
            }
        }

        onCraftMatrixChanged(this.craftingGrid.toContainer());
    }

    private void moveCraftingGridToNetwork() {
        for (int i = 0; i < this.craftingGrid.size(); ++i) {
            ItemStack stack = this.craftingGrid.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            int transferred = transferStackToContainer(stack.copy());
            if (transferred > 0) {
                stack.shrink(transferred);
                this.craftingGrid.setItemDirect(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }

        onCraftMatrixChanged(this.craftingGrid.toContainer());
    }

    public record MissingIngredientSlots(IntSet missingSlots, IntSet craftableSlots) {
    }
}
