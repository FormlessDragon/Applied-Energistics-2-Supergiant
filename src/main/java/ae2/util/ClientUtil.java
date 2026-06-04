package ae2.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ClientUtil {
    private ClientUtil() {
    }

    public static Vec3d getLawVec(Vec3d a, Vec3d b) {
        Vec3d normal = a.subtract(b);
        if (isZero(normal)) {
            return Vec3d.ZERO;
        }
        normal = normal.normalize();
        return new Vec3d(normal.y - normal.z, normal.z - normal.x, normal.x - normal.y).normalize();
    }

    public static Vec3d getLawVec2(Vec3d a, Vec3d b) {
        Vec3d normal = a.subtract(b);
        if (isZero(normal)) {
            return Vec3d.ZERO;
        }
        normal = normal.normalize();
        double x = normal.x;
        double y = normal.y;
        double z = normal.z;
        return new Vec3d(
            z * z - x * z + y * y - x * y,
            z * z - y * z - x * y + x * x,
            y * y + x * x - y * z - x * z
        ).normalize();
    }

    public static Vec3d getCenter(BlockPos a, BlockPos b) {
        return getCenter(a).add(getCenter(b)).scale(0.5D);
    }

    public static Vec3d getCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static boolean isZero(Vec3d vec) {
        return vec.x == 0.0D && vec.y == 0.0D && vec.z == 0.0D;
    }
}
