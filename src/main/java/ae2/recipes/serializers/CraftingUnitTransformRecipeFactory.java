package ae2.recipes.serializers;

import ae2.recipes.game.CraftingUnitTransformRecipeSerializer;

public final class CraftingUnitTransformRecipeFactory extends AERecipeFactoryAdapter {
    public CraftingUnitTransformRecipeFactory() {
        super(new CraftingUnitTransformRecipeSerializer());
    }
}
