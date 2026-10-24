package ae2.client.gui.me.common;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.longs.LongLongPair;
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
    public static ITextComponent format(AEKey what, LongLongPair rate, int windowMinutes) {
        if (rate == null || rate.leftLong() == 0 && rate.rightLong() == 0) {
            return null;
        }

        String window = formatWindow(windowMinutes);

        if (rate.leftLong() == 0) {
            return GuiText.FlowRateShort.text(window, formatOutRate(what, rate.rightLong()));
        } else if (rate.rightLong() == 0) {
            return GuiText.FlowRateShort.text(window, formatInRate(what, rate.leftLong()));
        } else {
            return GuiText.FlowRateFull.text(
                window,
                formatInRate(what, rate.leftLong()),
                formatOutRate(what, rate.rightLong()),
                formatTotalForChat(what, rate.leftLong() - rate.rightLong()));
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
        String magnitude = total == Long.MIN_VALUE
            ? Long.toUnsignedString(total)
            : Long.toString(Math.abs(total));
        return new TextComponentString(
            (total >= 0 ? TextFormatting.GREEN : TextFormatting.RED)
                + (total >= 0 ? "+" : "-") + magnitude + TextFormatting.RESET);
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
    public static String formatSignedAmount(AEKey what, long amount) {
        if (amount == Long.MIN_VALUE) {
            return "-" + Long.toUnsignedString(amount);
        }
        String formatted = what.formatAmount(Math.abs(amount), AmountFormat.FULL);
        return (amount >= 0 ? "+" : "-") + formatted;
    }
}
