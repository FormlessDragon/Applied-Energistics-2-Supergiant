package ae2.client.gui.me.requester;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.implementations.GuiRequester;
import ae2.container.implementations.ContainerRequesterTerm;
import ae2.container.me.common.AbstractContainerRequester;
import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.helpers.RequesterTerminalHost;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterSourceIdentityTest {
    private static HeadlessTerminal terminal(IGrid[] source) throws Exception {
        var terminal = allocate(HeadlessTerminal.class);
        terminal.windowId = 7;
        var node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
            new Class<?>[]{IGridNode.class}, (_, method, _) -> switch (method.getName()) {
                case "isActive" -> source[0] != null;
                case "grid" -> source[0];
                default -> null;
            });
        RequesterTerminalHost host = () -> node;
        set(ContainerRequesterTerm.class, terminal, "host", host);
        set(ContainerRequesterTerm.class, terminal, "byId", new Long2ObjectOpenHashMap<>());
        set(ContainerRequesterTerm.class, terminal, "byRequesters", new IdentityHashMap<>());
        return terminal;
    }

    private static IGrid grid() {
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
            (_, method, _) -> switch (method.getName()) {
                case "getActiveMachines" -> Set.of();
                case "getActiveMachineSetRevision" -> -1L;
                default -> null;
            });
    }

    private static IGrid grid(int[] enumerations) {
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
            (_, method, _) -> switch (method.getName()) {
                case "getActiveMachines" -> {
                    enumerations[0]++;
                    yield Set.of();
                }
                case "getActiveMachineSetRevision" -> 0L;
                default -> null;
            });
    }

    private static void synchronize(HeadlessTerminal terminal) throws Exception {
        var method = ContainerRequesterTerm.class.getDeclaredMethod("synchronizeRequesters");
        method.setAccessible(true);
        try {
            method.invoke(terminal);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static long generation(HeadlessTerminal terminal) throws Exception {
        return field(AbstractContainerRequester.class, "syncGeneration").getLong(terminal);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        field(owner, name).set(target, value);
    }

    @Test
    void singleRequesterClearAndRemovalReleaseTheClientReference() throws Exception {
        var gui = allocate(GuiRequester.class);
        var requester = new ClientRequester(4, null, 0, 1);
        set(GuiRequester.class, gui, "requesterReference", requester);
        gui.postRemoveRequester(3);
        assertSame(requester, field(GuiRequester.class, "requesterReference").get(gui));
        gui.postRemoveRequester(4);
        assertNull(field(GuiRequester.class, "requesterReference").get(gui));
        set(GuiRequester.class, gui, "requesterReference", requester);
        gui.postClearAll();
        assertNull(field(GuiRequester.class, "requesterReference").get(gui));
    }

    @Test
    void requesterRemovalKeepsOtherClientIdentityAndDefersOneRefresh() throws Exception {
        var gui = allocate(GuiRequesterTerm.class);
        var first = new ClientRequester(1, null, 0, 1);
        var second = new ClientRequester(2, null, 0, 1);
        var byId = new Long2ObjectOpenHashMap<ClientRequester>();
        byId.put(1, first);
        byId.put(2, second);
        set(GuiRequesterTerm.class, gui, "byId", byId);
        gui.postRemoveRequester(1);
        assertFalse(byId.containsKey(1));
        assertSame(second, byId.get(2));
        assertTrue(field(AbstractGuiRequester.class, "listRefreshPending").getBoolean(gui));
        field(AbstractGuiRequester.class, "listRefreshPending").setBoolean(gui, false);
        gui.postRemoveRequester(1);
        assertFalse(field(AbstractGuiRequester.class, "listRefreshPending").getBoolean(gui));
    }

    @Test
    void guiRejectsOtherWindowsAndOlderGenerationsAndDefersViewRefresh() throws Exception {
        var terminal = allocate(HeadlessTerminal.class);
        terminal.windowId = 7;
        var gui = allocate(HeadlessGui.class);
        set(AEBaseGui.class, gui, "container", terminal);
        assertFalse(gui.acceptSyncIdentity(8, 10));
        assertFalse(gui.acceptSyncIdentity(7, 0));
        assertEquals(0, gui.clears);
        assertTrue(gui.acceptSyncIdentity(7, 10));
        assertEquals(1, gui.clears);
        assertEquals(0, gui.refreshes);
        assertTrue(gui.acceptSyncIdentity(7, 10));
        assertEquals(1, gui.clears);
        assertTrue(gui.acceptSyncIdentity(7, 12));
        assertEquals(2, gui.clears);
        assertFalse(gui.acceptSyncIdentity(7, 10));
        assertEquals(2, gui.clears);
        assertTrue(field(AbstractGuiRequester.class, "listRefreshPending").getBoolean(gui));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sourceSwitchAdvancesGenerationAndOldSenderRetainsItsOriginalIdentity() throws Exception {
        IGrid[] source = {grid()};
        var terminal = terminal(source);
        synchronize(terminal);
        long first = generation(terminal);
        int sent = terminal.sendAttempts;
        var oldSender = (Consumer<RequesterSyncPacket>) field(AbstractContainerRequester.class,
            "requesterPacketSender").get(terminal);
        synchronize(terminal);
        assertEquals(first, generation(terminal));
        assertEquals(sent, terminal.sendAttempts);
        source[0] = null;
        synchronize(terminal);
        long disconnected = generation(terminal);
        assertTrue(disconnected > first);
        source[0] = grid();
        synchronize(terminal);
        assertTrue(generation(terminal) > disconnected);
        var oldPacket = RequesterSyncPacket.clearAll();
        oldSender.accept(oldPacket);
        assertEquals(first, field(RequesterSyncPacket.class, "sourceGeneration").getLong(oldPacket));
        assertEquals(7, field(RequesterSyncPacket.class, "windowId").getInt(oldPacket));
    }

    @Test
    void equalRevisionOnDifferentGridsDoesNotReuseThePreviousDirectory() throws Exception {
        int[] first = {0};
        int[] second = {0};
        IGrid[] source = {grid(first)};
        var terminal = terminal(source);
        synchronize(terminal);
        synchronize(terminal);
        assertEquals(1, first[0]);
        source[0] = grid(second);
        terminal.failSend = true;
        assertThrows(IllegalStateException.class, () -> synchronize(terminal));
        terminal.failSend = false;
        synchronize(terminal);
        synchronize(terminal);
        assertEquals(1, first[0]);
        assertEquals(1, second[0]);
    }

    @Test
    void failedSourceClearIsRetriedEvenWhenBothDirectoriesAreEmpty() throws Exception {
        var terminal = terminal(new IGrid[]{null});
        terminal.failSend = true;
        assertThrows(IllegalStateException.class, () -> synchronize(terminal));
        long failedGeneration = generation(terminal);
        assertTrue(field(ContainerRequesterTerm.class, "sourceResetPending").getBoolean(terminal));
        terminal.failSend = false;
        synchronize(terminal);
        assertEquals(failedGeneration, generation(terminal));
        assertFalse(field(ContainerRequesterTerm.class, "sourceResetPending").getBoolean(terminal));
        assertEquals(2, terminal.sendAttempts);
        synchronize(terminal);
        assertEquals(2, terminal.sendAttempts);
    }

    private static final class HeadlessTerminal extends ContainerRequesterTerm {
        private int sendAttempts;
        private boolean failSend;

        private HeadlessTerminal() {
            super(null, null);
        }

        @Override
        public EntityPlayer getPlayer() {
            this.sendAttempts++;
            if (this.failSend) {
                throw new IllegalStateException("send failed");
            }
            return null;
        }
    }

    private static final class HeadlessGui extends AbstractGuiRequester<HeadlessTerminal> {
        private int clears;
        private int refreshes;

        private HeadlessGui() {
            super(null, null, null, null, null);
        }

        @Override
        protected void clearData() {
            this.clears++;
        }

        @Override
        protected void refreshList() {
            this.refreshes++;
        }

        @Override
        protected Collection<?> getByName(String name) {
            return List.of();
        }

        @Override
        protected ClientRequester getById(long id, ITextComponent name, long sort, int count) {
            return null;
        }

        @Override
        protected ClientRequester findById(long id) {
            return null;
        }

        @Override
        protected boolean removeRequester(long id) {
            return false;
        }

        @Override
        protected Rectangle getFooterBounds() {
            return null;
        }
    }
}
