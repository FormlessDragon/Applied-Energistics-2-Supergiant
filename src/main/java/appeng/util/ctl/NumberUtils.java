package appeng.util.ctl;

import java.text.DecimalFormat;

public class NumberUtils {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");

    public static String formatNumberToInt(long value) {
        if (value < 1_000L) {
            return String.valueOf(value);
        } else if (value < 1_000_000L) {
            return value / 1_000 + "K";
        } else if (value < 1_000_000_000L) {
            return value / 1_000_000 + "M";
        } else if (value < 1_000_000_000_000L) {
            return value / 1_000_000_000L + "G";
        } else if (value < 1_000_000_000_000_000L) {
            return value / 1_000_000_000_000L + "T";
        } else if (value < 1_000_000_000_000_000_000L) {
            return value / 1_000_000_000_000_000L + "P";
        } else {
            return value / 1_000_000_000_000_000_000L + "E";
        }
    }

    public static String formatDecimal(double value) {
        return DECIMAL_FORMAT.format(value);
    }

}