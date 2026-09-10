package ae2.integration.modules.hei;

import ae2.api.storage.StorageCells;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
final class CellViewRegistryPlugin implements IRecipeRegistryPlugin {
    private final SuccessfulSnapshotCache cache = new SuccessfulSnapshotCache();

    @Override
    public <V> List<String> getRecipeCategoryUids(IFocus<V> focus) {
        if (focus.getValue() instanceof ItemStack stack && StorageCells.isCellHandled(stack)) {
            return Collections.singletonList(CellViewRecipeCategory.UID);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory,
                                                                   IFocus<V> focus) {
        if (!CellViewRecipeCategory.UID.equals(recipeCategory.getUid())
            || !(focus.getValue() instanceof ItemStack stack)
            || !StorageCells.isCellHandled(stack)) {
            return Collections.emptyList();
        }
        String languageCode = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        List<CellViewRecipe> cachedPages = this.cache.get(stack, languageCode);
        if (cachedPages != null) {
            return (List<T>) cachedPages;
        }

        List<CellViewRecipe> createdPages = CellViewRecipe.createPages(stack);
        this.cache.storeIfSuccessful(stack, languageCode, createdPages);
        return (List<T>) createdPages;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory) {
        if (CellViewRecipeCategory.UID.equals(recipeCategory.getUid())) {
            return Collections.singletonList((T) CellViewRecipe.placeholder());
        }
        return Collections.emptyList();
    }

    static final class SuccessfulSnapshotCache {
        @Nullable
        private ItemStack focus;
        @Nullable
        private String languageCode;
        private List<CellViewRecipe> pages = List.of();

        @Nullable
        List<CellViewRecipe> get(ItemStack candidate, String candidateLanguageCode) {
            return this.focus != null
                && candidateLanguageCode.equals(this.languageCode)
                && ItemStack.areItemStacksEqual(this.focus, candidate)
                    ? this.pages
                    : null;
        }

        void storeIfSuccessful(ItemStack candidate, String candidateLanguageCode,
                               List<CellViewRecipe> createdPages) {
            if (!createdPages.isEmpty()) {
                this.focus = candidate.copy();
                this.languageCode = candidateLanguageCode;
                this.pages = createdPages;
            }
        }
    }
}
