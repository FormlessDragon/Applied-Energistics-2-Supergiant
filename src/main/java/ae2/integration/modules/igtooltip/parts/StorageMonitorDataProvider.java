package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.parts.reporting.AbstractMonitorPart;

public final class StorageMonitorDataProvider implements BodyProvider<AbstractMonitorPart> {
    @Override
    public void buildTooltip(AbstractMonitorPart monitor, TooltipContext context, TooltipBuilder tooltip) {
        var displayed = monitor.getDisplayed();
        boolean locked = monitor.isLocked();

        if (displayed != null) {
            tooltip.addLine(TopText.showing.text().appendText(": ").appendSibling(displayed.getDisplayName()));
        }

        tooltip.addLine(locked ? TopText.locked.text() : TopText.unlocked.text());
    }
}
