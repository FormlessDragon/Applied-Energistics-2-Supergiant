package ae2.core.network.serverbound;

import ae2.container.me.common.AbstractContainerRequester;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class RequesterUpdatePacket extends ServerboundPacket {
    private UpdateType updateType = UpdateType.STATE;
    private int windowId;
    private long requesterId;
    private int requestIndex;
    private boolean enabled;
    private boolean forceStart;
    private long amount;
    private long batchSize;

    public RequesterUpdatePacket() {
    }

    public RequesterUpdatePacket(int windowId, long requesterId, int requestIndex, boolean enabled, boolean forceStart) {
        this.updateType = UpdateType.STATE;
        this.windowId = windowId;
        this.requesterId = requesterId;
        this.requestIndex = requestIndex;
        this.enabled = enabled;
        this.forceStart = forceStart;
    }

    public RequesterUpdatePacket(int windowId, long requesterId, int requestIndex, long amount, long batchSize) {
        this.updateType = UpdateType.NUMBERS;
        this.windowId = windowId;
        this.requesterId = requesterId;
        this.requestIndex = requestIndex;
        this.amount = amount;
        this.batchSize = batchSize;
    }

    private static UpdateType readUpdateType(PacketBuffer packetBuffer) {
        int ordinal = packetBuffer.readVarInt();
        UpdateType[] values = UpdateType.values();
        return ordinal >= 0 && ordinal < values.length - 1 ? values[ordinal] : UpdateType.INVALID;
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeVarLong(this.requesterId);
        packetBuffer.writeVarInt(this.requestIndex);
        packetBuffer.writeVarInt(this.updateType.ordinal());
        if (this.updateType == UpdateType.STATE) {
            packetBuffer.writeBoolean(this.enabled);
            packetBuffer.writeBoolean(this.forceStart);
        } else {
            packetBuffer.writeVarLong(this.amount);
            packetBuffer.writeVarLong(this.batchSize);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        try {
            this.windowId = packetBuffer.readVarInt();
            this.requesterId = packetBuffer.readVarLong();
            this.requestIndex = packetBuffer.readVarInt();
            this.updateType = readUpdateType(packetBuffer);
            if (this.updateType == UpdateType.INVALID) {
                return;
            }
            if (this.updateType == UpdateType.STATE) {
                this.enabled = packetBuffer.readBoolean();
                this.forceStart = packetBuffer.readBoolean();
            } else {
                this.amount = packetBuffer.readVarLong();
                this.batchSize = packetBuffer.readVarLong();
            }
        } catch (RuntimeException e) {
            this.updateType = UpdateType.INVALID;
        }
    }

    boolean isStateUpdate() {
        return this.updateType == UpdateType.STATE;
    }

    boolean isNumbersUpdate() {
        return this.updateType == UpdateType.NUMBERS;
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.updateType == UpdateType.INVALID) {
            return;
        }

        if (player.openContainer.windowId != this.windowId) {
            return;
        }

        if (player.openContainer instanceof AbstractContainerRequester container) {
            if (this.updateType == UpdateType.STATE) {
                container.updateRequesterState(this.requesterId, this.requestIndex, this.enabled, this.forceStart);
            } else {
                container.updateRequesterNumbers(this.requesterId, this.requestIndex, this.amount, this.batchSize);
            }
            container.syncInventoryActionState(player);
        }
    }

    int getWindowId() {
        return this.windowId;
    }

    long getRequesterId() {
        return this.requesterId;
    }

    int getRequestIndex() {
        return this.requestIndex;
    }

    boolean isEnabled() {
        return this.enabled;
    }

    long getAmount() {
        return this.amount;
    }

    long getBatchSize() {
        return this.batchSize;
    }

    private enum UpdateType {
        STATE,
        NUMBERS,
        INVALID
    }
}
