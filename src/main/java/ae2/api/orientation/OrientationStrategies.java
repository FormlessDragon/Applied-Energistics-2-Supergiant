package ae2.api.orientation;

import net.minecraft.block.BlockDirectional;

/**
 * Default implementations for {@link IOrientationStrategy}.
 */
public final class OrientationStrategies {
    private static final IOrientationStrategy none = new NoOrientationStrategy();
    private static final IOrientationStrategy horizontalFacing = new HorizontalFacingStrategy();
    private static final IOrientationStrategy facing = new FacingStrategy(BlockDirectional.FACING);
    private static final IOrientationStrategy facingNoPlayerRotation = new FacingStrategy(BlockDirectional.FACING,
        false);
    private static final IOrientationStrategy full = new FacingWithSpinStrategy();

    /**
     * The blocks orientation cannot be changed.
     */
    public static IOrientationStrategy none() {
        return none;
    }

    public static IOrientationStrategy horizontalFacing() {
        return horizontalFacing;
    }

    /**
     * Block can be oriented in 6 directions, but not swivel around that axis.
     */
    public static IOrientationStrategy facing() {
        return facing;
    }

    /**
     * Block can be oriented in 6 directions, but not swivel around that axis.
     */
    public static IOrientationStrategy facingNoPlayerRotation() {
        return facingNoPlayerRotation;
    }

    /**
     * Block can be oriented in 6 directions and then can also be swiveled around that axis in 90° increments.
     */
    public static IOrientationStrategy full() {
        return full;
    }
}
