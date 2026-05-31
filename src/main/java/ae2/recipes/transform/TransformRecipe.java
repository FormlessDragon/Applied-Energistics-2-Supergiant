package ae2.recipes.transform;

import ae2.core.definitions.AEItems;
import ae2.tile.qnb.TileQuantumBridge;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import java.util.List;

public final class TransformRecipe {
    private final List<Ingredient> ingredients;
    private final ItemStack output;
    private final TransformCircumstance circumstance;

    public TransformRecipe(List<Ingredient> ingredients, ItemStack output, TransformCircumstance circumstance) {
        this.ingredients = new ObjectArrayList<>(ingredients);
        this.output = output.copy();
        this.circumstance = circumstance;
    }

    public List<Ingredient> getIngredients() {
        return this.ingredients;
    }

    public ItemStack getResultItem() {
        ItemStack result = this.output.copy();
        if (AEItems.QUANTUM_ENTANGLED_SINGULARITY.is(result)) {
            TileQuantumBridge.assignFrequency(result);
        }
        return result;
    }

    public TransformCircumstance getCircumstance() {
        return this.circumstance;
    }

    public boolean matches(List<ItemStack> inputs) {
        if (inputs.size() < this.ingredients.size()) {
            return false;
        }

        boolean[] used = new boolean[inputs.size()];
        for (var ingredient : this.ingredients) {
            boolean matched = false;
            for (int i = 0; i < inputs.size(); i++) {
                if (!used[i] && ingredient.apply(inputs.get(i))) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }

        return true;
    }
}

