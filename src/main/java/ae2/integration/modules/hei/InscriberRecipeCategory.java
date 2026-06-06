package ae2.integration.modules.hei;

import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

class InscriberRecipeCategory implements IRecipeCategory<InscriberRecipeWrapper> {
    static final String UID = "ae2.inscriber";
    private static final int PADDING = 5;
    private static final int WIDTH = 105;
    private static final int HEIGHT = 54;

    private final IDrawable background;
    private final IDrawableAnimated progress;

    InscriberRecipeCategory(IGuiHelper guiHelper) {
        this.background = new HeiBackgroundDrawable(WIDTH + PADDING * 2, HEIGHT + PADDING * 2);
        ResourceLocation location = new ResourceLocation(AppEng.MOD_ID, "textures/guis/inscriber.png");
        IDrawableStatic progressDrawable = guiHelper.createDrawable(location, 177, 0, 6, 18);
        this.progress = new AnimatedProgressDrawable(progressDrawable, 6, 18);
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return AEBlocks.INSCRIBER.stack().getDisplayName();
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
        minecraft.getTextureManager().bindTexture(new ResourceLocation(AppEng.MOD_ID, "textures/guis/inscriber.png"));
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(PADDING, PADDING, 36, 20, WIDTH, HEIGHT, 256, 256);
        this.progress.draw(minecraft, PADDING + 100, PADDING + 19);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @Nonnull InscriberRecipeWrapper recipeWrapper,
                          @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, PADDING + 2, PADDING + 2);
        itemStacks.init(1, true, PADDING + 26, PADDING + 18);
        itemStacks.init(2, true, PADDING + 2, PADDING + 34);
        itemStacks.init(3, false, PADDING + 76, PADDING + 19);
        itemStacks.set(ingredients);
    }
}
