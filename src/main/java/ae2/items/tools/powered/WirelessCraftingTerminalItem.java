package ae2.items.tools.powered;

import ae2.container.GuiIds;
import ae2.helpers.WirelessCraftingTerminalGuiHost;
import net.minecraft.item.ItemStack;

public class WirelessCraftingTerminalItem extends WirelessTerminalItem {

    public WirelessCraftingTerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_crafting_terminal",
            GuiIds.GuiKey.WIRELESS_CRAFTING_TERMINAL,
            ItemStack::new,
            WirelessCraftingTerminalGuiHost::new,
            "wireless_crafting_terminal",
            2);
    }

}
