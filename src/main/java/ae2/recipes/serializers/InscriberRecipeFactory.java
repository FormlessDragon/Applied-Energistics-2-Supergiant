package ae2.recipes.serializers;

import ae2.recipes.handlers.InscriberRecipeSerializer;

public final class InscriberRecipeFactory extends AERecipeFactoryAdapter {
    public InscriberRecipeFactory() {
        super(new InscriberRecipeSerializer());
    }
}
