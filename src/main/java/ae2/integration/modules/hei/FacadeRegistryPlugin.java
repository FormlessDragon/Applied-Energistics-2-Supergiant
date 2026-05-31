package ae2.integration.modules.hei;

import ae2.items.parts.FacadeItem;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import net.minecraft.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
class FacadeRegistryPlugin implements IRecipeRegistryPlugin {
    private final FacadeItem itemFacade;
    private final ItemStack cableAnchor;

    FacadeRegistryPlugin(FacadeItem itemFacade, ItemStack cableAnchor) {
        this.itemFacade = itemFacade;
        this.cableAnchor = cableAnchor;
    }

    @Override
    public <V> List<String> getRecipeCategoryUids(IFocus<V> focus) {
        if (!(focus.getValue() instanceof ItemStack itemStack)) {
            return Collections.emptyList();
        }

        if (focus.getMode() == IFocus.Mode.OUTPUT && itemStack.getItem() instanceof FacadeItem) {
            return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
        }

        if (focus.getMode() == IFocus.Mode.INPUT && !this.itemFacade.createFacadeForItem(itemStack, true).isEmpty()) {
            return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
        if (!VanillaRecipeCategoryUid.CRAFTING.equals(recipeCategory.getUid())
            || !(focus.getValue() instanceof ItemStack itemStack)) {
            return Collections.emptyList();
        }

        if (focus.getMode() == IFocus.Mode.OUTPUT && itemStack.getItem() instanceof FacadeItem) {
            return Collections.singletonList((T) new FacadeRecipeWrapper(
                this.itemFacade.getTextureItem(itemStack), this.cableAnchor, itemStack));
        }

        if (focus.getMode() == IFocus.Mode.INPUT) {
            ItemStack facade = this.itemFacade.createFacadeForItem(itemStack, false);
            if (!facade.isEmpty()) {
                return Collections.singletonList((T) new FacadeRecipeWrapper(itemStack, this.cableAnchor, facade));
            }
        }

        return Collections.emptyList();
    }

    @Override
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory) {
        return Collections.emptyList();
    }
}
