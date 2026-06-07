package ae2.integration.modules.hei;

import ae2.api.stacks.GenericStack;
import ae2.integration.abstraction.HeiAdapter;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public class HeiModule implements HeiAdapter {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public @Nullable Object getCurrentGhostIngredient() {
        return HeiPlugin.GUI_HANDLER.getCurrentGhostIngredient();
    }

    @Override
    public @Nullable GenericStack ingredientToStack(Object ingredient) {
        return GenericIngredientHelper.ingredientToStack(ingredient);
    }

    @Override
    public ItemStack getDisplayStack(Object ingredient) {
        return AEGuiHandler.toGhostDisplayStack(ingredient);
    }

    @Override
    public void registerClientFeatures() {
        HeiClientFeatures.register();
    }

    @Override
    public void appendIngredientActionTooltip(ItemTooltipEvent event) {
        HeiClientFeatures.appendIngredientActionTooltip(event);
    }

    @Override
    public void addBookmarkGroup(List<GenericStack> stacks) {
        HeiBookmarkHelper.addBookmarkGroup(stacks);
    }

    @Override
    public @Nullable String getRecipeCategoryTitle(String uid) {
        try {
            var runtime = HeiPlugin.getRuntime();
            if (runtime == null) {
                return null;
            }

            var category = runtime.getRecipeRegistry().getRecipeCategory(uid);
            if (category == null) {
                return null;
            }

            String title = category.getTitle();
            return title == null || title.isEmpty() ? null : title;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
