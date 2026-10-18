package ae2.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import org.jetbrains.annotations.NotNull;

public final class ContainerConstants {
    /**
     * Shared event handler for detached crafting inventories. Do not add slots, listeners or player state to it.
     * Each caller owns its InventoryCrafting; this container holds none of their contents.
     */
    public static final Container EMPTY_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(@NotNull EntityPlayer player) {
            return false;
        }
    };

    private ContainerConstants() {
    }
}
