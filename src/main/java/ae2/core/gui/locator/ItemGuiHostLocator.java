package ae2.core.gui.locator;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.core.AELog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import org.jetbrains.annotations.Nullable;

/**
 * A locator for stacks of items that implement {@link IGuiItem}. This allows
 * such items to be stored in various places (player inventory, curio slots) when they host a container.
 */
public interface ItemGuiHostLocator extends GuiHostLocator {
    @Override
    default <T> T locate(EntityPlayer player, Class<T> hostInterface) {
        ItemStack it = locateItem(player);

        if (!it.isEmpty() && it.getItem() instanceof IGuiItem guiItem) {
            var guiHost = guiItem.getGuiHost(player, this, hitResult());
            if (hostInterface.isInstance(guiHost)) {
                return hostInterface.cast(guiHost);
            } else if (guiHost != null) {
                AELog.warn("Item in %s of %s did not create a compatible container of type %s: %s",
                    this, player, hostInterface, guiHost);
            }
        } else {
            AELog.warn("Item in %s of %s is not an IGuiItem: %s",
                this, player, it);
        }

        return null;
    }

    /**
     * @return The optional location where the item was used on to open the container.
     */
    @Nullable
    default RayTraceResult hitResult() {
        return null;
    }

    /**
     * @return The slot of the item in the player inventory if this locator represents a location in the player
     * inventory. Used to lock the slot against accidentally moving the item out.
     */
    @Nullable
    default Integer getPlayerInventorySlot() {
        return null;
    }

    /**
     * Locates the GUI item in the player's inventory and returns it if it satisfies the expected GUI host interface.
     * <strong>The returned stack will be modified by the {@link ItemGuiHost}</strong>, it must be updated in-place.
     */
    ItemStack locateItem(EntityPlayer player);
}
