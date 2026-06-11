package ae2.integration.modules.hei;

import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.container.me.items.ContainerCraftingTerm;
import ae2.core.localization.ItemModText;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.integration.modules.itemlists.CraftingHelper;
import ae2.util.EmptyArrays;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CraftingRecipeTransferHandler<T extends ContainerCraftingTerm> implements IRecipeTransferHandler<T> {
    private static final int RECIPE_OUTPUT_SLOT = 0;
    private static final int CRAFTING_GRID_SIZE = 9;

    private final Class<T> containerClass;
    private final IRecipeTransferHandlerHelper handlerHelper;

    public CraftingRecipeTransferHandler(Class<T> containerClass, IRecipeTransferHandlerHelper handlerHelper) {
        this.containerClass = containerClass;
        this.handlerHelper = handlerHelper;
    }

    private static List<List<ItemStack>> createEmptyTemplates() {
        List<List<ItemStack>> templates = new ObjectArrayList<>(CRAFTING_GRID_SIZE);
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            templates.add(new ObjectArrayList<>());
        }
        return templates;
    }

    private static void addTemplateStack(List<ItemStack> stacks, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ItemStack template = stack.copy();
        template.setCount(Math.clamp(template.getCount(), 1, 64));
        for (ItemStack existing : stacks) {
            if (ItemStack.areItemStacksEqual(existing, template)) {
                return;
            }
        }
        stacks.add(template);
    }

    private static @Nullable ExtractedPseudoRecipe extractTemporaryPseudoRecipe(ContainerCraftingTerm container,
                                                                                IRecipeLayout recipeLayout) {
        boolean craftingLayout = isCraftingLayout(recipeLayout);
        List<GenericStack> recipeInputs = selectFirstGenericStackPerSlot(
            GenericIngredientHelper.getIngredients(recipeLayout, true, craftingLayout,
                craftingLayout ? CRAFTING_GRID_SIZE : AEProcessingPattern.MAX_INPUT_SLOTS),
            AEProcessingPattern.MAX_INPUT_SLOTS);
        List<GenericStack> outputs = selectFirstGenericStackPerSlot(
            GenericIngredientHelper.getIngredients(recipeLayout, false, false, AEProcessingPattern.MAX_OUTPUT_SLOTS),
            AEProcessingPattern.MAX_OUTPUT_SLOTS);

        if (recipeInputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }

        List<GenericStack> missingInputs = getMissingInputsOnClient(container, recipeInputs);
        if (missingInputs.isEmpty()) {
            return null;
        }
        return new ExtractedPseudoRecipe(missingInputs, outputs);
    }

    private static List<GenericStack> selectFirstGenericStackPerSlot(List<List<GenericStack>> stacksBySlot, int maxSize) {
        List<GenericStack> result = new ObjectArrayList<>(Math.min(stacksBySlot.size(), maxSize));
        for (List<GenericStack> stacks : stacksBySlot) {
            if (!stacks.isEmpty()) {
                result.add(stacks.getFirst());
                if (result.size() >= maxSize) {
                    break;
                }
            }
        }
        return result;
    }

    static List<GenericStack> getMissingInputsOnClient(ContainerCraftingTerm container, List<GenericStack> recipeInputs) {
        KeyCounter available = new KeyCounter();

        var craftMatrix = container.getCraftingMatrix();
        for (int i = 0; i < craftMatrix.size(); i++) {
            addAvailableStack(available, GenericStack.fromItemStack(craftMatrix.getStackInSlot(i)));
        }

        IClientRepo clientRepo = container.getClientRepo();
        if (clientRepo != null && container.getLinkStatus().connected()) {
            for (GridInventoryEntry entry : clientRepo.getAllEntries()) {
                if (entry != null && entry.what() != null && entry.storedAmount() > 0) {
                    available.add(entry.what(), entry.storedAmount());
                }
            }
        }

        var playerItems = container.getPlayerInventory().mainInventory;
        for (int i = 0; i < playerItems.size(); i++) {
            if (container.isPlayerInventorySlotLocked(i)) {
                continue;
            }
            addAvailableStack(available, GenericStack.fromItemStack(playerItems.get(i)));
        }

        List<GenericStack> missing = new ObjectArrayList<>(recipeInputs.size());
        for (GenericStack input : recipeInputs) {
            if (!isValidStack(input)) {
                continue;
            }
            long remaining = input.amount();
            long fromAvailable = Math.min(remaining, available.get(input.what()));
            if (fromAvailable > 0) {
                available.remove(input.what(), fromAvailable);
                remaining -= fromAvailable;
            }
            if (remaining > 0) {
                addOrMerge(missing, new GenericStack(input.what(), remaining));
                if (missing.size() >= AEProcessingPattern.MAX_INPUT_SLOTS) {
                    break;
                }
            }
        }
        return missing;
    }

    private static void addAvailableStack(KeyCounter available, @Nullable GenericStack stack) {
        if (isValidStack(stack)) {
            available.add(stack.what(), stack.amount());
        }
    }

    private static void addOrMerge(List<GenericStack> stacks, GenericStack stack) {
        if (!isValidStack(stack)) {
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack existing = stacks.get(i);
            if (isValidStack(existing) && existing.what().equals(stack.what())) {
                stacks.set(i, GenericStack.sum(existing, stack));
                return;
            }
        }
        stacks.add(stack);
    }

    @Override
    public Class<T> getContainerClass() {
        return containerClass;
    }

    private static boolean isCraftingLayout(IRecipeLayout recipeLayout) {
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        return category != null && VanillaRecipeCategoryUid.CRAFTING.equals(category.getUid());
    }

    private static boolean isValidStack(@Nullable GenericStack stack) {
        return stack != null && stack.what() != null && stack.amount() > 0;
    }

    private record ExtractedRecipe(Int2ObjectMap<Ingredient> ingredients, List<List<ItemStack>> templates,
                                   boolean tooLarge) {
    }

    private record ExtractedPseudoRecipe(List<GenericStack> inputs, List<GenericStack> outputs) {
    }

    @Override
    public IRecipeTransferError transferRecipe(@NotNull T container,
                                               @NotNull IRecipeLayout recipeLayout,
                                               @NotNull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        if (recipeLayout == null) {
            return this.handlerHelper.createInternalError();
        }

        ExtractedRecipe extractedRecipe = extractRecipe(recipeLayout);
        ExtractedPseudoRecipe pseudoRecipe = GuiScreen.isCtrlKeyDown()
            ? extractTemporaryPseudoRecipe(container, recipeLayout)
            : null;
        if (extractedRecipe == null && pseudoRecipe == null) {
            return this.handlerHelper.createInternalError();
        }

        if (extractedRecipe != null && extractedRecipe.tooLarge) {
            return this.handlerHelper.createUserErrorWithTooltip(ItemModText.RecipeTooLarge.getLocal());
        }

        if (!doTransfer && pseudoRecipe != null) {
            return null;
        }

        if (!doTransfer && extractedRecipe != null) {
            ContainerCraftingTerm.MissingIngredientSlots missingSlots =
                container.findMissingIngredients(extractedRecipe.ingredients);
            CraftingRecipeTransferAnalysis analysis = CraftingRecipeTransferAnalysis.analyze(missingSlots,
                extractedRecipe.ingredients.size());
            if (analysis.outcome() != CraftingRecipeTransferAnalysis.Outcome.READY) {
                return CraftingRecipeTransferUserError.create(recipeLayout, analysis);
            }
            return null;
        }

        if (!doTransfer) {
            return null;
        }

        List<List<ItemStack>> templates = extractedRecipe != null ? extractedRecipe.templates : createEmptyTemplates();
        if (pseudoRecipe != null) {
            CraftingHelper.performTransfer(container, null, templates, true, pseudoRecipe.inputs, pseudoRecipe.outputs);
        } else {
            CraftingHelper.performTransfer(container, null, templates, GuiScreen.isCtrlKeyDown());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private @Nullable ExtractedRecipe extractRecipe(IRecipeLayout recipeLayout) {
        var ingredientGroup = recipeLayout.getIngredientsGroup(VanillaTypes.ITEM);
        if (ingredientGroup == null) {
            return null;
        }

        var guiIngredients = ingredientGroup.getGuiIngredients();
        if (guiIngredients == null || guiIngredients.isEmpty()) {
            return null;
        }

        Int2ObjectMap<Ingredient> ingredients = new Int2ObjectOpenHashMap<>();
        List<List<ItemStack>> templates = createEmptyTemplates();
        boolean craftingLayout = isCraftingLayout(recipeLayout);
        boolean tooLarge = false;
        int nextProcessingSlot = 0;

        Map.Entry<Integer, IGuiIngredient<ItemStack>>[] entries;

        if (guiIngredients instanceof Int2ObjectMap<? extends IGuiIngredient<ItemStack>> map) {
            var a = map.int2ObjectEntrySet().toArray(Int2ObjectMap.Entry[]::new);
            Arrays.sort(a, Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey));
            entries = a;
        } else {
            entries = guiIngredients.entrySet().toArray(Map.Entry[]::new);
            Arrays.sort(entries, Comparator.comparingInt(Map.Entry::getKey));
        }

        for (var entry : entries) {
            IGuiIngredient<ItemStack> guiIngredient = entry.getValue();
            if (guiIngredient == null || !guiIngredient.isInput()) {
                continue;
            }

            int gridSlot;
            if (craftingLayout) {
                int guiSlot;
                if (entry instanceof Int2ObjectMap.Entry<IGuiIngredient<ItemStack>> e) {
                    guiSlot = e.getIntKey();
                } else {
                    Integer guiSlotKey = entry.getKey();
                    if (guiSlotKey == null) {
                        continue;
                    }
                    guiSlot = guiSlotKey;
                }
                gridSlot = guiSlot - 1;
                if (guiSlot == RECIPE_OUTPUT_SLOT || gridSlot < 0) {
                    continue;
                }
                if (gridSlot >= CRAFTING_GRID_SIZE) {
                    tooLarge = true;
                    continue;
                }
            } else {
                if (nextProcessingSlot >= CRAFTING_GRID_SIZE) {
                    continue;
                }
                gridSlot = nextProcessingSlot++;
            }

            List<ItemStack> allIngredients = guiIngredient.getAllIngredients();
            if (allIngredients == null || allIngredients.isEmpty()) {
                continue;
            }

            List<ItemStack> stacks = new ObjectArrayList<>(allIngredients.size());
            addTemplateStack(stacks, guiIngredient.getDisplayedIngredient());
            for (ItemStack stack : allIngredients) {
                addTemplateStack(stacks, stack);
            }

            if (stacks.isEmpty()) {
                continue;
            }

            ItemStack[] matchingStacks = stacks.toArray(EmptyArrays.EMPTY_ITEM_STACK_ARRAY);
            ingredients.put(gridSlot, Ingredient.fromStacks(matchingStacks));
            templates.set(gridSlot, stacks);
        }

        return ingredients.isEmpty() && !tooLarge ? null : new ExtractedRecipe(ingredients, templates, tooLarge);
    }
}
