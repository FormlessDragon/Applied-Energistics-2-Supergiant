package ae2.api.orientation;

import net.minecraft.util.EnumFacing;

import java.util.Objects;

public enum RelativeSide {
    FRONT(EnumFacing.NORTH),
    BACK(EnumFacing.SOUTH),
    TOP(EnumFacing.UP),
    BOTTOM(EnumFacing.DOWN),
    LEFT(EnumFacing.WEST),
    RIGHT(EnumFacing.EAST);

    private static final RelativeSide[] BY_UNROTATED_SIDE = new RelativeSide[EnumFacing.VALUES.length];

    static {
        for (var side : values()) {
            BY_UNROTATED_SIDE[side.unrotatedSide.ordinal()] = side;
        }
    }

    private final EnumFacing unrotatedSide;

    RelativeSide(EnumFacing unrotatedSide) {
        this.unrotatedSide = unrotatedSide;
    }

    /**
     * Find the relative side on the given absolute side of a block, assuming its default orientation.
     */
    public static RelativeSide fromUnrotatedSide(EnumFacing side) {
        Objects.requireNonNull(side, "side");
        return BY_UNROTATED_SIDE[side.ordinal()];
    }

    public EnumFacing getUnrotatedSide() {
        return unrotatedSide;
    }
}
