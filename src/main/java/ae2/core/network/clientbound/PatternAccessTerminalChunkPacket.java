package ae2.core.network.clientbound;

import ae2.client.gui.me.patternaccess.IPatternProviderDisplay;
import ae2.container.me.patternaccess.IPatternAccess;
import ae2.core.AELog;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One bounded fragment of a compressed Pattern Access Terminal provider update.
 *
 * <p>Fragments are only assembled and decoded after every exact fragment of one transfer has been received. The
 * packet does not expose a reusable chunking API because its wire shape and payload bounds belong solely to the
 * Pattern Access Terminal protocol.</p>
 */
public class PatternAccessTerminalChunkPacket extends ClientboundPacket {
    static final int MAX_CHUNK_PAYLOAD_BYTES = 261_120;
    static final int MAX_CHUNK_COUNT = (PatternAccessTerminalPacket.MAX_RAW_PAYLOAD_BYTES
        + MAX_CHUNK_PAYLOAD_BYTES - 1) / MAX_CHUNK_PAYLOAD_BYTES;
    static final int MAX_PENDING_WIRE_BYTES = 8 * 1_048_576;
    static final long PENDING_TIMEOUT_NANOS = 5_000_000_000L;
    private static final int MAX_PENDING_TRANSFERS = 16;
    private static final Map<ChunkKey, PendingTransfer> PENDING_TRANSFERS = new LinkedHashMap<>(16, 0.75F, true);
    private static int nextTransferId;
    private static int pendingWireBytes;

    private int windowId;
    private long inventoryId;
    private int transferId;
    private int chunkIndex;
    private int totalChunks;
    private boolean compressed;
    private int uncompressedLength;
    private int totalWireBytes;
    private byte[] payload = new byte[0];
    private boolean malformed;

    public PatternAccessTerminalChunkPacket() {
    }

    PatternAccessTerminalChunkPacket(int windowId, long inventoryId, int transferId, int chunkIndex, int totalChunks,
                                     boolean compressed, int uncompressedLength, int totalWireBytes, byte[] payload) {
        this.windowId = windowId;
        this.inventoryId = inventoryId;
        this.transferId = transferId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.compressed = compressed;
        this.uncompressedLength = uncompressedLength;
        this.totalWireBytes = totalWireBytes;
        this.payload = Arrays.copyOf(payload, payload.length);
        validateHeader(this.payload.length);
    }

    static synchronized int nextTransferId() {
        return nextTransferId++;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        try {
            this.windowId = data.readVarInt();
            this.inventoryId = data.readVarLong();
            this.transferId = data.readInt();
            this.chunkIndex = data.readVarInt();
            this.totalChunks = data.readVarInt();
            this.compressed = data.readBoolean();
            this.uncompressedLength = data.readVarInt();
            this.totalWireBytes = data.readVarInt();
            int payloadLength = data.readVarInt();
            validateHeader(payloadLength);
            this.payload = new byte[payloadLength];
            data.readBytes(this.payload);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Trailing bytes in Pattern Access Terminal chunk");
            }
        } catch (RuntimeException e) {
            this.malformed = true;
            buf.skipBytes(buf.readableBytes());
            AELog.warn(e, "Ignoring malformed Pattern Access Terminal sync chunk");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        validateHeader(this.payload.length);
        PacketBuffer data = new PacketBuffer(buf);
        data.writeVarInt(this.windowId);
        data.writeVarLong(this.inventoryId);
        data.writeInt(this.transferId);
        data.writeVarInt(this.chunkIndex);
        data.writeVarInt(this.totalChunks);
        data.writeBoolean(this.compressed);
        data.writeVarInt(this.uncompressedLength);
        data.writeVarInt(this.totalWireBytes);
        data.writeVarInt(this.payload.length);
        data.writeBytes(this.payload);
    }

    private void validateHeader(int payloadLength) {
        if (this.windowId < 0) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal window id: " + this.windowId);
        }
        if (this.uncompressedLength <= 0 || this.uncompressedLength > PatternAccessTerminalPacket.MAX_RAW_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal raw payload length: "
                + this.uncompressedLength);
        }
        if (this.totalWireBytes <= 0 || this.totalWireBytes > PatternAccessTerminalPacket.MAX_RAW_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal total wire bytes: "
                + this.totalWireBytes);
        }
        int expectedChunks = (this.totalWireBytes + MAX_CHUNK_PAYLOAD_BYTES - 1) / MAX_CHUNK_PAYLOAD_BYTES;
        if (this.totalChunks != expectedChunks || this.totalChunks <= 1 || this.totalChunks > MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal chunk count: " + this.totalChunks);
        }
        if (this.chunkIndex < 0 || this.chunkIndex >= this.totalChunks) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal chunk index: " + this.chunkIndex);
        }
        int expectedPayloadLength = this.chunkIndex == this.totalChunks - 1
            ? this.totalWireBytes - this.chunkIndex * MAX_CHUNK_PAYLOAD_BYTES
            : MAX_CHUNK_PAYLOAD_BYTES;
        if (payloadLength != expectedPayloadLength) {
            throw new IllegalArgumentException("Invalid Pattern Access Terminal chunk payload length: " + payloadLength);
        }
        if (!this.compressed && this.totalWireBytes != this.uncompressedLength) {
            throw new IllegalArgumentException("Uncompressed Pattern Access Terminal chunk has inconsistent lengths");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        long now = System.nanoTime();
        if (minecraft.player == null || !(minecraft.currentScreen instanceof IPatternProviderDisplay)
            || !(minecraft.player.openContainer instanceof IPatternAccess)) {
            clearPendingTransfers();
            return;
        }

        int currentWindowId = minecraft.player.openContainer.windowId;
        PatternAccessTerminalPacket decoded = acceptChunk(this, currentWindowId, now);
        if (decoded != null) {
            PatternAccessTerminalPacket.applyToDisplay(minecraft, decoded);
        }
    }

    /**
     * Records one fragment and returns a decoded provider update only when the complete transfer has been received.
     */
    static PatternAccessTerminalPacket acceptChunk(PatternAccessTerminalChunkPacket packet, int currentWindowId,
                                                    long now) {
        cleanupPendingTransfers(currentWindowId, now);
        ChunkKey key = new ChunkKey(packet.windowId, packet.inventoryId, packet.transferId);
        if (packet.malformed || packet.windowId != currentWindowId) {
            removeTransfer(key);
            return null;
        }

        PendingTransfer transfer = PENDING_TRANSFERS.get(key);
        if (transfer == null) {
            makePendingCapacity(packet.totalWireBytes);
            transfer = new PendingTransfer(packet.totalChunks, packet.compressed, packet.uncompressedLength,
                packet.totalWireBytes, now);
            PENDING_TRANSFERS.put(key, transfer);
            pendingWireBytes += packet.totalWireBytes;
        }
        if (!transfer.accept(packet, now)) {
            removeTransfer(key);
            AELog.warn("Discarding inconsistent Pattern Access Terminal sync chunk transfer: inventory=%s, transfer=%s",
                packet.inventoryId, packet.transferId);
            return null;
        }
        if (!transfer.complete()) {
            return null;
        }

        removeTransfer(key);
        try {
            PatternAccessTerminalPacket decoded = PatternAccessTerminalPacket.decodePayload(packet.compressed,
                packet.uncompressedLength, transfer.combine());
            if (decoded.inventoryId() != packet.inventoryId) {
                throw new IllegalArgumentException("Pattern Access Terminal chunk inventory id does not match payload");
            }
            return decoded;
        } catch (RuntimeException e) {
            AELog.warn(e, "Ignoring malformed reassembled Pattern Access Terminal sync packet");
            return null;
        }
    }

    static int pendingWireBytes() {
        return pendingWireBytes;
    }

    static int pendingTransferCount() {
        return PENDING_TRANSFERS.size();
    }

    /**
     * Releases incomplete transfers owned by a Pattern Access GUI window when that GUI closes.
     */
    @ApiStatus.Internal
    public static void clearPendingTransfers(int windowId) {
        var iterator = PENDING_TRANSFERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkKey, PendingTransfer> entry = iterator.next();
            if (entry.getKey().windowId == windowId) {
                pendingWireBytes -= entry.getValue().totalWireBytes;
                iterator.remove();
            }
        }
    }

    static void clearPendingTransfers() {
        PENDING_TRANSFERS.clear();
        pendingWireBytes = 0;
    }

    private static void cleanupPendingTransfers(int currentWindowId, long now) {
        var iterator = PENDING_TRANSFERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkKey, PendingTransfer> entry = iterator.next();
            if (entry.getKey().windowId != currentWindowId
                || now - entry.getValue().lastUpdatedNanos > PENDING_TIMEOUT_NANOS) {
                pendingWireBytes -= entry.getValue().totalWireBytes;
                iterator.remove();
            }
        }
    }

    private static void makePendingCapacity(int requiredWireBytes) {
        while (!PENDING_TRANSFERS.isEmpty()
            && (PENDING_TRANSFERS.size() >= MAX_PENDING_TRANSFERS
                || pendingWireBytes + requiredWireBytes > MAX_PENDING_WIRE_BYTES)) {
            Map.Entry<ChunkKey, PendingTransfer> eldest = PENDING_TRANSFERS.entrySet().iterator().next();
            AELog.warn("Discarding LRU Pattern Access Terminal sync chunk transfer: inventory=%s, transfer=%s",
                eldest.getKey().inventoryId, eldest.getKey().transferId);
            removeTransfer(eldest.getKey());
        }
    }

    private static void removeTransfer(ChunkKey key) {
        PendingTransfer removed = PENDING_TRANSFERS.remove(key);
        if (removed != null) {
            pendingWireBytes -= removed.totalWireBytes;
        }
    }

    private record ChunkKey(int windowId, long inventoryId, int transferId) {
    }

    private static final class PendingTransfer {
        private final byte[][] chunks;
        private final boolean compressed;
        private final int uncompressedLength;
        private final int totalWireBytes;
        private int receivedChunks;
        private int receivedBytes;
        private long lastUpdatedNanos;

        private PendingTransfer(int totalChunks, boolean compressed, int uncompressedLength, int totalWireBytes,
                                long now) {
            this.chunks = new byte[totalChunks][];
            this.compressed = compressed;
            this.uncompressedLength = uncompressedLength;
            this.totalWireBytes = totalWireBytes;
            this.lastUpdatedNanos = now;
        }

        private boolean accept(PatternAccessTerminalChunkPacket chunk, long now) {
            if (chunk.totalChunks != this.chunks.length || chunk.compressed != this.compressed
                || chunk.uncompressedLength != this.uncompressedLength || chunk.totalWireBytes != this.totalWireBytes
                || chunk.chunkIndex < 0 || chunk.chunkIndex >= this.chunks.length) {
                return false;
            }
            byte[] previous = this.chunks[chunk.chunkIndex];
            if (previous != null) {
                if (Arrays.equals(previous, chunk.payload)) {
                    this.lastUpdatedNanos = now;
                    return true;
                }
                return false;
            }
            if (this.receivedBytes + chunk.payload.length > this.totalWireBytes) {
                return false;
            }
            this.chunks[chunk.chunkIndex] = Arrays.copyOf(chunk.payload, chunk.payload.length);
            this.receivedBytes += chunk.payload.length;
            this.receivedChunks++;
            this.lastUpdatedNanos = now;
            return true;
        }

        private boolean complete() {
            return this.receivedChunks == this.chunks.length && this.receivedBytes == this.totalWireBytes;
        }

        private byte[] combine() {
            byte[] combined = new byte[this.totalWireBytes];
            int offset = 0;
            for (byte[] chunk : this.chunks) {
                if (chunk == null) {
                    throw new IllegalStateException("Incomplete Pattern Access Terminal chunk transfer");
                }
                System.arraycopy(chunk, 0, combined, offset, chunk.length);
                offset += chunk.length;
            }
            return combined;
        }
    }
}
