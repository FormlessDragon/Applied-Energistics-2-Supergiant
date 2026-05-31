package ae2.recipes.mattercannon;

import ae2.recipes.AERecipeTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

public record MatterCannonAmmo(Ingredient ammo, float weight) {
    public MatterCannonAmmo {
        if (weight < 0) {
            throw new IllegalArgumentException("Weight must be non-negative");
        }
    }

    public static @Nullable MatterCannonAmmo findAmmo(ItemStack stack) {
        for (var ammo : AERecipeTypes.MATTER_CANNON_AMMO.getRecipes()) {
            if (ammo.matches(stack)) {
                return ammo;
            }
        }
        return null;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && this.ammo.apply(stack);
    }
}
