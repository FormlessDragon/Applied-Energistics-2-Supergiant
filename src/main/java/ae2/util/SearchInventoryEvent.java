package ae2.util;

import ae2.integration.modules.baubles.BaublesIntegration;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Event fired when AE2 is looking for ItemStacks in a player inventory. By default, AE2 only looks at the 36 usual
 * slots of the player inventory, use this event to make AE2 consider more stacks. AE2 will check after the event if
 * they contain the item it is searching.
 */
public class SearchInventoryEvent extends PlayerEvent {
    private final List<ItemStack> stacks;

    public SearchInventoryEvent(EntityPlayer player, List<ItemStack> stacks) {
        super(player);
        this.stacks = stacks;
    }

    public static List<ItemStack> getItems(EntityPlayer player) {
        List<ItemStack> items = new ObjectArrayList<>(player.inventory.mainInventory);
        var slots = BaublesIntegration.getSlots(player);
        for (int i = 0; i < slots; i++) {
            items.add(BaublesIntegration.getStackInSlot(player, i));
        }
        MinecraftForge.EVENT_BUS.post(new SearchInventoryEvent(player, items));
        return items;
    }

    public List<ItemStack> getStacks() {
        return stacks;
    }
}
