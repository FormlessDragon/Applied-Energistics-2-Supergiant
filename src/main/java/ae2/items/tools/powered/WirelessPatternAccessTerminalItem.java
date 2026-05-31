package ae2.items.tools.powered;

import ae2.container.GuiIds;
import ae2.helpers.WirelessPatternAccessTerminalGuiHost;
import net.minecraft.item.ItemStack;

public class WirelessPatternAccessTerminalItem extends WirelessTerminalItem {

    public WirelessPatternAccessTerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_pattern_access_terminal",
            GuiIds.GuiKey.WIRELESS_PATTERN_ACCESS_TERMINAL,
            ItemStack::new,
            WirelessPatternAccessTerminalGuiHost::new,
            "wireless_pattern_access_terminal",
            2);
    }

}
