package ae2.integration.modules.theoneprobe;

import ae2.api.stacks.AEKey;
import ae2.core.localization.LocalizationEnum;
import mcjty.theoneprobe.api.IProbeInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

public final class TopTooltipFormatter {
    private static final char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    private static final TextFormatting[] P2P_COLORS = {
        TextFormatting.WHITE,
        TextFormatting.GRAY,
        TextFormatting.DARK_GRAY,
        TextFormatting.DARK_GRAY,
        TextFormatting.GREEN,
        TextFormatting.YELLOW,
        TextFormatting.GOLD,
        TextFormatting.GOLD,
        TextFormatting.RED,
        TextFormatting.LIGHT_PURPLE,
        TextFormatting.LIGHT_PURPLE,
        TextFormatting.DARK_PURPLE,
        TextFormatting.BLUE,
        TextFormatting.AQUA,
        TextFormatting.DARK_AQUA,
        TextFormatting.DARK_GREEN
    };

    private TopTooltipFormatter() {
    }

    public static String localize(LocalizationEnum text) {
        return localize(text.getTranslationKey());
    }

    public static String localize(String translationKey) {
        return IProbeInfo.STARTLOC + translationKey + IProbeInfo.ENDLOC;
    }

    public static String style(String text, TextFormatting formatting) {
        return formatting + text;
    }

    public static String labeledValue(LocalizationEnum label, String value, TextFormatting valueColor) {
        return localize(label) + ": " + style(value, valueColor);
    }

    public static String displayName(ItemStack stack) {
        return stack.isEmpty() ? "" : stack.getDisplayName();
    }

    public static String displayName(AEKey key) {
        return key.getDisplayName().getFormattedText();
    }

    public static String p2pFrequency(short frequency) {
        var result = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int nibble = frequency >> 4 * (3 - i) & 0xF;
            result.append(P2P_COLORS[nibble]);
            result.append(HEX_DIGITS[nibble]);
        }
        result.append(TextFormatting.RESET);
        return result.toString();
    }
}
