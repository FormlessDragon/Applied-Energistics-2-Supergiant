package ae2.helpers;

import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.storage.ISubGuiHost;
import net.minecraft.util.EnumFacing;

import java.util.EnumSet;

public interface IOutputSideConfigHost extends ISubGuiHost {
    EnumSet<EnumFacing> getOutputSides();

    void setOutputSideEnabled(EnumFacing side, boolean enabled);

    BlockOrientation getBlockOrientation();

    default void clearOutputSides() {
        for (EnumFacing side : EnumFacing.VALUES) {
            setOutputSideEnabled(side, false);
        }
    }

    EnumSet<EnumFacing> getAllowedOutputSides();

    default boolean isOutputSideAllowed(EnumFacing side) {
        return getAllowedOutputSides().contains(side);
    }

    default boolean isRelativeOutputSideAllowed(RelativeSide side) {
        return isOutputSideAllowed(getBlockOrientation().getSide(side));
    }

    default boolean isOutputSideEnabled(EnumFacing side) {
        return getOutputSides().contains(side);
    }
}
