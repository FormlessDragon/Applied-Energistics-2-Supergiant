package ae2.recipes.transform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import net.minecraft.util.JsonUtils;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.jetbrains.annotations.Nullable;

public class TransformCircumstance {
    private static final TransformCircumstance EXPLOSION = new TransformCircumstance("explosion", null);
    private static final TransformCircumstance WATER = new TransformCircumstance("fluid", "water");

    private final String type;
    private final String fluidName;

    private TransformCircumstance(String type, String fluidName) {
        this.type = type;
        this.fluidName = fluidName;
    }

    public static TransformCircumstance explosion() {
        return EXPLOSION;
    }

    public static TransformCircumstance water() {
        return WATER;
    }

    public static TransformCircumstance fluid(String fluidName) {
        return new TransformCircumstance("fluid", validateFluidName(fluidName));
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
                return fluid(readFluidName(json, "fluid"));
            }
            if (json.has("tag")) {
                return fluid(readFluidName(json, "tag"));
            }
            return water();
        }
        throw new JsonParseException("Unknown transform circumstance: " + type);
    }

    private static String readFluidName(JsonObject json, String key) {
        return validateFluidName(JsonUtils.getString(json, key));
    }

    private static String validateFluidName(String fluidName) {
        if (fluidName.isEmpty()) {
            throw new JsonSyntaxException("Invalid transform circumstance fluid: " + fluidName);
        }
        if (FluidRegistry.getFluid(fluidName) == null) {
            throw new JsonSyntaxException("Unknown transform circumstance fluid: " + fluidName);
        }
        return fluidName;
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
            && fluid.getName().equals(this.fluidName);
    }

    public @Nullable Fluid getFluid() {
        return this.fluidName != null ? FluidRegistry.getFluid(this.fluidName) : null;
    }

    public @Nullable Fluid getFluidForRendering() {
        return getFluid();
    }
}
