package ae2.client.render;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumFacing;

public final class GlowRenderer {
    private static final int CYLINDER_SEGMENTS = 16;
    private static final double[] CIRCLE_COS = new double[CYLINDER_SEGMENTS + 1];
    private static final double[] CIRCLE_SIN = new double[CYLINDER_SEGMENTS + 1];

    static {
        fillCircleTable(CIRCLE_COS, CIRCLE_SIN, CYLINDER_SEGMENTS);
    }

    private GlowRenderer() {
    }

    private static void fillCircleTable(double[] cosTable, double[] sinTable, int segments) {
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            cosTable[i] = Math.cos(angle);
            sinTable[i] = Math.sin(angle);
        }
    }

    public static void addCylinder(BufferBuilder buffer, double startX, double startY, double startZ, double endX,
                                   double endY, double endZ, EnumFacing direction, double radius, GlowColor color,
                                   float alpha) {
        double firstX = direction.getAxis() == EnumFacing.Axis.X ? 0.0D : radius;
        double firstY = direction.getAxis() == EnumFacing.Axis.X ? radius : 0.0D;
        double firstZ = 0.0D;
        double secondX = 0.0D;
        double secondY = direction.getAxis() == EnumFacing.Axis.Z ? radius : 0.0D;
        double secondZ = direction.getAxis() == EnumFacing.Axis.Z ? 0.0D : radius;

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double radialAX = firstX * CIRCLE_COS[i] + secondX * CIRCLE_SIN[i];
            double radialAY = firstY * CIRCLE_COS[i] + secondY * CIRCLE_SIN[i];
            double radialAZ = firstZ * CIRCLE_COS[i] + secondZ * CIRCLE_SIN[i];
            double radialBX = firstX * CIRCLE_COS[i + 1] + secondX * CIRCLE_SIN[i + 1];
            double radialBY = firstY * CIRCLE_COS[i + 1] + secondY * CIRCLE_SIN[i + 1];
            double radialBZ = firstZ * CIRCLE_COS[i + 1] + secondZ * CIRCLE_SIN[i + 1];
            addQuad(buffer,
                startX + radialAX, startY + radialAY, startZ + radialAZ,
                endX + radialAX, endY + radialAY, endZ + radialAZ,
                endX + radialBX, endY + radialBY, endZ + radialBZ,
                startX + radialBX, startY + radialBY, startZ + radialBZ,
                color,
                alpha);
        }
    }

    private static void addQuad(BufferBuilder buffer, double firstX, double firstY, double firstZ, double secondX,
                                double secondY, double secondZ, double thirdX, double thirdY, double thirdZ,
                                double fourthX, double fourthY, double fourthZ, GlowColor color, float alpha) {
        addVertex(buffer, firstX, firstY, firstZ, color, alpha);
        addVertex(buffer, secondX, secondY, secondZ, color, alpha);
        addVertex(buffer, thirdX, thirdY, thirdZ, color, alpha);
        addVertex(buffer, fourthX, fourthY, fourthZ, color, alpha);
    }

    private static void addVertex(BufferBuilder buffer, double x, double y, double z, GlowColor color, float alpha) {
        buffer.pos(x, y, z).color(color.red(), color.green(), color.blue(), alpha).endVertex();
    }

    public record GlowColor(float red, float green, float blue) {
        public static GlowColor fromRgb(int rgb) {
            return new GlowColor(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F
            );
        }
    }
}
