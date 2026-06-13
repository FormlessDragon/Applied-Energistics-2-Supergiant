package ae2.recipes.serializers;

import ae2.recipes.handlers.CrystalFixerRecipeSerializer;

@SuppressWarnings("unused")
public final class CrystalFixerRecipeFactory extends AERecipeFactoryAdapter {
    public CrystalFixerRecipeFactory() {
        super(new CrystalFixerRecipeSerializer());
    }
}
