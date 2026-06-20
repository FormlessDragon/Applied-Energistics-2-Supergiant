package ae2.init.client;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import ae2.api.orientation.BlockOrientation;
import ae2.client.render.cablebus.CableBusModel;
import ae2.client.render.crafting.CraftingCubeModel;
import ae2.client.render.model.AutoRotatingModel;
import ae2.client.render.model.DriveModel;
import ae2.client.render.model.FixedOrientationModel;
import ae2.client.render.model.GlassModel;
import ae2.client.render.tesr.spatial.SpatialPylonModel;
import ae2.core.Tags;
import ae2.core.registries.CraftingUnitRegistry;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class BuiltInModelOverride {
    private static final Map<String, BlockOrientation> FULL_ORIENT_MODEL_BASES = Map.of(
        "cell_workbench", BlockOrientation.NORTH_UP,
        "inscriber", BlockOrientation.NORTH_UP,
        "io_port", BlockOrientation.NORTH_UP,
        "spatial_anchor", BlockOrientation.NORTH_UP,
        "spatial_io_port", BlockOrientation.NORTH_UP,
        "vibration_chamber", BlockOrientation.SOUTH_UP
    );

    private BuiltInModelOverride() {
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        IRegistry<ModelResourceLocation, IBakedModel> registry = event.getModelRegistry();
        for (Map.Entry<ModelResourceLocation, IBakedModel> entry : bakeModels().entrySet()) {
            registry.putObject(entry.getKey(), entry.getValue());
        }
        fixFullOrientationVariants(registry);
        wrapChestVariants(registry);
    }

    private static Map<ModelResourceLocation, IBakedModel> bakeModels() {
        Map<ModelResourceLocation, IBakedModel> bakedModels = new Object2ObjectLinkedOpenHashMap<>();
        Function<ResourceLocation, TextureAtlasSprite> textureGetter = ModelLoader.defaultTextureGetter();

        IBakedModel cableBusModel = new CableBusModel().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
            textureGetter);
        bakedModels.put(location("builtin/cable_bus", "normal"), cableBusModel);

        IModel driveModel = new DriveModel();
        putDriveVariants(bakedModels, driveModel, textureGetter);

        IBakedModel glassModel = new GlassModel().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, textureGetter);
        bakedModels.put(location("builtin/quartz_glass", "normal"), glassModel);
        bakedModels.put(location("quartz_glass", "normal"), glassModel);
        bakedModels.put(location("quartz_vibrant_glass", "normal"), glassModel);

        putChargerVariants(bakedModels, textureGetter);

        IModel spatialPylonModel = new SpatialPylonModel();
        IBakedModel spatialPylon = spatialPylonModel.bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
            textureGetter);
        bakedModels.put(location("blocks/spatial_pylon/builtin", "powered_on=false"), spatialPylon);
        bakedModels.put(location("blocks/spatial_pylon/builtin", "powered_on=true"), spatialPylon);
        bakedModels.put(location("block/spatial_pylon", "powered_on=false"), spatialPylon);
        bakedModels.put(location("block/spatial_pylon", "powered_on=true"), spatialPylon);
        bakedModels.put(location("spatial_pylon", "powered_on=false"), spatialPylon);
        bakedModels.put(location("spatial_pylon", "powered_on=true"), spatialPylon);

        for (ICraftingUnitDefinition definition : CraftingUnitRegistry.getInstance().getDefinitions()) {
            putCraftingModel(bakedModels, definition, textureGetter);
        }
        return bakedModels;
    }

    private static void wrapChestVariants(IRegistry<ModelResourceLocation, IBakedModel> registry) {
        for (ModelResourceLocation location : new ObjectOpenHashSet<>(registry.getKeys())) {
            if (Tags.MOD_ID.equals(location.getNamespace()) && "chest".equals(location.getPath())) {
                IBakedModel model = registry.getObject(location);
                if (model != null && !(model instanceof AutoRotatingModel)) {
                    registry.putObject(location, new AutoRotatingModel(model));
                }
            }
        }
    }

    private static void fixFullOrientationVariants(IRegistry<ModelResourceLocation, IBakedModel> registry) {
        for (ModelResourceLocation location : new ObjectOpenHashSet<>(registry.getKeys())) {
            if (!Tags.MOD_ID.equals(location.getNamespace())) {
                continue;
            }

            BlockOrientation baseOrientation = FULL_ORIENT_MODEL_BASES.get(location.getPath());
            if (baseOrientation == null) {
                continue;
            }

            String variantText = location.getVariant();
            if ("inventory".equals(variantText) || "normal".equals(variantText)) {
                continue;
            }

            FullBlockVariant variant;
            try {
                variant = FullBlockVariant.parse(variantText);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ModelResourceLocation baseLocation = location(location.getPath(), variant.withOrientation(baseOrientation));
            IBakedModel baseModel = registry.getObject(baseLocation);
            if (baseModel == null) {
                continue;
            }

            BlockOrientation rotation = variant.rotationFrom(baseOrientation);
            if (rotation.isRedundant()) {
                continue;
            }

            registry.putObject(location, new FixedOrientationModel(baseModel, rotation));
        }
    }

    private static void putCraftingModel(Map<ModelResourceLocation, IBakedModel> bakedModels,
                                         ICraftingUnitDefinition definition,
                                         Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        String path = definition.getVisualDefinition().formedModel().getPath();
        String shortPath = path.startsWith("block/") ? path.substring("block/".length()) : path;
        IBakedModel model = new CraftingCubeModel(definition.getVisualDefinition())
            .bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, textureGetter);
        bakedModels.put(location(path, "formed=true,powered=false"), model);
        bakedModels.put(location(path, "formed=true,powered=true"), model);
        bakedModels.put(location(shortPath, "formed=true,powered=false"), model);
        bakedModels.put(location(shortPath, "formed=true,powered=true"), model);
    }

    private static void putDriveVariants(Map<ModelResourceLocation, IBakedModel> bakedModels, IModel driveModel,
                                         Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        for (EnumFacing facing : EnumFacing.values()) {
            for (int spin = 0; spin < 4; spin++) {
                BlockOrientation orientation = BlockOrientation.get(facing, spin);
                IBakedModel bakedModel = driveModel.bake(createBlockStateTransform(orientation),
                    DefaultVertexFormats.BLOCK, textureGetter);
                String variant = "facing=" + facing.getName() + ",spin=" + spin;
                bakedModels.put(location("builtin/drive", variant), bakedModel);
                bakedModels.put(location("drive", variant), bakedModel);
            }
        }
    }

    private static void putChargerVariants(Map<ModelResourceLocation, IBakedModel> bakedModels,
                                           Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        ResourceLocation modelLocation = new ResourceLocation(Tags.MOD_ID, "block/charger");
        IModel model = ModelLoaderRegistry.getModelOrLogError(modelLocation, "Missing model: " + modelLocation);
        if (model == null) {
            return;
        }

        for (EnumFacing facing : EnumFacing.values()) {
            for (int spin = 0; spin < 4; spin++) {
                BlockOrientation orientation = BlockOrientation.get(facing, spin);
                IBakedModel bakedModel = model.bake(createBlockStateTransform(orientation),
                    DefaultVertexFormats.BLOCK, textureGetter);
                bakedModels.put(location("charger", "facing=" + facing.getName() + ",spin=" + spin), bakedModel);
            }
        }
    }

    private static TRSRTransformation createBlockStateTransform(BlockOrientation orientation) {
        if (orientation.isRedundant()) {
            return TRSRTransformation.identity();
        }
        return TRSRTransformation.blockCenterToCorner(orientation.getTransformation());
    }

    private static ModelResourceLocation location(String path, String variant) {
        return new ModelResourceLocation(new ResourceLocation(Tags.MOD_ID, path), variant);
    }
}
