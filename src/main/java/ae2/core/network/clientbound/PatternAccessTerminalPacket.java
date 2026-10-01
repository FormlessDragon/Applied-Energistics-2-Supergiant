package ae2.core.network.clientbound;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.client.gui.me.patternaccess.IPatternProviderDisplay;
import ae2.core.AELog;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Delivers one pattern provider inventory update to a Pattern Access Terminal.
 *
 * <p>The provider state is encoded into a bounded raw payload before it is written to the wire. Large encoded
 * payloads are delivered by {@link PatternAccessTerminalChunkPacket}; this packet is intentionally only the
 * single-packet form of the same protocol.</p>
 */
public class PatternAccessTerminalPacket extends ClientboundPacket {
    static final int MAX_SLOT_UPDATES = 4096;
    static final int MAX_INVENTORY_SIZE = 4096;
    static final int MAX_RAW_PAYLOAD_BYTES = 8 * 1_048_576;
    static final int MAX_WIRE_PAYLOAD_BYTES = 261_120;

    private boolean fullUpdate;
    private long inventoryId;
    private int inventorySize;
    private long sortBy;
    private boolean canEditTerminalName;
    private boolean canModifyTerminalVisibility;
    private boolean readOnly;
    private PatternContainerGroup group;
    private Int2ObjectMap<ItemStack> slots = new Int2ObjectOpenHashMap<>();

    private boolean compressed;
    private int uncompressedLength;
    private byte[] payload = new byte[0];
    private boolean wirePayload;
    private boolean malformed;

    public PatternAccessTerminalPacket() {
    }

    private PatternAccessTerminalPacket(boolean fullUpdate, long inventoryId, int inventorySize, long sortBy,
                                        boolean canEditTerminalName, boolean canModifyTerminalVisibility,
                                        boolean readOnly, PatternContainerGroup group,
                                        Int2ObjectMap<ItemStack> slots) {
        this.fullUpdate = fullUpdate;
        this.inventoryId = inventoryId;
        this.inventorySize = inventorySize;
        this.sortBy = sortBy;
        this.canEditTerminalName = canEditTerminalName;
        this.canModifyTerminalVisibility = canModifyTerminalVisibility;
        this.readOnly = readOnly;
        this.group = group;
        this.slots = slots;
    }

    private PatternAccessTerminalPacket(boolean compressed, int uncompressedLength, byte[] payload) {
        this.compressed = compressed;
        this.uncompressedLength = uncompressedLength;
        this.payload = Arrays.copyOf(payload, payload.length);
        this.wirePayload = true;
    }

    public static PatternAccessTerminalPacket fullUpdate(long inventoryId, int inventorySize, long sortBy,
                                                         boolean canEditTerminalName, boolean canModifyTerminalVisibility,
                                                         boolean readOnly, PatternContainerGroup group,
                                                         Int2ObjectMap<ItemStack> slots) {
        return new PatternAccessTerminalPacket(true, inventoryId, inventorySize, sortBy, canEditTerminalName,
            canModifyTerminalVisibility, readOnly, group, slots);
    }

    public static PatternAccessTerminalPacket incrementalUpdate(long inventoryId, Int2ObjectMap<ItemStack> slots) {
        return new PatternAccessTerminalPacket(false, inventoryId, 0, 0, false, false, false, null, slots);
    }

    /**
     * Builds the bounded wire packets for a provider update.
     *
     * <p>There is deliberately no oversized direct-packet fallback. A malformed or oversized provider update returns
     * {@code null} so the Pattern Access server synchronization can clear and rebuild the complete authoritative
     * provider list.</p>
     */
    public static @Nullable List<ClientboundPacket> createPackets(PatternAccessTerminalPacket update, int windowId) {
        final byte[] rawPayload;
        try {
            rawPayload = update.serializeRaw();
        } catch (RuntimeException e) {
            AELog.error(e, String.format("Could not serialize Pattern Access Terminal update for inventory %s",
                update.inventoryId));
            return null;
        }

        if (rawPayload.length > MAX_RAW_PAYLOAD_BYTES) {
            AELog.error("Pattern Access Terminal update for inventory %s exceeded raw payload limit: %s bytes",
                update.inventoryId, rawPayload.length);
            return null;
        }

        final TerminalPayloadCodec.EncodedPayload encoded;
        try {
            encoded = TerminalPayloadCodec.encode(rawPayload, MAX_RAW_PAYLOAD_BYTES);
        } catch (RuntimeException e) {
            AELog.error(e, String.format("Could not compress Pattern Access Terminal update for inventory %s",
                update.inventoryId));
            return null;
        }

        byte[] encodedPayload = encoded.payload();
        if (encodedPayload.length <= MAX_WIRE_PAYLOAD_BYTES) {
            return List.of(new PatternAccessTerminalPacket(encoded.compressed(), encoded.uncompressedLength(),
                encodedPayload));
        }

        int totalChunks = (encodedPayload.length + PatternAccessTerminalChunkPacket.MAX_CHUNK_PAYLOAD_BYTES - 1)
            / PatternAccessTerminalChunkPacket.MAX_CHUNK_PAYLOAD_BYTES;
        if (totalChunks > PatternAccessTerminalChunkPacket.MAX_CHUNK_COUNT) {
            AELog.error("Pattern Access Terminal update for inventory %s requires too many chunks: %s",
                update.inventoryId, totalChunks);
            return null;
        }

        int transferId = PatternAccessTerminalChunkPacket.nextTransferId();
        List<ClientboundPacket> result = new ObjectArrayList<>(totalChunks);
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            int offset = chunkIndex * PatternAccessTerminalChunkPacket.MAX_CHUNK_PAYLOAD_BYTES;
            int length = Math.min(PatternAccessTerminalChunkPacket.MAX_CHUNK_PAYLOAD_BYTES,
                encodedPayload.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(encodedPayload, offset, chunk, 0, length);
            result.add(new PatternAccessTerminalChunkPacket(windowId, update.inventoryId, transferId, chunkIndex,
                totalChunks, encoded.compressed(), encoded.uncompressedLength(), encodedPayload.length, chunk));
        }
        return result;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        try {
            this.compressed = data.readBoolean();
            this.uncompressedLength = data.readVarInt();
            int payloadLength = data.readVarInt();
            if (payloadLength < 0 || payloadLength > MAX_WIRE_PAYLOAD_BYTES || payloadLength > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid Pattern Access Terminal wire payload length: " + payloadLength);
            }
            this.payload = new byte[payloadLength];
            data.readBytes(this.payload);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Trailing bytes in Pattern Access Terminal packet");
            }
            PatternAccessTerminalPacket decoded = decodePayload(this.compressed, this.uncompressedLength, this.payload);
            copyDecodedState(decoded);
        } catch (RuntimeException e) {
            this.malformed = true;
            buf.skipBytes(buf.readableBytes());
            AELog.warn(e, "Ignoring malformed Pattern Access Terminal packet");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        if (this.wirePayload) {
            writeEncoded(buf, this.compressed, this.uncompressedLength, this.payload);
            return;
        }
        TerminalPayloadCodec.EncodedPayload encoded = TerminalPayloadCodec.encode(serializeRaw(), MAX_RAW_PAYLOAD_BYTES);
        if (encoded.payload().length > MAX_WIRE_PAYLOAD_BYTES) {
            throw new IllegalStateException("Pattern Access Terminal direct packet exceeds wire payload limit");
        }
        writeEncoded(buf, encoded.compressed(), encoded.uncompressedLength(), encoded.payload());
    }

    static void writeEncoded(ByteBuf buf, boolean compressed, int uncompressedLength, byte[] payload) {
        if (payload.length > MAX_WIRE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Pattern Access Terminal wire payload exceeds direct packet limit");
        }
        PacketBuffer data = new PacketBuffer(buf);
        data.writeBoolean(compressed);
        data.writeVarInt(uncompressedLength);
        data.writeVarInt(payload.length);
        data.writeBytes(payload);
    }

    private byte[] serializeRaw() {
        ByteBuf buffer = Unpooled.buffer(256, MAX_RAW_PAYLOAD_BYTES);
        try {
            writeRaw(new PacketBuffer(buffer));
            if (buffer.readableBytes() > MAX_RAW_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Pattern Access Terminal raw payload exceeds " + MAX_RAW_PAYLOAD_BYTES
                    + " bytes");
            }
            byte[] raw = new byte[buffer.readableBytes()];
            buffer.readBytes(raw);
            return raw;
        } finally {
            buffer.release();
        }
    }

    private void writeRaw(PacketBuffer packetBuffer) {
        validateLogicalState();
        packetBuffer.writeVarLong(this.inventoryId);
        packetBuffer.writeBoolean(this.fullUpdate);
        if (this.fullUpdate) {
            packetBuffer.writeVarInt(this.inventorySize);
            packetBuffer.writeVarLong(this.sortBy);
            packetBuffer.writeBoolean(this.canEditTerminalName);
            packetBuffer.writeBoolean(this.canModifyTerminalVisibility);
            packetBuffer.writeBoolean(this.readOnly);
            this.group.writeToPacket(packetBuffer);
        }

        packetBuffer.writeVarInt(this.slots.size());
        for (var entry : this.slots.int2ObjectEntrySet()) {
            packetBuffer.writeVarInt(entry.getIntKey());
            packetBuffer.writeItemStack(entry.getValue());
        }
    }

    static PatternAccessTerminalPacket decodePayload(boolean compressed, int uncompressedLength, byte[] payload) {
        byte[] raw = TerminalPayloadCodec.decode(compressed, uncompressedLength, payload, MAX_RAW_PAYLOAD_BYTES);
        ByteBuf buffer = Unpooled.wrappedBuffer(raw);
        try {
            PatternAccessTerminalPacket decoded = new PatternAccessTerminalPacket();
            decoded.readRaw(new PacketBuffer(buffer));
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Trailing bytes in Pattern Access Terminal raw payload");
            }
            return decoded;
        } finally {
            buffer.release();
        }
    }

    long inventoryId() {
        return this.inventoryId;
    }

    boolean fullUpdate() {
        return this.fullUpdate;
    }

    private void copyDecodedState(PatternAccessTerminalPacket decoded) {
        this.fullUpdate = decoded.fullUpdate;
        this.inventoryId = decoded.inventoryId;
        this.inventorySize = decoded.inventorySize;
        this.sortBy = decoded.sortBy;
        this.canEditTerminalName = decoded.canEditTerminalName;
        this.canModifyTerminalVisibility = decoded.canModifyTerminalVisibility;
        this.readOnly = decoded.readOnly;
        this.group = decoded.group;
        this.slots = decoded.slots;
    }

    private void readRaw(PacketBuffer packetBuffer) {
        this.inventoryId = packetBuffer.readVarLong();
        this.fullUpdate = packetBuffer.readBoolean();
        if (this.fullUpdate) {
            this.inventorySize = packetBuffer.readVarInt();
            if (this.inventorySize < 0 || this.inventorySize > MAX_INVENTORY_SIZE) {
                throw new IllegalArgumentException("Invalid pattern access terminal inventory size: " + this.inventorySize);
            }
            this.sortBy = packetBuffer.readVarLong();
            this.canEditTerminalName = packetBuffer.readBoolean();
            this.canModifyTerminalVisibility = packetBuffer.readBoolean();
            this.readOnly = packetBuffer.readBoolean();
            this.group = PatternContainerGroup.readFromPacket(packetBuffer);
            if (this.group == null) {
                throw new IllegalArgumentException("Pattern Access Terminal full update has no provider group");
            }
        }

        int slotCount = packetBuffer.readVarInt();
        if (slotCount < 0 || slotCount > MAX_SLOT_UPDATES) {
            throw new IllegalArgumentException("Invalid pattern access terminal slot count: " + slotCount);
        }
        this.slots = new Int2ObjectOpenHashMap<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            int slot = packetBuffer.readVarInt();
            if (slot < 0 || (this.fullUpdate && slot >= this.inventorySize) || this.slots.containsKey(slot)) {
                throw new IllegalArgumentException("Invalid pattern access terminal slot index: " + slot);
            }
            try {
                this.slots.put(slot, packetBuffer.readItemStack());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read pattern access terminal slot", e);
            }
        }
    }

    private void validateLogicalState() {
        if (this.slots == null || this.slots.size() > MAX_SLOT_UPDATES) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal slot update count");
        }
        if (this.fullUpdate) {
            if (this.inventorySize < 0 || this.inventorySize > MAX_INVENTORY_SIZE || this.group == null) {
                throw new IllegalArgumentException("Invalid Pattern Access Terminal full update state");
            }
        }
        for (var entry : this.slots.int2ObjectEntrySet()) {
            int slot = entry.getIntKey();
            if (slot < 0 || (this.fullUpdate && slot >= this.inventorySize)) {
                throw new IllegalArgumentException("Invalid Pattern Access Terminal slot index: " + slot);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    static void applyToDisplay(Minecraft minecraft, PatternAccessTerminalPacket packet) {
        if (minecraft.currentScreen instanceof IPatternProviderDisplay display) {
            if (packet.fullUpdate) {
                display.postFullUpdate(packet.inventoryId, packet.sortBy, packet.canEditTerminalName,
                    packet.canModifyTerminalVisibility, packet.readOnly, packet.group, packet.inventorySize,
                    packet.slots);
            } else {
                display.postIncrementalUpdate(packet.inventoryId, packet.slots);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (!this.malformed) {
            applyToDisplay(minecraft, this);
        }
    }
}
