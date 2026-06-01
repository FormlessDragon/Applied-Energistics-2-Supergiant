package ae2.core.network.serverbound;

import ae2.container.me.common.AbstractContainerRequester;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;

public class RequesterSlotUpdatePacket extends ServerboundPacket {
    private int windowId;
    private int row;
    private boolean visible;
    private long requesterId;
    private int requestIndex;
    private boolean locked;
    private ItemStack stack = ItemStack.EMPTY;

    public RequesterSlotUpdatePacket() {
    }

    private RequesterSlotUpdatePacket(int windowId, int row, boolean visible, long requesterId, int requestIndex,
                                      boolean locked, ItemStack stack) {
        this.windowId = windowId;
        this.row = row;
        this.visible = visible;
        this.requesterId = requesterId;
        this.requestIndex = requestIndex;
        this.locked = locked;
        this.stack = stack.copy();
    }

    public static RequesterSlotUpdatePacket visible(int windowId, int row, long requesterId, int requestIndex,
                                                    boolean locked, ItemStack stack) {
        return new RequesterSlotUpdatePacket(windowId, row, true, requesterId, requestIndex, locked, stack);
    }

    public static RequesterSlotUpdatePacket hidden(int windowId, int row) {
        return new RequesterSlotUpdatePacket(windowId, row, false, 0, -1, false, ItemStack.EMPTY);
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.row = packetBuffer.readVarInt();
        this.visible = packetBuffer.readBoolean();
        if (this.visible) {
            this.requesterId = packetBuffer.readVarLong();
            this.requestIndex = packetBuffer.readVarInt();
            this.locked = packetBuffer.readBoolean();
            try {
                this.stack = packetBuffer.readItemStack();
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read requester slot stack", e);
            }
        } else {
            this.requesterId = 0;
            this.requestIndex = -1;
            this.locked = false;
            this.stack = ItemStack.EMPTY;
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
            packetBuffer.writeBoolean(this.locked);
            packetBuffer.writeItemStack(this.stack);
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer.windowId != this.windowId) {
            return;
        }

        if (player.openContainer instanceof AbstractContainerRequester container) {
            container.updateRequestSlot(this.row, this.visible, this.requesterId, this.requestIndex, this.locked,
                this.stack);
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

    boolean isLocked() {
        return this.locked;
    }

    ItemStack getStack() {
        return this.stack;
    }
}
