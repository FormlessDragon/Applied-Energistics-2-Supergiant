package ae2.items.contents;

import ae2.api.config.CondenserOutput;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.storage.VoidCellItem;
import net.minecraft.entity.player.EntityPlayer;

public class VoidCellGuiHost extends ItemGuiHost<VoidCellItem> {

    public VoidCellGuiHost(VoidCellItem item, EntityPlayer player, ItemGuiHostLocator locator) {
        super(item, player, locator);
    }

    public CondenserOutput getMode() {
        return getItem().getMode(getItemStack());
    }

    public void setMode(CondenserOutput mode) {
        getItem().setMode(getItemStack(), mode);
    }
}
