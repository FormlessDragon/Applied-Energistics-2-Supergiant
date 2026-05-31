package ae2.api.orientation;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.List;

/**
 * Extends {@link FacingStrategy} to also allow the block to be rotated around its facing axis.
 */
public class FacingWithSpinStrategy implements IOrientationStrategy {

    private final List<IProperty<?>> properties;

    protected FacingWithSpinStrategy() {
        this.properties = List.of(
            BlockDirectional.FACING,
            SPIN);
    }

    @Override
    public EnumFacing getFacing(IBlockState state) {
        return state.getValue(BlockDirectional.FACING);
    }

    @Override
    public int getSpin(IBlockState state) {
        return state.getValue(SPIN);
    }

    @Override
    public IBlockState setFacing(IBlockState state, EnumFacing facing) {
        return state.withProperty(BlockDirectional.FACING, facing);
    }

    @Override
    public IBlockState setSpin(IBlockState state, int spin) {
        return state.withProperty(SPIN, spin);
    }

    @Override
    public IBlockState getStateForPlacement(IBlockState state, World world, BlockPos pos, EnumFacing clickedSide,
                                            float hitX, float hitY, float hitZ, EntityLivingBase placer) {
        var up = EnumFacing.UP;
        var forward = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        if (placer != null) {
            if (placer.rotationPitch > 65) {
                up = forward.getOpposite();
                forward = EnumFacing.UP;
            } else if (placer.rotationPitch < -65) {
                up = forward.getOpposite();
                forward = EnumFacing.DOWN;
            }
        }

        return setOrientation(state, forward, up);
    }

    @Override
    public Collection<IProperty<?>> getProperties() {
        return properties;
    }
}
