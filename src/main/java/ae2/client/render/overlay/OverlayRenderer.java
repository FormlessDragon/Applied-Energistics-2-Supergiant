package ae2.client.render.overlay;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public class OverlayRenderer {
    private static final double INSET = 0.01D;

    private final IOverlayDataSource source;

    OverlayRenderer(IOverlayDataSource source) {
        this.source = source;
    }

    private static void addLine(BufferBuilder buffer, double x1, double y1, double z1,
                                double x2, double y2, double z2, int[] colors) {
        addVertex(buffer, x1, y1, z1, colors);
        addVertex(buffer, x2, y2, z2, colors);
    }

    private static void addQuad(BufferBuilder buffer, double x1, double y1, double z1,
                                double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4,
                                int[] colors) {
        addVertex(buffer, x1, y1, z1, colors);
        addVertex(buffer, x2, y2, z2, colors);
        addVertex(buffer, x3, y3, z3, colors);
        addVertex(buffer, x4, y4, z4, colors);
    }

    private static void addVertex(BufferBuilder buffer, double x, double y, double z, int[] colors) {
        buffer.pos(x, y, z).color(colors[1], colors[2], colors[3], colors[0]).endVertex();
    }

    private static int[] decomposeColor(int color) {
        return new int[]{
            color >> 24 & 0xFF,
            color >> 16 & 0xFF,
            color >> 8 & 0xFF,
            color & 0xFF
        };
    }

    public void renderFaces() {
        render(false, this.source.getOverlayColor());
    }

    public void renderVisibleLines() {
        render(true, this.source.getOverlayColor());
    }

    public void renderOccludedLines() {
        render(true, 0x30FFFFFF);
    }

    private void render(boolean renderLines, int color) {
        int[] colors = decomposeColor(color);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(renderLines ? GL11.GL_LINES : GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (ChunkPos pos : this.source.getOverlayChunks()) {
            addVertices(buffer, pos, colors, renderLines);
        }

        tessellator.draw();
    }

    private void addVertices(BufferBuilder buffer, ChunkPos pos, int[] colors, boolean renderLines) {
        Set<ChunkPos> chunks = this.source.getOverlayChunks();

        double x1 = (pos.x << 4) + INSET;
        double x2 = (pos.x << 4) + 16.0D - INSET;
        double y1 = 0.0D + INSET;
        double y2 = 256.0D - INSET;
        double z1 = (pos.z << 4) + INSET;
        double z2 = (pos.z << 4) + 16.0D - INSET;

        boolean noNorth = !chunks.contains(new ChunkPos(pos.x, pos.z - 1));
        boolean noSouth = !chunks.contains(new ChunkPos(pos.x, pos.z + 1));
        boolean noWest = !chunks.contains(new ChunkPos(pos.x - 1, pos.z));
        boolean noEast = !chunks.contains(new ChunkPos(pos.x + 1, pos.z));

        if (renderLines) {
            if (noNorth || noWest) {
                addLine(buffer, x1, y1, z1, x1, y2, z1, colors);
            }
            if (noNorth || noEast) {
                addLine(buffer, x2, y2, z1, x2, y1, z1, colors);
            }
            if (noSouth || noEast) {
                addLine(buffer, x2, y1, z2, x2, y2, z2, colors);
            }
            if (noSouth || noWest) {
                addLine(buffer, x1, y2, z2, x1, y1, z2, colors);
            }
            if (noNorth) {
                addLine(buffer, x1, y1, z1, x2, y1, z1, colors);
                addLine(buffer, x2, y2, z1, x1, y2, z1, colors);
            }
            if (noSouth) {
                addLine(buffer, x2, y1, z2, x1, y1, z2, colors);
                addLine(buffer, x1, y2, z2, x2, y2, z2, colors);
            }
            if (noWest) {
                addLine(buffer, x1, y1, z1, x1, y1, z2, colors);
                addLine(buffer, x1, y2, z2, x1, y2, z1, colors);
            }
            if (noEast) {
                addLine(buffer, x2, y1, z2, x2, y1, z1, colors);
                addLine(buffer, x2, y2, z1, x2, y2, z2, colors);
            }
        } else {
            if (noNorth) {
                addQuad(buffer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, colors);
            }
            if (noSouth) {
                addQuad(buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, colors);
            }
            if (noWest) {
                addQuad(buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, colors);
            }
            if (noEast) {
                addQuad(buffer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, colors);
            }
            addQuad(buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, colors);
            addQuad(buffer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, colors);
        }
    }
}
