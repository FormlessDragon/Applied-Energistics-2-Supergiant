package ae2.recipes.serializers;

import ae2.recipes.handlers.ChargerRecipeSerializer;

public final class ChargerRecipeFactory extends AERecipeFactoryAdapter {
    public ChargerRecipeFactory() {
        super(new ChargerRecipeSerializer());
    }
}
