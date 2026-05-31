package ae2.api.integrations.igtooltip.providers;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
@FunctionalInterface
public interface BodyProvider<T> {
    void buildTooltip(T object, TooltipContext context, TooltipBuilder tooltip);
}
