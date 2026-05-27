package appeng.client.render.cablebus;

import appeng.api.orientation.BlockOrientation;
import appeng.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.List;

public class QuadRotator {

    private static int getByte(int[] data, int offset) {
        int idx = offset / 4;
        int subOffset = offset % 4;
        return (byte) (data[idx] >> (subOffset * 8));
    }

    private static void setByte(int[] data, int offset, int value) {
        int idx = offset / 4;
        int subOffset = offset % 4;
        int mask = 0xFF << (subOffset * 8);
        data[idx] = data[idx] & (~mask) | ((value & 0xFF) << (subOffset * 8));
    }

    public List<BakedQuad> rotateQuads(List<BakedQuad> quads, EnumFacing newForward, EnumFacing newUp) {
        if (newForward == EnumFacing.NORTH && newUp == EnumFacing.UP) {
            return quads;
        }

        List<BakedQuad> result = new ObjectArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            result.add(this.rotateQuad(quad, newForward, newUp));
        }
        return result;
    }

    private BakedQuad rotateQuad(BakedQuad quad, EnumFacing forward, EnumFacing up) {
        if (forward.getAxis() == up.getAxis()) {
            up = up.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;
        }

        BlockOrientation rotation = BlockOrientation.get(forward, up);
        TRSRTransformation transformation = rotation.getTransformation();

        int[] newData = quad.getVertexData().clone();
        VertexFormat format = quad.getFormat();
        int stride = format.getIntegerSize();
        int posOffset = this.findElementOffset(format, VertexFormatElement.EnumUsage.POSITION) / 4;
        int normalOffsetBytes = this.findElementOffset(format, VertexFormatElement.EnumUsage.NORMAL);
        VertexFormatElement.EnumType normalType = this.findNormalElementType(format);

        Vector4f pos = new Vector4f();
        Vector3f normal = new Vector3f();

        for (int i = 0; i < 4; i++) {
            int vertexOffset = i * stride;
            pos.set(
                Float.intBitsToFloat(newData[vertexOffset + posOffset]) - 0.5f,
                Float.intBitsToFloat(newData[vertexOffset + posOffset + 1]) - 0.5f,
                Float.intBitsToFloat(newData[vertexOffset + posOffset + 2]) - 0.5f,
                1.0f);

            transformation.transformPosition(pos);

            newData[vertexOffset + posOffset] = Float.floatToIntBits(pos.x + 0.5f);
            newData[vertexOffset + posOffset + 1] = Float.floatToIntBits(pos.y + 0.5f);
            newData[vertexOffset + posOffset + 2] = Float.floatToIntBits(pos.z + 0.5f);

            if (normalOffsetBytes >= 0 && normalType != null) {
                if (normalType == VertexFormatElement.EnumType.FLOAT) {
                    int normalOffset = normalOffsetBytes / 4;
                    normal.set(
                        Float.intBitsToFloat(newData[vertexOffset + normalOffset]),
                        Float.intBitsToFloat(newData[vertexOffset + normalOffset + 1]),
                        Float.intBitsToFloat(newData[vertexOffset + normalOffset + 2]));

                    transformation.transformNormal(normal);

                    newData[vertexOffset + normalOffset] = Float.floatToIntBits(normal.x);
                    newData[vertexOffset + normalOffset + 1] = Float.floatToIntBits(normal.y);
                    newData[vertexOffset + normalOffset + 2] = Float.floatToIntBits(normal.z);
                } else if (normalType == VertexFormatElement.EnumType.BYTE) {
                    int normalIndex = i * format.getSize() + normalOffsetBytes;
                    normal.set(
                        getByte(newData, normalIndex) / 127.0f,
                        getByte(newData, normalIndex + 1) / 127.0f,
                        getByte(newData, normalIndex + 2) / 127.0f);

                    transformation.transformNormal(normal);

                    setByte(newData, normalIndex, (int) (normal.x * 127));
                    setByte(newData, normalIndex + 1, (int) (normal.y * 127));
                    setByte(newData, normalIndex + 2, (int) (normal.z * 127));
                } else {
                    AELog.warn("Unsupported normal format: {}", normalType);
                }
            }
        }

        EnumFacing rotatedFace = rotation.rotate(quad.getFace());
        return new BakedQuad(newData, quad.getTintIndex(), rotatedFace, quad.getSprite(),
            quad.shouldApplyDiffuseLighting(), format);
    }

    private int findElementOffset(VertexFormat format, VertexFormatElement.EnumUsage usage) {
        int offset = 0;
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == usage) {
                return offset;
            }
            offset += element.getSize();
        }
        return -1;
    }

    @Nullable
    private VertexFormatElement.EnumType findNormalElementType(VertexFormat format) {
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == VertexFormatElement.EnumUsage.NORMAL) {
                return element.getType();
            }
        }
        return null;
    }
}
