package ae2.api.orientation;

import ae2.block.orientation.SpinMapping;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Specifies how a block determines its orientation and stores it in the blockstate. For use with
 * {@link IOrientableBlock}.
 */
public interface IOrientationStrategy {
    // This is the clockwise rotation around the facing, starting at:
    // UP for horizontal axes
    // NORTH for vertical axes
    PropertyInteger SPIN = PropertyInteger.create("spin", 0, 3);

    static IOrientationStrategy get(IBlockState state) {
        if (state.getBlock() instanceof IOrientableBlock orientableBlock) {
            return orientableBlock.getOrientationStrategy();
        }
        return OrientationStrategies.none();
    }

    private static <T extends Comparable<T>> Stream<IBlockState> enumerateValues(Stream<IBlockState> stream,
                                                                                 IProperty<T> property) {
        return stream.flatMap(
            baseState -> property.getAllowedValues().stream().map(value -> baseState.withProperty(property, value)));
    }

    default EnumFacing getFacing(IBlockState state) {
        return EnumFacing.NORTH;
    }

    default int getSpin(IBlockState state) {
        return 0;
    }

    default IBlockState setFacing(IBlockState state, EnumFacing facing) {
        return state;
    }

    default IBlockState setSpin(IBlockState state, int spin) {
        return state;
    }

    default IBlockState setUp(IBlockState state, EnumFacing up) {
        var facing = getFacing(state);
        var spin = SpinMapping.getSpinFromUp(facing, up);
        return setSpin(state, spin);
    }

    default IBlockState setOrientation(IBlockState state, EnumFacing facing, int spin) {
        return setSpin(setFacing(state, facing), spin);
    }

    default IBlockState setOrientation(IBlockState state, EnumFacing facing, EnumFacing up) {
        return setUp(setFacing(state, facing), up);
    }

    default EnumFacing getSide(IBlockState state, RelativeSide side) {
        return BlockOrientation.get(this, state).rotate(side.getUnrotatedSide());
    }

    default IBlockState getStateForPlacement(IBlockState state, World world, BlockPos pos, EnumFacing clickedSide,
                                             float hitX, float hitY, float hitZ, EntityLivingBase placer) {
        return state;
    }

    default Stream<IBlockState> getAllStates(IBlockState baseState) {
        var result = Stream.of(baseState);
        for (var property : getProperties()) {
            result = enumerateValues(result, property);
        }
        return result;
    }

    /**
     * Indicates that this orientation can be changed by the player (i.e. by wrench).
     */
    default boolean allowsPlayerRotation() {
        return true;
    }

    /**
     * @return The block state properties used for storing orientation by this strategy.
     */
    Collection<IProperty<?>> getProperties();
}
