package ae2.container.me.items;

import ae2.api.storage.ITerminalHost;
import ae2.container.GuiIds;
import ae2.container.me.common.ContainerMEStorage;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerBasicCellChest extends ContainerMEStorage {
    public ContainerBasicCellChest(InventoryPlayer ip, ITerminalHost host) {
        super(GuiIds.GuiKey.BASIC_CELL_CHEST, ip, host);
    }

    @Override
    public boolean canConfigureTypeFilter() {
        return false;
    }
}
