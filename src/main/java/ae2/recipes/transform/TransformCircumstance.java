package ae2.recipes.transform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.jetbrains.annotations.Nullable;

public class TransformCircumstance {
    private static final TransformCircumstance EXPLOSION = new TransformCircumstance("explosion", null);
    private static final TransformCircumstance WATER = new TransformCircumstance("fluid",
        new ResourceLocation("minecraft", "water"));
    private static final ResourceLocation WATER_TAG = new ResourceLocation("minecraft", "water");

    private final String type;
    private final ResourceLocation fluidId;

    private TransformCircumstance(String type, ResourceLocation fluidId) {
        this.type = type;
        this.fluidId = fluidId;
    }

    public static TransformCircumstance explosion() {
        return EXPLOSION;
    }

    public static TransformCircumstance water() {
        return WATER;
    }

    public static TransformCircumstance fluid(ResourceLocation fluidId) {
        return new TransformCircumstance("fluid", fluidId);
    }

    public static TransformCircumstance fromJson(JsonObject json) {
        if (json == null) {
            return water();
        }

        String type = JsonUtils.getString(json, "type", "fluid");
        if ("explosion".equals(type)) {
            return explosion();
        }
        if ("fluid".equals(type)) {
            if (json.has("fluid")) {
                return fluid(readResourceLocation(json, "fluid"));
            }
            if (json.has("tag")) {
                return fluid(readResourceLocation(json, "tag"));
            }
            return water();
        }
        throw new JsonParseException("Unknown transform circumstance: " + type);
    }

    private static ResourceLocation readResourceLocation(JsonObject json, String key) {
        String id = JsonUtils.getString(json, key);
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException e) {
            throw new JsonSyntaxException("Invalid transform circumstance " + key + ": " + id, e);
        }
    }

    public boolean isExplosion() {
        return "explosion".equals(this.type);
    }

    public boolean isFluid() {
        return "fluid".equals(this.type);
    }

    public boolean isFluid(Fluid fluid) {
        return this.isFluid()
            && fluid != null
            && fluid.getName() != null
            && new ResourceLocation(fluid.getName()).equals(this.fluidId);
    }

    public @Nullable Fluid getFluid() {
        return this.fluidId != null ? FluidRegistry.getFluid(this.fluidId.toString()) : null;
    }

    public @Nullable Fluid getFluidForRendering() {
        Fluid fluid = getFluid();
        if (fluid != null) {
            return fluid;
        }

        if (WATER_TAG.equals(this.fluidId)) {
            return FluidRegistry.WATER;
        }

        return null;
    }
}
