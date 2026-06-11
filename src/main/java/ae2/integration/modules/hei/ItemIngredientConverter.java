package ae2.integration.modules.hei;

import ae2.api.integrations.hei.IngredientConverter;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import com.google.common.primitives.Ints;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ItemIngredientConverter implements IngredientConverter<ItemStack> {
    @Override
    public IIngredientType<ItemStack> getIngredientType() {
        return VanillaTypes.ITEM;
    }

    @Nullable
    @Override
    public ItemStack getIngredientFromStack(GenericStack stack) {
        if (stack == null) {
            return null;
        }

        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack(Math.max(1, Ints.saturatedCast(stack.amount())));
        }
        return null;
    }

    @Nullable
    @Override
    public GenericStack getStackFromIngredient(ItemStack ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }

        return GenericStack.fromItemStack(ingredient);
    }
}
