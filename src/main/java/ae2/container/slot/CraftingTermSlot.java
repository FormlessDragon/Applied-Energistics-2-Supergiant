/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.container.slot;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.helpers.InventoryAction;
import ae2.items.storage.ViewCellItem;
import ae2.util.Platform;
import ae2.util.inv.CarriedItemInventory;
import ae2.util.inv.PlayerInternalInventory;
import ae2.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.List;

/**
 * This is the crafting result slot of the crafting terminal, which also handles performing the actual crafting when a
 * player clicks it.
 */
public class CraftingTermSlot extends AppEngCraftingSlot {
    private static final int GRID_SIZE = 9;

    private static final Container DUMMY_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    };

    private final InternalInventory craftInv;
    private final InternalInventory pattern;
    private final IActionSource actionSource;
    private final IEnergySource energySource;
    private final MEStorage storage;
    private final ICraftingGridContainer container;

    public CraftingTermSlot(EntityPlayer player, IActionSource actionSource, IEnergySource energySource,
                            MEStorage storage, InternalInventory craftingMatrix, InternalInventory craftInventory,
                            ICraftingGridContainer container, int x, int y) {
        super(player, craftingMatrix, x, y);
        this.actionSource = actionSource;
        this.energySource = energySource;
        this.storage = storage;
        this.pattern = craftingMatrix;
        this.craftInv = craftInventory;
        this.container = container;
    }

    private static InventoryCrafting createRecipeInput(List<ItemStack> stacks) {
        InventoryCrafting recipeInput = new InventoryCrafting(DUMMY_CONTAINER, 3, 3);
        for (int slot = 0; slot < stacks.size(); slot++) {
            recipeInput.setInventorySlotContents(slot, stacks.get(slot));
        }
        return recipeInput;
    }

    private static boolean areStacksEqual(ItemStack first, ItemStack second) {
        return ItemStack.areItemsEqual(first, second)
            && ItemStack.areItemStackTagsEqual(first, second)
            && first.getCount() == second.getCount();
    }

    private static int getMaxTimesForTarget(InternalInventory target, ItemStack crafted, int upperLimit) {
        int maxTimes = 0;
        for (int times = 1; times <= upperLimit; times++) {
            ItemStack stack = crafted.copy();
            stack.setCount(crafted.getCount() * times);
            if (!target.simulateAdd(stack).isEmpty()) {
                break;
            }
            maxTimes = times;
        }
        return maxTimes;
    }

    private static boolean isAllowedByFilter(IPartitionList filter, AEItemKey key) {
        return filter == null || filter.isListed(key);
    }

    private static boolean shouldCheckFuzzy(ItemStack template) {
        return template.hasTagCompound() || template.isItemStackDamageable();
    }

    private static List<ItemStack> copyInputs(List<ItemStack> inputs) {
        List<ItemStack> result = new ObjectArrayList<>(inputs.size());
        for (ItemStack input : inputs) {
            result.add(input.copy());
        }
        return result;
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return false;
    }

    @Override
    public ItemStack onTake(EntityPlayer player, ItemStack stack) {
        return stack;
    }

    public void doClick(InventoryAction action, EntityPlayer who) {
        if (this.getStack().isEmpty() || isRemote()) {
            return;
        }

        InternalInventory target;
        boolean batch;
        int maxTimesToCraft;
        if (action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL) {
            target = new PlayerInternalInventory(who.inventory);
            batch = true;
            maxTimesToCraft = getMaxTimesForTarget(target, this.getStack(), getStackCraftLimit());
        } else if (action == InventoryAction.CRAFT_STACK) {
            target = new CarriedItemInventory(getContainer());
            batch = true;
            maxTimesToCraft = getMaxTimesForTarget(target, this.getStack(), getStackCraftLimit());
        } else {
            target = new CarriedItemInventory(getContainer());
            batch = false;
            maxTimesToCraft = 1;
        }

        if (maxTimesToCraft <= 0) {
            return;
        }

        CraftPlan plan = batch
            ? planBatchCraft(who, maxTimesToCraft)
            : planSingleCraft(who);
        if (plan == null || plan.times() <= 0) {
            return;
        }

        ItemStack output = plan.crafted().copy();
        output.setCount(plan.crafted().getCount() * plan.times());
        if (!target.simulateAdd(output).isEmpty()) {
            return;
        }

        executePlan(who, target, plan, output);
    }

    @Override
    protected NonNullList<ItemStack> getRemainingItems(InventoryCrafting crafting, World world) {
        IRecipe recipe = findRecipe(crafting, world);
        if (recipe != null && recipe.matches(crafting, world)) {
            return recipe.getRemainingItems(crafting);
        }
        return super.getRemainingItems(crafting, world);
    }

    private int getStackCraftLimit() {
        ItemStack output = this.getStack();
        if (output.isEmpty() || output.getCount() <= 0) {
            return 0;
        }
        return Math.max(1, output.getMaxStackSize() / output.getCount());
    }

    private CraftPlan planSingleCraft(EntityPlayer player) {
        PlanContext ctx = createPlanContext(player);
        if (ctx == null) {
            return null;
        }

        CraftOperation operation = planSingleOperation(ctx);
        if (operation == null) {
            return null;
        }
        ctx.operations.add(operation);
        if (!addRefills(ctx, false)) {
            return null;
        }
        return new CraftPlan(ctx.crafted, ctx.templateInputs, ctx.operations, ctx.refills, ctx.networkRequests,
            ctx.gridConsumes);
    }

    private CraftPlan planBatchCraft(EntityPlayer player, int maxTimesToCraft) {
        PlanContext ctx = createPlanContext(player);
        if (ctx == null) {
            return null;
        }

        for (int crafted = 0; crafted < maxTimesToCraft; crafted++) {
            CraftOperation operation = planBatchOperation(ctx);
            if (operation == null) {
                break;
            }
            ctx.operations.add(operation);
        }

        if (ctx.operations.isEmpty()) {
            return null;
        }

        addRefills(ctx, true);
        return new CraftPlan(ctx.crafted, ctx.templateInputs, ctx.operations, ctx.refills, ctx.networkRequests,
            ctx.gridConsumes);
    }

    private PlanContext createPlanContext(EntityPlayer player) {
        List<ItemStack> templateInputs = getTemplateInputs();
        InventoryCrafting recipeInput = createRecipeInput(templateInputs);
        IRecipe recipe = findRecipe(recipeInput, player.world);
        setRecipeUsed(recipe);
        if (recipe == null || !recipe.matches(recipeInput, player.world)) {
            return null;
        }

        ItemStack crafted = recipe.getCraftingResult(recipeInput);
        if (crafted.isEmpty()) {
            return null;
        }

        NonNullList<ItemStack> templateRemainders = recipe.getRemainingItems(recipeInput);
        return new PlanContext(player, player.world, recipe, crafted, templateInputs, templateRemainders,
            ViewCellItem.createItemFilter(this.container.getViewCells()));
    }

    private List<ItemStack> getTemplateInputs() {
        List<ItemStack> result = new ObjectArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            result.add(this.pattern.getStackInSlot(slot).copy());
        }
        return result;
    }

    private CraftOperation planSingleOperation(PlanContext ctx) {
        SlotUse[] uses = new SlotUse[GRID_SIZE];
        List<ItemStack> recipeInputs = copyInputs(ctx.templateInputs);

        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack template = ctx.templateInputs.get(slot);
            if (template.isEmpty()) {
                continue;
            }

            SlotUse use = planSingleSlot(ctx, slot, template);
            if (use == null) {
                return null;
            }
            uses[slot] = use;
            recipeInputs.set(slot, use.recipeInput());
        }

        return new CraftOperation(getRemainders(ctx, uses, recipeInputs));
    }

    private SlotUse planSingleSlot(PlanContext ctx, int slot, ItemStack template) {
        FluidContainerInput fluidInput = getFluidContainerInput(ctx, slot, template);
        if (fluidInput != null) {
            SlotUse fluidUse = tryUseNetworkFluid(ctx, slot, template, fluidInput);
            if (fluidUse != null) {
                return fluidUse;
            }

            SlotUse bucketUse = tryUseNetworkItem(ctx, slot, template, CraftSource.NETWORK_FLUID_BUCKET_ITEM);
            if (bucketUse != null) {
                return bucketUse;
            }

            return tryUseGrid(ctx, slot, template);
        }

        SlotUse exactUse = tryUseNetworkItem(ctx, slot, template, CraftSource.NETWORK_EXACT_ITEM);
        if (exactUse != null) {
            return exactUse;
        }

        int remainingGrid = ctx.gridRemaining[slot] - ctx.gridConsumes[slot];
        if (remainingGrid > 1) {
            return tryUseGrid(ctx, slot, template);
        }

        SlotUse lastGridUse = tryUseGrid(ctx, slot, template);
        if (lastGridUse != null) {
            ctx.slotsRequiringRefill[slot] = true;
        }
        return lastGridUse;
    }

    private CraftOperation planBatchOperation(PlanContext ctx) {
        SlotUse[] uses = new SlotUse[GRID_SIZE];
        List<ItemStack> recipeInputs = copyInputs(ctx.templateInputs);
        boolean usedFuzzy = false;

        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack template = ctx.templateInputs.get(slot);
            if (template.isEmpty()) {
                continue;
            }

            SlotUse use = planBatchSlot(ctx, slot, template, recipeInputs);
            if (use == null) {
                rollbackUses(ctx, uses);
                return null;
            }
            if (use.source() == CraftSource.NETWORK_FUZZY_ITEM) {
                usedFuzzy = true;
            }
            uses[slot] = use;
            recipeInputs.set(slot, use.recipeInput());
        }

        if (usedFuzzy && !isSameRecipeOutput(ctx.recipe, recipeInputs, ctx.world, ctx.crafted)) {
            rollbackUses(ctx, uses);
            return null;
        }

        return new CraftOperation(getRemainders(ctx, uses, recipeInputs));
    }

    private SlotUse planBatchSlot(PlanContext ctx, int slot, ItemStack template, List<ItemStack> recipeInputs) {
        FluidContainerInput fluidInput = getFluidContainerInput(ctx, slot, template);
        if (fluidInput != null) {
            SlotUse fluidUse = tryUseNetworkFluid(ctx, slot, template, fluidInput);
            if (fluidUse != null) {
                return fluidUse;
            }

            SlotUse bucketUse = tryUseNetworkItem(ctx, slot, template, CraftSource.NETWORK_FLUID_BUCKET_ITEM);
            if (bucketUse != null) {
                return bucketUse;
            }

            return tryUseGrid(ctx, slot, template);
        }

        int remainingGrid = ctx.gridRemaining[slot] - ctx.gridConsumes[slot];
        if (remainingGrid > 0) {
            return tryUseGrid(ctx, slot, template);
        }

        SlotUse exactUse = tryUseNetworkItem(ctx, slot, template, CraftSource.NETWORK_EXACT_ITEM);
        if (exactUse != null) {
            return exactUse;
        }

        return tryUseFuzzyNetworkItem(ctx, slot, template, recipeInputs, true);
    }

    private SlotUse tryUseNetworkFluid(PlanContext ctx, int slot, ItemStack template,
                                       FluidContainerInput fluidInput) {
        if (canExtract(ctx, fluidInput.fluid(), fluidInput.amount())) {
            addNetworkRequest(ctx, fluidInput.fluid(), fluidInput.amount());
            return new SlotUse(slot, CraftSource.NETWORK_FLUID_CONTENT, template.copy(), fluidInput.fluid(),
                fluidInput.amount(), false, true);
        }
        return null;
    }

    private SlotUse tryUseNetworkItem(PlanContext ctx, int slot, ItemStack template, CraftSource source) {
        AEItemKey key = AEItemKey.of(template);
        if (key == null || !isAllowedByFilter(ctx.filter, key) || !canExtract(ctx, key, 1)) {
            return null;
        }

        addNetworkRequest(ctx, key, 1);
        return new SlotUse(slot, source, template.copy(), key, 1, false, false);
    }

    private SlotUse tryUseFuzzyNetworkItem(PlanContext ctx, int slot, ItemStack template,
                                           List<ItemStack> recipeInputs, boolean validateOutput) {
        AEItemKey requested = AEItemKey.of(template);
        if (requested == null || !shouldCheckFuzzy(template)) {
            return null;
        }

        for (Object2LongMap.Entry<AEKey> entry : ctx.getAvailableStacks().findFuzzy(requested,
            FuzzyMode.IGNORE_ALL)) {
            if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                continue;
            }
            if (itemKey.equals(requested) || itemKey.matches(ctx.crafted) || !isAllowedByFilter(ctx.filter, itemKey)) {
                continue;
            }
            if (!canExtract(ctx, itemKey, 1)) {
                continue;
            }

            ItemStack candidate = itemKey.toStack();
            if (validateOutput) {
                List<ItemStack> adjustedInputs = copyInputs(recipeInputs);
                adjustedInputs.set(slot, candidate);
                if (!isSameRecipeOutput(ctx.recipe, adjustedInputs, ctx.world, ctx.crafted)) {
                    continue;
                }
            }

            addNetworkRequest(ctx, itemKey, 1);
            return new SlotUse(slot, CraftSource.NETWORK_FUZZY_ITEM, candidate, itemKey, 1, false, false);
        }
        return null;
    }

    private SlotUse tryUseGrid(PlanContext ctx, int slot, ItemStack template) {
        int remainingGrid = ctx.gridRemaining[slot] - ctx.gridConsumes[slot];
        if (remainingGrid <= 0) {
            return null;
        }
        ctx.gridConsumes[slot]++;
        return new SlotUse(slot, CraftSource.GRID_ITEM, template.copy(), null, 0, true, false);
    }

    private void rollbackUses(PlanContext ctx, SlotUse[] uses) {
        for (SlotUse use : uses) {
            if (use == null) {
                continue;
            }
            if (use.consumeGrid()) {
                ctx.gridConsumes[use.slot()]--;
            }
            if (use.networkKey() != null && use.networkAmount() > 0) {
                ctx.networkRequests.addTo(use.networkKey(), -use.networkAmount());
            }
        }
    }

    private boolean addRefills(PlanContext ctx, boolean validateFuzzy) {
        boolean requiredRefillsSatisfied = true;
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            if (!shouldRefillSlot(ctx, slot)) {
                continue;
            }
            ItemStack template = ctx.templateInputs.get(slot);

            AEItemKey exact = AEItemKey.of(template);
            if (exact != null && isAllowedByFilter(ctx.filter, exact) && canExtract(ctx, exact, 1)) {
                addNetworkRequest(ctx, exact, 1);
                ctx.refills.add(new Refill(slot, exact));
                continue;
            }

            SlotUse fuzzyRefill = tryUseFuzzyNetworkItem(ctx, slot, template, ctx.templateInputs, validateFuzzy);
            if (fuzzyRefill != null && fuzzyRefill.networkKey() instanceof AEItemKey refillKey) {
                ctx.refills.add(new Refill(slot, refillKey));
            } else if (ctx.slotsRequiringRefill[slot]) {
                requiredRefillsSatisfied = false;
            }
        }
        return requiredRefillsSatisfied;
    }

    private boolean shouldRefillSlot(PlanContext ctx, int slot) {
        if (ctx.templateInputs.get(slot).isEmpty() || ctx.gridRemaining[slot] - ctx.gridConsumes[slot] > 0) {
            return false;
        }

        for (CraftOperation operation : ctx.operations) {
            if (slot < operation.remainders().size() && !operation.remainders().get(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private NonNullList<ItemStack> getRemainders(PlanContext ctx, SlotUse[] uses, List<ItemStack> recipeInputs) {
        InventoryCrafting crafting = createRecipeInput(recipeInputs);
        ForgeHooks.setCraftingPlayer(ctx.player);
        NonNullList<ItemStack> remainders;
        try {
            remainders = getRemainingItems(crafting, ctx.world);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }

        for (SlotUse use : uses) {
            if (use != null && use.suppressRemainder() && use.slot() < remainders.size()) {
                remainders.set(use.slot(), ItemStack.EMPTY);
            }
        }
        return remainders;
    }

    private boolean isSameRecipeOutput(IRecipe recipe, List<ItemStack> recipeInputs, World world, ItemStack expected) {
        InventoryCrafting adjustedInput = createRecipeInput(recipeInputs);
        if (!recipe.matches(adjustedInput, world)) {
            return false;
        }
        return areStacksEqual(recipe.getCraftingResult(adjustedInput), expected);
    }

    private FluidContainerInput getFluidContainerInput(PlanContext ctx, int slot, ItemStack template) {
        GenericStack contained = ContainerItemStrategies.getContainedStack(template);
        if (contained == null || !(contained.what() instanceof AEFluidKey fluidKey)) {
            return null;
        }

        if (slot >= ctx.templateRemainders.size()) {
            return null;
        }
        ItemStack remainder = ctx.templateRemainders.get(slot);
        if (remainder.getCount() == 1 && remainder.getItem() == Items.BUCKET) {
            return new FluidContainerInput(fluidKey, contained.amount());
        }
        return null;
    }

    private boolean canExtract(PlanContext ctx, AEKey key, long amount) {
        long total = amount + ctx.networkRequests.getLong(key);
        return StorageHelper.poweredExtraction(this.energySource, this.storage, key, total, this.actionSource,
            Actionable.SIMULATE) >= total;
    }

    private void addNetworkRequest(PlanContext ctx, AEKey key, long amount) {
        ctx.networkRequests.addTo(key, amount);
    }

    private void executePlan(EntityPlayer player, InternalInventory target, CraftPlan plan, ItemStack output) {
        for (Object2LongMap.Entry<AEKey> entry : plan.networkRequests.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            long extracted = StorageHelper.poweredExtraction(this.energySource, this.storage, entry.getKey(), amount,
                this.actionSource, Actionable.MODULATE);
            if (extracted < amount) {
                return;
            }
        }

        for (int slot = 0; slot < plan.gridConsumes.length; slot++) {
            int amount = plan.gridConsumes[slot];
            if (amount > 0) {
                this.craftInv.extractItem(slot, amount, false);
            }
        }

        onCrafted(player, output, plan.eventInputs());

        ItemStack extra = target.addItems(output);
        if (!extra.isEmpty()) {
            Platform.spawnDrops(player.world, player.getPosition(), List.of(extra));
        }

        applyRemainders(player, plan);
        applyRefills(plan);

        if (getContainer() != null) {
            getContainer().onCraftMatrixChanged(this.craftInv.toContainer());
        }
    }

    private void onCrafted(EntityPlayer player, ItemStack output, List<ItemStack> eventInputs) {
        onCrafting(output, output.getCount());
        output.getItem().onCreated(output, player.world, player);
        FMLCommonHandler.instance().firePlayerCraftingEvent(player, output, createRecipeInput(eventInputs));
    }

    private void applyRemainders(EntityPlayer player, CraftPlan plan) {
        for (CraftOperation operation : plan.operations()) {
            NonNullList<ItemStack> remainders = operation.remainders();
            for (int slot = 0; slot < Math.min(remainders.size(), this.craftInv.size()); slot++) {
                ItemStack remainder = remainders.get(slot);
                if (!remainder.isEmpty()) {
                    insertRemainder(player, slot, remainder.copy());
                }
            }
        }
    }

    private void insertRemainder(EntityPlayer player, int slot, ItemStack remainder) {
        remainder = this.craftInv.getSlotInv(slot).addItems(remainder);
        if (remainder.isEmpty()) {
            return;
        }

        AEItemKey key = AEItemKey.of(remainder);
        if (key != null) {
            long inserted = StorageHelper.poweredInsert(this.energySource, this.storage, key, remainder.getCount(),
                this.actionSource);
            remainder.shrink((int) inserted);
        }

        if (!remainder.isEmpty()) {
            Platform.spawnDrops(player.world, new BlockPos((int) player.posX, (int) player.posY, (int) player.posZ),
                List.of(remainder));
        }
    }

    private void applyRefills(CraftPlan plan) {
        for (Refill refill : plan.refills()) {
            if (!this.craftInv.getStackInSlot(refill.slot()).isEmpty()) {
                continue;
            }
            this.craftInv.setItemDirect(refill.slot(), refill.key().toStack());
        }
    }

    IRecipe findRecipe(InventoryCrafting recipeInput, World world) {
        if (this.container instanceof ContainerCraftingTerm craftingTermContainer) {
            IRecipe recipe = craftingTermContainer.getCurrentRecipe();
            if (recipe != null && recipe.matches(recipeInput, world)) {
                return recipe;
            }
        }

        return net.minecraft.item.crafting.CraftingManager.findMatchingRecipe(recipeInput, world);
    }

    private enum CraftSource {
        NETWORK_EXACT_ITEM,
        NETWORK_FUZZY_ITEM,
        NETWORK_FLUID_CONTENT,
        NETWORK_FLUID_BUCKET_ITEM,
        GRID_ITEM
    }

    private record SlotUse(int slot, CraftSource source, ItemStack recipeInput, AEKey networkKey, long networkAmount,
                           boolean consumeGrid, boolean suppressRemainder) {
    }

    private record Refill(int slot, AEItemKey key) {
    }

    private record CraftOperation(NonNullList<ItemStack> remainders) {
    }

    private record CraftPlan(ItemStack crafted, List<ItemStack> eventInputs, List<CraftOperation> operations,
                             List<Refill> refills, AEKey2LongMap networkRequests,
                             int[] gridConsumes) {
        int times() {
            return operations.size();
        }
    }

    private record FluidContainerInput(AEFluidKey fluid, long amount) {
    }

    private final class PlanContext {
        final EntityPlayer player;
        final World world;
        final IRecipe recipe;
        final ItemStack crafted;
        final List<ItemStack> templateInputs;
        final NonNullList<ItemStack> templateRemainders;
        final IPartitionList filter;
        final int[] gridRemaining = new int[GRID_SIZE];
        final int[] gridConsumes = new int[GRID_SIZE];
        final boolean[] slotsRequiringRefill = new boolean[GRID_SIZE];
        final AEKey2LongMap networkRequests = new AEKey2LongMap.OpenHashMap();
        final List<CraftOperation> operations = new ObjectArrayList<>();
        final List<Refill> refills = new ObjectArrayList<>();
        KeyCounter availableStacks;

        PlanContext(EntityPlayer player, World world, IRecipe recipe, ItemStack crafted, List<ItemStack> templateInputs,
                    NonNullList<ItemStack> templateRemainders, IPartitionList filter) {
            this.player = player;
            this.world = world;
            this.recipe = recipe;
            this.crafted = crafted;
            this.templateInputs = templateInputs;
            this.templateRemainders = templateRemainders;
            this.filter = filter;
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                this.gridRemaining[slot] = templateInputs.get(slot).getCount();
            }
        }

        KeyCounter getAvailableStacks() {
            if (this.availableStacks == null) {
                this.availableStacks = CraftingTermSlot.this.storage.getAvailableStacks();
            }
            return this.availableStacks;
        }
    }
}
