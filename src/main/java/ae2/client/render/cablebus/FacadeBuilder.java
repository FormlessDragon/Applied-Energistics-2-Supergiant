package ae2.client.render.cablebus;

import ae2.api.parts.PartHelper;
import ae2.api.util.AEAxisAlignedBB;
import ae2.client.render.mesh.ModelHelper;
import ae2.client.render.mesh.MutableQuadView;
import ae2.thirdparty.codechicken.lib.model.pipeline.transformers.QuadClamper;
import ae2.thirdparty.codechicken.lib.model.pipeline.transformers.QuadCornerKicker;
import ae2.thirdparty.codechicken.lib.model.pipeline.transformers.QuadFaceStripper;
import ae2.thirdparty.codechicken.lib.model.pipeline.transformers.QuadReInterpolator;
import ae2.thirdparty.codechicken.lib.model.pipeline.transformers.QuadTinter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;

import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class FacadeBuilder {

    public static final double THICK_THICKNESS = 2D / 16D;
    public static final double THIN_THICKNESS = 1D / 16D;

    public static final AxisAlignedBB[] THICK_FACADE_BOXES = new AxisAlignedBB[]{
        new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, THICK_THICKNESS, 1.0),
        new AxisAlignedBB(0.0, 1.0 - THICK_THICKNESS, 0.0, 1.0, 1.0, 1.0),
        new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, THICK_THICKNESS),
        new AxisAlignedBB(0.0, 0.0, 1.0 - THICK_THICKNESS, 1.0, 1.0, 1.0),
        new AxisAlignedBB(0.0, 0.0, 0.0, THICK_THICKNESS, 1.0, 1.0),
        new AxisAlignedBB(1.0 - THICK_THICKNESS, 0.0, 0.0, 1.0, 1.0, 1.0)};

    public static final AxisAlignedBB[] THIN_FACADE_BOXES = new AxisAlignedBB[]{
        new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, THIN_THICKNESS, 1.0),
        new AxisAlignedBB(0.0, 1.0 - THIN_THICKNESS, 0.0, 1.0, 1.0, 1.0),
        new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, THIN_THICKNESS),
        new AxisAlignedBB(0.0, 0.0, 1.0 - THIN_THICKNESS, 1.0, 1.0, 1.0),
        new AxisAlignedBB(0.0, 0.0, 0.0, THIN_THICKNESS, 1.0, 1.0),
        new AxisAlignedBB(1.0 - THIN_THICKNESS, 0.0, 0.0, 1.0, 1.0, 1.0)};

    private final Map<EnumFacing, List<BakedQuad>> cableAnchorStilts;

    public FacadeBuilder(@Nullable IBakedModel cableAnchorStiltModel) {
        this.cableAnchorStilts = buildCableAnchorStilts(cableAnchorStiltModel);
    }

    private static Map<EnumFacing, List<BakedQuad>> buildCableAnchorStilts(@Nullable IBakedModel model) {
        EnumMap<EnumFacing, List<BakedQuad>> rotatedQuads = new EnumMap<>(EnumFacing.class);

        if (model == null) {
            for (EnumFacing side : EnumFacing.values()) {
                rotatedQuads.put(side, Collections.emptyList());
            }
            return rotatedQuads;
        }

        QuadRotator rotator = new QuadRotator();

        for (EnumFacing side : EnumFacing.values()) {
            List<BakedQuad> rotated = new ObjectArrayList<>();

            for (int cullFaceIdx = 0; cullFaceIdx <= ModelHelper.NULL_FACE_ID; cullFaceIdx++) {
                EnumFacing cullFace = ModelHelper.faceFromIndex(cullFaceIdx);
                List<BakedQuad> quads = model.getQuads(null, cullFace, 0L);
                rotated.addAll(rotator.rotateQuads(quads, side, EnumFacing.UP));
            }

            rotatedQuads.put(side, rotated);
        }

        return rotatedQuads;
    }

    @Nullable
    private static AEAxisAlignedBB getCutOutBox(AxisAlignedBB facadeBox, List<AxisAlignedBB> partBoxes) {
        AEAxisAlignedBB cutOutBox = null;
        for (AxisAlignedBB partBox : partBoxes) {
            if (partBox.intersects(facadeBox)) {
                if (cutOutBox == null) {
                    cutOutBox = AEAxisAlignedBB.fromBounds(partBox);
                } else {
                    cutOutBox.maxX = Math.max(cutOutBox.maxX, partBox.maxX);
                    cutOutBox.maxY = Math.max(cutOutBox.maxY, partBox.maxY);
                    cutOutBox.maxZ = Math.max(cutOutBox.maxZ, partBox.maxZ);
                    cutOutBox.minX = Math.min(cutOutBox.minX, partBox.minX);
                    cutOutBox.minY = Math.min(cutOutBox.minY, partBox.minY);
                    cutOutBox.minZ = Math.min(cutOutBox.minZ, partBox.minZ);
                }
            }
        }
        return cutOutBox;
    }

    private static List<AxisAlignedBB> getBoxes(AxisAlignedBB facadeBox, @Nullable AEAxisAlignedBB hole, Axis axis) {
        if (hole == null) {
            return Collections.singletonList(facadeBox);
        }

        List<AxisAlignedBB> boxes = new ObjectArrayList<>();
        switch (axis) {
            case Y -> {
                boxes.add(new AxisAlignedBB(facadeBox.minX, facadeBox.minY, facadeBox.minZ, hole.minX, facadeBox.maxY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(hole.maxX, facadeBox.minY, facadeBox.minZ, facadeBox.maxX, facadeBox.maxY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(hole.minX, facadeBox.minY, facadeBox.minZ, hole.maxX, facadeBox.maxY,
                    hole.minZ));
                boxes.add(new AxisAlignedBB(hole.minX, facadeBox.minY, hole.maxZ, hole.maxX, facadeBox.maxY,
                    facadeBox.maxZ));
            }
            case Z -> {
                boxes.add(new AxisAlignedBB(facadeBox.minX, facadeBox.minY, facadeBox.minZ, facadeBox.maxX, hole.minY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(facadeBox.minX, hole.maxY, facadeBox.minZ, facadeBox.maxX, facadeBox.maxY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(facadeBox.minX, hole.minY, facadeBox.minZ, hole.minX, hole.maxY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(hole.maxX, hole.minY, facadeBox.minZ, facadeBox.maxX, hole.maxY,
                    facadeBox.maxZ));
            }
            case X -> {
                boxes.add(new AxisAlignedBB(facadeBox.minX, facadeBox.minY, facadeBox.minZ, facadeBox.maxX, hole.minY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(facadeBox.minX, hole.maxY, facadeBox.minZ, facadeBox.maxX, facadeBox.maxY,
                    facadeBox.maxZ));
                boxes.add(new AxisAlignedBB(facadeBox.minX, hole.minY, facadeBox.minZ, facadeBox.maxX, hole.maxY,
                    hole.minZ));
                boxes.add(new AxisAlignedBB(facadeBox.minX, hole.minY, hole.maxZ, facadeBox.maxX, hole.maxY,
                    facadeBox.maxZ));
            }
            default -> {
            }
        }

        return boxes;
    }

    private static boolean isUseThinFacades(List<AxisAlignedBB> partBoxes) {
        final double min = 2.0 / 16.0;
        final double max = 14.0 / 16.0;

        for (AxisAlignedBB bb : partBoxes) {
            int outside = 0;
            outside += bb.maxX > max ? 1 : 0;
            outside += bb.maxY > max ? 1 : 0;
            outside += bb.maxZ > max ? 1 : 0;
            outside += bb.minX < min ? 1 : 0;
            outside += bb.minY < min ? 1 : 0;
            outside += bb.minZ < min ? 1 : 0;

            if (outside >= 2) {
                return true;
            }
        }

        return false;
    }

    private static void applyAlpha(MutableQuadView quad) {
        final int alpha = 0x4C;
        for (int i = 0; i < 4; i++) {
            quad.color(i, (quad.color(i) & 0x00FFFFFF) | (alpha << 24));
        }
    }

    private static void emitFacadeQuads(IBakedModel model, IBlockState renderedState, EnumFacing side,
                                        FacadeBlockAccess facadeAccess, BlockPos pos, long rand,
                                        List<AxisAlignedBB> holeStrips, QuadFaceStripper faceStripper,
                                        QuadCornerKicker kicker, QuadReInterpolator interpolator,
                                        MutableQuadView mutableQuad, BlockColors blockColors,
                                        TextureAtlasSprite fallbackSprite, boolean transparent,
                                        List<BakedQuad> quadsOut) {
        for (int cullFaceIdx = 0; cullFaceIdx <= ModelHelper.NULL_FACE_ID; cullFaceIdx++) {
            EnumFacing cullFace = ModelHelper.faceFromIndex(cullFaceIdx);
            List<BakedQuad> quads = model.getQuads(renderedState, cullFace, rand);
            for (BakedQuad quad : quads) {
                QuadTinter quadTinter = null;
                if (quad.hasTintIndex()) {
                    quadTinter = new QuadTinter(blockColors.colorMultiplier(renderedState, facadeAccess, pos,
                        quad.getTintIndex()));
                }

                TextureAtlasSprite sprite = quad.getSprite() != null ? quad.getSprite() : fallbackSprite;

                for (AxisAlignedBB box : holeStrips) {
                    mutableQuad.fromVanilla(quad, cullFace == side ? side : null);
                    mutableQuad.nominalFace(getNominalFace(quad));
                    interpolator.setInputQuad(mutableQuad);

                    if (!new QuadClamper(box).transform(mutableQuad)) {
                        continue;
                    }

                    if (!faceStripper.transform(mutableQuad)) {
                        continue;
                    }

                    if (!kicker.transform(mutableQuad)) {
                        continue;
                    }

                    interpolator.transform(mutableQuad);

                    if (quadTinter != null) {
                        quadTinter.transform(mutableQuad);
                    }
                    if (transparent) {
                        applyAlpha(mutableQuad);
                    }

                    quadsOut.add(toTransformedBakedQuad(mutableQuad, quad, sprite));
                }
            }
        }
    }

    private static BakedQuad toTransformedBakedQuad(MutableQuadView mutableQuad, BakedQuad sourceQuad,
                                                    TextureAtlasSprite sprite) {
        int[] vertexData = new int[sourceQuad.getVertexData().length];
        mutableQuad.toVanilla(vertexData, 0);
        return new BakedQuad(vertexData, sourceQuad.getTintIndex(), mutableQuad.lightFace(), sprite,
            sourceQuad.shouldApplyDiffuseLighting(), sourceQuad.getFormat());
    }

    private static EnumFacing getNominalFace(BakedQuad quad) {
        EnumFacing face = quad.getFace();
        return face != null ? face : EnumFacing.UP;
    }

    public void addFacadeQuads(CableBusRenderState renderState, long rand, @Nullable BlockRenderLayer layer,
                               List<BakedQuad> quadsOut) {
        Map<EnumFacing, FacadeRenderState> facadeStates = renderState.getFacades();
        if (facadeStates.isEmpty()) {
            return;
        }

        BlockPos pos = renderState.getPos();
        IBlockAccess level = renderState.getWorld();
        if (pos == null || level == null) {
            return;
        }

        List<AxisAlignedBB> partBoxes = renderState.getBoundingBoxes();
        Set<EnumFacing> sidesWithParts = renderState.getAttachments().keySet();
        boolean thinFacades = isUseThinFacades(partBoxes);
        boolean transparent = PartHelper.getCableRenderMode().transparentFacades;

        for (Entry<EnumFacing, FacadeRenderState> entry : facadeStates.entrySet()) {
            EnumFacing side = entry.getKey();

            if (!sidesWithParts.contains(side)
                && (layer == null || layer == BlockRenderLayer.CUTOUT || layer == BlockRenderLayer.CUTOUT_MIPPED)) {
                quadsOut.addAll(this.cableAnchorStilts.get(side));
            }

            if (transparent && layer != null && layer != BlockRenderLayer.TRANSLUCENT) {
                continue;
            }

            IBlockState sourceState = entry.getValue().sourceBlock();
            if (!transparent && layer != null && !sourceState.getBlock().canRenderInLayer(sourceState, layer)) {
                continue;
            }

            addFacadeQuadSet(level, pos, side, entry.getValue(), facadeStates, partBoxes, thinFacades, transparent, layer,
                rand, quadsOut);
        }
    }

    private void addFacadeQuadSet(IBlockAccess level, BlockPos pos, EnumFacing side, FacadeRenderState facadeRenderState,
                                  Map<EnumFacing, FacadeRenderState> facadeStates, List<AxisAlignedBB> partBoxes, boolean thinFacades,
                                  boolean transparent, @Nullable BlockRenderLayer layer, long rand, List<BakedQuad> quadsOut) {
        IBlockState sourceState = facadeRenderState.sourceBlock();
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        BlockColors blockColors = Minecraft.getMinecraft().getBlockColors();
        FacadeBlockAccess facadeAccess = new FacadeBlockAccess(level, pos, sourceState);
        IBlockState renderedState = sourceState;
        try {
            renderedState = renderedState.getActualState(facadeAccess, pos);
        } catch (RuntimeException ignored) {
        }
        IBakedModel model = dispatcher.getModelForState(renderedState);
        try {
            renderedState = renderedState.getBlock().getExtendedState(renderedState, facadeAccess, pos);
        } catch (RuntimeException ignored) {
        }
        TextureAtlasSprite fallbackSprite = model.getParticleTexture();

        AxisAlignedBB fullBounds = thinFacades ? THIN_FACADE_BOXES[side.ordinal()] : THICK_FACADE_BOXES[side.ordinal()];
        AxisAlignedBB facadeBox = fullBounds;
        if (facadeRenderState.transparent()) {
            double offset = thinFacades ? THIN_THICKNESS : THICK_THICKNESS;
            AEAxisAlignedBB shrunkBox = null;
            for (EnumFacing face : EnumFacing.values()) {
                if (face.getAxis() == side.getAxis()) {
                    continue;
                }

                FacadeRenderState otherState = facadeStates.get(face);
                if (otherState == null || otherState.transparent()) {
                    continue;
                }

                if (shrunkBox == null) {
                    shrunkBox = AEAxisAlignedBB.fromBounds(facadeBox);
                }

                switch (face) {
                    case DOWN -> shrunkBox.minY += offset;
                    case UP -> shrunkBox.maxY -= offset;
                    case NORTH -> shrunkBox.minZ += offset;
                    case SOUTH -> shrunkBox.maxZ -= offset;
                    case WEST -> shrunkBox.minX += offset;
                    case EAST -> shrunkBox.maxX -= offset;
                    default -> {
                    }
                }
            }

            if (shrunkBox != null) {
                facadeBox = shrunkBox.getBoundingBox();
            }
        }

        int facadeMask = 0;
        for (Entry<EnumFacing, FacadeRenderState> entry : facadeStates.entrySet()) {
            EnumFacing otherSide = entry.getKey();
            if (otherSide.getAxis() != side.getAxis() && !entry.getValue().transparent()) {
                facadeMask |= 1 << otherSide.ordinal();
            }
        }

        AEAxisAlignedBB cutOutBox = getCutOutBox(facadeBox, partBoxes);
        List<AxisAlignedBB> holeStrips = getBoxes(facadeBox, cutOutBox, side.getAxis());

        QuadFaceStripper faceStripper = new QuadFaceStripper(fullBounds, facadeMask);
        QuadCornerKicker kicker = new QuadCornerKicker();
        kicker.setSide(side.ordinal());
        kicker.setFacadeMask(facadeMask);
        kicker.setBox(fullBounds);
        kicker.setThickness(thinFacades ? THIN_THICKNESS : THICK_THICKNESS);

        QuadReInterpolator interpolator = new QuadReInterpolator();
        MutableQuadView mutableQuad = MutableQuadView.getInstance();

        if (transparent || layer == null) {
            for (BlockRenderLayer forcedLayer : BlockRenderLayer.values()) {
                if (!renderedState.getBlock().canRenderInLayer(renderedState, forcedLayer)) {
                    continue;
                }
                ForgeHooksClient.setRenderLayer(forcedLayer);
                emitFacadeQuads(model, renderedState, side, facadeAccess, pos, rand, holeStrips, faceStripper, kicker,
                    interpolator, mutableQuad, blockColors, fallbackSprite, transparent, quadsOut);
            }
            ForgeHooksClient.setRenderLayer(null);
        } else {
            emitFacadeQuads(model, renderedState, side, facadeAccess, pos, rand, holeStrips, faceStripper, kicker,
                interpolator, mutableQuad, blockColors, fallbackSprite, transparent, quadsOut);
        }
    }

    public List<BakedQuad> buildFacadeItemQuads(ItemStack textureItem, EnumFacing side) {
        List<BakedQuad> facadeQuads = new ObjectArrayList<>();
        IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(textureItem, null, null);
        QuadReInterpolator interpolator = new QuadReInterpolator();
        QuadClamper clamper = new QuadClamper(THICK_FACADE_BOXES[side.ordinal()]);
        ItemColors itemColors = Minecraft.getMinecraft().getItemColors();
        MutableQuadView mutableQuad = MutableQuadView.getInstance();
        TextureAtlasSprite fallbackSprite = model.getParticleTexture();

        for (int cullFaceIdx = 0; cullFaceIdx <= ModelHelper.NULL_FACE_ID; cullFaceIdx++) {
            EnumFacing cullFace = ModelHelper.faceFromIndex(cullFaceIdx);
            List<BakedQuad> quads = model.getQuads(null, cullFace, 0L);

            for (BakedQuad quad : quads) {
                QuadTinter quadTinter = null;

                if (quad.hasTintIndex()) {
                    quadTinter = new QuadTinter(itemColors.colorMultiplier(textureItem, quad.getTintIndex()));
                }

                mutableQuad.fromVanilla(quad, cullFace);
                mutableQuad.nominalFace(getNominalFace(quad));
                interpolator.setInputQuad(mutableQuad);

                if (!clamper.transform(mutableQuad)) {
                    continue;
                }

                interpolator.transform(mutableQuad);

                if (quadTinter != null) {
                    quadTinter.transform(mutableQuad);
                }

                facadeQuads.add(toTransformedBakedQuad(mutableQuad, quad,
                    quad.getSprite() != null ? quad.getSprite() : fallbackSprite));
            }
        }

        return facadeQuads;
    }
}
