package ae2.recipes.handlers;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.GenericStack;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.recipes.AERecipeTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CrystalAssemblerRecipe {
    private final List<SizedIngredient> inputs;
    @Nullable
    private final SizedFluidIngredient fluid;
    private final ItemStack output;

    public CrystalAssemblerRecipe(List<SizedIngredient> inputs, @Nullable SizedFluidIngredient fluid,
                                  ItemStack output) {
        this.inputs = List.copyOf(inputs);
        this.fluid = fluid;
        this.output = output.copy();
    }

    @Nullable
    public static CrystalAssemblerRecipe findRecipe(Iterable<ItemStack> input, GenericStackInv tank,
                                                    InternalOutput output) {
        for (var recipe : AERecipeTypes.CRYSTAL_ASSEMBLER.getRecipes()) {
            if (recipe.matches(input, tank, output)) {
                return recipe;
            }
        }
        return null;
    }

    private static List<ItemStack> copyInput(Iterable<ItemStack> input) {
        List<ItemStack> copy = new ObjectArrayList<>();
        for (ItemStack stack : input) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static boolean consume(List<ItemStack> input, SizedIngredient ingredient, boolean live) {
        int remaining = ingredient.amount();
        for (ItemStack stack : input) {
            if (stack.isEmpty() || !ingredient.ingredient().apply(stack)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            if (live) {
                stack.shrink(taken);
            } else {
                stack.setCount(stack.getCount() - taken);
            }
            remaining -= taken;
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public List<SizedIngredient> getInputs() {
        return this.inputs;
    }

    @Nullable
    public SizedFluidIngredient getFluid() {
        return this.fluid;
    }

    public ItemStack getResultItem() {
        return this.output.copy();
    }

    public boolean matches(Iterable<ItemStack> input, GenericStackInv tank, InternalOutput output) {
        if (!output.canAccept(this.output)) {
            return false;
        }

        List<ItemStack> remaining = copyInput(input);
        for (var ingredient : this.inputs) {
            if (!consume(remaining, ingredient, false)) {
                return false;
            }
        }

        return this.fluid == null || this.fluid.matches(tank.getStack(0));
    }

    public void consume(Iterable<ItemStack> input, GenericStackInv tank) {
        List<ItemStack> live = new ObjectArrayList<>();
        for (ItemStack stack : input) {
            live.add(stack);
        }
        for (var ingredient : this.inputs) {
            consume(live, ingredient, true);
        }
        if (this.fluid != null && tank.getStack(0) != null) {
            var stack = tank.getStack(0);
            long remaining = stack.amount() - this.fluid.amount();
            tank.setStack(0, remaining <= 0 ? null : new GenericStack(stack.what(), remaining));
        }
    }

    public interface InternalOutput {
        boolean canAccept(ItemStack stack);
    }

    public record SizedIngredient(Ingredient ingredient, int amount) {
    }

    public record SizedFluidIngredient(AEFluidKey fluid, long amount) {
        public boolean matches(@Nullable GenericStack stack) {
            return stack != null && stack.what().equals(this.fluid) && stack.amount() >= this.amount;
        }

        public FluidStack toFluidStack() {
            return this.fluid.toStack((int) this.amount);
        }
    }
}
