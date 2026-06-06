package ae2.integration.modules.igtooltip.blocks;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.integration.modules.theoneprobe.TopTooltipFormatter;
import ae2.tile.crafting.TileCraftingMonitor;
import net.minecraft.util.text.TextFormatting;

/**
 * Shows the name of the item being crafted.
 */
public final class CraftingMonitorDataProvider implements BodyProvider<TileCraftingMonitor> {
    @Override
    public void buildTooltip(TileCraftingMonitor monitor, TooltipContext context, TooltipBuilder tooltip) {
        var displayStack = monitor.getJobProgress();

        if (displayStack != null) {
            tooltip.addLabel(TopText.crafting, TopTooltipFormatter.displayName(displayStack.what()), TextFormatting.GREEN);
        }
    }
}
