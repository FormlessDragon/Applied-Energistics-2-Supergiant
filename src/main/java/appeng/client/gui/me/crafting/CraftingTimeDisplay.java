package appeng.client.gui.me.crafting;

import appeng.container.me.crafting.CraftingStatus;
import appeng.core.localization.ButtonToolTips;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public final class CraftingTimeDisplay {
    private static final String DURATION_PATTERN = "HH:mm:ss";

    private CraftingTimeDisplay() {
    }

    @Nullable
    public static String getElapsedTimeTitleSuffix(@Nullable CraftingStatus status, int entryCount) {
        if (status == null || entryCount <= 0 || status.elapsedTime() <= 0) {
            return null;
        }

        return formatElapsedTime(status.elapsedTime());
    }

    public static LocalizedTooltip getElapsedTimeTooltip(float progress, long elapsedTimeNanos) {
        return new LocalizedTooltip(
            ButtonToolTips.CpuStatusElapsedTime.getTranslationKey(),
            new Object[]{Math.round(progress * 100) + "%", formatElapsedTime(elapsedTimeNanos)});
    }

    private static String formatElapsedTime(long elapsedTimeNanos) {
        long millis = TimeUnit.MILLISECONDS.convert(elapsedTimeNanos, TimeUnit.NANOSECONDS);
        return DurationFormatUtils.formatDuration(millis, DURATION_PATTERN);
    }

    public record LocalizedTooltip(String translationKey, Object[] args) {
    }
}
