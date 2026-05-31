package ae2.api.orientation;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Implements a strategy that allows blocks to be oriented using a single directional property.
 */
public class FacingStrategy implements IOrientationStrategy {
    private final PropertyDirection property;
    private final List<IProperty<?>> properties;
    private final boolean allowsPlayerRotation;

    protected FacingStrategy(PropertyDirection property) {
        this(property, true);
    }

    protected FacingStrategy(PropertyDirection property, boolean allowsPlayerRotation) {
        this.property = property;
        this.properties = Collections.singletonList(property);
        this.allowsPlayerRotation = allowsPlayerRotation;
    }

    @Override
    public EnumFacing getFacing(IBlockState state) {
        return state.getValue(property);
    }

    @Override
    public IBlockState setFacing(IBlockState state, EnumFacing facing) {
        if (!property.getAllowedValues().contains(facing)) {
            return state;
        }
        return state.withProperty(property, facing);
    }

    @Override
    public IBlockState getStateForPlacement(IBlockState state, World world, BlockPos pos, EnumFacing clickedSide,
                                            float hitX, float hitY, float hitZ, EntityLivingBase placer) {
        return setFacing(state, clickedSide);
    }

    @Override
    public boolean allowsPlayerRotation() {
        return allowsPlayerRotation;
    }

    @Override
    public Collection<IProperty<?>> getProperties() {
        return properties;
    }
}
