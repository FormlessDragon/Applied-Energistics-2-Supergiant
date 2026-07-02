package ae2.items.tools.powered;

import ae2.container.GuiIds;
import ae2.helpers.WirelessRequesterTerminalGuiHost;
import net.minecraft.item.ItemStack;

public class WirelessRequesterTerminalItem extends WirelessTerminalItem {

    public WirelessRequesterTerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_requester_terminal",
            GuiIds.GuiKey.WIRELESS_REQUESTER_TERMINAL,
            ItemStack::new,
            WirelessRequesterTerminalGuiHost::new,
            WirelessTerminalDefinitionFactories.requesterContainer(),
            WirelessTerminalDefinitionFactories.requesterScreen(),
            "wireless_requester_terminal",
            2,
            true);
    }
}
