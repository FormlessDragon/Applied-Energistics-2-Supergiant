package ae2.core.registries;

import ae2.api.client.crafting.ICraftingUnitClientRegistry;
import ae2.api.client.crafting.ICraftingUnitModelProvider;
import ae2.api.crafting.cpu.CraftingUnitVisualKind;
import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import ae2.client.render.crafting.CraftingCubeModel;
import ae2.core.AELog;
import ae2.core.AppEng;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class CraftingUnitClientRegistry implements ICraftingUnitClientRegistry {
    public static final ResourceLocation DEFAULT_CUBE_PROVIDER_ID = AppEng.makeId("crafting_cube");

    private static final CraftingUnitClientRegistry INSTANCE = new CraftingUnitClientRegistry();

    private final Map<ResourceLocation, ICraftingUnitModelProvider> providers = new Object2ObjectLinkedOpenHashMap<>();
    private boolean initialized;

    private CraftingUnitClientRegistry() {
    }

    public static CraftingUnitClientRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void initBuiltins() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.registerModelProvider(DEFAULT_CUBE_PROVIDER_ID,
            (definition, format, textureGetter) -> new CraftingCubeModel(definition.getVisualDefinition()).bake(
                ModelRotation.X0_Y0, format, textureGetter));
    }

    @Override
    public synchronized void registerModelProvider(ResourceLocation id, ICraftingUnitModelProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        ICraftingUnitModelProvider previous = this.providers.putIfAbsent(id, provider);
        if (previous != null && previous != provider) {
            AELog.warn("Ignoring duplicate crafting unit model provider registration for %s", id);
            throw new IllegalStateException("Duplicate crafting unit model provider: " + id);
        }
    }

    @Override
    public synchronized @Nullable ICraftingUnitModelProvider getModelProvider(ResourceLocation id) {
        return this.providers.get(id);
    }

    @Override
    public synchronized IBakedModel createFormedModel(ICraftingUnitDefinition definition,
                                                      VertexFormat format,
                                                      Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        var visual = definition.getVisualDefinition();
        ResourceLocation providerId = visual.formedModelProviderId();
        if (providerId != null) {
            ICraftingUnitModelProvider provider = this.providers.get(providerId);
            if (provider == null) {
                throw new IllegalStateException("Missing crafting unit model provider: " + providerId);
            }
            return provider.createFormedModel(definition, format, textureGetter);
        }
        if (visual.visualKind() == CraftingUnitVisualKind.CUSTOM) {
            throw new IllegalStateException("Custom crafting unit definition requires a formed model provider: " + definition.id());
        }
        ICraftingUnitModelProvider provider = Objects.requireNonNull(this.providers.get(DEFAULT_CUBE_PROVIDER_ID),
            "Default crafting unit model provider not initialized");
        return provider.createFormedModel(definition, format, textureGetter);
    }
}
