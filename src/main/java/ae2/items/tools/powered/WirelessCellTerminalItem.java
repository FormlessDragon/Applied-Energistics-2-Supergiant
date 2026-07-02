package ae2.items.tools.powered;

import ae2.container.GuiIds;
import ae2.helpers.WirelessTerminalGuiHost;
import net.minecraft.item.ItemStack;

public class WirelessCellTerminalItem extends WirelessTerminalItem {

    public WirelessCellTerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_cell_terminal",
            GuiIds.GuiKey.WIRELESS_CELL_TERMINAL,
            ItemStack::new,
            WirelessTerminalGuiHost::new,
            WirelessTerminalDefinitionFactories.cellTerminalContainer(),
            WirelessTerminalDefinitionFactories.cellTerminalScreen(),
            "wireless_cell_terminal",
            2,
            true);
    }
}
