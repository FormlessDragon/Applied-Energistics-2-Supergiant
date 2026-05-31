package ae2.integration.modules.hei;

import ae2.core.AppEng;
import ae2.core.localization.ItemModText;
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
class P2PAttunementCategory implements IRecipeCategory<P2PAttunementRecipeWrapper> {
    static final String UID = "ae2.p2p_attunement";

    private final IDrawable background;
    private final IDrawable slotDrawable;
    private final IDrawable arrow;

    P2PAttunementCategory(IGuiHelper guiHelper) {
        ResourceLocation location = new ResourceLocation(AppEng.MOD_ID, "textures/guis/jei.png");
        this.background = new HeiBackgroundDrawable(94, 36);
        this.slotDrawable = guiHelper.createDrawable(location, 0, 34, 18, 18);
        this.arrow = guiHelper.createDrawable(location, 0, 0, 24, 17);
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return ItemModText.P2P_TUNNEL_ATTUNEMENT.getLocal();
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
        this.arrow.draw(minecraft, 31, 10);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, P2PAttunementRecipeWrapper recipeWrapper,
                          IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, 4, 5);
        itemStacks.init(1, false, 61, 5);
        itemStacks.setBackground(0, this.slotDrawable);
        itemStacks.setBackground(1, this.slotDrawable);
        itemStacks.set(ingredients);
        itemStacks.addTooltipCallback((slotIndex, input, ignored, tooltip) -> {
            if (input && slotIndex == 0) {
                tooltip.addAll(recipeWrapper.getDescription());
            }
        });
    }
}
