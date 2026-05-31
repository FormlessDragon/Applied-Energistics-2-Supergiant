package ae2.integration.modules.hei;

import ae2.core.localization.HeiText;
import ae2.items.tools.powered.EntropyManipulatorItem;
import ae2.recipes.entropy.EntropyRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
class EntropyRecipeWrapper implements IRecipeWrapper {
    private final EntropyRecipe recipe;
    @Nullable
    private final ItemStack inputItem;
    @Nullable
    private final FluidStack inputFluid;
    private final List<ItemStack> outputItems;
    @Nullable
    private final FluidStack outputFluid;
    private final boolean outputConsumed;
    private final boolean inputFlowing;
    private final boolean outputFlowing;

    EntropyRecipeWrapper(EntropyRecipe recipe) {
        this.recipe = recipe;

        var inputBlock = recipe.input().block().map(EntropyRecipe.BlockInput::block).orElse(null);
        var directInputFluid = recipe.input().fluid().map(EntropyRecipe.FluidInput::fluid).orElse(null);
        this.inputFluid = directInputFluid != null ? new FluidStack(directInputFluid, Fluid.BUCKET_VOLUME)
            : getFluidStack(inputBlock);
        this.inputItem = this.inputFluid == null ? createBlockStack(inputBlock, 0) : null;
        this.inputFlowing = isFlowingBlock(inputBlock);

        IBlockState inputState = inputBlock != null ? inputBlock.getDefaultState() : null;
        IBlockState outputBlockState = recipe.getOutputBlockState(inputState);
        FluidStack outputFluidStack = recipe.getOutputFluidStack(this.inputFluid);
        var outputBlock = outputBlockState != null ? outputBlockState.getBlock() : null;
        int outputBlockMeta = outputBlockState != null ? outputBlock.getMetaFromState(outputBlockState) : -1;

        this.outputConsumed = outputBlock == Blocks.AIR && outputFluidStack == null;
        this.outputFluid = this.outputConsumed && this.inputFluid != null ? this.inputFluid.copy() : outputFluidStack;
        this.outputFlowing = this.outputFluid != null
            && (this.outputFluid.getFluid() == FluidRegistry.WATER || this.outputFluid.getFluid() == FluidRegistry.LAVA)
            && isFlowingBlock(outputBlock);

        it.unimi.dsi.fastutil.objects.ObjectList<ItemStack> outputs = new ObjectArrayList<>();
        if (this.outputConsumed) {
            if (this.inputItem != null && !this.inputItem.isEmpty()) {
                outputs.add(this.inputItem.copy());
            }
        } else if (this.outputFluid == null) {
            ItemStack outputBlockStack = createBlockStack(outputBlock, outputBlockMeta);
            if (!outputBlockStack.isEmpty()) {
                outputs.add(outputBlockStack);
            }
        }
        for (var drop : recipe.getDrops()) {
            outputs.add(drop.copy());
        }
        this.outputItems = ObjectLists.unmodifiable(outputs);
    }

    private static ItemStack createBlockStack(@Nullable net.minecraft.block.Block block, int metadata) {
        if (block == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = metadata >= 0 ? new ItemStack(block, 1, metadata) : new ItemStack(block);
        return stack.getItem() == Items.AIR ? ItemStack.EMPTY : stack;
    }

    @Nullable
    private static FluidStack getFluidStack(@Nullable net.minecraft.block.Block block) {
        if (block == null) {
            return null;
        }

        Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
        return fluid == null ? null : new FluidStack(fluid, Fluid.BUCKET_VOLUME);
    }

    private static boolean isFlowingBlock(@Nullable net.minecraft.block.Block block) {
        return block == Blocks.FLOWING_WATER || block == Blocks.FLOWING_LAVA;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        if (this.inputItem != null && !this.inputItem.isEmpty()) {
            ingredients.setInput(VanillaTypes.ITEM, this.inputItem);
        }
        if (this.inputFluid != null) {
            ingredients.setInput(VanillaTypes.FLUID, this.inputFluid);
        }
        if (!this.outputItems.isEmpty()) {
            ingredients.setOutputs(VanillaTypes.ITEM, this.outputItems);
        }
        if (this.outputFluid != null) {
            ingredients.setOutput(VanillaTypes.FLUID, this.outputFluid);
        }
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        String modeText = switch (this.recipe.mode()) {
            case HEAT -> HeiText.EntropyManipulatorHeat.getLocal(EntropyManipulatorItem.ENERGY_PER_USE);
            case COOL -> HeiText.EntropyManipulatorCool.getLocal(EntropyManipulatorItem.ENERGY_PER_USE);
        };
        String interactionText = switch (this.recipe.mode()) {
            case HEAT -> HeiText.RightClick.getLocal();
            case COOL -> HeiText.ShiftRightClick.getLocal();
        };

        int modeWidth = minecraft.fontRenderer.getStringWidth(modeText);
        minecraft.fontRenderer.drawString(modeText, (recipeWidth - modeWidth) / 2, 4, 0x7E7E7E);

        int actionWidth = minecraft.fontRenderer.getStringWidth(interactionText);
        minecraft.fontRenderer.drawString(interactionText, (recipeWidth - actionWidth) / 2, 44, 0x7E7E7E);
    }

    boolean hasInputItem() {
        return this.inputItem != null && !this.inputItem.isEmpty();
    }

    ItemStack getInputItem() {
        return this.inputItem == null ? ItemStack.EMPTY : this.inputItem;
    }

    boolean hasInputFluid() {
        return this.inputFluid != null;
    }

    @Nullable
    FluidStack getInputFluid() {
        return this.inputFluid;
    }

    List<ItemStack> getOutputItems() {
        return this.outputItems;
    }

    boolean hasOutputFluid() {
        return this.outputFluid != null;
    }

    @Nullable
    FluidStack getOutputFluid() {
        return this.outputFluid;
    }

    boolean isOutputConsumed() {
        return this.outputConsumed;
    }

    boolean isInputFlowing() {
        return this.inputFlowing;
    }

    boolean isOutputFlowing() {
        return this.outputFlowing;
    }
}
