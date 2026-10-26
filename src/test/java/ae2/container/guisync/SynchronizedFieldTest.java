package ae2.container.guisync;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.invoke.VarHandle;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedFieldTest {
    @BeforeAll
    static void registerCustomField() {
        SynchronizedField.register(CustomValue.class, (source, varHandle, _) -> new CustomValueField(source, varHandle));
    }

    @Test
    void synchronizesPrivatePrimitiveAndRegisteredCustomFields() {
        var server = new Host();
        server.number = 42;
        server.custom = new CustomValue(1234);
        var client = new Host();

        var serverSync = new DataSynchronization(server);
        var clientSync = new DataSynchronization(client);
        ByteBuf data = Unpooled.buffer();
        try {
            serverSync.writeFull(data);
            clientSync.readUpdate(data);
        } finally {
            data.release();
        }

        assertEquals(42, client.number);
        assertEquals(new CustomValue(1234), client.custom);
        assertFalse(serverSync.hasChanges());
        assertFalse(clientSync.hasChanges());
    }

    private static Stream<Arguments> numericFields() {
        return Stream.of(
            Arguments.of("number", 1234, Integer.MIN_VALUE, Integer.BYTES),
            Arguments.of("boxedNumber", 1234, Integer.MIN_VALUE, Integer.BYTES),
            Arguments.of("largeNumber", 1234567890123L, Long.MIN_VALUE, Long.BYTES),
            Arguments.of("boxedLargeNumber", 1234567890123L, Long.MIN_VALUE, Long.BYTES),
            Arguments.of("fraction", 1234.5, Double.POSITIVE_INFINITY, Double.BYTES),
            Arguments.of("boxedFraction", 1234.5, Double.POSITIVE_INFINITY, Double.BYTES),
            Arguments.of("enabled", true, false, 1),
            Arguments.of("boxedEnabled", true, false, 1));
    }

    private static Stream<String> doubleFields() {
        return Stream.of("fraction", "boxedFraction");
    }

    private static Stream<Arguments> primitiveFields() {
        return numericFields().filter(arguments -> !((String) arguments.get()[0]).startsWith("boxed"));
    }

    private static Stream<String> boxedFields() {
        return Stream.of("boxedNumber", "boxedLargeNumber", "boxedFraction", "boxedEnabled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"number", "boxedNumber"})
    void rejectsFinalFieldsBeforeSynchronization(String fieldName) throws NoSuchFieldException {
        var field = ImmutableHost.class.getDeclaredField(fieldName);
        var error = assertThrows(IllegalArgumentException.class,
            () -> SynchronizedField.create(new ImmutableHost(), field));
        assertTrue(error.getMessage().contains("final field"));
        assertTrue(error.getMessage().contains(fieldName));
    }

    @ParameterizedTest
    @MethodSource("numericFields")
    void synchronizesPrimitiveAndBoxedValues(String fieldName, Object value, Object updatedValue, int wireBytes)
        throws NoSuchFieldException, IllegalAccessException {
        var server = new NumericHost();
        var client = new NumericHost();
        var field = NumericHost.class.getDeclaredField(fieldName);
        var serverField = SynchronizedField.create(server, field);
        var clientField = SynchronizedField.create(client, field);
        assertTrue(serverField.hasChanges());
        assertTrue(clientField.hasChanges());

        ByteBuf data = Unpooled.buffer();
        try {
            field.set(server, value);
            serverField.write(data);
            assertEquals(wireBytes, data.readableBytes());
            clientField.read(data);
            assertEquals(value, field.get(client));
            assertFalse(data.isReadable());
            assertFalse(serverField.hasChanges());
            assertFalse(clientField.hasChanges());

            field.set(server, updatedValue);
            assertTrue(serverField.hasChanges());
            data.clear();
            serverField.write(data);
            clientField.read(data);
            assertEquals(updatedValue, field.get(client));
            assertFalse(serverField.hasChanges());
            assertFalse(clientField.hasChanges());

            field.set(client, value);
            assertTrue(clientField.hasChanges());
        } finally {
            data.release();
        }
    }

    @Test
    void sendsInitialPrimitiveDefaultsAndOnlyChangedFieldsAfterwards() {
        var server = new NumericHost();
        var client = new NumericHost();
        client.number = 42;
        client.largeNumber = 1234567890123L;
        client.fraction = 1.5;
        client.enabled = true;
        var serverSync = new DataSynchronization(server);
        var clientSync = new DataSynchronization(client);
        assertTrue(serverSync.hasChanges());

        ByteBuf data = Unpooled.buffer();
        try {
            serverSync.writeUpdate(data);
            var updatedFields = clientSync.readUpdate(data);
            assertEquals(4, updatedFields.size());
            assertEquals(0, client.number);
            assertEquals(0L, client.largeNumber);
            assertEquals(0.0, client.fraction);
            assertFalse(client.enabled);
            assertFalse(serverSync.hasChanges());
            assertFalse(clientSync.hasChanges());

            data.clear();
            serverSync.writeUpdate(data);
            assertEquals(Short.BYTES, data.readableBytes());
            assertTrue(clientSync.readUpdate(data).isEmpty());

            server.largeNumber = Long.MAX_VALUE;
            server.enabled = true;
            assertTrue(serverSync.hasChanges());
            data.clear();
            serverSync.writeUpdate(data);
            updatedFields = clientSync.readUpdate(data);
            assertEquals(2, updatedFields.size());
            assertTrue(updatedFields.contains((short) 2));
            assertTrue(updatedFields.contains((short) 4));
            assertEquals(Long.MAX_VALUE, client.largeNumber);
            assertTrue(client.enabled);
            assertFalse(serverSync.hasChanges());
            assertFalse(clientSync.hasChanges());
        } finally {
            data.release();
        }
    }

    @ParameterizedTest
    @MethodSource("doubleFields")
    void preservesNaNAndSignedZeroChangeDetection(String fieldName)
        throws NoSuchFieldException, IllegalAccessException {
        var host = new NumericHost();
        var field = NumericHost.class.getDeclaredField(fieldName);
        var synchronizedField = SynchronizedField.create(host, field);
        ByteBuf data = Unpooled.buffer();
        try {
            field.set(host, Double.NaN);
            synchronizedField.write(data);
            assertFalse(synchronizedField.hasChanges());
            field.set(host, Double.longBitsToDouble(0x7ff0000000000001L));
            assertFalse(synchronizedField.hasChanges());

            field.set(host, 0.0);
            assertTrue(synchronizedField.hasChanges());
            data.clear();
            synchronizedField.write(data);
            field.set(host, -0.0);
            assertTrue(synchronizedField.hasChanges());
            data.clear();
            synchronizedField.write(data);
            assertEquals(Long.MIN_VALUE, data.readLong());
            assertFalse(synchronizedField.hasChanges());
        } finally {
            data.release();
        }
    }

    @ParameterizedTest
    @MethodSource("primitiveFields")
    void retainsPreviousSnapshotWhenPrimitiveWriteFails(String fieldName, Object value, Object updatedValue,
                                                        int wireBytes)
        throws NoSuchFieldException, IllegalAccessException {
        var host = new NumericHost();
        var field = NumericHost.class.getDeclaredField(fieldName);
        var synchronizedField = SynchronizedField.create(host, field);
        ByteBuf data = Unpooled.buffer(wireBytes, wireBytes);
        try {
            field.set(host, value);
            synchronizedField.write(data);
            field.set(host, updatedValue);
            assertThrows(IndexOutOfBoundsException.class, () -> synchronizedField.write(data));
            assertTrue(synchronizedField.hasChanges());
        } finally {
            data.release();
        }
    }

    @ParameterizedTest
    @MethodSource("boxedFields")
    void detectsBoxedNullTransitions(String fieldName) throws NoSuchFieldException, IllegalAccessException {
        var host = new NumericHost();
        var field = NumericHost.class.getDeclaredField(fieldName);
        var synchronizedField = SynchronizedField.create(host, field);
        Object initialValue = field.get(host);
        field.set(host, null);
        assertFalse(synchronizedField.hasChanges());
        field.set(host, initialValue);
        assertTrue(synchronizedField.hasChanges());

        ByteBuf data = Unpooled.buffer();
        try {
            synchronizedField.write(data);
            assertFalse(synchronizedField.hasChanges());
            field.set(host, null);
            assertTrue(synchronizedField.hasChanges());
        } finally {
            data.release();
        }
    }

    private static final class ImmutableHost {
        private final int number = 1;
        private final Integer boxedNumber = 1;
    }

    // Tests write these fields through reflection and synchronization VarHandles.
    @SuppressWarnings("FieldMayBeFinal")
    private static final class NumericHost {
        @GuiSync(1)
        private int number;

        @GuiSync(2)
        private long largeNumber;

        @GuiSync(3)
        private double fraction;

        @GuiSync(4)
        private boolean enabled;

        private Integer boxedNumber = 0;
        private Long boxedLargeNumber = 0L;
        private Double boxedFraction = 0.0;
        private Boolean boxedEnabled = false;
    }

    private static final class Host {
        @GuiSync(1)
        private int number;

        @GuiSync(2)
        private CustomValue custom = new CustomValue(0);
    }

    private record CustomValue(int value) {
    }

    private static final class CustomValueField extends SynchronizedField {
        private CustomValueField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            data.writeInt(((CustomValue) get()).value());
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(new CustomValue(data.readInt()));
        }
    }
}
