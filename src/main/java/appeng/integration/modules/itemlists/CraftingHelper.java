package appeng.integration.modules.itemlists;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.container.me.common.GridInventoryEntry;
import appeng.container.me.common.IClientRepo;
import appeng.container.me.items.ContainerCraftingTerm;
import appeng.core.AELog;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.util.CraftingRecipeUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CraftingHelper {
    private static final int CRAFTING_GRID_SIZE = 9;

    private CraftingHelper() {
    }

    public static void performTransfer(ContainerCraftingTerm container, @Nullable ResourceLocation recipeId, IRecipe recipe,
                                       boolean craftMissing) {
        List<List<ItemStack>> templateItems = findTemplateItems(recipe, container);
        performTransfer(container, recipeId, templateItems, craftMissing);
    }

    public static void performTransfer(ContainerCraftingTerm container, @Nullable ResourceLocation recipeId,
                                       NonNullList<ItemStack> templateItems, boolean craftMissing) {
        performTransfer(container, recipeId, convertSingleTemplates(templateItems), craftMissing);
    }

    public static void performTransfer(ContainerCraftingTerm container, @Nullable ResourceLocation recipeId,
                                       List<List<ItemStack>> templateItems, boolean craftMissing) {
        performTransfer(container, recipeId, templateItems, craftMissing, List.of(), List.of());
    }

    public static void performTransfer(ContainerCraftingTerm container, @Nullable ResourceLocation recipeId,
                                       List<List<ItemStack>> templateItems, boolean craftMissing,
                                       List<GenericStack> temporaryPseudoInputs,
                                       List<GenericStack> temporaryPseudoOutputs) {
        if (recipeId != null && CraftingManager.REGISTRY.getObject(recipeId) == null) {
            AELog.debug("Cannot send recipe id %s to server because it's transient", recipeId);
            recipeId = null;
        }

        InitNetwork.sendToServer(new FillCraftingGridFromRecipePacket(recipeId, templateItems, craftMissing,
            temporaryPseudoInputs, temporaryPseudoOutputs));
    }

    private static List<List<ItemStack>> findTemplateItems(IRecipe recipe, ContainerCraftingTerm container) {
        List<List<ItemStack>> templateItems = createEmptyTemplateList();
        NonNullList<Ingredient> ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        IClientRepo clientRepo = container.getClientRepo();

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient == Ingredient.EMPTY) {
                continue;
            }

            templateItems.set(i, findTemplateStacks(ingredient, clientRepo));
        }

        return templateItems;
    }

    private static List<ItemStack> findTemplateStacks(Ingredient ingredient, @Nullable IClientRepo clientRepo) {
        List<ItemStack> templates = new ObjectArrayList<>();

        ItemStack preferredStack = findBestTemplateStack(ingredient, clientRepo);
        if (!preferredStack.isEmpty()) {
            templates.add(preferredStack.copy());
        }

        ItemStack[] matchingStacks = ingredient.getMatchingStacks();
        for (ItemStack matchingStack : matchingStacks) {
            if (matchingStack == null || matchingStack.isEmpty() || containsEquivalentStack(templates, matchingStack)) {
                continue;
            }
            templates.add(matchingStack.copy());
        }

        return templates;
    }

    private static ItemStack findBestTemplateStack(Ingredient ingredient, @Nullable IClientRepo clientRepo) {
        if (clientRepo != null) {
            GridInventoryEntry bestEntry = null;
            AEItemKey bestKey = null;

            for (GridInventoryEntry entry : clientRepo.getByIngredient(ingredient)) {
                AEKey what = entry.what();
                if (!(what instanceof AEItemKey itemKey)) {
                    continue;
                }

                if (!itemKey.matches(ingredient)) {
                    continue;
                }

                if (bestEntry == null || entry.storedAmount() > bestEntry.storedAmount()
                    || entry.storedAmount() == bestEntry.storedAmount()
                    && entry.requestableAmount() > bestEntry.requestableAmount()) {
                    bestEntry = entry;
                    bestKey = itemKey;
                }
            }

            if (bestKey != null) {
                return bestKey.toStack();
            }
        }

        ItemStack[] matchingStacks = ingredient.getMatchingStacks();
        return matchingStacks.length > 0 ? matchingStacks[0].copy() : ItemStack.EMPTY;
    }

    private static boolean containsEquivalentStack(List<ItemStack> templates, ItemStack candidate) {
        for (ItemStack template : templates) {
            if (ItemStack.areItemStacksEqual(template, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<List<ItemStack>> convertSingleTemplates(NonNullList<ItemStack> templateItems) {
        List<List<ItemStack>> result = createEmptyTemplateList();
        for (int i = 0; i < templateItems.size() && i < result.size(); i++) {
            ItemStack template = templateItems.get(i);
            if (!template.isEmpty()) {
                result.get(i).add(template.copy());
            }
        }
        return result;
    }

    private static List<List<ItemStack>> createEmptyTemplateList() {
        List<List<ItemStack>> result = new ObjectArrayList<>(CRAFTING_GRID_SIZE);
        for (int i = 0; i < CRAFTING_GRID_SIZE; i++) {
            result.add(new ObjectArrayList<>());
        }
        return result;
    }
}
