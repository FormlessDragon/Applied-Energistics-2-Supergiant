package ae2.api.integrations.igtooltip.providers;

import ae2.api.integrations.igtooltip.TooltipContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the mod name shown in the in-game tooltip. Used to override the mod-name for part hosts to show the
 * highlighted part's original mod.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
@FunctionalInterface
public interface ModNameProvider<T> {
    /**
     * @return Null if this provider can't provide a mod name for the object.
     */
    @Nullable
    String getModName(T object, TooltipContext context);
}
