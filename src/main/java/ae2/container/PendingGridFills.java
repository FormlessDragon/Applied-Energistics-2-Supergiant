package ae2.container;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.networking.IGrid;
import ae2.api.stacks.KeyCounter;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingTree;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.interfaces.ICraftingGridContainer.GridFill;
import ae2.hooks.ticking.TickHandler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
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
    private static final Map<UUID, Order> pendingByPlayer = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, PlayerSession> sessions = new Object2ObjectOpenHashMap<>();
    private static boolean accepting;

    private PendingGridFills() {
    }

    /**
     * Remembers the slots an order has to fill for a player's terminal.
     */
    public static void set(EntityPlayer player, List<GridFill> fills) {
        if (session(player) != null && player.openContainer instanceof ICraftingGridContainer terminal) {
            var node = terminal.getGridNode();
            set(player, fills, terminal, node == null ? null : node.grid());
        }
    }

    public static void set(EntityPlayer player, List<GridFill> fills, ICraftingGridContainer terminal,
                           @Nullable IGrid grid) {
        var session = session(player);
        if (session == null) {
            return;
        }
        var playerId = player.getUniqueID();
        var previous = pendingByPlayer.get(playerId);
        var destination = destination(terminal);
        if (destination != null && grid != null && session(player) == session) {
            var node = terminal.getGridNode();
            if (node != null && node.grid() == grid && session(player) == session
                && pendingByPlayer.get(playerId) == previous) {
                pendingByPlayer.put(playerId, new Order(session, fills, destination, grid));
            }
        }
    }

    /**
     * The slots still waiting to be filled, or null when that player has no pending order. The returned list is owned
     * by this store, so the terminal may remove the entries it was able to fill.
     */
    @Nullable
    public static List<GridFill> get(EntityPlayer player) {
        var order = getOrder(player);
        return order == null ? null : order.fills;
    }

    @Nullable
    public static Order getOrder(EntityPlayer player) {
        var session = session(player);
        if (session == null) {
            return null;
        }
        var order = pendingByPlayer.get(player.getUniqueID());
        return order != null && order.session == session ? order : null;
    }

    @Nullable
    public static List<GridFill> get(EntityPlayer player, ICraftingGridContainer terminal, @Nullable IGrid grid) {
        var order = getOrder(player);
        if (order == null) {
            return null;
        }
        Object expected = order.destination.get();
        IGrid expectedGrid = order.grid.get();
        boolean matches = expected != null
            && expected == destination(terminal) && expectedGrid != null && expectedGrid == grid;
        if (matches) {
            var node = terminal.getGridNode();
            matches = node != null && node.grid() == grid;
        }
        if (getOrder(player) != order) {
            return null;
        }
        if (!matches) {
            pendingByPlayer.remove(player.getUniqueID(), order);
            return null;
        }
        return order.fills;
    }

    @Nullable
    public static Order getOrderForHost(EntityPlayer player, Object host, @Nullable IGrid grid) {
        var order = getOrder(player);
        if (order == null) {
            return null;
        }
        var expected = order.destination.get();
        boolean matches = expected != null && expected == destination(host) && grid != null
            && order.grid.get() == grid;
        return matches && getOrder(player) == order ? order : null;
    }

    @Nullable
    private static Object destination(ICraftingGridContainer terminal) {
        if (terminal instanceof AEBaseContainer container) {
            var target = container.getTarget();
            if (target != null) {
                return destination(target);
            }
        }
        return terminal.getCraftingMatrix();
    }

    @Nullable
    private static Object destination(Object host) {
        if (host instanceof ItemGuiHost<?> item) {
            var stack = item.getItemStack();
            return stack.isEmpty() ? null : stack;
        }
        return host;
    }

    @Nullable
    private static PlayerSession session(EntityPlayer player) {
        if (!accepting || !(player instanceof EntityPlayerMP)) {
            return null;
        }
        var session = sessions.get(player.getUniqueID());
        return session != null && session.player.get() == player && session.level.get() == player.world
            ? session : null;
    }

    public static void startSession() {
        pendingByPlayer.clear();
        sessions.clear();
        accepting = true;
    }

    public static void shutdown() {
        accepting = false;
        pendingByPlayer.clear();
        sessions.clear();
    }

    public static void playerJoined(EntityPlayer player) {
        if (accepting && player instanceof EntityPlayerMP) {
            pendingByPlayer.remove(player.getUniqueID());
            sessions.put(player.getUniqueID(), new PlayerSession(player));
        }
    }

    public static void clearWorld(World level) {
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().level.get() == level) {
                pendingByPlayer.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    @Nullable
    public static Submission beginSubmission(@Nullable Order order) {
        return order == null ? null : new Submission(order);
    }

    /**
     * Forgets the player's pending slots.
     */
    public static void clear(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        var session = sessions.get(player.getUniqueID());
        if (session != null && session.player.get() == player) {
            pendingByPlayer.remove(player.getUniqueID());
        }
    }

    public static void playerLeft(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        var session = sessions.get(player.getUniqueID());
        if (session != null && session.player.get() == player) {
            pendingByPlayer.remove(player.getUniqueID());
            sessions.remove(player.getUniqueID(), session);
        }
    }

    /**
     * Forgets the slots of materials that cannot be obtained at all, so the terminal does not keep showing slots that
     * nothing will ever be put into.
     */
    public static void dropUnavailable(EntityPlayer player, KeyCounter missingItems) {
        dropUnavailable(player, getOrder(player), missingItems);
    }

    public static void dropUnavailable(EntityPlayer player, @Nullable Order order, KeyCounter missingItems) {
        if (order != null && getOrder(player) == order) {
            order.fills.removeIf(fill -> missingItems.get(fill.what()) > 0);
        }
    }

    /**
     * Drops the pending slots when a confirmation was left without ordering, unless the player stays in one of its
     * sub-GUIs such as the crafting tree. Like the interrupt below, this is only decided one tick later, because the
     * confirmation is closed before the next GUI is known.
     */
    public static void scheduleAbandonCheck(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            scheduleAbandonCheck(player, getOrder(player));
        }
    }

    public static void scheduleAbandonCheck(EntityPlayer player, @Nullable Order order) {
        if (!(player instanceof EntityPlayerMP) || order == null) {
            return;
        }

        UUID playerId = player.getUniqueID();
        if (getOrder(player) != order) {
            return;
        }
        var level = player.world;
        if (level == null) {
            return;
        }

        TickHandler.instance().addCallable(level, () -> {
            if (getOrder(player) != order) {
                return;
            }
            if (!hasTerminalOpen(player) || !order.retainAfterConfirmation()
                && player.openContainer instanceof ICraftingGridContainer) {
                pendingByPlayer.remove(playerId, order);
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
        var order = getOrder(player);
        if (order == null) {
            return;
        }
        var level = player.world;
        if (level == null) {
            return;
        }

        TickHandler.instance().addCallable(level, () -> {
            if (getOrder(player) != order) {
                return;
            }
            if (!hasTerminalOpen(player)) {
                pendingByPlayer.remove(playerId, order);
            }
        });
    }

    /**
     * True while the player is looking at a crafting terminal or one of its sub-GUIs, such as the crafting
     * confirmation or the crafting tree.
     */
    private static boolean hasTerminalOpen(EntityPlayer player) {
        var order = getOrder(player);
        if (player.openContainer instanceof ICraftingGridContainer terminal) {
            var node = terminal.getGridNode();
            return get(player, terminal, node == null ? null : node.grid()) != null;
        }
        if (player.openContainer instanceof ContainerCraftConfirm confirm) {
            return confirm.getPendingFillsOrder() == order;
        }
        return player.openContainer instanceof ContainerCraftingTree tree && tree.getPendingFillsOrder() == order;
    }

    private static final class PlayerSession {
        private final WeakReference<EntityPlayer> player;
        private final WeakReference<World> level;

        private PlayerSession(EntityPlayer player) {
            this.player = new WeakReference<>(player);
            this.level = new WeakReference<>(player.world);
        }
    }

    /**
     * Identity and submission ownership of one terminal order; it never holds a player or world.
     */
    public static final class Order {
        private final PlayerSession session;
        private final List<GridFill> fills;
        private final WeakReference<Object> destination;
        private final WeakReference<IGrid> grid;
        private int submitting;
        private boolean submitted;
        private boolean unconfirmed;

        private Order(PlayerSession session, List<GridFill> fills, Object destination, IGrid grid) {
            this.session = session;
            this.fills = fills;
            this.destination = new WeakReference<>(destination);
            this.grid = new WeakReference<>(grid);
        }

        private boolean retainAfterConfirmation() {
            return this.submitting > 0 || this.submitted || this.unconfirmed;
        }
    }

    /**
     * Owner-thread receipt. Closing without a definite result preserves an unconfirmed order.
     */
    public static final class Submission implements AutoCloseable {
        private final Order order;
        private boolean finished;

        private Submission(Order order) {
            this.order = order;
            order.submitting++;
        }

        public void complete(boolean submitted) {
            if (!this.finished) {
                this.finished = true;
                this.order.submitting--;
                this.order.submitted |= submitted;
            }
        }

        @Override
        public void close() {
            if (!this.finished) {
                this.finished = true;
                this.order.submitting--;
                this.order.unconfirmed = true;
            }
        }
    }
}
