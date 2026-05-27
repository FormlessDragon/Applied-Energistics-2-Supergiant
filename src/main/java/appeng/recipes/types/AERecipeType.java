package appeng.recipes.types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

public final class AERecipeType<T> {
    private final ResourceLocation id;
    private final List<T> recipes = new ObjectArrayList<>();

    public AERecipeType(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public void clear() {
        this.recipes.clear();
    }

    public void register(T recipe) {
        this.recipes.add(recipe);
    }

    public List<T> getRecipes() {
        return Collections.unmodifiableList(this.recipes);
    }
}
