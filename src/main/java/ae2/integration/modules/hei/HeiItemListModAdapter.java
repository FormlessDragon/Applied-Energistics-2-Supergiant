package ae2.integration.modules.hei;

import ae2.integration.abstraction.ItemListModAdapter;
import com.google.common.base.Strings;
import mezz.jei.api.IIngredientFilter;
import mezz.jei.api.IIngredientListOverlay;

import java.util.Objects;

class HeiItemListModAdapter implements ItemListModAdapter {

    private final IIngredientFilter ingredientFilter;
    private final IIngredientListOverlay ingredientListOverlay;

    HeiItemListModAdapter(IIngredientFilter ingredientFilter, IIngredientListOverlay ingredientListOverlay) {
        this.ingredientFilter = Objects.requireNonNull(ingredientFilter, "HEI ingredient filter was null");
        this.ingredientListOverlay = Objects.requireNonNull(ingredientListOverlay, "HEI ingredient list overlay was null");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getShortName() {
        return "HEI";
    }

    @Override
    public String getSearchText() {
        return Strings.nullToEmpty(this.ingredientFilter.getFilterText());
    }

    @Override
    public void setSearchText(String text) {
        this.ingredientFilter.setFilterText(text);
    }

    @Override
    public boolean hasSearchFocus() {
        return this.ingredientListOverlay.hasKeyboardFocus();
    }
}
