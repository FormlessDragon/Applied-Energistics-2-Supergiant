package appeng.container.me.items;

import appeng.api.storage.ITerminalHost;
import appeng.container.GuiIds;
import appeng.container.me.common.ContainerMEStorage;
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
