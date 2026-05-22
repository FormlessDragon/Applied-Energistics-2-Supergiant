package appeng.container.me.items;

import appeng.api.networking.IGridNode;
import appeng.container.GuiIds;
import appeng.helpers.WirelessCraftingTerminalGuiHost;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerWirelessCraftingTerm extends ContainerCraftingTerm {

    private final WirelessCraftingTerminalGuiHost<?> containerHost;

    public ContainerWirelessCraftingTerm( InventoryPlayer ip, WirelessCraftingTerminalGuiHost<?> monitorable) {
        super(GuiIds.GuiKey.WIRELESS_CRAFTING_TERMINAL, ip, monitorable, false);
        this.addPlayerInventorySlots(0, 0);
        this.containerHost = monitorable;
    }

    public IGridNode getGridNode() {
        return this.containerHost.getActionableNode();
    }
}
