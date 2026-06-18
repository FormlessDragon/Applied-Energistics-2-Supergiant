package ae2.recipes.serializers;

import ae2.recipes.handlers.CrystalAssemblerRecipeSerializer;

@SuppressWarnings("unused")
public final class CrystalAssemblerRecipeFactory extends AERecipeFactoryAdapter {
    public CrystalAssemblerRecipeFactory() {
        super(new CrystalAssemblerRecipeSerializer());
    }
}
