package ae2.integration.modules.hei;

import ae2.client.gui.Icon;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
class CondenserCategory implements IRecipeCategory<CondenserOutputWrapper> {
    static final String UID = "ae2.condenser";
    private static final int PADDING = 7;
    private static final int WIDTH = 96;
    private static final int HEIGHT = 48;

    private final IDrawable background;
    private final IDrawable iconTrash;
    private final IDrawableAnimated progress;
    private final IDrawable iconButton;

    CondenserCategory(IGuiHelper guiHelper) {
        this.background = new HeiBackgroundDrawable(WIDTH + PADDING * 2, HEIGHT + PADDING * 2);
        ResourceLocation location = new ResourceLocation(AppEng.MOD_ID, "textures/guis/condenser.png");
        this.iconTrash = new IconDrawable(Icon.CONDENSER_OUTPUT_TRASH);
        this.iconButton = new IconDrawable(Icon.TOOLBAR_BUTTON_BACKGROUND);
        IDrawableStatic progressDrawable = guiHelper.createDrawable(location, 178, 25, 6, 18);
        this.progress = new AnimatedProgressDrawable(progressDrawable, 6, 18);
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return GuiText.Condenser.getLocal();
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
    public void drawExtras(Minecraft minecraft) {
        minecraft.getTextureManager().bindTexture(new ResourceLocation(AppEng.MOD_ID, "textures/guis/condenser.png"));
        Gui.drawModalRectWithCustomSizedTexture(PADDING, PADDING, 48, 25, WIDTH, HEIGHT, 256, 256);
        this.progress.draw(minecraft, PADDING + 72, PADDING);
        this.iconTrash.draw(minecraft, PADDING + 3, PADDING + 27);
        this.iconButton.draw(minecraft, PADDING + 80, PADDING + 28);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, CondenserOutputWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, false, PADDING + 56, PADDING + 26);
        itemStacks.init(1, true, PADDING + 52, PADDING);
        itemStacks.set(ingredients);
    }
}
