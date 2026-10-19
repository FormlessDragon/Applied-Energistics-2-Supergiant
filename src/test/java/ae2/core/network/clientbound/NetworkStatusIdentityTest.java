package ae2.core.network.clientbound;

import ae2.api.networking.IGrid;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.pathing.IPathingService;
import ae2.api.stacks.AEItemKey;
import ae2.container.implementations.ContainerNetworkStatus;
import ae2.container.networking.NetworkStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkStatusIdentityTest {
    private static int intField(Object instance, String name) throws Exception {
        return field(instance, name).getInt(instance);
    }

    private static long longField(Object instance, String name) throws Exception {
        return field(instance, name).getLong(instance);
    }

    private static Object objectField(Object instance, String name) throws Exception {
        return field(instance, name).get(instance);
    }

    private static Field field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static IGrid emptyGridProxy() {
        var services = Proxy.newProxyInstance(NetworkStatusIdentityTest.class.getClassLoader(),
            new Class<?>[]{IEnergyService.class, IPathingService.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUsedChannels")) {
                    return 0;
                }
                return primitiveDefault(method);
            });
        return (IGrid) Proxy.newProxyInstance(NetworkStatusIdentityTest.class.getClassLoader(),
            new Class<?>[]{IGrid.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getEnergyService", "getPathingService", "getService" -> services;
                    case "getMachineClasses", "getNodes" -> List.of();
                    case "isEmpty" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "empty-grid";
                    default -> primitiveDefault(method);
                };
            });
    }

    private static Object primitiveDefault(Method method) {
        if (!method.getReturnType().isPrimitive()) {
            return null;
        }
        if (method.getReturnType() == boolean.class) {
            return false;
        }
        if (method.getReturnType() == long.class) {
            return 0L;
        }
        if (method.getReturnType() == double.class) {
            return 0D;
        }
        if (method.getReturnType() == float.class) {
            return 0F;
        }
        if (method.getReturnType() == char.class) {
            return '\0';
        }
        return 0;
    }

    @Test
    void statusPacketRoundTripsWindowSourceAndRevision() throws Exception {
        var packet = new NetworkStatusPacket(77, 123L, 9L, new NetworkStatus(), true);
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new NetworkStatusPacket();
            decoded.fromBytes(buffer);
            assertEquals(77, intField(decoded, "windowId"));
            assertEquals(123L, longField(decoded, "sourceGeneration"));
            assertEquals(9L, longField(decoded, "snapshotRevision"));
            assertNotNull(objectField(decoded, "status"));
        } finally {
            buffer.release();
        }
    }

    @Test
    void sourceChangeResetsRevisionButDelayedOldSourceIsRejected() throws Exception {
        var container = allocate(ContainerNetworkStatus.class);
        container.windowId = 77;
        assertTrue(container.acceptStatusIdentity(77, 10, 9));
        assertFalse(container.acceptStatusIdentity(77, 10, 9));
        assertTrue(container.acceptStatusIdentity(77, 11, 1));
        assertFalse(container.acceptStatusIdentity(77, 10, 10));
        assertFalse(container.acceptStatusIdentity(76, 12, 1));
    }

    @Test
    void statusPagesPublishOnlyAfterAllPagesAndOversizeClearsView() throws Exception {
        var container = allocate(ContainerNetworkStatus.class);
        container.windowId = 77;
        assertTrue(container.acceptStatusPage(77, 10, 1, 0, 1, false, new NetworkStatus(), true));
        assertTrue(field(container, "canExportGrid").getBoolean(container));
        assertFalse(container.acceptStatusPage(76, 10, 2, 0, 1, true, null, false));
        assertTrue(field(container, "canExportGrid").getBoolean(container));
        assertFalse(container.acceptStatusPage(77, 9, 2, 0, 1, true, null, false));
        assertTrue(field(container, "canExportGrid").getBoolean(container));

        assertTrue(container.acceptStatusPage(77, 10, 2, 0, 1, true, null, true));
        assertFalse(field(container, "canExportGrid").getBoolean(container));

        container = allocate(ContainerNetworkStatus.class);
        container.windowId = 77;
        assertFalse(container.acceptStatusPage(77, 10, 1, 0, 2, false, new NetworkStatus(), true));
        assertTrue(container.acceptStatusPage(77, 10, 1, 1, 2, false, new NetworkStatus(), true));
        assertTrue(field(container, "canExportGrid").getBoolean(container));
        assertFalse(container.acceptStatusPage(77, 10, 2, 0, 2, false, new NetworkStatus(), false));
        assertTrue(container.acceptStatusPage(77, 10, 2, 1, 2, false, new NetworkStatus(), false));
        assertFalse(field(container, "canExportGrid").getBoolean(container));
        assertTrue(container.acceptStatusPage(77, 10, 3, 0, 1, true, null, true));
        assertFalse(field(container, "canExportGrid").getBoolean(container));
        assertNotNull(container.getStatus());
    }

    @Test
    void duplicateAndOutOfOrderPagesDoNotPublishTwiceOrReplaceTheCompletedSnapshot() throws Exception {
        var container = allocate(ContainerNetworkStatus.class);
        container.windowId = 77;

        assertFalse(container.acceptStatusPage(77, 10, 1, 1, 2, false, new NetworkStatus(), true));
        assertFalse(container.acceptStatusPage(77, 10, 1, 1, 2, false, new NetworkStatus(), false));
        assertTrue(container.acceptStatusPage(77, 10, 1, 0, 2, false, new NetworkStatus(), true));
        assertTrue(field(container, "canExportGrid").getBoolean(container));

        assertFalse(container.acceptStatusPage(77, 10, 1, 0, 2, false, new NetworkStatus(), false));
        assertTrue(field(container, "canExportGrid").getBoolean(container));
    }

    @Test
    void newSourceWithLowerRevisionCannotReusePagesOrAcceptThePreviousSource() throws Exception {
        var container = allocate(ContainerNetworkStatus.class);
        container.windowId = 77;
        assertTrue(container.acceptStatusPage(77, 10, 20, 0, 1, false, new NetworkStatus(), true));
        assertFalse(container.acceptStatusPage(77, 11, 1, 1, 2, false, new NetworkStatus(), false));
        assertFalse(container.acceptStatusPage(77, 10, 21, 0, 2, false, new NetworkStatus(), true));
        assertTrue(container.acceptStatusPage(77, 11, 1, 0, 2, false, new NetworkStatus(), false));
        assertFalse(field(container, "canExportGrid").getBoolean(container));
        assertFalse(container.acceptStatusPage(77, 10, 22, 0, 1, false, new NetworkStatus(), true));
    }

    @Test
    void machineGroupsBeyondOneWirePageAreCombinedWithoutLosingTheLastEntry() throws Exception {
        Bootstrap.register();
        var container = allocate(ContainerNetworkStatus.class);
        container.windowId = 77;
        for (int pageIndex : new int[]{1, 0}) {
            var data = new PacketBuffer(Unpooled.buffer());
            NetworkStatus page;
            try {
                for (int i = 0; i < 4; i++) data.writeDouble(17 + i);
                for (int i = 0; i < 4; i++) data.writeBoolean(false);
                data.writeDouble(4);
                data.writeVarInt(9);
                int count = pageIndex == 0 ? NetworkStatus.MAX_MACHINE_GROUPS : 1;
                data.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    AEItemKey.of(Items.APPLE).writeToPacket(data);
                    data.writeBoolean(false);
                    data.writeDouble(i);
                    data.writeDouble(0);
                    data.writeVarInt(pageIndex * NetworkStatus.MAX_MACHINE_GROUPS + i + 1);
                }
                page = NetworkStatus.read(data);
            } finally {
                data.release();
            }
            var wire = Unpooled.buffer();
            try {
                new NetworkStatusPacket(77, 10, 1, pageIndex, 2, false, page, true).toBytes(wire);
                var decoded = new NetworkStatusPacket();
                decoded.fromBytes(wire);
                assertEquals(0, wire.readableBytes());
                assertEquals(pageIndex == 0, container.acceptStatusPage(intField(decoded, "windowId"),
                    longField(decoded, "sourceGeneration"), longField(decoded, "snapshotRevision"),
                    intField(decoded, "pageIndex"), intField(decoded, "pageCount"), false,
                    (NetworkStatus) objectField(decoded, "status"), true));
            } finally {
                wire.release();
            }
        }
        var groups = container.getStatus().getGroupedMachines();
        assertEquals(NetworkStatus.MAX_MACHINE_GROUPS + 1, groups.size());
        for (int i = 0; i < groups.size(); i++) assertEquals(i + 1, groups.get(i).getCount());
        assertEquals(19, container.getStatus().getStoredPower());
    }

    @Test
    void sharedSnapshotIsReusedOnlyWithinOneGridWindow() {
        var grid = emptyGridProxy();
        var first = NetworkStatus.fromGridShared(grid, 100);
        assertSame(first, NetworkStatus.fromGridShared(grid, 110));
        assertNotSame(first, NetworkStatus.fromGridShared(grid, 116));
    }
}
