package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.AppEngSlot;
import ae2.tile.misc.TileIngredientBuffer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerIngredientBuffer extends AEBaseContainer {
    public ContainerIngredientBuffer(InventoryPlayer ip, TileIngredientBuffer host) {
        super(ip, host);
        for (int index = 0; index < host.getBuffer().size(); index++) {
            addSlot(new AppEngSlot(host.getBuffer().createGuiWrapper(), index), SlotSemantics.STORAGE);
        }
        addPlayerInventorySlots(8, 112);
    }
}
