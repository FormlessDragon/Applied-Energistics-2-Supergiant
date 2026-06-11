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

    private static int multiplyClamped(int value, int multiplier) {
        long result = (long) value * multiplier;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static long multiplyClamped(long value, int multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private static int clampToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int getMaxRunsForItems(Iterable<ItemStack> input, SizedIngredient ingredient) {
        long available = 0;
        for (ItemStack stack : input) {
            if (!stack.isEmpty() && ingredient.ingredient().apply(stack)) {
                available += stack.getCount();
                if (available >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE / ingredient.amount();
                }
            }
        }
        return (int) (available / ingredient.amount());
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
        this.consume(input, tank, 1);
    }

    public void consume(Iterable<ItemStack> input, GenericStackInv tank, int runs) {
        if (runs <= 0) {
            return;
        }
        List<ItemStack> live = new ObjectArrayList<>();
        for (ItemStack stack : input) {
            live.add(stack);
        }
        for (var ingredient : this.inputs) {
            consume(live, new SizedIngredient(ingredient.ingredient(), multiplyClamped(ingredient.amount(), runs)),
                true);
        }
        var fluid = this.fluid;
        var stack = tank.getStack(0);
        if (fluid != null && stack != null) {
            long amount = multiplyClamped(fluid.amount(), runs);
            long remaining = stack.amount() <= amount ? 0 : stack.amount() - amount;
            tank.setStack(0, remaining <= 0 ? null : new GenericStack(stack.what(), remaining));
        }
    }

    public int getMaxRuns(Iterable<ItemStack> input, GenericStackInv tank, int limit) {
        int runs = Math.max(0, limit);
        if (this.fluid != null) {
            GenericStack fluidStack = tank.getStack(0);
            if (fluidStack == null || !fluidStack.what().equals(this.fluid.fluid())) {
                return 0;
            }
            runs = Math.min(runs, clampToInt(fluidStack.amount() / this.fluid.amount()));
        }
        for (var ingredient : this.inputs) {
            runs = Math.min(runs, getMaxRunsForItems(input, ingredient));
        }

        for (int i = runs; i > 0; i--) {
            List<ItemStack> remaining = copyInput(input);
            boolean matches = true;
            for (var ingredient : this.inputs) {
                if (!consume(remaining, new SizedIngredient(ingredient.ingredient(), multiplyClamped(ingredient.amount(), i)),
                    false)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }
        return 0;
    }

    public interface InternalOutput {
        boolean canAccept(ItemStack stack);
    }

    public record SizedIngredient(Ingredient ingredient, int amount) {
        public SizedIngredient {
            if (amount <= 0) {
                throw new IllegalArgumentException("Crystal assembler item ingredient amount must be positive.");
            }
        }
    }

    public record SizedFluidIngredient(AEFluidKey fluid, long amount) {
        public SizedFluidIngredient {
            if (amount <= 0) {
                throw new IllegalArgumentException("Crystal assembler fluid ingredient amount must be positive.");
            }
        }

        public boolean matches(@Nullable GenericStack stack) {
            return stack != null && stack.what().equals(this.fluid) && stack.amount() >= this.amount;
        }

        public FluidStack toFluidStack() {
            if (this.amount > Integer.MAX_VALUE) {
                throw new IllegalStateException("Crystal assembler fluid ingredient amount is too large.");
            }
            return this.fluid.toStack((int) this.amount);
        }
    }
}
