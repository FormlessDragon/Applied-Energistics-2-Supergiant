package ae2.core.network.clientbound;

import ae2.api.storage.ILinkStatus;
import ae2.container.implementations.CellTerminalClientState;
import ae2.container.implementations.ContainerCellTerminal;
import ae2.core.AELog;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.InitNetwork;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

public class CellTerminalSyncPacket extends ClientboundPacket {
    static final int CHUNK_PAYLOAD_BYTES = 262_144;
    static final int MAX_CHUNKED_BYTES = 8 * 1_048_576;
    private static final String TAG_WINDOW_ID = "windowId";
    private static final String TAG_STATE = "state";
    private static final int MAX_PACKET_BYTES = 1_048_576;
    private int windowId;
    private CellTerminalClientState state = CellTerminalClientState.empty();
    private boolean malformed;

    public CellTerminalSyncPacket() {
    }

    public CellTerminalSyncPacket(int windowId, CellTerminalClientState state) {
        this.windowId = windowId;
        this.state = state;
    }

    public static void sendToClient(EntityPlayerMP player, int windowId, CellTerminalClientState state) {
        NBTTagCompound root = rootTag(windowId, state);
        byte[] payload = encodeRoot(root);
        if (payload.length <= MAX_PACKET_BYTES) {
            InitNetwork.sendToClient(player, new CellTerminalSyncPacket(windowId, state));
            return;
        }

        if (payload.length > MAX_CHUNKED_BYTES) {
            AELog.error("Cell Terminal sync packet exceeded chunked limit %s bytes: %s",
                MAX_CHUNKED_BYTES, payload.length);
            CellTerminalClientState lightState = state.lightSnapshot();
            byte[] lightPayload = encodeRoot(rootTag(windowId, lightState));
            if (lightPayload.length <= MAX_PACKET_BYTES) {
                InitNetwork.sendToClient(player, new CellTerminalSyncPacket(windowId, lightState));
                return;
            }
            InitNetwork.sendToClient(player, new CellTerminalSyncPacket(windowId, CellTerminalClientState.offline(
                state.tab(),
                ILinkStatus.ofDisconnected())));
            return;
        }

        int transferId = (int) (System.nanoTime() ^ ((long) windowId << 16) ^ state.cacheRevision());
        int totalChunks = (payload.length + CHUNK_PAYLOAD_BYTES - 1) / CHUNK_PAYLOAD_BYTES;
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            int start = chunkIndex * CHUNK_PAYLOAD_BYTES;
            int length = Math.min(CHUNK_PAYLOAD_BYTES, payload.length - start);
            byte[] chunk = new byte[length];
            System.arraycopy(payload, start, chunk, 0, length);
            InitNetwork.sendToClient(player, new CellTerminalSyncChunkPacket(
                windowId,
                transferId,
                chunkIndex,
                totalChunks,
                payload.length,
                chunk));
        }
    }

    static NBTTagCompound rootTag(int windowId, CellTerminalClientState state) {
        var root = new NBTTagCompound();
        root.setInteger(TAG_WINDOW_ID, windowId);
        root.setTag(TAG_STATE, state.toTag());
        return root;
    }

    static byte[] encodeRoot(NBTTagCompound tag) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new PacketBuffer(buffer).writeCompoundTag(tag);
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            return payload;
        } finally {
            buffer.release();
        }
    }

    static void applyEncodedRoot(Minecraft minecraft, byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        try {
            NBTTagCompound root = new PacketBuffer(buffer).readCompoundTag();
            if (root == null || !root.hasKey(TAG_STATE)) {
                throw new IllegalArgumentException("Cell Terminal chunked sync packet has no state tag");
            }
            applyState(minecraft, root.getInteger(TAG_WINDOW_ID),
                CellTerminalClientState.fromTag(root.getCompoundTag(TAG_STATE)));
        } catch (RuntimeException | IOException e) {
            AELog.warn(e, "Ignoring malformed chunked Cell Terminal sync packet");
        } finally {
            buffer.release();
        }
    }

    private static int encodedSize(NBTTagCompound tag) {
        ByteBuf sizeBuffer = Unpooled.buffer();
        try {
            new PacketBuffer(sizeBuffer).writeCompoundTag(tag);
            return sizeBuffer.readableBytes();
        } finally {
            sizeBuffer.release();
        }
    }

    @SideOnly(Side.CLIENT)
    static void applyState(Minecraft minecraft, int windowId, CellTerminalClientState state) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.openContainer instanceof ContainerCellTerminal container
            && container.windowId == windowId) {
            container.applyClientState(state);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        try {
            if (buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Cell Terminal sync packet exceeds " + MAX_PACKET_BYTES
                    + " bytes before NBT decode: " + buf.readableBytes());
            }
            NBTTagCompound root = packetBuffer.readCompoundTag();
            if (root == null || !root.hasKey(TAG_STATE)) {
                throw new IllegalArgumentException("Cell Terminal sync packet has no state tag");
            }
            this.windowId = root.getInteger(TAG_WINDOW_ID);
            this.state = CellTerminalClientState.fromTag(root.getCompoundTag(TAG_STATE));
        } catch (RuntimeException | IOException e) {
            this.malformed = true;
            buf.skipBytes(buf.readableBytes());
            AELog.warn(e, "Ignoring malformed Cell Terminal sync packet");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var root = rootTag(this.windowId, this.state);
        int encodedSize = encodedSize(root);
        if (encodedSize > MAX_PACKET_BYTES) {
            AELog.error("Cell Terminal sync packet exceeded %s bytes: %s", MAX_PACKET_BYTES, encodedSize);
            root.setTag(TAG_STATE, this.state.lightSnapshot().toTag());
            int lightSize = encodedSize(root);
            if (lightSize > MAX_PACKET_BYTES) {
                AELog.error("Cell Terminal light sync packet exceeded %s bytes: %s", MAX_PACKET_BYTES, lightSize);
                root.setTag(TAG_STATE, CellTerminalClientState.offline(
                    this.state.tab(),
                    ILinkStatus.ofDisconnected()).toTag());
            }
        }
        new PacketBuffer(buf).writeCompoundTag(root);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.malformed || minecraft.player == null) {
            return;
        }
        applyState(minecraft, this.windowId, this.state);
    }

    @SuppressWarnings("unused")
    public CellTerminalClientState getState() {
        return this.state;
    }
}
