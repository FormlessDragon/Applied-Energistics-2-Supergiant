package ae2.integration.modules.hei;

import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.List;

class CrystalFixerRecipeCategory implements IRecipeCategory<CrystalFixerRecipeWrapper> {
    static final String UID = "ae2.crystal_fixer";
    private static final int WIDTH = 114;
    private static final int HEIGHT = 63;

    private final IDrawable background;

    CrystalFixerRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(new ResourceLocation(AppEng.MOD_ID,
            "textures/guis/crystal_fixer.png"), 0, 0, WIDTH, HEIGHT);
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return AEBlocks.CRYSTAL_FIXER.stack().getDisplayName();
    }

    @Override
    public String getModName() {
        return AppEng.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        GlStateManager.color(1, 1, 1, 1);
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(AEBlocks.CRYSTAL_FIXER.stack(), 49, 32);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @NotNull CrystalFixerRecipeWrapper recipeWrapper,
                          @NotNull IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, 0, 18);
        itemStacks.init(1, true, 48, 11);
        itemStacks.init(2, false, 96, 18);
        itemStacks.set(0, recipeWrapper.getInput());
        List<List<ItemStack>> fuelInputs = recipeWrapper.getFuelInputs();
        if (!fuelInputs.isEmpty()) {
            itemStacks.set(1, fuelInputs.getFirst());
        }
        itemStacks.set(2, recipeWrapper.getOutput());
    }
}
