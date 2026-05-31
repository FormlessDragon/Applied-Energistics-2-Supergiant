package ae2.api.storage;

import ae2.container.ISubGui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Implemented by objects that open a container, which then opens a submenu. It's used to determine how to return to the main
 * container from a submenu.
 */
public interface ISubGuiHost {
    /**
     * Returns to the primary user interface for this host. Used by sub-menus when players want to return to the
     * previous screen.
     */
    void returnToMainContainer(EntityPlayer player, ISubGui subGui);

    /**
     * Gets the icon to represent the host of the submenu. Used as the icon for the back button.
     */
    ItemStack getMainContainerIcon();
}
