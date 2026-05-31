package ae2.recipes.handlers;

import ae2.api.stacks.AEFluidKey;
import ae2.recipes.AERecipeTypes;
import ae2.recipes.IAERecipeFactory;
import ae2.recipes.serializers.JsonRecipeUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CrystalAssemblerRecipeSerializer implements IAERecipeFactory {
    private static List<CrystalAssemblerRecipe.SizedIngredient> readItemInputs(JsonObject json, JsonContext ctx) {
        JsonArray array = JsonUtils.getJsonArray(json, "input_items");
        List<CrystalAssemblerRecipe.SizedIngredient> result = new ObjectArrayList<>(array.size());
        for (var element : array) {
            JsonObject input = element.getAsJsonObject();
            Ingredient ingredient = JsonRecipeUtils.readIngredient(input, "ingredient", ctx);
            int amount = JsonUtils.getInt(input, "amount", 1);
            result.add(new CrystalAssemblerRecipe.SizedIngredient(ingredient, amount));
        }
        return result;
    }

    @Nullable
    private static CrystalAssemblerRecipe.SizedFluidIngredient readFluidInput(JsonObject json, JsonContext ctx) {
        if (!json.has("input_fluid")) {
            return null;
        }

        JsonObject input = JsonUtils.getJsonObject(json, "input_fluid");
        JsonObject fluidObject = input.has("ingredient") ? JsonUtils.getJsonObject(input, "ingredient") : input;
        int amount = JsonUtils.getInt(input, "amount", JsonUtils.getInt(fluidObject, "amount", 1000));
        if (fluidObject.has("fluid")) {
            Fluid fluid = FluidRegistry.getFluid(JsonUtils.getString(fluidObject, "fluid"));
            if (fluid == null) {
                throw new JsonSyntaxException("Unknown fluid: " + JsonUtils.getString(fluidObject, "fluid"));
            }
            AEFluidKey key = AEFluidKey.of(new FluidStack(fluid, amount));
            if (key == null) {
                throw new JsonSyntaxException("input_fluid could not be converted to AE fluid key");
            }
            return new CrystalAssemblerRecipe.SizedFluidIngredient(key, amount);
        }
        JsonObject normalized = fluidObject.deepCopy();
        normalized.addProperty("amount", amount);
        FluidStack stack = FluidUtil.getFluidContained(JsonRecipeUtils.readItemStack(normalized, ctx));
        if (stack == null) {
            throw new JsonSyntaxException("input_fluid must resolve to a fluid container item in 1.12.2");
        }
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            throw new JsonSyntaxException("input_fluid could not be converted to AE fluid key");
        }
        return new CrystalAssemblerRecipe.SizedFluidIngredient(key, amount);
    }

    @Override
    public void register(JsonObject json, JsonContext ctx) {
        List<CrystalAssemblerRecipe.SizedIngredient> inputs = readItemInputs(json, ctx);
        CrystalAssemblerRecipe.SizedFluidIngredient fluid = readFluidInput(json, ctx);
        AERecipeTypes.CRYSTAL_ASSEMBLER.register(new CrystalAssemblerRecipe(
            inputs,
            fluid,
            JsonRecipeUtils.readItemStack(json, "output", ctx)));
    }
}
