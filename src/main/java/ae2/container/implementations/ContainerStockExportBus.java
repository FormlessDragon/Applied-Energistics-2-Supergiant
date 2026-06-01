package ae2.container.implementations;

import ae2.container.SlotSemantics;
import ae2.parts.automation.StockExportBusPart;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

public class ContainerStockExportBus extends ContainerIOBus {
    public ContainerStockExportBus(InventoryPlayer ip, StockExportBusPart host) {
        super(ip, host);
    }

    public boolean isConfigSlot(Slot slot) {
        return slot != null && getSlots(SlotSemantics.CONFIG).contains(slot);
    }
}
