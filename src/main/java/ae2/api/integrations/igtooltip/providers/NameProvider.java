package ae2.api.integrations.igtooltip.providers;

import ae2.api.integrations.igtooltip.TooltipContext;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the name shown in the in-game tooltip.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
@FunctionalInterface
public interface NameProvider<T> {
    /**
     * @return Null if this provider can't provide a name for the object.
     */
    @Nullable
    ITextComponent getName(T object, TooltipContext context);
}
