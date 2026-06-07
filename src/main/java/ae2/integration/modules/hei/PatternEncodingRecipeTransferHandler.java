package ae2.integration.modules.hei;

import ae2.api.client.PatternImportPriorityContext;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.client.patternimport.PatternImportPriorityContextImpl;
import ae2.client.patternimport.PatternImportPrioritySelector;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.me.items.ContainerPatternEncodingTerm;
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
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Objects;

public class PatternEncodingRecipeTransferHandler implements IRecipeTransferHandler<ContainerPatternEncodingTerm> {
    @SuppressWarnings("unused")
    private static final int RECIPE_OUTPUT_SLOT = 0;
    private static final int CRAFTING_GRID_SIZE = 9;

    private static void encodeCraftingRecipe(ContainerPatternEncodingTerm container, IRecipeLayout recipeLayout) {
        container.setMode(EncodingMode.CRAFTING);
        PatternImportPriorityContext context = PatternImportPriorityContextImpl.create(container,
            HeiBookmarkHelper.getBookmarkedStacks());
        List<List<GenericStack>> inputs = getCraftingInputs(recipeLayout);

        FakeSlot[] slots = container.getCraftingGridSlots();
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = ItemStack.EMPTY;
            if (i < inputs.size() && !inputs.get(i).isEmpty()) {
                GenericStack genericStack = PatternImportPrioritySelector.selectIngredient(inputs.get(i), context, true);
                if (genericStack.what() instanceof AEItemKey itemKey) {
                    stack = itemKey.toStack();
                } else {
                    stack = GenericStack.wrapInItemStack(genericStack.what(), 1);
                }
            }
            setFilter(container, slots[i], stack);
        }

        for (FakeSlot slot : container.getProcessingOutputSlots()) {
            setFilter(container, slot, ItemStack.EMPTY);
        }
    }

    private static void encodeProcessingRecipe(ContainerPatternEncodingTerm container, IRecipeLayout recipeLayout) {
        container.setMode(EncodingMode.PROCESSING);
        PatternImportPriorityContext context = PatternImportPriorityContextImpl.create(container,
            HeiBookmarkHelper.getBookmarkedStacks());

        List<List<String>> inputCandidateKeyTags = encodeProcessingInputsIntoSlots(container,
            getGenericInputs(recipeLayout), context, container.getProcessingInputSlots());
        encodeSelectedStacksIntoSlots(container, getGenericOutputs(recipeLayout), context,
            container.getProcessingOutputSlots());
        container.setHeiProcessingRecipe(recipeLayout.getRecipeCategory().getUid(), inputCandidateKeyTags);
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

    private static RecipeTransferSlots findTransferSlots(ContainerPatternEncodingTerm container,
                                                         IRecipeLayout recipeLayout) {
        IntList missingSlots = new IntArrayList();
        IntList craftableSlots = new IntArrayList();
        IClientRepo repo = container.getClientRepo();
        if (repo == null) {
            return new RecipeTransferSlots(missingSlots, craftableSlots);
        }
        Object2IntMap<Object> reservedAmounts = new Object2IntOpenHashMap<>();
        var map = recipeLayout.getIngredientsGroup(VanillaTypes.ITEM).getGuiIngredients();

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
                    craftableSlots.add(entry.getKey().intValue());
                } else {
                    missingSlots.add(entry.getKey().intValue());
                }
            }
        }
        return new RecipeTransferSlots(missingSlots, craftableSlots);
    }

    private static boolean isCraftable(IClientRepo repo, Ingredient ingredient) {
        for (GridInventoryEntry entry : repo.getAllEntries()) {
            if (entry.what() instanceof AEItemKey itemKey && itemKey.matches(ingredient) && entry.craftable()) {
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

    private static void encodeSelectedStacksIntoSlots(ContainerPatternEncodingTerm container,
                                                      List<List<GenericStack>> possibleInputsBySlot,
                                                      PatternImportPriorityContext context,
                                                      FakeSlot[] slots) {
        List<GenericStack> encodedInputs = new ObjectArrayList<>();
        for (List<GenericStack> genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                addOrMerge(encodedInputs, PatternImportPrioritySelector.selectIngredient(genericIngredient, context,
                    false));
            }
        }

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < encodedInputs.size() ? GenericStack.wrapInItemStack(encodedInputs.get(i))
                : ItemStack.EMPTY;
            setFilter(container, slots[i], stack);
        }
    }

    private static List<List<String>> encodeProcessingInputsIntoSlots(ContainerPatternEncodingTerm container,
                                                                      List<List<GenericStack>> possibleInputsBySlot,
                                                                      PatternImportPriorityContext context,
                                                                      FakeSlot[] slots) {
        List<GenericStack> encodedInputs = new ObjectArrayList<>();
        List<List<AEKey>> candidatesByEncodedSlot = new ObjectArrayList<>();
        for (List<GenericStack> genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                addOrMergeInput(encodedInputs, candidatesByEncodedSlot,
                    PatternImportPrioritySelector.selectIngredient(genericIngredient, context, false),
                    genericIngredient);
            }
        }

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < encodedInputs.size() ? GenericStack.wrapInItemStack(encodedInputs.get(i))
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
        if (!newCandidates.contains(newStack.what())) {
            newCandidates.add(newStack.what());
        }
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existingStack = stacks.get(i);
            if (Objects.equals(existingStack.what(), newStack.what())) {
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
            if (possibleInput != null && !candidates.contains(possibleInput.what())) {
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
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existingStack = stacks.get(i);
            if (Objects.equals(existingStack.what(), newStack.what())) {
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

    private static void setFilter(ContainerPatternEncodingTerm container, FakeSlot slot, ItemStack stack) {
        InitNetwork.sendToServer(new InventoryActionPacket(
            container.windowId,
            InventoryAction.SET_FILTER,
            slot.slotNumber,
            stack));
    }

    @Override
    public Class<ContainerPatternEncodingTerm> getContainerClass() {
        return ContainerPatternEncodingTerm.class;
    }

    @Override
    public IRecipeTransferError transferRecipe(@Nonnull ContainerPatternEncodingTerm container,
                                               @Nonnull IRecipeLayout recipeLayout,
                                               @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        if (recipeLayout.getRecipeCategory().getUid().equals(VanillaRecipeCategoryUid.INFORMATION)
            || recipeLayout.getRecipeCategory().getUid().equals(VanillaRecipeCategoryUid.FUEL)) {
            return null;
        }

        if (!doTransfer) {
            RecipeTransferSlots slots = findTransferSlots(container, recipeLayout);
            return new PatternRecipeTransferUserError(recipeLayout, slots.missingGuiSlots(), slots.craftableGuiSlots());
        }

        if (recipeLayout.getRecipeCategory().getUid().equals(VanillaRecipeCategoryUid.CRAFTING)) {
            encodeCraftingRecipe(container, recipeLayout);
        } else {
            encodeProcessingRecipe(container, recipeLayout);
        }
        return null;
    }

    private record RecipeTransferSlots(IntList missingGuiSlots, IntList craftableGuiSlots) {
    }
}
