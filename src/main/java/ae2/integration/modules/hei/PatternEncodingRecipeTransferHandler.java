package ae2.integration.modules.hei;

import ae2.api.client.PatternImportPriorityContext;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.client.patternimport.PatternImportPriorityContextImpl;
import ae2.client.patternimport.PatternImportPrioritySelector;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.container.slot.FakeSlot;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
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
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import org.jetbrains.annotations.NotNull;

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

    private void encodeCraftingRecipe(C container, IRecipeLayout recipeLayout) {
        container.setMode(EncodingMode.CRAFTING);
        PatternImportPriorityContext context = PatternImportPriorityContextImpl.create(container,
            HeiBookmarkHelper.getBookmarkedStacks());
        List<List<GenericStack>> inputs = getCraftingInputs(recipeLayout);

        FakeSlot[] slots = container.getCraftingGridSlots();
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = ItemStack.EMPTY;
            if (i < inputs.size() && !inputs.get(i).isEmpty()) {
                GenericStack genericStack = PatternImportPrioritySelector.selectIngredient(inputs.get(i), context, true);
                if (isValidStack(genericStack)) {
                    if (genericStack.what() instanceof AEItemKey itemKey) {
                        stack = itemKey.toStack();
                    } else {
                        stack = GenericStack.wrapInItemStack(genericStack.what(), 1);
                    }
                }
            }
            setFilter(container, slots[i], stack);
        }

        for (FakeSlot slot : container.getProcessingOutputSlots()) {
            setFilter(container, slot, ItemStack.EMPTY);
        }
    }

    private void encodeProcessingRecipe(C container, IRecipeLayout recipeLayout) {
        container.setMode(EncodingMode.PROCESSING);
        PatternImportPriorityContext context = PatternImportPriorityContextImpl.create(container,
            HeiBookmarkHelper.getBookmarkedStacks());

        List<List<String>> inputCandidateKeyTags = encodeProcessingInputsIntoSlots(container,
            getGenericInputs(recipeLayout), context, container.getProcessingInputSlots());
        encodeSelectedStacksIntoSlots(container, getGenericOutputs(recipeLayout), context,
            container.getProcessingOutputSlots());
        container.setHeiProcessingRecipe(getRecipeCategoryUid(recipeLayout), inputCandidateKeyTags);
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
        if (repo == null || ingredientGroup == null) {
            return new RecipeTransferSlots(missingSlots, craftableSlots);
        }
        Object2IntMap<Object> reservedAmounts = new Object2IntOpenHashMap<>();
        var map = ingredientGroup.getGuiIngredients();
        if (map == null || map.isEmpty()) {
            return new RecipeTransferSlots(missingSlots, craftableSlots);
        }

        if (map instanceof Int2ObjectMap<? extends IGuiIngredient<ItemStack>> m) {
            for (var entry : m.int2ObjectEntrySet()) {
                IGuiIngredient<ItemStack> guiIngredient = entry.getValue();
                if (guiIngredient == null || !guiIngredient.isInput()) {
                    continue;
                }

                List<ItemStack> allIngredients = guiIngredient.getAllIngredients();
                if (allIngredients == null || allIngredients.isEmpty()) {
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
                if (allIngredients == null || allIngredients.isEmpty()) {
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

    private void encodeSelectedStacksIntoSlots(C container,
                                               List<List<GenericStack>> possibleInputsBySlot,
                                               PatternImportPriorityContext context,
                                               FakeSlot[] slots) {
        List<GenericStack> encodedInputs = new ObjectArrayList<>();
        for (List<GenericStack> genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                GenericStack selected = PatternImportPrioritySelector.selectIngredient(genericIngredient, context,
                    false);
                if (isValidStack(selected)) {
                    addOrMerge(encodedInputs, selected);
                }
            }
        }

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < encodedInputs.size() && isValidStack(encodedInputs.get(i))
                ? GenericStack.wrapInItemStack(encodedInputs.get(i))
                : ItemStack.EMPTY;
            setFilter(container, slots[i], stack);
        }
    }

    private List<List<String>> encodeProcessingInputsIntoSlots(C container,
                                                               List<List<GenericStack>> possibleInputsBySlot,
                                                               PatternImportPriorityContext context,
                                                               FakeSlot[] slots) {
        List<GenericStack> encodedInputs = new ObjectArrayList<>();
        List<List<AEKey>> candidatesByEncodedSlot = new ObjectArrayList<>();
        for (List<GenericStack> genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                GenericStack selected = PatternImportPrioritySelector.selectIngredient(genericIngredient, context,
                    false);
                if (isValidStack(selected)) {
                    addOrMergeInput(encodedInputs, candidatesByEncodedSlot, selected, genericIngredient);
                }
            }
        }

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < encodedInputs.size() && isValidStack(encodedInputs.get(i))
                ? GenericStack.wrapInItemStack(encodedInputs.get(i))
                : ItemStack.EMPTY;
            setFilter(container, slots[i], stack);
        }

        List<List<String>> candidateKeyTags = new ObjectArrayList<>(candidatesByEncodedSlot.size());
        for (List<AEKey> candidates : candidatesByEncodedSlot) {
            List<String> tags = new ObjectArrayList<>(candidates.size());
            for (AEKey candidate : candidates) {
                tags.add(candidate.toTagGeneric().toString());
            }
            candidateKeyTags.add(tags);
        }
        return candidateKeyTags;
    }

    private static void addOrMergeInput(List<GenericStack> stacks, List<List<AEKey>> candidatesByEncodedSlot,
                                        GenericStack newStack, List<GenericStack> possibleInputs) {
        List<AEKey> newCandidates = getCandidateKeys(possibleInputs);
        if (!isValidStack(newStack)) {
            return;
        }
        if (!newCandidates.contains(newStack.what())) {
            newCandidates.add(newStack.what());
        }
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existingStack = stacks.get(i);
            if (isValidStack(existingStack) && Objects.equals(existingStack.what(), newStack.what())) {
                long newAmount = LongMath.saturatedAdd(existingStack.amount(), newStack.amount());
                stacks.set(i, new GenericStack(newStack.what(), newAmount));
                candidatesByEncodedSlot.set(i, intersectCandidates(candidatesByEncodedSlot.get(i), newCandidates));

                long overflow = newStack.amount() - (newAmount - existingStack.amount());
                if (overflow > 0) {
                    stacks.add(new GenericStack(newStack.what(), overflow));
                    candidatesByEncodedSlot.add(new ObjectArrayList<>(newCandidates));
                }
                return;
            }
        }

        stacks.add(newStack);
        candidatesByEncodedSlot.add(new ObjectArrayList<>(newCandidates));
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

    private static void addOrMerge(List<GenericStack> stacks, GenericStack newStack) {
        if (!isValidStack(newStack)) {
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existingStack = stacks.get(i);
            if (isValidStack(existingStack) && Objects.equals(existingStack.what(), newStack.what())) {
                long newAmount = LongMath.saturatedAdd(existingStack.amount(), newStack.amount());
                stacks.set(i, new GenericStack(newStack.what(), newAmount));

                long overflow = newStack.amount() - (newAmount - existingStack.amount());
                if (overflow > 0) {
                    stacks.add(new GenericStack(newStack.what(), overflow));
                }
                return;
            }
        }

        stacks.add(newStack);
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
    public Class<C> getContainerClass() {
        return this.containerClass;
    }

    private static String getRecipeCategoryUid(IRecipeLayout recipeLayout) {
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        return category == null ? "" : category.getUid();
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
