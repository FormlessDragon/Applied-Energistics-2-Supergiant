package ae2.core.network.clientbound;

import ae2.client.gui.me.requester.RequesterDisplay;
import ae2.core.network.ClientboundPacket;
import ae2.text.TextComponents;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class RequesterSyncPacket extends ClientboundPacket {
    private static final int MAX_REQUEST_COUNT = 64;
    private static final int MAX_ROW_COUNT = 64;
    private static final int MIN_ROW_BYTES = 2;

    private boolean clearAll;
    private boolean fullUpdate;
    private long requesterId;
    private int requestCount;
    private long sortValue;
    private @Nullable ITextComponent requesterName;
    private Int2ObjectMap<NBTTagCompound> rows = new Int2ObjectOpenHashMap<>();

    public RequesterSyncPacket() {
    }

    private RequesterSyncPacket(boolean fullUpdate, long requesterId, @Nullable ITextComponent requesterName,
                                long sortValue, int requestCount, Int2ObjectMap<NBTTagCompound> rows) {
        this.fullUpdate = fullUpdate;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.sortValue = sortValue;
        this.requestCount = requestCount;
        this.rows = copyRows(rows);
    }

    public static RequesterSyncPacket clearAll() {
        var packet = new RequesterSyncPacket();
        packet.clearAll = true;
        return packet;
    }

    public static RequesterSyncPacket fullUpdate(long requesterId, ITextComponent requesterName, long sortValue,
                                                 int requestCount, Int2ObjectMap<NBTTagCompound> rows) {
        return new RequesterSyncPacket(true, requesterId, requesterName, sortValue, requestCount, rows);
    }

    public static RequesterSyncPacket incrementalUpdate(long requesterId, Int2ObjectMap<NBTTagCompound> rows) {
        return new RequesterSyncPacket(false, requesterId, null, 0, 0, rows);
    }

    private static Int2ObjectMap<NBTTagCompound> copyRows(Int2ObjectMap<NBTTagCompound> rows) {
        var copy = new Int2ObjectOpenHashMap<NBTTagCompound>(rows.size());
        for (var entry : rows.int2ObjectEntrySet()) {
            copy.put(entry.getIntKey(), entry.getValue().copy());
        }
        return copy;
    }

    public boolean isFullUpdate() {
        return this.fullUpdate;
    }

    public boolean isClearAll() {
        return this.clearAll;
    }

    public long getRequesterId() {
        return this.requesterId;
    }

    public int getRequestCount() {
        return this.requestCount;
    }

    public long getSortValue() {
        return this.sortValue;
    }

    public @Nullable ITextComponent getRequesterName() {
        return this.requesterName;
    }

    public Int2ObjectMap<NBTTagCompound> getRows() {
        return this.rows;
    }

    @Override
    protected void read(ByteBuf buf) {
        try {
            readChecked(buf);
        } catch (RuntimeException | IOException e) {
            discard(buf);
        }
    }

    private void readChecked(ByteBuf buf) throws IOException {
        var packetBuffer = new PacketBuffer(buf);
        this.clearAll = packetBuffer.readBoolean();
        if (this.clearAll) {
            resetToClearAll();
            return;
        }

        this.requesterId = packetBuffer.readVarLong();
        this.fullUpdate = packetBuffer.readBoolean();
        if (this.fullUpdate) {
            this.requestCount = packetBuffer.readVarInt();
            if (this.requestCount < 0 || this.requestCount > MAX_REQUEST_COUNT) {
                throw new IllegalArgumentException("Invalid requester request count: " + this.requestCount);
            }
            this.sortValue = packetBuffer.readVarLong();
            this.requesterName = TextComponents.readFromPacket(packetBuffer);
        } else {
            this.requestCount = 0;
            this.sortValue = 0;
            this.requesterName = null;
        }

        int rowCount = packetBuffer.readVarInt();
        if (rowCount < 0 || rowCount > MAX_ROW_COUNT || rowCount > packetBuffer.readableBytes() / MIN_ROW_BYTES
            || this.fullUpdate && rowCount != this.requestCount) {
            throw new IllegalArgumentException("Invalid requester row count: " + rowCount);
        }

        this.rows = new Int2ObjectOpenHashMap<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            int row = packetBuffer.readVarInt();
            NBTTagCompound tag = packetBuffer.readCompoundTag();
            this.rows.put(row, tag == null ? new NBTTagCompound() : tag);
        }
    }

    private void resetToClearAll() {
        this.clearAll = true;
        this.fullUpdate = false;
        this.requesterId = 0;
        this.requestCount = 0;
        this.sortValue = 0;
        this.requesterName = null;
        this.rows = new Int2ObjectOpenHashMap<>();
    }

    private void discard(ByteBuf buf) {
        this.clearAll = false;
        this.fullUpdate = false;
        this.requesterId = 0;
        this.requestCount = 0;
        this.sortValue = 0;
        this.requesterName = null;
        this.rows = new Int2ObjectOpenHashMap<>();
        buf.skipBytes(buf.readableBytes());
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeBoolean(this.clearAll);
        if (this.clearAll) {
            return;
        }

        packetBuffer.writeVarLong(this.requesterId);
        packetBuffer.writeBoolean(this.fullUpdate);
        if (this.fullUpdate) {
            packetBuffer.writeVarInt(this.requestCount);
            packetBuffer.writeVarLong(this.sortValue);
            TextComponents.writeToPacket(packetBuffer, this.requesterName);
        }

        packetBuffer.writeVarInt(this.rows.size());
        for (var entry : this.rows.int2ObjectEntrySet()) {
            packetBuffer.writeVarInt(entry.getIntKey());
            packetBuffer.writeCompoundTag(entry.getValue());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.currentScreen instanceof RequesterDisplay display) {
            if (this.clearAll) {
                display.postClearAll();
            } else if (this.fullUpdate) {
                display.postFullUpdate(this.requesterId, this.requesterName, this.sortValue, this.requestCount,
                    this.rows);
            } else {
                display.postIncrementalUpdate(this.requesterId, this.rows);
            }
        }
    }
}
