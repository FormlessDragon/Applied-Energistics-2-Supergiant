package ae2.recipes.serializers;

import ae2.recipes.entropy.EntropyRecipeSerializer;

public final class EntropyRecipeFactory extends AERecipeFactoryAdapter {
    public EntropyRecipeFactory() {
        super(new EntropyRecipeSerializer());
    }
}
