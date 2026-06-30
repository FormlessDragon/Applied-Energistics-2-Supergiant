package ae2.items.tools.powered;

import ae2.container.GuiIds;
import ae2.helpers.WirelessPEATerminalGuiHost;
import net.minecraft.item.ItemStack;

public class WirelessPEATerminalItem extends WirelessTerminalItem {

    public WirelessPEATerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_pattern_encoding_access_terminal",
            GuiIds.GuiKey.WIRELESS_PEA_TERMINAL,
            ItemStack::new,
            WirelessPEATerminalGuiHost::new,
            WirelessTerminalDefinitionFactories.patternEncodingAccessContainer(),
            WirelessTerminalDefinitionFactories.patternEncodingAccessScreen(),
            "wireless_pattern_encoding_access_terminal",
            2,
            true);
    }

}
