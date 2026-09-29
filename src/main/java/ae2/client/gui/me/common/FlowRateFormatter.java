package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.util.FlowRate;
import ae2.core.localization.GuiText;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public final class FlowRateFormatter {

    private FlowRateFormatter() {
    }

    /**
     * Formats the flow of a single key as a tooltip line.
     * <p/>
     * When the key only flowed in one direction the other direction is omitted, for example {@code Flow (2m): +1 B}
     * instead of showing a meaningless {@code -0}.
     *
     * @return the formatted line, or {@code null} if the key had no flow at all.
     */
    public static ITextComponent format(AEKey what, FlowRate rate, int windowMinutes) {
        if (rate == null || rate.in() == 0 && rate.out() == 0) {
            return null;
        }

        String window = formatWindow(windowMinutes);

        if (rate.in() == 0) {
            return GuiText.FlowRateShort.text(window, formatOutRate(what, rate.out()));
        } else if (rate.out() == 0) {
            return GuiText.FlowRateShort.text(window, formatInRate(what, rate.in()));
        } else {
            return GuiText.FlowRateFull.text(
                window,
                formatInRate(what, rate.in()),
                formatOutRate(what, rate.out()),
                formatTotalForChat(what, rate.net()));
        }
    }

    private static String formatWindow(int minutes) {
        return Math.max(1, minutes) + "m";
    }

    private static String formatInRate(AEKey what, long in) {
        return TextFormatting.GREEN + formatSignedAmount(what, in) + TextFormatting.RESET;
    }

    private static String formatOutRate(AEKey what, long out) {
        return TextFormatting.RED + formatSignedAmount(what, -out) + TextFormatting.RESET;
    }

    /**
     * Formats a single signed total for chat, greening inflows and reddening outflows.
     */
    public static ITextComponent formatTotalForChat(long total) {
        return new TextComponentString(
            (total >= 0 ? TextFormatting.GREEN : TextFormatting.RED)
                + (total >= 0 ? "+" : "-") + Math.abs(total) + TextFormatting.RESET);
    }

    private static ITextComponent formatTotalForChat(AEKey what, long total) {
        return new TextComponentString(
            (total >= 0 ? TextFormatting.GREEN : TextFormatting.RED) + formatSignedAmount(what, total)
                + TextFormatting.RESET);
    }

    /**
     * Formats a signed amount using the unit of the given key.
     * <p/>
     * The sign is added here because {@link AEKey#formatAmount} formats the magnitude only: passing a negative value
     * would place the minus sign inside the number and could never express the explicit plus sign used for inflows.
     */
    private static String formatSignedAmount(AEKey what, long amount) {
        String formatted = what.formatAmount(Math.abs(amount), AmountFormat.FULL);
        return (amount >= 0 ? "+" : "-") + formatted;
    }
}
