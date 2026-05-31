package ae2.recipes.serializers;

import ae2.recipes.game.StorageCellDisassemblyRecipeSerializer;

public final class StorageCellDisassemblyRecipeFactory extends AERecipeFactoryAdapter {
    public StorageCellDisassemblyRecipeFactory() {
        super(new StorageCellDisassemblyRecipeSerializer());
    }
}
