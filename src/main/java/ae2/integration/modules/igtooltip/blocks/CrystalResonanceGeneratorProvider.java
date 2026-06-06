package ae2.integration.modules.igtooltip.blocks;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.tile.networking.TileCrystalResonanceGenerator;
import net.minecraft.util.text.TextFormatting;

public final class CrystalResonanceGeneratorProvider implements BodyProvider<TileCrystalResonanceGenerator> {
    @Override
    public void buildTooltip(TileCrystalResonanceGenerator generator, TooltipContext context, TooltipBuilder tooltip) {
        if (generator.isSuppressed()) {
            tooltip.addLine(TopText.suppressed, TextFormatting.RED);
        }
    }
}
