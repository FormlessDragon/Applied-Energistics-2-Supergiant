package appeng.core.network.clientbound;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.container.me.common.ContainerMEStorage;
import appeng.container.me.common.GridInventoryEntry;
import appeng.container.me.common.IClientRepo;
import appeng.container.me.common.IncrementalUpdateHelper;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class MEInventoryUpdatePacket extends ClientboundPacket {
    private static final int UNCOMPRESSED_PACKET_BYTE_LIMIT = 512 * 1024;
    private static final int INITIAL_BUFFER_CAPACITY = 2 * 1024;

    private boolean fullUpdate;
    @Nullable
    private List<GridInventoryEntry> entries;
    private int encodedEntryCount;
    private byte[] encodedEntries;

    public MEInventoryUpdatePacket() {
    }

    public MEInventoryUpdatePacket(boolean fullUpdate, @Nullable List<GridInventoryEntry> entries,
                                   int encodedEntryCount, byte[] encodedEntries) {
        this.fullUpdate = fullUpdate;
        this.entries = entries;
        this.encodedEntryCount = encodedEntryCount;
        this.encodedEntries = encodedEntries;
    }

    public static Builder builder(boolean fullUpdate) {
        return new Builder(fullUpdate);
    }

    public static GridInventoryEntry readEntry(PacketBuffer buffer) {
        long serial = buffer.readVarLong();
        AEKey what = AEKey.readOptionalKey(buffer);
        long storedAmount = buffer.readVarLong();
        long requestableAmount = buffer.readVarLong();
        boolean craftable = buffer.readBoolean();
        return new GridInventoryEntry(serial, what, storedAmount, requestableAmount, craftable);
    }

    private static void writeEntry(PacketBuffer buffer, GridInventoryEntry entry) {
        buffer.writeVarLong(entry.serial());
        AEKey.writeOptionalKey(buffer, entry.what());
        buffer.writeVarLong(entry.storedAmount());
        buffer.writeVarLong(entry.requestableAmount());
        buffer.writeBoolean(entry.craftable());
    }

    private static List<GridInventoryEntry> decodeEntriesPayload(int entryCount, PacketBuffer data) {
        List<GridInventoryEntry> entries = new ObjectArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(readEntry(data));
        }
        return entries;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        this.fullUpdate = data.readBoolean();
        this.encodedEntryCount = data.readVarInt();
        this.encodedEntries = new byte[data.readInt()];
        data.readBytes(this.encodedEntries);
        this.entries = decodeEntriesPayload(this.encodedEntryCount,
            new PacketBuffer(Unpooled.wrappedBuffer(this.encodedEntries)));
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeBoolean(this.fullUpdate);

        byte[] payload;
        int entryCount;
        if (this.encodedEntries != null) {
            payload = this.encodedEntries;
            entryCount = this.encodedEntryCount;
        } else {
            PacketBuffer encoded = new PacketBuffer(Unpooled.buffer(INITIAL_BUFFER_CAPACITY));
            int count = 0;
            if (this.entries != null) {
                for (GridInventoryEntry entry : this.entries) {
                    writeEntry(encoded, entry);
                    count++;
                }
            }
            payload = new byte[encoded.writerIndex()];
            encoded.getBytes(0, payload);
            entryCount = count;
        }

        data.writeVarInt(entryCount);
        data.writeInt(payload.length);
        data.writeBytes(payload);
    }

    @Override
    public void handleClient(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (!(minecraft.player.openContainer instanceof ContainerMEStorage meContainer)) {
            return;
        }

        IClientRepo clientRepo = meContainer.getClientRepo();
        if (clientRepo == null) {
            AELog.info("Ignoring ME inventory update packet because no client repo is available.");
            return;
        }

        List<GridInventoryEntry> actualEntries = this.entries;
        if (actualEntries == null && this.encodedEntries != null) {
            actualEntries = decodeEntriesPayload(this.encodedEntryCount,
                new PacketBuffer(Unpooled.wrappedBuffer(this.encodedEntries)));
        }

        if (actualEntries != null) {
            clientRepo.handleUpdate(this.fullUpdate, actualEntries);
        }
    }

    public static class Builder {
        private final List<MEInventoryUpdatePacket> packets = new ObjectArrayList<>();
        private boolean fullUpdate;
        @Nullable
        private PacketBuffer encodedEntries;
        private int entryCount;
        @Nullable
        private AEKeyFilter filter;

        public Builder(boolean fullUpdate) {
            this.fullUpdate = fullUpdate;
        }

        public void setFilter(@Nullable AEKeyFilter filter) {
            this.filter = filter;
        }

        @SuppressWarnings("unused")
        public void addFull(IncrementalUpdateHelper updateHelper, KeyCounter networkStorage, Set<AEKey> craftables,
                            KeyCounter requestables) {
            Set<AEKey> keys = new ObjectOpenHashSet<>();
            keys.addAll(networkStorage.keySet());
            keys.addAll(craftables);
            keys.addAll(requestables.keySet());

            for (AEKey key : keys) {
                if (this.filter != null && !this.filter.matches(key)) {
                    continue;
                }

                long serial = updateHelper.getOrAssignSerial(key);
                add(new GridInventoryEntry(serial, key, networkStorage.get(key), requestables.get(key),
                    craftables.contains(key)));
            }
        }

        public void addChanges(IncrementalUpdateHelper updateHelper, KeyCounter networkStorage, Set<AEKey> craftables,
                               KeyCounter requestables) {
            for (AEKey key : updateHelper) {
                if (this.filter != null && !this.filter.matches(key)) {
                    continue;
                }

                AEKey sendKey;
                Long serial = updateHelper.getSerial(key);

                if (serial == null) {
                    sendKey = key;
                    serial = updateHelper.getOrAssignSerial(key);
                } else {
                    sendKey = null;
                }

                long storedAmount = networkStorage.get(key);
                boolean craftable = craftables.contains(key);
                long requestable = requestables.get(key);
                if (storedAmount <= 0 && requestable <= 0 && !craftable) {
                    add(new GridInventoryEntry(serial, sendKey, 0, 0, false));
                    updateHelper.removeSerial(key);
                } else {
                    add(new GridInventoryEntry(serial, sendKey, storedAmount, requestable, craftable));
                }
            }

            updateHelper.commitChanges();
        }

        public void add(GridInventoryEntry entry) {
            PacketBuffer data = ensureData();
            writeEntry(data, entry);
            ++this.entryCount;

            if (data.writerIndex() >= UNCOMPRESSED_PACKET_BYTE_LIMIT || this.entryCount >= Short.MAX_VALUE) {
                flushData();
            }
        }

        public List<MEInventoryUpdatePacket> build() {
            flushData();
            return this.packets;
        }

        public void buildAndSend(Consumer<MEInventoryUpdatePacket> sender) {
            for (MEInventoryUpdatePacket packet : build()) {
                sender.accept(packet);
            }
        }

        private PacketBuffer ensureData() {
            if (this.encodedEntries == null) {
                this.encodedEntries = new PacketBuffer(Unpooled.buffer(INITIAL_BUFFER_CAPACITY));
            }
            return this.encodedEntries;
        }

        private void flushData() {
            if (this.encodedEntries != null) {
                byte[] payload = new byte[this.encodedEntries.writerIndex()];
                this.encodedEntries.getBytes(0, payload);
                this.packets.add(new MEInventoryUpdatePacket(this.fullUpdate, null, this.entryCount,
                    payload));
                this.encodedEntries = null;
                this.entryCount = 0;
                this.fullUpdate = false;
            }
        }
    }
}
