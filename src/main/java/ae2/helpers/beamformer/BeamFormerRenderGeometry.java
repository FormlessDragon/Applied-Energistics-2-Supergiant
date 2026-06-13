package ae2.helpers.beamformer;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class BeamFormerRenderGeometry {

    private static final double MIN_VISIBLE_LENGTH = 0.0001;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0 * 96.0;

    private BeamFormerRenderGeometry() {
    }

    public static Vec3d computeBeamOriginOffset(BeamFormerEndpoint endpoint) {
        return endpoint.getBeamOriginOffset();
    }

    public static Vec3d computeBeamStart(BeamFormerEndpoint endpoint) {
        return new Vec3d(computeBeamStartX(endpoint), computeBeamStartY(endpoint), computeBeamStartZ(endpoint));
    }

    public static double computeBeamStartX(BeamFormerEndpoint endpoint) {
        BlockPos pos = endpoint.getBeamPos();
        return pos.getX() + 0.5D + endpoint.getBeamOriginOffsetX();
    }

    public static double computeBeamStartY(BeamFormerEndpoint endpoint) {
        BlockPos pos = endpoint.getBeamPos();
        return pos.getY() + 0.5D + endpoint.getBeamOriginOffsetY();
    }

    public static double computeBeamStartZ(BeamFormerEndpoint endpoint) {
        BlockPos pos = endpoint.getBeamPos();
        return pos.getZ() + 0.5D + endpoint.getBeamOriginOffsetZ();
    }

    public static double computeBeamLength(BeamFormerEndpoint source, BeamFormerEndpoint target) {
        double deltaX = computeBeamStartX(source) - computeBeamStartX(target);
        double deltaY = computeBeamStartY(source) - computeBeamStartY(target);
        double deltaZ = computeBeamStartZ(source) - computeBeamStartZ(target);
        return clampBeamLength(Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
    }

    public static boolean isNearEnoughToRender(BeamFormerEndpoint endpoint, double cameraX, double cameraY,
                                               double cameraZ) {
        BlockPos pos = endpoint.getBeamPos();
        double startX = pos.getX() + 0.5D + endpoint.getBeamOriginOffsetX();
        double startY = pos.getY() + 0.5D + endpoint.getBeamOriginOffsetY();
        double startZ = pos.getZ() + 0.5D + endpoint.getBeamOriginOffsetZ();
        EnumFacing direction = endpoint.getBeamDirection();
        double length = endpoint.getBeamLength();
        double endX = startX + direction.getXOffset() * length;
        double endY = startY + direction.getYOffset() * length;
        double endZ = startZ + direction.getZOffset() * length;
        return distanceToSegmentSquared(cameraX, cameraY, cameraZ, startX, startY, startZ, endX, endY, endZ)
            <= MAX_RENDER_DISTANCE_SQUARED;
    }

    public static AxisAlignedBB computeRenderBoundingBox(BeamFormerEndpoint endpoint) {
        BlockPos pos = endpoint.getBeamPos();
        EnumFacing direction = endpoint.getBeamDirection();
        int length = Math.min(BeamFormerEndpoint.MAX_BEAM_LENGTH,
            (int) Math.ceil(shouldRender(endpoint) ? endpoint.getBeamLength() : 1.0D));
        BlockPos end = pos.offset(direction, length);
        return new AxisAlignedBB(
            Math.min(pos.getX(), end.getX()),
            Math.min(pos.getY(), end.getY()),
            Math.min(pos.getZ(), end.getZ()),
            Math.max(pos.getX(), end.getX()) + 1,
            Math.max(pos.getY(), end.getY()) + 1,
            Math.max(pos.getZ(), end.getZ()) + 1).grow(1.0D);
    }

    public static boolean shouldRender(BeamFormerEndpoint endpoint) {
        return shouldRender(endpoint.isBeamLinked(), endpoint.isBeamVisible(), endpoint.getBeamLength());
    }

    public static boolean shouldRender(boolean linked, boolean visible, double length) {
        return linked && visible && length > MIN_VISIBLE_LENGTH;
    }

    public static double clampBeamLength(double length) {
        return length > MIN_VISIBLE_LENGTH ? length : 0.0;
    }

    private static double distanceToSegmentSquared(double pointX, double pointY, double pointZ, double startX,
                                                   double startY, double startZ, double endX, double endY,
                                                   double endZ) {
        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double segmentZ = endZ - startZ;
        double lengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        if (lengthSquared <= MIN_VISIBLE_LENGTH) {
            double deltaX = pointX - startX;
            double deltaY = pointY - startY;
            double deltaZ = pointZ - startZ;
            return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        }
        double fromStartX = pointX - startX;
        double fromStartY = pointY - startY;
        double fromStartZ = pointZ - startZ;
        double t = (fromStartX * segmentX + fromStartY * segmentY + fromStartZ * segmentZ) / lengthSquared;
        t = Math.clamp(t, 0.0D, 1.0D);
        double closestX = startX + segmentX * t;
        double closestY = startY + segmentY * t;
        double closestZ = startZ + segmentZ * t;
        double deltaX = pointX - closestX;
        double deltaY = pointY - closestY;
        double deltaZ = pointZ - closestZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }
}
