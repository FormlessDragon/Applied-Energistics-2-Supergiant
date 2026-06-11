package ae2.recipes;

import ae2.core.AppEng;
import ae2.recipes.entropy.EntropyRecipeSerializer;
import ae2.recipes.game.CraftingUnitTransformRecipeSerializer;
import ae2.recipes.game.StorageCellDisassemblyRecipeSerializer;
import ae2.recipes.handlers.ChargerRecipeSerializer;
import ae2.recipes.handlers.CrystalAssemblerRecipeSerializer;
import ae2.recipes.handlers.CrystalFixerRecipeSerializer;
import ae2.recipes.handlers.InscriberRecipeSerializer;
import ae2.recipes.mattercannon.MatterCannonAmmoSerializer;
import ae2.recipes.serializers.JsonRecipeUtils;
import ae2.recipes.transform.TransformRecipeSerializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.Map;

public final class AERecipeSerializers {
    private static final Map<ResourceLocation, IAERecipeFactory> FACTORIES = new Object2ObjectOpenHashMap<>();

    static {
        register(AppEng.makeId("charger"), new ChargerRecipeSerializer());
        register(AppEng.makeId("inscriber"), new InscriberRecipeSerializer());
        register(AppEng.makeId("crystal_assembler"), new CrystalAssemblerRecipeSerializer());
        register(AppEng.makeId("crystal_fixer"), new CrystalFixerRecipeSerializer());
        register(AppEng.makeId("matter_cannon"), new MatterCannonAmmoSerializer());
        register(AppEng.makeId("transform"), new TransformRecipeSerializer());
        register(AppEng.makeId("cell_disassembly"), new StorageCellDisassemblyRecipeSerializer());
        register(AppEng.makeId("storage_cell_disassembly"), new StorageCellDisassemblyRecipeSerializer());
        register(AppEng.makeId("crafting_unit_transform"), new CraftingUnitTransformRecipeSerializer());
        register(AppEng.makeId("entropy"), new EntropyRecipeSerializer());
    }

    private AERecipeSerializers() {
    }

    public static void register(ResourceLocation id, IAERecipeFactory factory) {
        FACTORIES.put(id, factory);
    }

    public static void register(JsonObject json, JsonContext ctx) {
        if (!JsonRecipeUtils.shouldLoad(json)) {
            return;
        }

        String type = ctx.appendModId(JsonUtils.getString(json, "type"));
        IAERecipeFactory factory = FACTORIES.get(readType(type));
        if (factory == null) {
            return;
        }

        factory.register(json, ctx);
    }

    private static ResourceLocation readType(String type) {
        try {
            return new ResourceLocation(type);
        } catch (RuntimeException e) {
            throw new JsonSyntaxException("Invalid AE2 recipe type: " + type, e);
        }
    }
}
