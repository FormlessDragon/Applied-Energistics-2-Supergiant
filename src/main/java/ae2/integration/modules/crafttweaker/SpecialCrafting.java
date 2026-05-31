package ae2.integration.modules.crafttweaker;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import net.minecraft.util.ResourceLocation;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.appliedenergistics2.SpecialCrafting")
public final class SpecialCrafting {
    private SpecialCrafting() {
    }

    @ZenMethod
    public static void remove(String registryName) {
        CraftTweakerAPI.apply(AE2CraftTweakerActions.removeForgeRecipe(new ResourceLocation(registryName),
            "Removing AE2 special crafting recipe"));
    }
}
