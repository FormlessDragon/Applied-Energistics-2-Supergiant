package ae2.client.render.model;

import ae2.hooks.CompassManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class MeteoriteCompassBakedModel implements IBakedModel {
    private final IBakedModel base;
    private final IBakedModel pointer;
    private final float rotation;

    public MeteoriteCompassBakedModel(IBakedModel base, IBakedModel pointer) {
        this(base, pointer, 0.0F);
    }

    private MeteoriteCompassBakedModel(IBakedModel base, IBakedModel pointer, float rotation) {
        this.base = base;
        this.pointer = pointer;
        this.rotation = rotation;
    }

    public static float getAnimatedRotation(@Nullable BlockPos pos, boolean prefetch, float playerRotation) {
        if (pos != null) {
            ChunkPos ourChunkPos = new ChunkPos(pos);
            BlockPos closestMeteorite = CompassManager.INSTANCE.getClosestMeteorite(ourChunkPos, prefetch);
            if (closestMeteorite != null) {
                double dx = pos.getX() - closestMeteorite.getX();
                double dz = pos.getZ() - closestMeteorite.getZ();
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq <= 36.0D) {
                    long timeMillis = System.currentTimeMillis() % 500L;
                    return timeMillis / 500.0F * (float) Math.PI * 2.0F;
                }

                return (float) rad(pos.getX(), pos.getZ(), closestMeteorite.getX(), closestMeteorite.getZ())
                    + playerRotation;
            }
        }

        long timeMillis = System.currentTimeMillis() % 3000L;
        return timeMillis / 3000.0F * (float) Math.PI * 2.0F;
    }

    private static double rad(double ax, double az, double bx, double bz) {
        double up = bz - az;
        double side = bx - ax;
        return Math.atan2(-up, side) - Math.PI / 2.0D;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> quads = new ObjectArrayList<>(this.base.getQuads(state, side, rand));
        if (side == null && state == null) {
            MatrixVertexTransformer transformer = new MatrixVertexTransformer(createRotationMatrix(this.rotation));
            for (BakedQuad bakedQuad : this.pointer.getQuads(state, side, rand)) {
                UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(bakedQuad.getFormat());
                transformer.setParent(builder);
                transformer.setVertexFormat(builder.getVertexFormat());
                bakedQuad.pipe(transformer);
                quads.add(builder.build());
            }
        }
        return quads;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.base.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return this.base.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.base.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.base.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return new ItemOverrideList(Collections.emptyList()) {
            @Override
            public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world,
                                               EntityLivingBase entity) {
                BlockPos pos = entity != null ? entity.getPosition() : getPlayerPos();
                float playerRotation = entity != null ? (float) (entity.rotationYaw / 180.0F * Math.PI + Math.PI) : 0.0F;
                float animatedRotation = getAnimatedRotation(pos, entity != null, playerRotation);
                return new MeteoriteCompassBakedModel(MeteoriteCompassBakedModel.this.base,
                    MeteoriteCompassBakedModel.this.pointer, animatedRotation);
            }
        };
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
        Pair<? extends IBakedModel, Matrix4f> pair = this.base.handlePerspective(cameraTransformType);
        Matrix4f matrix = pair != null ? pair.getValue() : TRSRTransformation.identity().getMatrix();
        float adjustedRotation = this.rotation + getDisplayRotationOffset(matrix);
        if (pair != null) {
            return Pair.of(new MeteoriteCompassBakedModel(this.base, this.pointer, adjustedRotation), pair.getValue());
        }
        return Pair.of(new MeteoriteCompassBakedModel(this.base, this.pointer, adjustedRotation), matrix);
    }

    @Nullable
    private BlockPos getPlayerPos() {
        if (net.minecraft.client.Minecraft.getMinecraft().player == null) {
            return null;
        }
        return net.minecraft.client.Minecraft.getMinecraft().player.getPosition();
    }

    private Matrix4f createRotationMatrix(float rotation) {
        Matrix4f transform = new Matrix4f();
        transform.setIdentity();
        transform.rotY(rotation);
        return transform;
    }

    private float getDisplayRotationOffset(Matrix4f transform) {
        Vector4f pointerNormal = new Vector4f(0.0F, 0.0F, 1.0F, 0.0F);
        transform.transform(pointerNormal);

        double x = pointerNormal.x;
        double z = pointerNormal.z;
        if (x * x + z * z < 1.0E-6D) {
            return 0.0F;
        }

        return (float) (Math.atan2(z, x) - Math.atan2(1.0D, 0.0D));
    }
}
