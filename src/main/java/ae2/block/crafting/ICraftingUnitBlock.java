package ae2.block.crafting;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Block-side contract for crafting unit family membership and definition lookup.
 */
public interface ICraftingUnitBlock {
    ICraftingUnitDefinition getCraftingUnitDefinition(IBlockState state, IBlockAccess world, BlockPos pos);

    boolean isCompatibleCraftingUnit(IBlockState selfState, IBlockAccess world, BlockPos selfPos,
                                     IBlockState otherState, BlockPos otherPos);
}
