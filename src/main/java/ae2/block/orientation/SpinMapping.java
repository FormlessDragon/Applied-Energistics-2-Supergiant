package ae2.block.orientation;

import net.minecraft.util.EnumFacing;

public final class SpinMapping {
    private static final EnumFacing[][] SPIN_DIRECTIONS = new EnumFacing[][]{
        {EnumFacing.NORTH, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.EAST},
        {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST},
        {EnumFacing.UP, EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.EAST},
        {EnumFacing.UP, EnumFacing.EAST, EnumFacing.DOWN, EnumFacing.WEST},
        {EnumFacing.UP, EnumFacing.SOUTH, EnumFacing.DOWN, EnumFacing.NORTH},
        {EnumFacing.UP, EnumFacing.NORTH, EnumFacing.DOWN, EnumFacing.SOUTH},
    };

    private SpinMapping() {
    }

    public static int getSpinFromUp(EnumFacing facing, EnumFacing up) {
        var spinDirs = SPIN_DIRECTIONS[facing.ordinal()];
        for (int i = 0; i < spinDirs.length; i++) {
            if (spinDirs[i] == up) {
                return i;
            }
        }
        return 0;
    }

    public static EnumFacing getUpFromSpin(EnumFacing facing, int spin) {
        return SPIN_DIRECTIONS[facing.ordinal()][spin];
    }
}

