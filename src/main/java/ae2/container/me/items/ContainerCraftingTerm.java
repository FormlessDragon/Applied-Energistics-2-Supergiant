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
import ae2.api.storage.StorageHelper;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.crafting.CraftingGridTweaks;
import ae2.container.crafting.DeferredRecipeLookup;
import ae2.container.crafting.LastCraftingRecipeTracker;
import ae2.container.crafting.RecipeSelection;
import ae2.container.guisync.GuiSync;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.slot.CraftingMatrixSlot;
import ae2.container.slot.CraftingTermSlot;
import ae2.core.AELog;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.crafting.TemporaryPseudoCraftingProvider;
import ae2.helpers.InventoryAction;
import ae2.me.storage.LinkStatusRespectingInventory;
import ae2.parts.reporting.CraftingTerminalPart;
import ae2.util.inv.PlayerInternalInventory;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortSet;
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
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Can only be used with a host that implements {@link ISegmentedInventory} and exposes an inventory named "crafting" to
 * store the crafting grid.
 */
public class ContainerCraftingTerm extends ContainerMEStorage implements ICraftingGridContainer {

    private static final String ACTION_CLEAR_TO_PLAYER = "clearToPlayer";
    private static final String ACTION_SET_CLEAR_ON_CLOSE = "setClearOnClose";
    private static final String ACTION_SELECT_RECIPE = "selectRecipe";
    private static final String ACTION_ROTATE_GRID = "rotateGrid";
    private static final String ACTION_BALANCE_GRID = "balanceGrid";
    private static final String ACTION_RESTORE_LAST_RECIPE = "restoreLastRecipe";
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
    private List<RecipeSelection.Candidate> recipeCandidates = List.of();
    @GuiSync(90)
    @Nullable
    private ResourceLocation selectedRecipeId;
    private final DeferredRecipeLookup deferredRecipeLookup = new DeferredRecipeLookup();
    @GuiSync(91)
    private boolean authoritativeRecipeSelection;
    @GuiSync(92)
    private long recipeSelectionRevision;
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
        registerClientAction(ACTION_SELECT_RECIPE, String.class, 256, this::selectRecipeFromClient);
        registerClientAction(ACTION_ROTATE_GRID, Boolean.class, this::rotateGrid);
        registerClientAction(ACTION_BALANCE_GRID, Boolean.class, this::balanceGrid);
        registerClientAction(ACTION_RESTORE_LAST_RECIPE, Boolean.class, this::restoreLastRecipe);
    }

    @Override
    public IEnergySource getEnergySource() {
        return this.energySource;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        super.onCraftMatrixChanged(inventory);
        if (isServerSide()) {
            markCraftingRecipeDirty();
        } else if (!this.authoritativeRecipeSelection) {
            updateCurrentRecipeAndOutput(false);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            resolveDeferredCraftingRecipe();
        }
        super.broadcastChanges();
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

        this.recipeCandidates = RecipeSelection.findCandidates(craftingInventory, this.getPlayer().world);
        RecipeSelection.Candidate selected = RecipeSelection.select(this.recipeCandidates, this.selectedRecipeId);
        this.currentRecipe = selected == null ? null : selected.recipe();
        this.selectedRecipeId = selected == null ? null : selected.id();
        this.authoritativeRecipeSelection = false;
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
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        if (this.authoritativeRecipeSelection) {
            applyAuthoritativeRecipeSelectionOnClient();
        } else if (updatedFields.contains((short) 91)) {
            // Slot synchronization may already have refreshed the local matrix. Reuse that
            // result and only enumerate recipes if the final matrix actually differs.
            updateCurrentRecipeAndOutput(false);
        } else if (this.selectedRecipeId != null) {
            applyRecipeSelection(this.selectedRecipeId);
        }
    }

    private void applyAuthoritativeRecipeSelectionOnClient() {
        ResourceLocation recipeId = this.selectedRecipeId;
        if (recipeId == null) {
            AELog.error("Server marked crafting recipe selection authoritative without a recipe id");
            this.recipeCandidates = List.of();
            this.currentRecipe = null;
            this.outputSlot.setRecipeUsed(null);
            this.outputSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
            return;
        }

        IRecipe recipe = CraftingManager.REGISTRY.getObject(recipeId);
        if (recipe == null || !recipeId.equals(recipe.getRegistryName())) {
            AELog.error("Server synchronized unknown authoritative crafting recipe %s", recipeId);
            this.recipeCandidates = List.of();
            this.currentRecipe = null;
            this.outputSlot.setRecipeUsed(null);
            this.outputSlot.setDisplayedCraftingOutput(ItemStack.EMPTY);
            return;
        }

        NonNullList<ItemStack> testInput = NonNullList.withSize(this.craftingSlots.length, ItemStack.EMPTY);
        InventoryCrafting craftingInventory = new InventoryCrafting(RECIPE_FINDING_CONTAINER, GRID_WIDTH, GRID_HEIGHT);
        for (int i = 0; i < testInput.size(); i++) {
            ItemStack stack = this.craftingSlots[i].getStack().copy();
            testInput.set(i, stack);
            craftingInventory.setInventorySlotContents(i, stack);
        }

        this.lastTestedInput = testInput;
        this.recipeCandidates = List.of(new RecipeSelection.Candidate(
            recipeId, recipe, recipe.getCraftingResult(craftingInventory).copy()));
        this.currentRecipe = recipe;
        this.outputSlot.setRecipeUsed(recipe);
        this.outputSlot.setDisplayedCraftingOutput(this.recipeCandidates.getFirst().output().copy());
    }

    public List<RecipeSelection.Candidate> getRecipeCandidates() {
        return this.recipeCandidates;
    }

    @Nullable
    public ResourceLocation getSelectedRecipeId() {
        return this.selectedRecipeId;
    }

    public void selectRecipe(ResourceLocation recipeId) {
        if (isClientSide()) {
            sendClientAction(ACTION_SELECT_RECIPE, recipeId.toString());
            return;
        }
        applyRecipeSelection(recipeId);
    }

    private void selectRecipeFromClient(String recipeId) {
        if (this.deferredRecipeLookup.isDirty()) {
            AELog.debug("Ignoring crafting recipe selection while the crafting matrix is pending lookup");
            return;
        }
        try {
            applyRecipeSelection(new ResourceLocation(recipeId));
        } catch (RuntimeException e) {
            AELog.warn(e, "Ignoring invalid crafting recipe selection '%s'", recipeId);
        }
    }

    /**
     * Applies a recipe supplied by HEI after its transfer has populated the matrix.
     *
     * @return {@code true} when the registered recipe matches the current matrix
     */
    public boolean applyHeiCraftingRecipe(ResourceLocation recipeId) {
        if (!isServerSide() || recipeId == null) {
            return false;
        }

        NonNullList<ItemStack> testInput = NonNullList.withSize(this.craftingSlots.length, ItemStack.EMPTY);
        InventoryCrafting craftingInventory = new InventoryCrafting(RECIPE_FINDING_CONTAINER, GRID_WIDTH, GRID_HEIGHT);
        for (int i = 0; i < testInput.size(); i++) {
            ItemStack stack = this.craftingSlots[i].getStack().copy();
            testInput.set(i, stack);
            craftingInventory.setInventorySlotContents(i, stack);
        }

        RecipeSelection.Candidate selected = RecipeSelection.findCandidateById(
            craftingInventory, this.getPlayer().world, recipeId);
        if (selected == null) {
            markCraftingRecipeDirty();
            return false;
        }

        this.lastTestedInput = testInput;
        this.recipeCandidates = List.of(selected);
        this.selectedRecipeId = selected.id();
        this.currentRecipe = selected.recipe();
        this.authoritativeRecipeSelection = true;
        this.recipeSelectionRevision++;
        this.outputSlot.setRecipeUsed(this.currentRecipe);
        this.outputSlot.setDisplayedCraftingOutput(selected.output().copy());
        this.deferredRecipeLookup.clear();
        return true;
    }

    private void markCraftingRecipeDirty() {
        if (!isServerSide()) {
            return;
        }

        if (this.authoritativeRecipeSelection && !isCurrentMatrixUnchanged()) {
            this.authoritativeRecipeSelection = false;
        }
        this.deferredRecipeLookup.markDirty(this.getPlayer().world.getTotalWorldTime());
    }

    private boolean isCurrentMatrixUnchanged() {
        if (this.lastTestedInput == null || this.lastTestedInput.size() != this.craftingSlots.length) {
            return false;
        }

        for (int i = 0; i < this.craftingSlots.length; i++) {
            if (!ItemStack.areItemStacksEqual(this.lastTestedInput.get(i), this.craftingSlots[i].getStack())) {
                return false;
            }
        }
        return true;
    }

    private void resolveDeferredCraftingRecipe() {
        if (this.deferredRecipeLookup.isDue(this.getPlayer().world.getTotalWorldTime())) {
            this.deferredRecipeLookup.clear();
            updateCurrentRecipeAndOutput(false);
        }
    }

    private void applyRecipeSelection(ResourceLocation recipeId) {
        RecipeSelection.Candidate selected = RecipeSelection.select(this.recipeCandidates, recipeId);
        if (selected == null || !selected.id().equals(recipeId)) {
            return;
        }
        this.selectedRecipeId = selected.id();
        this.currentRecipe = selected.recipe();
        this.authoritativeRecipeSelection = false;
        this.outputSlot.setRecipeUsed(this.currentRecipe);
        this.outputSlot.setDisplayedCraftingOutput(selected.output().copy());
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

    public void rotateGrid(boolean counterClockwise) {
        if (isClientSide()) {
            sendClientAction(ACTION_ROTATE_GRID, counterClockwise);
            return;
        }

        CraftingGridTweaks.rotate(this.craftingGrid, counterClockwise);
        onCraftMatrixChanged(this.craftingGrid.toContainer());
    }

    public void balanceGrid(boolean fill) {
        if (isClientSide()) {
            sendClientAction(ACTION_BALANCE_GRID, fill);
            return;
        }

        if (fill) {
            CraftingGridTweaks.spread(this.craftingGrid);
        } else {
            CraftingGridTweaks.balance(this.craftingGrid);
        }
        onCraftMatrixChanged(this.craftingGrid.toContainer());
        this.broadcastChanges();
    }

    public void restoreLastRecipe(boolean singleSet) {
        if (isClientSide()) {
            sendClientAction(ACTION_RESTORE_LAST_RECIPE, singleSet);
            return;
        }

        List<ItemStack> recipe = LastCraftingRecipeTracker.get(getPlayer());
        if (recipe == null || recipe.size() != this.craftingGrid.size()) {
            return;
        }

        moveCraftingGridToNetwork();
        for (int slot = 0; slot < this.craftingGrid.size(); slot++) {
            if (!this.craftingGrid.getStackInSlot(slot).isEmpty()) {
                return;
            }
        }
        PlayerInternalInventory playerInv = new PlayerInternalInventory(getPlayerInventory());
        boolean[] restored = new boolean[recipe.size()];
        for (int firstSlot = 0; firstSlot < recipe.size(); firstSlot++) {
            if (restored[firstSlot]) {
                continue;
            }
            ItemStack wanted = recipe.get(firstSlot);
            if (wanted.isEmpty()) {
                restored[firstSlot] = true;
                continue;
            }

            IntList targetSlots = new IntArrayList();
            for (int targetSlot = firstSlot; targetSlot < recipe.size(); targetSlot++) {
                ItemStack target = recipe.get(targetSlot);
                if (ItemStack.areItemsEqual(wanted, target) && ItemStack.areItemStackTagsEqual(wanted, target)) {
                    targetSlots.add(targetSlot);
                    restored[targetSlot] = true;
                }
            }

            int wantedAmount = singleSet ? targetSlots.size() : targetSlots.size() * wanted.getMaxStackSize();
            int gathered = 0;
            for (int playerSlot = 0; playerSlot < playerInv.size() && gathered < wantedAmount; playerSlot++) {
                ItemStack available = playerInv.getStackInSlot(playerSlot);
                if (available.isEmpty() || !ItemStack.areItemsEqual(wanted, available)
                    || !ItemStack.areItemStackTagsEqual(wanted, available)) {
                    continue;
                }
                int amount = Math.min(wantedAmount - gathered, available.getCount());
                ItemStack taken = playerInv.extractItem(playerSlot, amount, false);
                gathered += taken.getCount();
            }

            AEItemKey key = AEItemKey.of(wanted);
            if (key != null && gathered < wantedAmount) {
                long extracted = StorageHelper.poweredExtraction(this.energySource, this.storage, key,
                    wantedAmount - gathered, getActionSource());
                gathered += (int) extracted;
            }

            int[] counts = CraftingGridTweaks.distributeEvenly(gathered, targetSlots.size());
            for (int i = 0; i < targetSlots.size(); i++) {
                if (counts[i] <= 0) {
                    continue;
                }
                ItemStack stack = wanted.copy();
                stack.setCount(counts[i]);
                this.craftingGrid.setItemDirect(targetSlots.getInt(i), stack);
            }
        }
        onCraftMatrixChanged(this.craftingGrid.toContainer());
        this.broadcastChanges();
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
