package ae2.integration.modules.hei;

import ae2.api.integrations.hei.IngredientConverter;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.GenericStack;
import com.google.common.primitives.Ints;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class FluidIngredientConverter implements IngredientConverter<FluidStack> {
    @Override
    public IIngredientType<FluidStack> getIngredientType() {
        return VanillaTypes.FLUID;
    }

    @Nullable
    @Override
    public FluidStack getIngredientFromStack(GenericStack stack) {
        if (stack == null) {
            return null;
        }

        if (stack.what() instanceof AEFluidKey fluidKey) {
            return fluidKey.toStack(Math.max(1, Ints.saturatedCast(stack.amount())));
        }
        return null;
    }

    @Nullable
    @Override
    public GenericStack getStackFromIngredient(FluidStack ingredient) {
        if (ingredient == null) {
            return null;
        }

        return GenericStack.fromFluidStack(ingredient);
    }
}
