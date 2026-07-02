package ae2.recipes.serializers;

import ae2.recipes.IAERecipeFactory;
import com.google.gson.JsonObject;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

public class AERecipeFactoryAdapter implements IRecipeFactory {
    private final IAERecipeFactory delegate;

    public AERecipeFactoryAdapter(IAERecipeFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        this.delegate.register(json, context);
        return new AENonCraftingRecipe();
    }
}
