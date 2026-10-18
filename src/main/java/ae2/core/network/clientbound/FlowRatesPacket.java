package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongLongMutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

public class FlowRatesPacket extends ClientboundPacket {

    public static final int MAX_RATES = 4096;
    public static final int MAX_PAGES = 64;
    private int windowId;
    private long sourceGeneration;
    private boolean trackingActive;
    private long snapshotId;
    private int pageIndex;
    private int pageCount;
    private boolean complete;
    private Map<AEKey, LongLongPair> rates;
    private static final int MIN_BYTES_PER_RATE = 19;

    public FlowRatesPacket() {
    }

    public FlowRatesPacket(final Map<AEKey, LongLongPair> rates) {
        this(-1, 0, true, rates);
    }

    public FlowRatesPacket(int windowId, long sourceGeneration, boolean trackingActive,
                           final Map<AEKey, LongLongPair> rates) {
        this(windowId, sourceGeneration, trackingActive, 0, 0, 1, true, rates);
    }

    public FlowRatesPacket(int windowId, long sourceGeneration, boolean trackingActive, long snapshotId,
                           int pageIndex, int pageCount, boolean complete, Map<AEKey, LongLongPair> rates) {
        if (windowId < -1) {
            throw new IllegalArgumentException("Invalid flow window id: " + windowId);
        }
        if (sourceGeneration < 0) {
            throw new IllegalArgumentException("Invalid flow source generation: " + sourceGeneration);
        }
        if (snapshotId < 0 || pageCount > MAX_PAGES
            || complete && (pageIndex < 0 || pageIndex >= pageCount)
            || !complete && (pageCount != 0 || pageIndex != 0)) {
            throw new IllegalArgumentException("Invalid flow snapshot page identity");
        }
        if (rates.size() > MAX_RATES) {
            throw new IllegalArgumentException("Flow snapshot page exceeds the key limit");
        }
        this.windowId = windowId;
        this.sourceGeneration = sourceGeneration;
        this.trackingActive = trackingActive;
        this.snapshotId = snapshotId;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.complete = complete;
        this.rates = rates;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.sourceGeneration = packetBuffer.readVarLong();
        this.trackingActive = packetBuffer.readBoolean();
        this.snapshotId = packetBuffer.readVarLong();
        this.pageIndex = packetBuffer.readVarInt();
        this.pageCount = packetBuffer.readVarInt();
        this.complete = packetBuffer.readBoolean();
        if (this.windowId < -1 || this.sourceGeneration < 0 || this.snapshotId < 0
            || this.pageCount < 0 || this.pageCount > MAX_PAGES
            || this.complete && (this.pageCount == 0 || this.pageIndex < 0 || this.pageIndex >= this.pageCount)
            || !this.complete && (this.pageCount != 0 || this.pageIndex != 0)) {
            this.rates = Map.of();
            buf.skipBytes(buf.readableBytes());
            return;
        }
        final int size = packetBuffer.readInt();
        if (size < 0 || size > MAX_RATES || size > packetBuffer.readableBytes() / MIN_BYTES_PER_RATE) {
            this.rates = Map.of();
            buf.skipBytes(buf.readableBytes());
            return;
        }

        final Map<AEKey, LongLongPair> decoded = new HashMap<>(size);
        for(int i = 0; i < size; i++) {
            final AEKey key = AEKey.readKey(packetBuffer);
            final long in = packetBuffer.readLong();
            final long out = packetBuffer.readLong();
            if (key != null) {
                decoded.put(key, new LongLongMutablePair(in, out));
            }
        }
        this.rates = decoded;
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeVarLong(this.sourceGeneration);
        packetBuffer.writeBoolean(this.trackingActive);
        packetBuffer.writeVarLong(this.snapshotId);
        packetBuffer.writeVarInt(this.pageIndex);
        packetBuffer.writeVarInt(this.pageCount);
        packetBuffer.writeBoolean(this.complete);
        final int size = Math.min(this.rates.size(), MAX_RATES);
        packetBuffer.writeInt(size);

        int written = 0;
        for (Map.Entry<AEKey, LongLongPair> entry : this.rates.entrySet()) {
            if (++written > size) {
                break;
            }
            AEKey.writeKey(packetBuffer, entry.getKey());
            packetBuffer.writeLong(entry.getValue().leftLong());
            packetBuffer.writeLong(entry.getValue().rightLong());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.world == null) {
            return;
        }

        if (minecraft.currentScreen instanceof GuiMEStorage<?> guiMEStorage) {
            guiMEStorage.updateFlowRates(this.windowId, this.sourceGeneration, this.trackingActive,
                this.snapshotId, this.pageIndex, this.pageCount, this.complete, this.rates);
        }
    }
}
