package ae2.cellterminal.server;

import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Stable server-side reference to a scanned Cell Terminal subnet.
 *
 * @param stableTargetId Stable subnet target id from the latest scan.
 * @param locator        Live resolver locator for the subnet anchor.
 * @param subnetId       Stable subnet identity used by UI state and persistence.
 */
public record CellTerminalSubnetHandle(String stableTargetId, CellTerminalTargetLocator locator, String subnetId) {
    private static final String TAG_STABLE_TARGET_ID = "stableTargetId";
    private static final String TAG_SUBNET_ID = "subnetId";
    private static final String TAG_KIND = "kind";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_SIDE = "side";

    public CellTerminalSubnetHandle {
        Objects.requireNonNull(stableTargetId, "stableTargetId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(subnetId, "subnetId");
        if (stableTargetId.isEmpty()) {
            throw new IllegalArgumentException("stableTargetId must not be empty");
        }
        if (subnetId.isEmpty()) {
            throw new IllegalArgumentException("subnetId must not be empty");
        }
    }

    /**
     * Creates a stable handle from a live subnet target.
     *
     * @param target Live subnet target.
     * @return Stable subnet handle.
     */
    public static CellTerminalSubnetHandle fromTarget(CellTerminalSubnetTarget target) {
        Objects.requireNonNull(target, "target");
        return new CellTerminalSubnetHandle(target.stableTargetId(), target.locator(), target.subnetId());
    }

    /**
     * Restores a subnet handle from NBT.
     *
     * @param tag Serialized tag.
     * @return Restored handle.
     */
    public static @Nullable CellTerminalSubnetHandle fromTag(@Nullable NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        if (!tag.hasKey(TAG_STABLE_TARGET_ID) || !tag.hasKey(TAG_SUBNET_ID) || !tag.hasKey(TAG_KIND)) {
            return null;
        }
        var side = tag.hasKey(TAG_SIDE) ? EnumFacing.byIndex(tag.getInteger(TAG_SIDE)) : null;
        return new CellTerminalSubnetHandle(
            tag.getString(TAG_STABLE_TARGET_ID),
            new CellTerminalTargetLocator(
                new ResourceLocation(tag.getString(TAG_KIND)),
                tag.getInteger(TAG_DIMENSION),
                new BlockPos(tag.getInteger(TAG_X), tag.getInteger(TAG_Y), tag.getInteger(TAG_Z)),
                side),
            tag.getString(TAG_SUBNET_ID));
    }

    /**
     * Serializes this handle into NBT.
     *
     * @return Serialized tag.
     */
    public NBTTagCompound toTag() {
        var tag = new NBTTagCompound();
        tag.setString(TAG_STABLE_TARGET_ID, this.stableTargetId);
        tag.setString(TAG_SUBNET_ID, this.subnetId);
        tag.setString(TAG_KIND, this.locator.kindId().toString());
        tag.setInteger(TAG_DIMENSION, this.locator.dimensionId());
        tag.setInteger(TAG_X, this.locator.pos().getX());
        tag.setInteger(TAG_Y, this.locator.pos().getY());
        tag.setInteger(TAG_Z, this.locator.pos().getZ());
        if (this.locator.side() != null) {
            tag.setInteger(TAG_SIDE, this.locator.side().getIndex());
        }
        return tag;
    }
}
