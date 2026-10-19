package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.container.implementations.ContainerCraftingCPU;
import ae2.container.me.crafting.CraftingStatus;
import ae2.container.me.crafting.CraftingStatusEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CraftingStatusIdentityTest {
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

    @Test
    void packetCarriesCpuEpochAndStatusRevision() throws Exception {
        var packet = new CraftingStatusPacket(7, 12, 4, CraftingStatus.EMPTY);
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            var decoded = new CraftingStatusPacket();
            decoded.fromBytes(buffer);
            assertEquals(7, intField(decoded, "containerId"));
            assertEquals(12L, longField(decoded, "cpuEpoch"));
            assertEquals(4L, longField(decoded, "statusRevision"));
            assertNotNull(objectField(decoded, "status"));
        } finally {
            buffer.release();
        }
    }

    @Test
    void clientModelRejectsOldRevisionAndAcceptsDeltaAfterFull() throws Exception {
        var container = allocate(ContainerCraftingCPU.class);
        var full = CraftingStatus.EMPTY;
        var delta = new CraftingStatus(false, 0, 0, 0, List.of(), false, 0, null);

        container.acceptClientStatus(3, 1, full);
        assertEquals(full, container.consumeClientStatus());
        container.acceptClientStatus(3, 1, delta);
        assertNull(container.consumeClientStatus());
        container.acceptClientStatus(3, 2, delta);
        assertEquals(CraftingStatus.EMPTY, container.consumeClientStatus());
    }

    @Test
    void clientModelMergesDeltasBeforeGuiConsumes() throws Exception {
        var container = allocate(ContainerCraftingCPU.class);
        var firstKey = new TestKey("first");
        var secondKey = new TestKey("second");
        var full = new CraftingStatus(true, 0, 0, 0, List.of(
            new CraftingStatusEntry(1, firstKey, 10, 0, 0),
            new CraftingStatusEntry(2, secondKey, 20, 0, 0)), false, 0, null);
        var firstDelta = new CraftingStatus(false, 1, 9, 10, List.of(
            new CraftingStatusEntry(1, null, 8, 2, 0)), false, 0, null);
        var secondDelta = new CraftingStatus(false, 2, 8, 10, List.of(
            new CraftingStatusEntry(2, null, 17, 3, 0)), false, 0, null);

        container.acceptClientStatus(4, 1, full);
        assertEquals(full, container.consumeClientStatus());
        container.acceptClientStatus(4, 2, firstDelta);
        container.acceptClientStatus(4, 3, secondDelta);

        var merged = container.consumeClientStatus();
        assertNotNull(merged);
        assertEquals(2, merged.entries().size());
        assertEquals(8, merged.entries().get(0).storedAmount());
        assertEquals(17, merged.entries().get(1).storedAmount());
        assertEquals(2, merged.entries().get(0).activeAmount());
        assertEquals(3, merged.entries().get(1).activeAmount());
        assertEquals(2, merged.elapsedTime());
    }

    @Test
    void cpuSwitchClearsPreviousEntriesAndLateStatusCannotRestoreThem() throws Exception {
        var container = allocate(ContainerCraftingCPU.class);
        var firstKey = new TestKey("first");
        var secondKey = new TestKey("second");
        container.acceptClientStatus(4, 10, new CraftingStatus(true, 0, 0, 0,
            List.of(new CraftingStatusEntry(1, firstKey, 7, 0, 0)), false, 0, null));
        assertEquals(firstKey, container.consumeClientStatus().entries().getFirst().what());
        container.acceptClientStatus(5, 1, new CraftingStatus(true, 0, 0, 0,
            List.of(new CraftingStatusEntry(2, secondKey, 3, 0, 0)), false, 0, null));
        container.acceptClientStatus(4, 11, new CraftingStatus(true, 0, 0, 0,
            List.of(new CraftingStatusEntry(1, firstKey, 99, 0, 0)), false, 0, null));
        var current = container.consumeClientStatus();
        assertEquals(1, current.entries().size());
        assertEquals(secondKey, current.entries().getFirst().what());
        container.acceptClientStatus(5, 2, new CraftingStatus(false, 0, 0, 0,
            List.of(new CraftingStatusEntry(2, null, 0, 0, 0)), false, 0, null));
        assertEquals(CraftingStatus.EMPTY, container.consumeClientStatus());
        container.acceptClientStatus(5, 1, current);
        assertNull(container.consumeClientStatus());
    }

    private static final class TestKey extends AEKey {
        private final String name;

        private TestKey(String name) {
            this.name = name;
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public NBTTagCompound toTag() {
            return new NBTTagCompound();
        }

        @Override
        public Object getPrimaryKey() {
            return name;
        }

        @Override
        public ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(PacketBuffer data) {
        }

        @Override
        public Object getReadOnlyStack() {
            return this;
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString(name);
        }

        @Override
        public NBTBase get(String componentId) {
            return null;
        }
    }
}
