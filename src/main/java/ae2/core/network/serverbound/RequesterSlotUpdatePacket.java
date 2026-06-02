package ae2.core.network.serverbound;

import ae2.container.me.common.AbstractContainerRequester;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class RequesterSlotUpdatePacket extends ServerboundPacket {
    private int windowId;
    private int row;
    private boolean visible;
    private long requesterId;
    private int requestIndex;
    private boolean invalid;

    public RequesterSlotUpdatePacket() {
    }

    private RequesterSlotUpdatePacket(int windowId, int row, boolean visible, long requesterId, int requestIndex) {
        this.windowId = windowId;
        this.row = row;
        this.visible = visible;
        this.requesterId = requesterId;
        this.requestIndex = requestIndex;
    }

    public static RequesterSlotUpdatePacket visible(int windowId, int row, long requesterId, int requestIndex) {
        return new RequesterSlotUpdatePacket(windowId, row, true, requesterId, requestIndex);
    }

    public static RequesterSlotUpdatePacket hidden(int windowId, int row) {
        return new RequesterSlotUpdatePacket(windowId, row, false, 0, -1);
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        try {
            this.windowId = packetBuffer.readVarInt();
            this.row = packetBuffer.readVarInt();
            this.visible = packetBuffer.readBoolean();
            if (this.visible) {
                this.requesterId = packetBuffer.readVarLong();
                this.requestIndex = packetBuffer.readVarInt();
            } else {
                this.requesterId = 0;
                this.requestIndex = -1;
            }
        } catch (RuntimeException e) {
            this.invalid = true;
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeVarInt(this.row);
        packetBuffer.writeBoolean(this.visible);
        if (this.visible) {
            packetBuffer.writeVarLong(this.requesterId);
            packetBuffer.writeVarInt(this.requestIndex);
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.invalid) {
            return;
        }

        if (player.openContainer.windowId != this.windowId) {
            return;
        }

        if (player.openContainer instanceof AbstractContainerRequester container) {
            container.updateRequestSlot(this.row, this.visible, this.requesterId, this.requestIndex);
        }
    }

    int getWindowId() {
        return this.windowId;
    }

    int getRow() {
        return this.row;
    }

    boolean isVisible() {
        return this.visible;
    }

    long getRequesterId() {
        return this.requesterId;
    }

    int getRequestIndex() {
        return this.requestIndex;
    }

}
