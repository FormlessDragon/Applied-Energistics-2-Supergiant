package ae2.integration.modules.crafttweaker;

import ae2.recipes.types.AERecipeType;
import crafttweaker.IAction;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;

import java.util.function.Predicate;

final class AE2CraftTweakerActions {
    private AE2CraftTweakerActions() {
    }

    static <T> IAction addAERecipe(AERecipeType<T> type, T recipe, String description) {
        return new IAction() {
            @Override
            public void apply() {
                type.register(recipe);
            }

            @Override
            public String describe() {
                return description;
            }
        };
    }

    static <T> IAction removeAERecipes(AERecipeType<T> type, Predicate<T> predicate, String description) {
        return new IAction() {
            private int removed;

            @Override
            public void apply() {
                this.removed = type.removeIf(predicate);
            }

            @Override
            public String describe() {
                return description + " (" + this.removed + " removed)";
            }
        };
    }

    static IAction registerForgeRecipe(IRecipe recipe, ResourceLocation id, String description) {
        return new IAction() {
            @Override
            public void apply() {
                recipe.setRegistryName(id);
                ForgeRegistries.RECIPES.register(recipe);
            }

            @Override
            public String describe() {
                return description + " as " + id;
            }
        };
    }

    static IAction removeForgeRecipe(ResourceLocation id, String description) {
        return new IAction() {
            private boolean removed;

            @Override
            public void apply() {
                this.removed = RegistryManager.ACTIVE.getRegistry(GameData.RECIPES).remove(id) != null;
            }

            @Override
            public String describe() {
                return description + " " + id + " (" + (this.removed ? "removed" : "missing") + ")";
            }
        };
    }
}
