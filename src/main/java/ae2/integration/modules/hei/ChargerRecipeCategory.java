package ae2.integration.modules.hei;

import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import ae2.core.localization.HeiText;
import ae2.tile.misc.TileCharger;
import ae2.tile.misc.TileCrank;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
class ChargerRecipeCategory implements IRecipeCategory<ChargerRecipeWrapper> {
    static final String UID = "ae2.charger";
    private static final int WIDTH = 130;
    private static final int HEIGHT = 50;
    private static final int POWER_TEXT_X = 20;
    private static final int POWER_TEXT_Y = 36;
    private static final int POWER_TEXT_MAX_WIDTH = WIDTH - POWER_TEXT_X - 4;

    private final IDrawable background;
    private final IDrawable slotDrawable;
    private final IDrawable arrow;

    ChargerRecipeCategory(IGuiHelper guiHelper) {
        ResourceLocation location = new ResourceLocation(AppEng.MOD_ID, "textures/guis/jei.png");
        this.background = new HeiBackgroundDrawable(WIDTH, HEIGHT);
        this.slotDrawable = guiHelper.createDrawable(location, 0, 34, 18, 18);
        this.arrow = guiHelper.createDrawable(location, 0, 0, 24, 17);
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return AEBlocks.CHARGER.stack().getDisplayName();
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
        this.arrow.draw(minecraft, 50, 8);
        int turns = (TileCharger.POWER_MAXIMUM_AMOUNT + TileCrank.POWER_PER_CRANK_TURN - 1)
            / TileCrank.POWER_PER_CRANK_TURN;
        String text = HeiText.ChargerRequiredPower.getLocal(turns, TileCharger.POWER_MAXIMUM_AMOUNT);
        minecraft.fontRenderer.drawString(minecraft.fontRenderer.trimStringToWidth(text, POWER_TEXT_MAX_WIDTH),
            POWER_TEXT_X, POWER_TEXT_Y, 0x7E7E7E);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, ChargerRecipeWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, 31, 8);
        itemStacks.init(1, false, 81, 8);
        itemStacks.init(2, true, 3, 30);
        itemStacks.setBackground(0, this.slotDrawable);
        itemStacks.setBackground(1, this.slotDrawable);
        itemStacks.setBackground(2, this.slotDrawable);
        itemStacks.set(ingredients);
        itemStacks.set(2, AEBlocks.CRANK.stack());
    }
}
