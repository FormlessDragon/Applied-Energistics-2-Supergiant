package ae2.integration.modules.hei;

import ae2.core.AppEng;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.List;

class CrystalAssemblerRecipeCategory implements IRecipeCategory<CrystalAssemblerRecipeWrapper> {
    static final String UID = "ae2.crystal_assembler";
    private static final int PADDING = 5;
    private static final int WIDTH = 135;
    private static final int HEIGHT = 58;

    private final IDrawable background;
    private final IDrawableAnimated progress;

    CrystalAssemblerRecipeCategory(IGuiHelper guiHelper) {
        this.background = new HeiBackgroundDrawable(WIDTH + PADDING * 2, HEIGHT + PADDING * 2);
        ResourceLocation location = new ResourceLocation(AppEng.MOD_ID, "textures/guis/crystal_assembler.png");
        IDrawableStatic progressDrawable = guiHelper.createDrawable(location, 176, 0, 6, 18);
        this.progress = new AnimatedProgressDrawable(progressDrawable, 6, 18);
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return I18n.format("tile.ae2.crystal_assembler.name");
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
    public void drawExtras(@Nonnull Minecraft minecraft) {
        minecraft.getTextureManager().bindTexture(new ResourceLocation(AppEng.MOD_ID,
            "textures/guis/crystal_assembler.png"));
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(PADDING, PADDING, 23, 19, WIDTH, HEIGHT,
            256, 256);
        this.progress.draw(minecraft, PADDING + 129, PADDING + 20);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @Nonnull CrystalAssemblerRecipeWrapper recipeWrapper,
                          @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        int slot = 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                itemStacks.init(slot++, true, PADDING + 2 + x * 18, PADDING + 2 + y * 18);
            }
        }
        itemStacks.init(9, false, PADDING + 106, PADDING + 20);
        itemStacks.set(ingredients);
        List<List<ItemStack>> inputs = recipeWrapper.getItemInputs();
        for (int i = 0; i < inputs.size() && i < 9; i++) {
            itemStacks.set(i, inputs.get(i));
        }
        itemStacks.set(9, recipeWrapper.getOutput());

        IGuiFluidStackGroup fluids = recipeLayout.getFluidStacks();
        fluids.init(0, true, PADDING + 58, PADDING + 39, 16, 16, 16000, false, null);
        fluids.set(ingredients);
    }
}
