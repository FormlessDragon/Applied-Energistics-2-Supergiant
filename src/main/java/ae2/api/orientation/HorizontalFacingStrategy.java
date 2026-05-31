package ae2.api.orientation;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Implements a strategy that allows blocks to be oriented using a single directional property. It doesn't allow up and
 * down, and uses the player facing instead in those cases.
 */
public class HorizontalFacingStrategy extends FacingStrategy {
    protected HorizontalFacingStrategy() {
        super(BlockHorizontal.FACING);
    }

    @Override
    public IBlockState getStateForPlacement(IBlockState state, World world, BlockPos pos, EnumFacing clickedSide,
                                            float hitX, float hitY, float hitZ, EntityLivingBase placer) {
        if (placer == null) {
            return setFacing(state, EnumFacing.NORTH);
        }

        return setFacing(state, placer.getHorizontalFacing().getOpposite());
    }
}
