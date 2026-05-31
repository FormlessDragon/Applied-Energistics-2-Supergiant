/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.render.model;

import ae2.client.render.VertexFormats;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import it.unimi.dsi.fastutil.floats.FloatFloatPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.BlockPartRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.EnumHelperClient;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.animation.ModelBlockAnimation;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.ITransformation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.NonNull;
import org.lwjgl.util.vector.Vector3f;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.Function;

@SuppressWarnings("deprecation")
public enum UVLModelLoader implements ICustomModelLoader {
    INSTANCE;

    private static final Gson GSON = new Gson();
    private static final Constructor<?> VANILLA_MODEL_WRAPPER;
    private static final Field FACE_BAKERY_FIELD;
    private static final Object VANILLA_LOADER;
    private static final MethodHandle LOADER_GETTER;

    static {
        try {
            FACE_BAKERY_FIELD = ReflectionHelper.findField(ModelBakery.class, "faceBakery", "field_177607_l");

            Class<?> wrapperClass = Class.forName(ModelLoader.class.getName() + "$VanillaModelWrapper");
            VANILLA_MODEL_WRAPPER = wrapperClass.getDeclaredConstructor(ModelLoader.class, ResourceLocation.class,
                ModelBlock.class, boolean.class, ModelBlockAnimation.class);
            VANILLA_MODEL_WRAPPER.setAccessible(true);

            Class<?> vanillaLoaderClass = Class.forName(ModelLoader.class.getName() + "$VanillaLoader");
            Field instanceField = vanillaLoaderClass.getField("INSTANCE");
            VANILLA_LOADER = instanceField.get(null);
            Field loaderField = vanillaLoaderClass.getDeclaredField("loader");
            loaderField.setAccessible(true);
            LOADER_GETTER = MethodHandles.lookup().unreflectGetter(loaderField);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private IResourceManager resourceManager;

    private static Object deserializer(Class<?> clazz) {
        try {
            Class<?> deserializerClass = Class.forName(clazz.getName() + "$Deserializer");
            Constructor<?> constructor = deserializerClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <M extends IModel> M vanillaModelWrapper(ModelLoader loader, ResourceLocation location,
                                                            ModelBlock model, ModelBlockAnimation animation) {
        try {
            return (M) VANILLA_MODEL_WRAPPER.newInstance(loader, location, model, false, animation);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static void setFaceBakery(ModelBakery modelBakery, FaceBakery faceBakery) {
        try {
            EnumHelperClient.setFailsafeFieldValue(FACE_BAKERY_FIELD, modelBakery, faceBakery);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public ModelLoader getLoader() {
        try {
            return (ModelLoader) LOADER_GETTER.invoke(VANILLA_LOADER);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void onResourceManagerReload(@NonNull IResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        String modelPath = modelLocation.getPath();
        if (modelPath.startsWith("models/")) {
            modelPath = modelPath.substring("models/".length());
        }

        try (InputStreamReader reader = new InputStreamReader(Minecraft.getMinecraft().getResourceManager()
                                                                       .getResource(new ResourceLocation(modelLocation.getNamespace(), "models/" + modelPath + ".json"))
                                                                       .getInputStream())) {
            UVLMarker marker = GSON.fromJson(reader, UVLMarker.class);
            return marker != null && marker.ae2_uvl_marker;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public IModel loadModel(@NonNull ResourceLocation modelLocation) {
        return new UVLModelWrapper(modelLocation);
    }

    private static final class UVLMarker {
        boolean ae2_uvl_marker;
    }

    public class UVLModelWrapper implements IModel {

        private final Object2ObjectMap<BlockPartFace, FloatFloatPair> uvlightmap = new Object2ObjectOpenHashMap<>();
        private final IModel parent;

        public UVLModelWrapper(ResourceLocation modelLocation) {
            String modelPath = modelLocation.getPath();
            if (modelPath.startsWith("models/")) {
                modelPath = modelPath.substring("models/".length());
            }

            ResourceLocation armatureLocation = new ResourceLocation(modelLocation.getNamespace(),
                "armatures/" + modelPath + ".json");
            ModelBlockAnimation animation = ModelBlockAnimation.loadVanillaAnimation(UVLModelLoader.this.resourceManager,
                armatureLocation);

            Reader reader = null;
            IResource resource = null;
            ModelBlock model;
            try {
                resource = Minecraft.getMinecraft().getResourceManager()
                                    .getResource(new ResourceLocation(modelLocation.getNamespace(), "models/" + modelPath + ".json"));
                reader = new InputStreamReader(resource.getInputStream(), Charsets.UTF_8);
                Gson serializer = new GsonBuilder()
                    .registerTypeAdapter(ModelBlock.class, deserializer(ModelBlock.class))
                    .registerTypeAdapter(BlockPart.class, deserializer(BlockPart.class))
                    .registerTypeAdapter(BlockPartFace.class, new BlockPartFaceOverrideSerializer())
                    .registerTypeAdapter(BlockFaceUV.class, deserializer(BlockFaceUV.class))
                    .registerTypeAdapter(ItemTransformVec3f.class, deserializer(ItemTransformVec3f.class))
                    .registerTypeAdapter(ItemCameraTransforms.class, deserializer(ItemCameraTransforms.class))
                    .registerTypeAdapter(ItemOverride.class, deserializer(ItemOverride.class))
                    .create();
                model = JsonUtils.gsonDeserialize(serializer, reader, ModelBlock.class, false);
                if (model != null) {
                    model.name = modelLocation.toString();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(reader);
                IOUtils.closeQuietly(resource);
            }

            this.parent = vanillaModelWrapper(UVLModelLoader.this.getLoader(), modelLocation, model, animation);
        }

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return this.parent.getDependencies();
        }

        @Override
        public Collection<ResourceLocation> getTextures() {
            return this.parent.getTextures();
        }

        @Override
        public IBakedModel bake(@NonNull IModelState state, @NonNull VertexFormat format,
                                @NonNull Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
            setFaceBakery(UVLModelLoader.this.getLoader(), new FaceBakeryOverride());
            IBakedModel model = this.parent.bake(state, format, bakedTextureGetter);
            setFaceBakery(UVLModelLoader.this.getLoader(), new FaceBakery());
            return model;
        }

        @Override
        public IModelState getDefaultState() {
            return this.parent.getDefaultState();
        }

        private final class BlockPartFaceOverrideSerializer implements JsonDeserializer<BlockPartFace> {
            @Override
            public BlockPartFace deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
                JsonObject object = json.getAsJsonObject();
                EnumFacing cullFace = parseCullFace(object);
                int tintIndex = parseTintIndex(object);
                String texture = parseTexture(object);
                BlockFaceUV blockFaceUv = context.deserialize(object, BlockFaceUV.class);
                BlockPartFace blockFace = new BlockPartFace(cullFace, tintIndex, texture, blockFaceUv);
                UVLModelWrapper.this.uvlightmap.put(blockFace, parseUVL(object));
                return blockFace;
            }

            private int parseTintIndex(JsonObject object) {
                return JsonUtils.getInt(object, "tintindex", -1);
            }

            private String parseTexture(JsonObject object) {
                return JsonUtils.getString(object, "texture");
            }

            @Nullable
            private EnumFacing parseCullFace(JsonObject object) {
                return EnumFacing.byName(JsonUtils.getString(object, "cullface", ""));
            }

            @Nullable
            private FloatFloatPair parseUVL(JsonObject object) {
                if (!object.has("uvlightmap")) {
                    return null;
                }
                JsonObject uvLightmap = object.get("uvlightmap").getAsJsonObject();
                return FloatFloatPair.of(JsonUtils.getFloat(uvLightmap, "sky", 0),
                    JsonUtils.getFloat(uvLightmap, "block", 0));
            }
        }

        private final class FaceBakeryOverride extends FaceBakery {
            @Override
            public BakedQuad makeBakedQuad(Vector3f posFrom, Vector3f posTo, BlockPartFace face,
                                           TextureAtlasSprite sprite, EnumFacing facing, ITransformation modelRotationIn,
                                           BlockPartRotation partRotation, boolean uvLocked, boolean shade) {
                BakedQuad quad = super.makeBakedQuad(posFrom, posTo, face, sprite, facing, modelRotationIn,
                    partRotation, uvLocked, shade);

                FloatFloatPair brightness = UVLModelWrapper.this.uvlightmap.get(face);
                if (brightness == null) {
                    return quad;
                }

                VertexFormat newFormat = VertexFormats.getFormatWithLightMap(quad.getFormat());
                UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(newFormat);
                final VertexLighterFlat transformer = new VertexLighterFlat(Minecraft.getMinecraft().getBlockColors()) {
                    @Override
                    protected void updateLightmap(float @NonNull [] normal, float[] lightmap, float x, float y, float z) {
                        lightmap[0] = brightness.rightFloat();
                        lightmap[1] = brightness.leftFloat();
                    }

                    @Override
                    public void setQuadTint(int tint) {
                    }
                };
                transformer.setParent(builder);
                quad.pipe(transformer);
                builder.setQuadTint(quad.getTintIndex());
                builder.setQuadOrientation(quad.getFace());
                builder.setTexture(quad.getSprite());
                builder.setApplyDiffuseLighting(false);
                return builder.build();
            }
        }
    }
}
