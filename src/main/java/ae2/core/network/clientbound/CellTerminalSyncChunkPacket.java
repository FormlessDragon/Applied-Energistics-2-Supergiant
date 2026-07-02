package ae2.core.network.clientbound;

import ae2.core.AELog;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class CellTerminalSyncChunkPacket extends ClientboundPacket {
    private static final int MAX_PENDING_TRANSFERS = 16;
    private static final Map<ChunkKey, PendingTransfer> PENDING_TRANSFERS = new PendingTransferMap();

    private int windowId;
    private int transferId;
    private int chunkIndex;
    private int totalChunks;
    private int totalBytes;
    private byte[] payload = new byte[0];
    private boolean malformed;

    public CellTerminalSyncChunkPacket() {
    }

    public CellTerminalSyncChunkPacket(int windowId, int transferId, int chunkIndex, int totalChunks, int totalBytes,
                                       byte[] payload) {
        this.windowId = windowId;
        this.transferId = transferId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.totalBytes = totalBytes;
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    protected void read(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        try {
            this.windowId = data.readVarInt();
            this.transferId = data.readInt();
            this.chunkIndex = data.readVarInt();
            this.totalChunks = data.readVarInt();
            this.totalBytes = data.readVarInt();
            int payloadSize = data.readVarInt();
            validateHeader(payloadSize);
            this.payload = new byte[payloadSize];
            data.readBytes(this.payload);
        } catch (RuntimeException e) {
            this.malformed = true;
            buf.skipBytes(buf.readableBytes());
            AELog.warn(e, "Ignoring malformed Cell Terminal sync chunk");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeVarInt(this.windowId);
        data.writeInt(this.transferId);
        data.writeVarInt(this.chunkIndex);
        data.writeVarInt(this.totalChunks);
        data.writeVarInt(this.totalBytes);
        data.writeVarInt(this.payload.length);
        data.writeBytes(this.payload);
    }

    private void validateHeader(int payloadSize) {
        if (this.totalBytes <= 0 || this.totalBytes > CellTerminalSyncPacket.MAX_CHUNKED_BYTES) {
            throw new IllegalArgumentException("Invalid Cell Terminal chunk total bytes: " + this.totalBytes);
        }
        if (this.totalChunks <= 0
            || this.totalChunks > (CellTerminalSyncPacket.MAX_CHUNKED_BYTES
            + CellTerminalSyncPacket.CHUNK_PAYLOAD_BYTES - 1) / CellTerminalSyncPacket.CHUNK_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid Cell Terminal chunk count: " + this.totalChunks);
        }
        if (this.chunkIndex < 0 || this.chunkIndex >= this.totalChunks) {
            throw new IllegalArgumentException("Invalid Cell Terminal chunk index: " + this.chunkIndex);
        }
        if (payloadSize <= 0 || payloadSize > CellTerminalSyncPacket.CHUNK_PAYLOAD_BYTES
            || payloadSize > this.totalBytes) {
            throw new IllegalArgumentException("Invalid Cell Terminal chunk payload size: " + payloadSize);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.malformed || minecraft.player == null) {
            return;
        }

        ChunkKey key = new ChunkKey(this.windowId, this.transferId);
        PendingTransfer transfer = PENDING_TRANSFERS.computeIfAbsent(key,
            ignored -> new PendingTransfer(this.totalChunks, this.totalBytes));
        if (!transfer.accept(this.chunkIndex, this.payload)) {
            PENDING_TRANSFERS.remove(key);
            AELog.warn("Discarding inconsistent Cell Terminal sync chunk transfer: window=%s, transfer=%s",
                this.windowId, this.transferId);
            return;
        }
        if (!transfer.complete()) {
            return;
        }

        PENDING_TRANSFERS.remove(key);
        CellTerminalSyncPacket.applyEncodedRoot(minecraft, transfer.combine());
    }

    private record ChunkKey(int windowId, int transferId) {
    }

    private static final class PendingTransferMap extends LinkedHashMap<ChunkKey, PendingTransfer> {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ChunkKey, PendingTransfer> eldest) {
            return size() > MAX_PENDING_TRANSFERS;
        }

        @Override
        public Object clone() {
            throw new AssertionError();
        }
    }

    private static final class PendingTransfer {
        private final byte[][] chunks;
        private final int totalBytes;
        private int receivedBytes;
        private int receivedChunks;

        private PendingTransfer(int totalChunks, int totalBytes) {
            this.chunks = new byte[totalChunks][];
            this.totalBytes = totalBytes;
        }

        private boolean accept(int chunkIndex, byte[] chunk) {
            if (chunkIndex < 0 || chunkIndex >= this.chunks.length || chunk.length == 0) {
                return false;
            }
            byte[] previous = this.chunks[chunkIndex];
            if (previous != null) {
                return Arrays.equals(previous, chunk);
            }
            if (this.receivedBytes + chunk.length > this.totalBytes) {
                return false;
            }
            this.chunks[chunkIndex] = Arrays.copyOf(chunk, chunk.length);
            this.receivedBytes += chunk.length;
            this.receivedChunks++;
            return true;
        }

        private boolean complete() {
            return this.receivedChunks == this.chunks.length && this.receivedBytes == this.totalBytes;
        }

        private byte[] combine() {
            byte[] combined = new byte[this.totalBytes];
            int offset = 0;
            for (byte[] chunk : this.chunks) {
                System.arraycopy(chunk, 0, combined, offset, chunk.length);
                offset += chunk.length;
            }
            return combined;
        }
    }
}
