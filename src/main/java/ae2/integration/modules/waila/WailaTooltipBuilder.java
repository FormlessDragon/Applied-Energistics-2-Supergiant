package ae2.integration.modules.waila;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.core.localization.LocalizationEnum;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

final class WailaTooltipBuilder implements TooltipBuilder {
    private final List<String> tooltip;

    WailaTooltipBuilder(List<String> tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public String localize(LocalizationEnum text) {
        return text.getLocal();
    }

    @Override
    public String localize(String translationKey) {
        return I18n.format(translationKey);
    }

    @Override
    public void addLine(String line) {
        this.tooltip.add(line);
    }

    @Override
    public void addLine(LocalizationEnum line) {
        addLine(localize(line));
    }

    @Override
    public void addLine(LocalizationEnum line, TextFormatting formatting) {
        addLine(formatting + localize(line));
    }

    @Override
    public void addLabel(LocalizationEnum label, String value) {
        addLabel(label, value, TextFormatting.WHITE);
    }

    @Override
    public void addLabel(LocalizationEnum label, String value, TextFormatting valueFormatting) {
        addLine(localize(label) + ": " + valueFormatting + value + TextFormatting.WHITE);
    }
}
