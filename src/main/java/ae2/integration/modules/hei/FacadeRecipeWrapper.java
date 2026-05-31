package ae2.integration.modules.hei;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.wrapper.IShapedCraftingRecipeWrapper;
import net.minecraft.item.ItemStack;

import java.util.List;

class FacadeRecipeWrapper implements IShapedCraftingRecipeWrapper {
    private final ItemStack textureItem;
    private final ItemStack cableAnchor;
    private final ItemStack facade;

    FacadeRecipeWrapper(ItemStack textureItem, ItemStack cableAnchor, ItemStack facade) {
        this.textureItem = textureItem;
        this.cableAnchor = cableAnchor;
        this.facade = facade;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<ItemStack> input = new ObjectArrayList<>(9);
        input.add(ItemStack.EMPTY);
        input.add(this.cableAnchor);
        input.add(ItemStack.EMPTY);
        input.add(this.cableAnchor);
        input.add(this.textureItem);
        input.add(this.cableAnchor);
        input.add(ItemStack.EMPTY);
        input.add(this.cableAnchor);
        input.add(ItemStack.EMPTY);
        ingredients.setInputs(VanillaTypes.ITEM, input);
        ingredients.setOutput(VanillaTypes.ITEM, this.facade);
    }
}
