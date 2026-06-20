package ae2.api.crafting.cpu;

import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable rendering contract for a crafting unit definition.
 * <p>
 * Unformed rendering remains owned by the blockstate/model resources of the block itself.
 * Formed rendering is driven by this definition.
 */
public final class CraftingUnitVisualDefinition {
    private final CraftingUnitVisualKind visualKind;
    private final ResourceLocation unformedModel;
    private final ResourceLocation formedModel;
    private final ResourceLocation ringCornerTexture;
    private final ResourceLocation ringSideHorTexture;
    private final ResourceLocation ringSideVerTexture;
    @Nullable
    private final ResourceLocation baseTexture;
    @Nullable
    private final ResourceLocation lightTexture;
    @Nullable
    private final ResourceLocation monitorBaseTexture;
    @Nullable
    private final ResourceLocation monitorLightDarkTexture;
    @Nullable
    private final ResourceLocation monitorLightMediumTexture;
    @Nullable
    private final ResourceLocation monitorLightBrightTexture;
    @Nullable
    private final ResourceLocation formedModelProviderId;

    private CraftingUnitVisualDefinition(Builder builder) {
        this.visualKind = Objects.requireNonNull(builder.visualKind, "visualKind");
        this.unformedModel = Objects.requireNonNull(builder.unformedModel, "unformedModel");
        this.formedModel = Objects.requireNonNull(builder.formedModel, "formedModel");
        this.ringCornerTexture = Objects.requireNonNull(builder.ringCornerTexture, "ringCornerTexture");
        this.ringSideHorTexture = Objects.requireNonNull(builder.ringSideHorTexture, "ringSideHorTexture");
        this.ringSideVerTexture = Objects.requireNonNull(builder.ringSideVerTexture, "ringSideVerTexture");
        this.baseTexture = builder.baseTexture;
        this.lightTexture = builder.lightTexture;
        this.monitorBaseTexture = builder.monitorBaseTexture;
        this.monitorLightDarkTexture = builder.monitorLightDarkTexture;
        this.monitorLightMediumTexture = builder.monitorLightMediumTexture;
        this.monitorLightBrightTexture = builder.monitorLightBrightTexture;
        this.formedModelProviderId = builder.formedModelProviderId;
    }

    public static Builder builder(CraftingUnitVisualKind visualKind,
                                  ResourceLocation unformedModel,
                                  ResourceLocation formedModel) {
        return new Builder(visualKind, unformedModel, formedModel);
    }

    public CraftingUnitVisualKind visualKind() {
        return this.visualKind;
    }

    public ResourceLocation unformedModel() {
        return this.unformedModel;
    }

    public ResourceLocation formedModel() {
        return this.formedModel;
    }

    public ResourceLocation ringCornerTexture() {
        return this.ringCornerTexture;
    }

    public ResourceLocation ringSideHorTexture() {
        return this.ringSideHorTexture;
    }

    public ResourceLocation ringSideVerTexture() {
        return this.ringSideVerTexture;
    }

    @Nullable
    public ResourceLocation baseTexture() {
        return this.baseTexture;
    }

    @Nullable
    public ResourceLocation lightTexture() {
        return this.lightTexture;
    }

    @Nullable
    public ResourceLocation monitorBaseTexture() {
        return this.monitorBaseTexture;
    }

    @Nullable
    public ResourceLocation monitorLightDarkTexture() {
        return this.monitorLightDarkTexture;
    }

    @Nullable
    public ResourceLocation monitorLightMediumTexture() {
        return this.monitorLightMediumTexture;
    }

    @Nullable
    public ResourceLocation monitorLightBrightTexture() {
        return this.monitorLightBrightTexture;
    }

    @Nullable
    public ResourceLocation formedModelProviderId() {
        return this.formedModelProviderId;
    }

    public static final class Builder {
        private final CraftingUnitVisualKind visualKind;
        private final ResourceLocation unformedModel;
        private final ResourceLocation formedModel;
        private ResourceLocation ringCornerTexture;
        private ResourceLocation ringSideHorTexture;
        private ResourceLocation ringSideVerTexture;
        private ResourceLocation baseTexture;
        private ResourceLocation lightTexture;
        private ResourceLocation monitorBaseTexture;
        private ResourceLocation monitorLightDarkTexture;
        private ResourceLocation monitorLightMediumTexture;
        private ResourceLocation monitorLightBrightTexture;
        private ResourceLocation formedModelProviderId;

        private Builder(CraftingUnitVisualKind visualKind,
                        ResourceLocation unformedModel,
                        ResourceLocation formedModel) {
            this.visualKind = visualKind;
            this.unformedModel = unformedModel;
            this.formedModel = formedModel;
        }

        public Builder ringTextures(ResourceLocation ringCornerTexture,
                                    ResourceLocation ringSideHorTexture,
                                    ResourceLocation ringSideVerTexture) {
            this.ringCornerTexture = ringCornerTexture;
            this.ringSideHorTexture = ringSideHorTexture;
            this.ringSideVerTexture = ringSideVerTexture;
            return this;
        }

        public Builder baseTexture(ResourceLocation baseTexture) {
            this.baseTexture = baseTexture;
            return this;
        }

        public Builder lightTexture(ResourceLocation lightTexture) {
            this.lightTexture = lightTexture;
            return this;
        }

        public Builder monitorTextures(ResourceLocation monitorBaseTexture,
                                       ResourceLocation monitorLightDarkTexture,
                                       ResourceLocation monitorLightMediumTexture,
                                       ResourceLocation monitorLightBrightTexture) {
            this.monitorBaseTexture = monitorBaseTexture;
            this.monitorLightDarkTexture = monitorLightDarkTexture;
            this.monitorLightMediumTexture = monitorLightMediumTexture;
            this.monitorLightBrightTexture = monitorLightBrightTexture;
            return this;
        }

        public Builder formedModelProviderId(@Nullable ResourceLocation formedModelProviderId) {
            this.formedModelProviderId = formedModelProviderId;
            return this;
        }

        public CraftingUnitVisualDefinition build() {
            return new CraftingUnitVisualDefinition(this);
        }
    }
}
