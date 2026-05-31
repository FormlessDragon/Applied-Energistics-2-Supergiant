package ae2.integration.modules.crafttweaker;

import ae2.recipes.game.StorageCellDisassemblyRecipe;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.List;

@ZenRegister
@ZenClass("mods.appliedenergistics2.StorageCellDisassembly")
public final class StorageCellDisassembly {
    private StorageCellDisassembly() {
    }

    @ZenMethod
    public static void addRecipe(IItemStack cell, IItemStack[] drops) {
        List<ItemStack> stacks = new ObjectArrayList<>();
        for (IItemStack drop : drops) {
            stacks.add(CraftTweakerMC.getItemStack(drop));
        }
        register(cell, stacks, "Adding AE2 storage cell disassembly recipe for " + cell);
    }

    @ZenMethod
    public static void remove(IItemStack cell) {
        register(cell, List.of(), "Removing AE2 storage cell disassembly recipe for " + cell);
    }

    private static void register(IItemStack cell, List<ItemStack> drops, String description) {
        var recipe = new StorageCellDisassemblyRecipe(CraftTweakerMC.getItemStack(cell).getItem(), drops);
        CraftTweakerAPI.apply(new IAction() {
            @Override
            public void apply() {
                StorageCellDisassemblyRecipe.register(recipe);
            }

            @Override
            public String describe() {
                return description;
            }
        });
    }
}
