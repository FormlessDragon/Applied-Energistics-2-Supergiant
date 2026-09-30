package ae2.container;

import ae2.api.stacks.KeyCounter;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingTree;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.interfaces.ICraftingGridContainer.GridFill;
import ae2.hooks.ticking.TickHandler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crafting grid slots a batch order of the crafting terminal will fill once it delivered the materials it ordered.
 * <p>
 * The terminals recreate their container whenever a sub-GUI such as the crafting confirmation is opened, so a pending
 * batch cannot live on a container: the confirmation would drop it before the player even submits the order. It is kept
 * per player instead and belongs to the terminal session that ordered it. Leaving that session is the only interruption
 * needed, which is what the scheduled checks below detect: opening the confirmation or the crafting tree stays in the
 * session, because they are sub-GUIs of the same terminal.
 */
public final class PendingGridFills {
    private static final Map<UUID, List<GridFill>> pendingByPlayer = new Object2ObjectOpenHashMap<>();

    private PendingGridFills() {
    }

    /**
     * Remembers the slots an order has to fill for a player's terminal.
     */
    public static void set(EntityPlayer player, List<GridFill> fills) {
        pendingByPlayer.put(player.getUniqueID(), fills);
    }

    /**
     * The slots still waiting to be filled, or null when that player has no pending order. The returned list is owned
     * by this store, so the terminal may remove the entries it was able to fill.
     */
    @Nullable
    public static List<GridFill> get(EntityPlayer player) {
        return pendingByPlayer.get(player.getUniqueID());
    }

    /**
     * Forgets the player's pending slots.
     */
    public static void clear(EntityPlayer player) {
        pendingByPlayer.remove(player.getUniqueID());
    }

    /**
     * Forgets the slots of materials that cannot be obtained at all, so the terminal does not keep showing slots that
     * nothing will ever be put into.
     */
    public static void dropUnavailable(EntityPlayer player, KeyCounter missingItems) {
        List<GridFill> fills = get(player);
        if (fills != null) {
            fills.removeIf(fill -> missingItems.get(fill.what()) > 0);
        }
    }

    /**
     * Drops the pending slots when a confirmation was left without ordering, unless the player stays in one of its
     * sub-GUIs such as the crafting tree. Like the interrupt below, this is only decided one tick later, because the
     * confirmation is closed before the next GUI is known.
     */
    public static void scheduleAbandonCheck(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }

        UUID playerId = player.getUniqueID();
        var level = player.world;
        if (level == null) {
            return;
        }

        TickHandler.instance().addCallable(level, () -> {
            if (!pendingByPlayer.containsKey(playerId)) {
                return;
            }
            if (!(player.openContainer instanceof ContainerCraftConfirm)
                && !(player.openContainer instanceof ContainerCraftingTree)) {
                pendingByPlayer.remove(playerId);
            }
        });
    }

    /**
     * Drops the pending slots once the player has left the terminal, which is only known one tick later: opening the
     * crafting confirmation closes the terminal's container as well, and the player is back in the terminal right
     * after.
     */
    public static void scheduleInterrupt(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            // Containers are closed on the client as well, and only the server is in charge of the pending slots.
            return;
        }

        UUID playerId = player.getUniqueID();
        var level = player.world;
        if (level == null) {
            return;
        }

        TickHandler.instance().addCallable(level, () -> {
            if (!pendingByPlayer.containsKey(playerId)) {
                return;
            }
            if (!hasTerminalOpen(player)) {
                pendingByPlayer.remove(playerId);
            }
        });
    }

    /**
     * True while the player is looking at a crafting terminal or one of its sub-GUIs, such as the crafting
     * confirmation or the crafting tree.
     */
    private static boolean hasTerminalOpen(EntityPlayer player) {
        return player.openContainer instanceof ICraftingGridContainer
            || player.openContainer instanceof ContainerCraftConfirm
            || player.openContainer instanceof ContainerCraftingTree;
    }
}
