package ae2.api.integrations.igtooltip.providers;

import ae2.api.integrations.igtooltip.TooltipContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the icon shown in the in-game tooltip.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
@FunctionalInterface
public interface IconProvider<T> {
    /**
     * @return Null if this provider can't provide an icon for the object.
     */
    @Nullable
    ItemStack getIcon(T object, TooltipContext context);
}
