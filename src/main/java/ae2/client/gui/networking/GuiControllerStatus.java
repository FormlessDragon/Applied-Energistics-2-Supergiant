package ae2.client.gui.networking;

import ae2.client.gui.me.networktool.GuiNetworkStatus;
import ae2.client.gui.style.GuiStyleManager;
import ae2.container.networking.ContainerControllerStatus;
import net.minecraft.entity.player.InventoryPlayer;

public class GuiControllerStatus extends GuiNetworkStatus<ContainerControllerStatus> {

    public GuiControllerStatus(ContainerControllerStatus container, InventoryPlayer playerInventory) {
        super(container, playerInventory, GuiStyleManager.loadStyleDoc("/screens/network_status.json"));
    }
}
