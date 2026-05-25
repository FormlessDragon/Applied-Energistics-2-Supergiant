package appeng.util;

import appeng.api.stacks.GenericStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import org.jetbrains.annotations.Nullable;

/**
 * Allows generalized extraction from item-based containers such as buckets or tanks.
 */
public final class GenericContainerHelper {
    private GenericContainerHelper() {
    }

    @Nullable
    public static GenericStack getContainedFluidStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        final FluidStack content;

        if (stack.getItem() instanceof ItemBlock b && b.getBlock() instanceof IFluidBlock f) {
            content = new FluidStack(f.getFluid(), 1000);
        } else {
            content = FluidUtil.getFluidContained(stack);
        }

        return content != null ? GenericStack.fromFluidStack(content) : null;
    }
}
