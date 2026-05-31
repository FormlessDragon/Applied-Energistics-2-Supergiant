package ae2.client.gui.me.search;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEKey;
import ae2.container.me.common.GridInventoryEntry;
import ae2.core.AEConfig;
import ae2.util.Platform;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class TooltipsSearchPredicate implements Predicate<GridInventoryEntry> {
    private final String tooltip;
    private final Map<AEKey, String> tooltipCache;

    public TooltipsSearchPredicate(String tooltip, Map<AEKey, String> tooltipCache) {
        this.tooltip = normalize(tooltip);
        this.tooltipCache = tooltipCache;
    }

    private static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    @Override
    public boolean test(GridInventoryEntry gridInventoryEntry) {
        AEKey entryInfo = Objects.requireNonNull(gridInventoryEntry.what());
        String tooltipText = getTooltipText(entryInfo);

        return tooltipText.contains(tooltip);
    }

    private String getTooltipText(AEKey what) {
        return tooltipCache.computeIfAbsent(what, key -> {
            List<ITextComponent> lines = AEKeyRendering.getTooltip(key);
            StringBuilder tooltipText = new StringBuilder();

            for (int i = 0; i < lines.size(); i++) {
                ITextComponent line = lines.get(i);
                String text = TextFormatting.getTextWithoutFormattingCodes(line.getFormattedText());

                if (i > 0 && i >= lines.size() - 1 && !AEConfig.instance().isSearchModNameInTooltips()) {
                    String modName = Platform.getModName(key.getModId());
                    if (Objects.equals(text, modName) || Objects.equals(text, key.getModId())) {
                        continue;
                    }
                }

                if (!tooltipText.isEmpty()) {
                    tooltipText.append('\n');
                }
                tooltipText.append(text);
            }

            return normalize(tooltipText.toString());
        });
    }
}
