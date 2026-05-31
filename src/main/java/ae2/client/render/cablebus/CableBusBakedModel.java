package ae2.client.render.cablebus;

import ae2.api.parts.IPartBakedModel;
import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.block.networking.CableBusBlock;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CableBusBakedModel implements IBakedModel {

    private static final int CACHE_QUAD_COUNT = 5000;

    private final LoadingCache<CableBusRenderState, List<BakedQuad>> cableModelCache;
    private final CableBuilder cableBuilder;
    private final FacadeBuilder facadeBuilder;
    private final Map<ResourceLocation, IBakedModel> partModels;
    private final TextureAtlasSprite particleTexture;
    private final TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
    private final CableBusRenderState defaultRenderState = new CableBusRenderState();

    CableBusBakedModel(CableBuilder cableBuilder, FacadeBuilder facadeBuilder,
                       Map<ResourceLocation, IBakedModel> partModels, TextureAtlasSprite particleTexture) {
        this.cableBuilder = cableBuilder;
        this.facadeBuilder = facadeBuilder;
        this.partModels = partModels;
        this.particleTexture = particleTexture;
        this.cableModelCache = CacheBuilder.newBuilder()
                                           .maximumWeight(CACHE_QUAD_COUNT)
                                           .weigher((Weigher<CableBusRenderState, List<BakedQuad>>) (ignored, value) -> value.size())
                                           .build(new CacheLoader<>() {
                                               @Override
                                               public @Nonnull List<BakedQuad> load(@Nonnull CableBusRenderState renderState) {
                                                   List<BakedQuad> result = new ObjectArrayList<>();
                                                   addCableQuads(renderState, result);
                                                   return result;
                                               }
                                           });
    }

    private static boolean isStraightLine(AECableType cableType, EnumMap<EnumFacing, AECableType> sides) {
        Iterator<Entry<EnumFacing, AECableType>> iterator = sides.entrySet().iterator();
        if (!iterator.hasNext()) {
            return false;
        }

        Entry<EnumFacing, AECableType> firstConnection = iterator.next();
        EnumFacing firstSide = firstConnection.getKey();
        AECableType firstType = firstConnection.getValue();

        if (!iterator.hasNext()) {
            return false;
        }
        if (firstSide.getOpposite() != iterator.next().getKey()) {
            return false;
        }
        if (iterator.hasNext()) {
            return false;
        }

        AECableType secondType = sides.get(firstSide.getOpposite());
        return firstType == secondType && cableType == firstType;
    }

    public List<BakedQuad> getQuads(CableBusRenderState renderState, long rand) {
        if (renderState == null) {
            return Collections.emptyList();
        }

        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        List<BakedQuad> quads = new ObjectArrayList<>();

        if (layer == null || layer == BlockRenderLayer.CUTOUT || layer == BlockRenderLayer.CUTOUT_MIPPED) {
            quads.addAll(this.cableModelCache.getUnchecked(renderState));
            addAttachmentQuads(renderState, rand, quads);
        }

        this.facadeBuilder.addFacadeQuads(renderState, rand, layer, quads);

        return quads;
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        if (side != null) {
            return Collections.emptyList();
        }
        return this.getQuads(getRenderState(state), rand);
    }

    private CableBusRenderState getRenderState(IBlockState state) {
        if (state instanceof IExtendedBlockState) {
            CableBusRenderState renderState = ((IExtendedBlockState) state).getValue(CableBusBlock.RENDER_STATE);
            if (renderState != null) {
                return renderState;
            }
        }
        return this.defaultRenderState;
    }

    private void addAttachmentQuads(CableBusRenderState renderState, long rand, List<BakedQuad> quadsOut) {
        QuadRotator rotator = new QuadRotator();

        for (EnumFacing facing : EnumFacing.values()) {
            IPartModel partModel = renderState.getAttachments().get(facing);
            if (partModel == null) {
                continue;
            }

            int spin = getAttachmentSpin(renderState, facing);

            for (ResourceLocation modelId : partModel.getModels()) {
                IBakedModel bakedModel = this.partModels.get(modelId);
                if (bakedModel == null) {
                    throw new IllegalStateException("Trying to use an unregistered part model: " + modelId);
                }

                List<BakedQuad> partQuads;
                if (bakedModel instanceof IPartBakedModel) {
                    partQuads = ((IPartBakedModel) bakedModel)
                        .getPartQuads(renderState.getPartModelData().get(facing), rand);
                } else {
                    partQuads = bakedModel.getQuads(null, null, rand);
                }

                quadsOut.addAll(rotator.rotateQuads(partQuads, facing, spinToUpFacing(facing, spin)));
            }
        }
    }

    private EnumFacing spinToUpFacing(EnumFacing facing, int spin) {
        return ae2.api.orientation.BlockOrientation.get(facing, spin).getSide(
            ae2.api.orientation.RelativeSide.TOP);
    }

    private int getAttachmentSpin(CableBusRenderState renderState, EnumFacing facing) {
        if (renderState.getAttachmentSpins().containsKey(facing)) {
            return renderState.getAttachmentSpins().getInt(facing);
        }

        Object partModelData = renderState.getPartModelData().get(facing);
        if (partModelData instanceof Long) {
            return 0;
        }
        if (partModelData instanceof Number) {
            return ((Number) partModelData).intValue();
        }
        return 0;
    }

    private void addCableQuads(CableBusRenderState renderState, List<BakedQuad> quadsOut) {
        AECableType cableType = renderState.getCableType();
        if (cableType == AECableType.NONE) {
            return;
        }

        AEColor cableColor = renderState.getCableColor();
        EnumMap<EnumFacing, AECableType> connectionTypes = renderState.getConnectionTypes();

        boolean noAttachments = renderState.getAttachments().values().stream()
                                           .noneMatch(IPartModel::requireCableConnection);
        if (noAttachments && isStraightLine(cableType, connectionTypes)) {
            EnumFacing facing = connectionTypes.keySet().iterator().next();
            switch (cableType) {
                case GLASS -> this.cableBuilder.addStraightGlassConnection(facing, cableColor, quadsOut);
                case COVERED -> this.cableBuilder.addStraightCoveredConnection(facing, cableColor, quadsOut);
                case SMART -> this.cableBuilder.addStraightSmartConnection(facing, cableColor,
                    renderState.getChannelsOnSide().getOrDefault(facing, 0), quadsOut);
                case DENSE_COVERED -> this.cableBuilder.addStraightDenseCoveredConnection(facing, cableColor, quadsOut);
                case DENSE_SMART -> this.cableBuilder.addStraightDenseSmartConnection(facing, cableColor,
                    renderState.getChannelsOnSide().getOrDefault(facing, 0), quadsOut);
                default -> {
                }
            }
            return;
        }

        CableCoreType coreType = renderState.getCoreType();
        if (coreType == null) {
            coreType = CableCoreType.fromCableType(cableType);
        }
        if (coreType != null) {
            this.cableBuilder.addCableCore(coreType, cableColor, quadsOut);
        }

        for (Entry<EnumFacing, Integer> attachmentConnection : renderState.getAttachmentConnections().reference2IntEntrySet()) {
            EnumFacing facing = attachmentConnection.getKey();
            int distance = attachmentConnection.getValue();
            int channels = renderState.getChannelsOnSide().getOrDefault(facing, 0);

            switch (cableType) {
                case GLASS -> this.cableBuilder.addConstrainedGlassConnection(facing, cableColor, distance, quadsOut);
                case COVERED ->
                    this.cableBuilder.addConstrainedCoveredConnection(facing, cableColor, distance, quadsOut);
                case SMART -> this.cableBuilder.addConstrainedSmartConnection(facing, cableColor, distance, channels,
                    quadsOut);
                default -> {
                }
            }
        }

        for (Entry<EnumFacing, AECableType> connection : connectionTypes.entrySet()) {
            EnumFacing facing = connection.getKey();
            AECableType connectionType = connection.getValue();
            boolean cableBusAdjacent = renderState.getCableBusAdjacent().contains(facing);
            int channels = renderState.getChannelsOnSide().getOrDefault(facing, 0);

            switch (cableType) {
                case GLASS -> this.cableBuilder.addGlassConnection(facing, cableColor, connectionType,
                    cableBusAdjacent, quadsOut);
                case COVERED -> this.cableBuilder.addCoveredConnection(facing, cableColor, connectionType,
                    cableBusAdjacent, quadsOut);
                case SMART -> this.cableBuilder.addSmartConnection(facing, cableColor, connectionType,
                    cableBusAdjacent, channels, quadsOut);
                case DENSE_COVERED -> this.cableBuilder.addDenseCoveredConnection(facing, cableColor, connectionType,
                    cableBusAdjacent, quadsOut);
                case DENSE_SMART -> this.cableBuilder.addDenseSmartConnection(facing, cableColor, connectionType,
                    cableBusAdjacent, channels, quadsOut);
                default -> {
                }
            }
        }
    }

    public List<TextureAtlasSprite> getParticleTextures(CableBusRenderState renderState) {
        List<TextureAtlasSprite> result = new ObjectArrayList<>();
        CableCoreType coreType = renderState.getCoreType();
        if (coreType == null) {
            coreType = CableCoreType.fromCableType(renderState.getCableType());
        }
        if (coreType != null) {
            result.add(this.cableBuilder.getCoreTexture(coreType, renderState.getCableColor()));
        }

        for (IPartModel partModel : renderState.getAttachments().values()) {
            for (ResourceLocation modelId : partModel.getModels()) {
                IBakedModel bakedModel = this.partModels.get(modelId);
                if (bakedModel != null) {
                    TextureAtlasSprite particleTexture = bakedModel.getParticleTexture();
                    if (this.textureMap.getMissingSprite() != particleTexture) {
                        result.add(particleTexture);
                    }
                }
            }
        }

        for (FacadeRenderState facadeState : renderState.getFacades().values()) {
            IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher()
                                         .getModelForState(facadeState.sourceBlock());
            if (model != null) {
                TextureAtlasSprite particleTexture = model.getParticleTexture();
                if (this.textureMap.getMissingSprite() != particleTexture) {
                    result.add(particleTexture);
                }
            }
        }

        return result;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.particleTexture;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
