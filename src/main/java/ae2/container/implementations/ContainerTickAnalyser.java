package ae2.container.implementations;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.container.AEBaseContainer;
import ae2.items.tools.TickAnalyserConfig;
import ae2.items.tools.TickAnalyserItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerTickAnalyser extends AEBaseContainer {
    public ContainerTickAnalyser(InventoryPlayer playerInventory, ItemGuiHost<?> host) {
        super(playerInventory, host);
    }

    public TickAnalyserConfig getConfig() {
        return TickAnalyserItem.getConfig(getItemGuiHost().getItemStack());
    }

    public void saveConfig(TickAnalyserConfig config) {
        ItemStack stack = getItemGuiHost().getItemStack();
        if (stack.getItem() instanceof TickAnalyserItem) {
            TickAnalyserItem.setConfig(stack, config);
        }
    }
}
