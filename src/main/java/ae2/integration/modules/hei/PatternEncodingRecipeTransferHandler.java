package ae2.integration.modules.hei;

import ae2.api.client.PatternImportPriorityContext;
import ae2.api.client.PatternRecipeTransferSlot;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.client.patternimport.PatternImportPriorityContextImpl;
import ae2.client.patternimport.PatternImportPrioritySelector;
import ae2.client.patternimport.PatternTransferPlan;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.container.slot.FakeSlot;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import ae2.mixins.hei.AccessorRecipeLayout;
import ae2.parts.encoding.EncodingMode;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class PatternEncodingRecipeTransferHandler<C extends ContainerPatternEncodingTerm> implements IRecipeTransferHandler<C> {
    @SuppressWarnings("unused")
    private static final int RECIPE_OUTPUT_SLOT = 0;
    private static final int CRAFTING_GRID_SIZE = 9;

    private final Class<C> containerClass;

    public PatternEncodingRecipeTransferHandler(Class<C> containerClass) {
        this.containerClass = containerClass;
    }

    private static ResourceLocation getCraftingRecipeId(IRecipeLayout recipeLayout) {
        if (!(recipeLayout instanceof AccessorRecipeLayout accessor)) {
            return null;
        }

        IRecipeWrapper wrapper = accessor.ae2$getRecipeWrapper();
        return wrapper instanceof ICraftingRecipeWrapper craftingWrapper
            ? craftingWrapper.getRegistryName()
            : null;
    }

    private void encodeProcessingRecipe(C container, IRecipeLayout recipeLayout) {
        String recipeCategoryUid = getRecipeCategoryUid(recipeLayout);
        PatternImportPriorityContext context = PatternImportPriorityContextImpl.create(container,
            HeiBookmarkHelper.getBookmarkedStacks());

        List<PatternRecipeTransferSlot> inputs = planProcessingInputs(getGenericInputs(recipeLayout), context,
            container.isMergeHeiProcessingInputs());
        List<PatternRecipeTransferSlot> outputs = planStacks(getGenericOutputs(recipeLayout), context);

        if (PatternRecipeTransferEvents.post(container, EncodingMode.PROCESSING, recipeCategoryUid, null, inputs,
            outputs)) {
            return;
        }

        List<PatternRecipeTransferSlot> packedInputs = PatternTransferPlan.packedSlots(inputs);
        List<PatternRecipeTransferSlot> packedOutputs = PatternTransferPlan.packedSlots(outputs);

        container.setMode(EncodingMode.PROCESSING);
        writeSlots(container, container.getProcessingInputSlots(), packedInputs);
        writeSlots(container, container.getProcessingOutputSlots(), packedOutputs);
        container.setHeiProcessingRecipe(recipeCategoryUid,
            PatternTransferPlan.serializeCandidateKeyTags(packedInputs));
    }

    private static List<List<GenericStack>> getGenericInputs(IRecipeLayout recipeLayout) {
        return GenericIngredientHelper.getIngredients(recipeLayout, true, false, CRAFTING_GRID_SIZE);
    }

    private static List<List<GenericStack>> getGenericOutputs(IRecipeLayout recipeLayout) {
        return GenericIngredientHelper.getIngredients(recipeLayout, false, false, CRAFTING_GRID_SIZE);
    }

    private static List<List<GenericStack>> getCraftingInputs(IRecipeLayout recipeLayout) {
        return GenericIngredientHelper.getIngredients(recipeLayout, true, true, CRAFTING_GRID_SIZE);
    }

    private RecipeTransferSlots findTransferSlots(C container,
                                                         IRecipeLayout recipeLayout) {
        IntList missingSlots = new IntArrayList();
        IntList craftableSlots = new IntArrayList();
        IClientRepo repo = container.getClientRepo();
        var ingredientGroup = recipeLayout.getIngredientsGroup(VanillaTypes.ITEM);
        if (repo == null) {
            return new RecipeTransferSlots(missingSlots, craftableSlots);
        }
        Object2IntMap<Object> reservedAmounts = new Object2IntOpenHashMap<>();
        var map = ingredientGroup.getGuiIngredients();
        if (map.isEmpty()) {
            return new RecipeTransferSlots(missingSlots, craftableSlots);
        }

        if (map instanceof Int2ObjectMap<? extends IGuiIngredient<ItemStack>> m) {
            for (var entry : m.int2ObjectEntrySet()) {
                IGuiIngredient<ItemStack> guiIngredient = entry.getValue();
                if (guiIngredient == null || !guiIngredient.isInput()) {
                    continue;
                }

                List<ItemStack> allIngredients = guiIngredient.getAllIngredients();
                if (allIngredients.isEmpty()) {
                    continue;
                }

                Ingredient ingredient = createIngredient(allIngredients);
                if (ingredient == Ingredient.EMPTY || container.hasIngredient(ingredient, reservedAmounts)) {
                    continue;
                }

                if (isCraftable(repo, ingredient)) {
                    craftableSlots.add(entry.getIntKey());
                } else {
                    missingSlots.add(entry.getIntKey());
                }
            }
        } else {
            for (var entry : map.entrySet()) {
                Integer guiSlotKey = entry.getKey();
                if (guiSlotKey == null) {
                    continue;
                }
                int guiSlot = guiSlotKey;
                IGuiIngredient<ItemStack> guiIngredient = entry.getValue();
                if (guiIngredient == null || !guiIngredient.isInput()) {
                    continue;
                }

                List<ItemStack> allIngredients = guiIngredient.getAllIngredients();
                if (allIngredients.isEmpty()) {
                    continue;
                }

                Ingredient ingredient = createIngredient(allIngredients);
                if (ingredient == Ingredient.EMPTY || container.hasIngredient(ingredient, reservedAmounts)) {
                    continue;
                }

                if (isCraftable(repo, ingredient)) {
                    craftableSlots.add(guiSlot);
                } else {
                    missingSlots.add(guiSlot);
                }
            }
        }
        return new RecipeTransferSlots(missingSlots, craftableSlots);
    }

    private static boolean isCraftable(IClientRepo repo, Ingredient ingredient) {
        for (GridInventoryEntry entry : repo.getAllEntries()) {
            if (entry != null && entry.what() instanceof AEItemKey itemKey && itemKey.matches(ingredient)
                && entry.craftable()) {
                return true;
            }
        }
        return false;
    }

    private static Ingredient createIngredient(List<ItemStack> stacks) {
        ItemStack[] matchingStacks = stacks.stream()
                                           .filter(stack -> stack != null && !stack.isEmpty())
                                           .map(ItemStack::copy)
                                           .toArray(ItemStack[]::new);
        return matchingStacks.length == 0 ? Ingredient.EMPTY : Ingredient.fromStacks(matchingStacks);
    }

    private static List<PatternRecipeTransferSlot> planProcessingInputs(List<List<GenericStack>> possibleInputsBySlot,
                                                                       PatternImportPriorityContext context,
                                                                       boolean mergeInputs) {
        List<PatternRecipeTransferSlot> slots = new ObjectArrayList<>();
        for (List<GenericStack> genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                GenericStack selected = PatternImportPrioritySelector.selectIngredient(genericIngredient, context,
                    false);
                if (isValidStack(selected)) {
                    if (mergeInputs) {
                        addOrMergeInput(slots, selected, genericIngredient);
                    } else {
                        addInput(slots, selected, genericIngredient);
                    }
                }
            }
        }
        return slots;
    }

    private static List<PatternRecipeTransferSlot> planStacks(List<List<GenericStack>> possibleInputsBySlot,
                                                              PatternImportPriorityContext context) {
        List<PatternRecipeTransferSlot> slots = new ObjectArrayList<>();
        for (List<GenericStack> genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                GenericStack selected = PatternImportPrioritySelector.selectIngredient(genericIngredient, context,
                    false);
                if (isValidStack(selected)) {
                    addOrMerge(slots, selected);
                }
            }
        }
        return slots;
    }

    private static List<PatternRecipeTransferSlot> planCraftingGrid(List<List<GenericStack>> possibleInputsBySlot,
                                                                    PatternImportPriorityContext context) {
        List<PatternRecipeTransferSlot> slots = new ObjectArrayList<>(possibleInputsBySlot.size());
        for (int i = 0; i < possibleInputsBySlot.size(); i++) {
            List<GenericStack> genericIngredient = possibleInputsBySlot.get(i);
            GenericStack selected = genericIngredient.isEmpty()
                ? null
                : PatternImportPrioritySelector.selectIngredient(genericIngredient, context, true);
            var slot = new PatternRecipeTransferSlot(i, isValidStack(selected) ? selected : null);
            for (AEKey candidate : getCandidateKeys(genericIngredient)) {
                slot.addCandidate(candidate);
            }
            slots.add(slot);
        }
        return slots;
    }

    private void writeSlots(C container, FakeSlot[] slots, List<PatternRecipeTransferSlot> packedSlots) {
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < packedSlots.size()
                ? GenericStack.wrapInItemStack(packedSlots.get(i).getStack())
                : ItemStack.EMPTY;
            setFilter(container, slots[i], stack);
        }
    }

    private void writePositionalSlots(C container, FakeSlot[] slots, List<PatternRecipeTransferSlot> plannedSlots) {
        for (int i = 0; i < slots.length; i++) {
            GenericStack stack = i < plannedSlots.size() ? plannedSlots.get(i).getStack() : null;
            setFilter(container, slots[i], toCraftingStack(stack));
        }
    }

    private static ItemStack toCraftingStack(@Nullable GenericStack stack) {
        if (!isValidStack(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack();
        }
        return GenericStack.wrapInItemStack(stack.what(), 1);
    }

    private static void addOrMergeInput(List<PatternRecipeTransferSlot> slots, GenericStack newStack,
                                        List<GenericStack> possibleInputs) {
        List<AEKey> newCandidates = getCandidateKeys(possibleInputs);
        if (!isValidStack(newStack)) {
            return;
        }
        if (!newCandidates.contains(newStack.what())) {
            newCandidates.add(newStack.what());
        }
        for (int i = 0; i < slots.size(); i++) {
            PatternRecipeTransferSlot slot = slots.get(i);
            GenericStack existingStack = slot.getStack();
            if (isValidStack(existingStack) && Objects.equals(existingStack.what(), newStack.what())) {
                long newAmount = LongMath.saturatedAdd(existingStack.amount(), newStack.amount());
                slot.setStack(new GenericStack(newStack.what(), newAmount));
                replaceCandidates(slot, intersectCandidates(slot.getCandidates(), newCandidates));

                long overflow = newStack.amount() - (newAmount - existingStack.amount());
                if (overflow > 0) {
                    slots.add(new PatternRecipeTransferSlot(slots.size(),
                        new GenericStack(newStack.what(), overflow), newCandidates));
                }
                return;
            }
        }

        slots.add(new PatternRecipeTransferSlot(slots.size(), newStack, newCandidates));
    }

    private static void addInput(List<PatternRecipeTransferSlot> slots, GenericStack newStack,
                                 List<GenericStack> possibleInputs) {
        List<AEKey> candidates = getCandidateKeys(possibleInputs);
        if (!isValidStack(newStack)) {
            return;
        }
        if (!candidates.contains(newStack.what())) {
            candidates.add(newStack.what());
        }
        slots.add(new PatternRecipeTransferSlot(slots.size(), newStack, candidates));
    }

    private static void replaceCandidates(PatternRecipeTransferSlot slot, List<AEKey> candidates) {
        List<AEKey> live = slot.getCandidates();
        live.clear();
        for (AEKey candidate : candidates) {
            slot.addCandidate(candidate);
        }
    }

    private static List<AEKey> getCandidateKeys(List<GenericStack> possibleInputs) {
        List<AEKey> candidates = new ObjectArrayList<>();
        for (GenericStack possibleInput : possibleInputs) {
            if (isValidStack(possibleInput) && !candidates.contains(possibleInput.what())) {
                candidates.add(possibleInput.what());
            }
        }
        return candidates;
    }

    private static List<AEKey> intersectCandidates(List<AEKey> first, List<AEKey> second) {
        List<AEKey> result = new ObjectArrayList<>();
        for (AEKey key : first) {
            if (second.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private static void addOrMerge(List<PatternRecipeTransferSlot> slots, GenericStack newStack) {
        if (!isValidStack(newStack)) {
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            PatternRecipeTransferSlot slot = slots.get(i);
            GenericStack existingStack = slot.getStack();
            if (isValidStack(existingStack) && Objects.equals(existingStack.what(), newStack.what())) {
                long newAmount = LongMath.saturatedAdd(existingStack.amount(), newStack.amount());
                slot.setStack(new GenericStack(newStack.what(), newAmount));

                long overflow = newStack.amount() - (newAmount - existingStack.amount());
                if (overflow > 0) {
                    slots.add(new PatternRecipeTransferSlot(slots.size(),
                        new GenericStack(newStack.what(), overflow)));
                }
                return;
            }
        }

        slots.add(new PatternRecipeTransferSlot(slots.size(), newStack));
    }

    private void setFilter(C container, FakeSlot slot, ItemStack stack) {
        if (slot == null) {
            return;
        }
        InitNetwork.sendToServer(new InventoryActionPacket(
            container.windowId,
            InventoryAction.SET_FILTER,
            slot.slotNumber,
            stack));
    }

    @Override
    @NotNull
    public Class<C> getContainerClass() {
        return this.containerClass;
    }

    private static String getRecipeCategoryUid(IRecipeLayout recipeLayout) {
        return recipeLayout.getRecipeCategory().getUid();
    }

    private void encodeCraftingRecipe(C container, IRecipeLayout recipeLayout) {
        String recipeCategoryUid = getRecipeCategoryUid(recipeLayout);
        PatternImportPriorityContext context = PatternImportPriorityContextImpl.create(container,
            HeiBookmarkHelper.getBookmarkedStacks());
        List<PatternRecipeTransferSlot> grid = planCraftingGrid(getCraftingInputs(recipeLayout), context);
        ResourceLocation craftingRecipeId = getCraftingRecipeId(recipeLayout);

        if (PatternRecipeTransferEvents.post(container, EncodingMode.CRAFTING, recipeCategoryUid, craftingRecipeId,
            grid, List.of())) {
            return;
        }

        container.setMode(EncodingMode.CRAFTING);
        writePositionalSlots(container, container.getCraftingGridSlots(), grid);
        for (FakeSlot slot : container.getProcessingOutputSlots()) {
            setFilter(container, slot, ItemStack.EMPTY);
        }
        container.setHeiCraftingRecipe(craftingRecipeId);
    }

    private record RecipeTransferSlots(IntList missingGuiSlots, IntList craftableGuiSlots) {
    }

    private static boolean isValidStack(GenericStack stack) {
        return stack != null && stack.what() != null && stack.amount() > 0;
    }

    @Override
    public IRecipeTransferError transferRecipe(@NotNull C container,
                                               @NotNull IRecipeLayout recipeLayout,
                                               @NotNull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        String recipeCategoryUid = getRecipeCategoryUid(recipeLayout);
        if (VanillaRecipeCategoryUid.INFORMATION.equals(recipeCategoryUid)
            || VanillaRecipeCategoryUid.FUEL.equals(recipeCategoryUid)) {
            return null;
        }

        if (!doTransfer) {
            RecipeTransferSlots slots = findTransferSlots(container, recipeLayout);
            return new PatternRecipeTransferUserError(recipeLayout, slots.missingGuiSlots(), slots.craftableGuiSlots());
        }

        if (VanillaRecipeCategoryUid.CRAFTING.equals(recipeCategoryUid)) {
            encodeCraftingRecipe(container, recipeLayout);
        } else {
            encodeProcessingRecipe(container, recipeLayout);
        }
        return null;
    }
}
