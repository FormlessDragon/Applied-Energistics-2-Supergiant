/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package ae2.tile.misc;

import ae2.api.ids.AEItemIds;
import ae2.recipes.AERecipeTypes;
import ae2.recipes.handlers.InscriberProcessType;
import ae2.recipes.handlers.InscriberRecipe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class InscriberRecipes {
    private InscriberRecipes() {
    }

    public static List<InscriberRecipe> getRecipes() {
        return AERecipeTypes.INSCRIBER.getRecipes();
    }

    @Nullable
    public static InscriberRecipe findRecipe(ItemStack input, ItemStack plateA, ItemStack plateB, boolean supportNamePress) {
        if (supportNamePress) {
            boolean isNameA = isNamePress(plateA);
            boolean isNameB = isNamePress(plateB);

            if ((isNameA && isNameB) || (isNameA && plateB.isEmpty())) {
                return makeNamePressRecipe(input, plateA, plateB);
            } else if (plateA.isEmpty() && isNameB) {
                return makeNamePressRecipe(input, plateB, plateA);
            }
        }

        for (var recipe : getRecipes()) {
            boolean matchA = matchesOptional(recipe.getTopOptional(), plateA)
                && matchesOptional(recipe.getBottomOptional(), plateB);
            boolean matchB = matchesOptional(recipe.getTopOptional(), plateB)
                && matchesOptional(recipe.getBottomOptional(), plateA);

            if ((matchA || matchB) && recipe.getMiddleInput().apply(input)) {
                return recipe;
            }
        }

        return null;
    }

    public static boolean isValidOptionalIngredientCombination(ItemStack pressA, ItemStack pressB) {
        for (var recipe : getRecipes()) {
            if (matchesOptional(recipe.getTopOptional(), pressA) && matchesOptional(recipe.getBottomOptional(), pressB)
                || matchesOptional(recipe.getTopOptional(), pressB)
                && matchesOptional(recipe.getBottomOptional(), pressA)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidOptionalIngredient(ItemStack stack) {
        if (isNamePress(stack)) {
            return true;
        }

        for (var recipe : getRecipes()) {
            if (matchesOptional(recipe.getTopOptional(), stack) || matchesOptional(recipe.getBottomOptional(), stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNamePress(ItemStack stack) {
        ResourceLocation name = stack.isEmpty() ? null : stack.getItem().getRegistryName();
        return name != null && name.equals(AEItemIds.NAME_PRESS);
    }

    private static boolean matchesOptional(Ingredient ingredient, ItemStack stack) {
        return ingredient == Ingredient.EMPTY ? stack.isEmpty() : ingredient.apply(stack);
    }

    @Nullable
    private static InscriberRecipe makeNamePressRecipe(ItemStack input, ItemStack plateA, ItemStack plateB) {
        if (input.isEmpty()) {
            return null;
        }

        StringBuilder name = new StringBuilder();
        appendCustomName(name, plateA);
        appendCustomName(name, plateB);

        ItemStack renamedItem = input.copy();
        renamedItem.setCount(1);
        if (!name.isEmpty()) {
            renamedItem.setStackDisplayName(name.toString());
        }

        return new InscriberRecipe(
            Ingredient.fromStacks(input.copy()),
            renamedItem,
            plateA.isEmpty() ? Ingredient.EMPTY : Ingredient.fromStacks(plateA.copy()),
            plateB.isEmpty() ? Ingredient.EMPTY : Ingredient.fromStacks(plateB.copy()),
            InscriberProcessType.INSCRIBE);
    }

    private static void appendCustomName(StringBuilder name, ItemStack stack) {
        if (!stack.isEmpty() && stack.hasDisplayName()) {
            NBTTagCompound display = stack.getSubCompound("display");
            if (display == null || !display.hasKey("Name", 8)) {
                return;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(display.getString("Name"));
        }
    }
}
