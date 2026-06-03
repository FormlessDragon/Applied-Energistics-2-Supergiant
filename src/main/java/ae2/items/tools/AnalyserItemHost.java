package ae2.items.tools;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.core.gui.locator.ItemGuiHostLocator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;

public final class AnalyserItemHost<T extends Item> extends ItemGuiHost<T> {
    public AnalyserItemHost(T item, EntityPlayer player, ItemGuiHostLocator locator) {
        super(item, player, locator);
    }
}
