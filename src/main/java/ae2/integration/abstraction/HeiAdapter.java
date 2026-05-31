package ae2.integration.abstraction;

import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface HeiAdapter {

    static HeiAdapter none() {
        return () -> false;
    }

    boolean isEnabled();

    @Nullable
    default Object getCurrentGhostIngredient() {
        return null;
    }

    @Nullable
    default GenericStack ingredientToStack(Object ingredient) {
        return null;
    }

    default ItemStack getDisplayStack(Object ingredient) {
        return ItemStack.EMPTY;
    }

    default void registerClientFeatures() {
    }

    default void appendIngredientActionTooltip(ItemTooltipEvent event) {
    }

    default void addBookmarkGroup(List<GenericStack> stacks) {
    }
}
