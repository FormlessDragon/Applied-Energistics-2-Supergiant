package ae2.container.implementations;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.container.AEBaseContainer;
import ae2.items.tools.NetworkAnalyserConfig;
import ae2.items.tools.NetworkAnalyserItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerNetworkAnalyser extends AEBaseContainer {
    public ContainerNetworkAnalyser(InventoryPlayer playerInventory, ItemGuiHost<?> host) {
        super(playerInventory, host);
    }

    public NetworkAnalyserConfig getConfig() {
        ItemStack stack = getItemGuiHost().getItemStack();
        return NetworkAnalyserItem.getConfig(stack);
    }

    public void saveConfig(NetworkAnalyserConfig config) {
        ItemStack stack = getItemGuiHost().getItemStack();
        if (stack.getItem() instanceof NetworkAnalyserItem) {
            NetworkAnalyserItem.setConfig(stack, config);
        }
    }
}
