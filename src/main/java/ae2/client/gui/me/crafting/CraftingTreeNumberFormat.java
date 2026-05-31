package ae2.client.gui.me.crafting;

import java.text.DecimalFormat;

final class CraftingTreeNumberFormat {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");

    private CraftingTreeNumberFormat() {
    }

    static String formatDecimal(double value) {
        return DECIMAL_FORMAT.format(value);
    }
}
