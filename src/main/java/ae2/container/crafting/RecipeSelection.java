package ae2.container.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecipeSelection {
    private RecipeSelection() {
    }

    public static List<Candidate> findCandidates(InventoryCrafting input, World world) {
        List<Candidate> candidates = new ArrayList<>();
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ResourceLocation id = recipe.getRegistryName();
            if (id != null && recipe.matches(input, world)) {
                candidates.add(new Candidate(id, recipe, recipe.getCraftingResult(input).copy()));
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    public static Selection findFirstCandidateAndConflict(InventoryCrafting input, World world,
                                                          @Nullable ResourceLocation preferredId) {
        Candidate selected = preferredId == null ? null : findCandidateById(input, world, preferredId);
        int matches = 0;
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            ResourceLocation id = recipe.getRegistryName();
            if (id == null || !recipe.matches(input, world)) {
                continue;
            }

            matches++;
            if (selected == null) {
                selected = candidate(id, recipe, input);
            }
            if (matches > 1) {
                break;
            }
        }
        return new Selection(selected, matches > 1);
    }

    @Nullable
    public static Candidate findCandidateById(InventoryCrafting input, World world, ResourceLocation recipeId) {
        IRecipe recipe = CraftingManager.REGISTRY.getObject(recipeId);
        if (recipe == null || !recipeId.equals(recipe.getRegistryName()) || !recipe.matches(input, world)) {
            return null;
        }

        Candidate candidate = candidate(recipeId, recipe, input);
        return candidate.output().isEmpty() ? null : candidate;
    }

    private static Candidate candidate(ResourceLocation id, IRecipe recipe, InventoryCrafting input) {
        return new Candidate(id, recipe, recipe.getCraftingResult(input).copy());
    }

    @Nullable
    public static Candidate select(List<Candidate> candidates, @Nullable ResourceLocation preferredId) {
        if (preferredId != null) {
            for (Candidate candidate : candidates) {
                if (candidate.id().equals(preferredId)) {
                    return candidate;
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    public record Candidate(ResourceLocation id, IRecipe recipe, ItemStack output) {
    }

    public record Selection(@Nullable Candidate candidate, boolean conflict) {
    }
}
