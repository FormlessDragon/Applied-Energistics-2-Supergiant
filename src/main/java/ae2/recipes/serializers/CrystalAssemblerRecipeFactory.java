package ae2.recipes.serializers;

import ae2.recipes.handlers.CrystalAssemblerRecipeSerializer;

public final class CrystalAssemblerRecipeFactory extends AERecipeFactoryAdapter {
    public CrystalAssemblerRecipeFactory() {
        super(new CrystalAssemblerRecipeSerializer());
    }
}
