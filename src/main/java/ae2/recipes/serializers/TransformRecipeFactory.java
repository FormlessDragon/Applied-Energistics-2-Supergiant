package ae2.recipes.serializers;

import ae2.recipes.transform.TransformRecipeSerializer;

public final class TransformRecipeFactory extends AERecipeFactoryAdapter {
    public TransformRecipeFactory() {
        super(new TransformRecipeSerializer());
    }
}
