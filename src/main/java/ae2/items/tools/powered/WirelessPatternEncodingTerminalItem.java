package ae2.items.tools.powered;

import ae2.container.GuiIds;
import ae2.helpers.WirelessPatternEncodingTerminalGuiHost;
import net.minecraft.item.ItemStack;

public class WirelessPatternEncodingTerminalItem extends WirelessTerminalItem {

    public WirelessPatternEncodingTerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_pattern_encoding_terminal",
            GuiIds.GuiKey.WIRELESS_PATTERN_ENCODING_TERMINAL,
            ItemStack::new,
            WirelessPatternEncodingTerminalGuiHost::new,
            "wireless_pattern_encoding_terminal",
            2);
    }

}
