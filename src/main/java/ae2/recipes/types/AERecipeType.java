package ae2.recipes.types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class AERecipeType<T> {
    private final ResourceLocation id;
    private final List<T> recipes = new ObjectArrayList<>();
    private final List<T> recipesView = Collections.unmodifiableList(this.recipes);

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

    public boolean remove(T recipe) {
        return this.recipes.remove(recipe);
    }

    public int removeIf(Predicate<T> predicate) {
        int removed = 0;
        for (var iterator = this.recipes.iterator(); iterator.hasNext(); ) {
            if (predicate.test(iterator.next())) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public List<T> getRecipes() {
        return this.recipesView;
    }
}
