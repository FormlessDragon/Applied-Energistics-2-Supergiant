package ae2.api.integrations.igtooltip;

import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.integration.modules.igtooltip.parts.PartTooltipProviders;
import org.jetbrains.annotations.ApiStatus;

import static ae2.api.integrations.igtooltip.TooltipProvider.DEFAULT_PRIORITY;

/**
 * Add additional in-game tooltips for parts and integrate them automatically with AE2 supported in-game tooltip
 * integrations.
 */
@ApiStatus.Experimental
public final class PartTooltips {

    private PartTooltips() {
    }

    public static <T> void addServerData(Class<T> baseClass, ServerDataProvider<? super T> provider) {
        addServerData(baseClass, provider, DEFAULT_PRIORITY);
    }

    public static <T> void addServerData(Class<T> baseClass, ServerDataProvider<? super T> provider, int priority) {
        PartTooltipProviders.addServerData(baseClass, provider, priority);
    }

    public static <T> void addBody(Class<T> baseClass, BodyProvider<? super T> provider) {
        addBody(baseClass, provider, DEFAULT_PRIORITY);
    }

    public static <T> void addBody(Class<T> baseClass, BodyProvider<? super T> provider, int priority) {
        PartTooltipProviders.addBody(baseClass, provider, priority);
    }
}
