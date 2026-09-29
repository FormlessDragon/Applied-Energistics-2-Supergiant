package ae2.core.network.serverbound;

import ae2.api.stacks.AEKey;
import ae2.container.AEBaseContainer;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.network.ServerboundPacket;
import ae2.helpers.InventoryAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class InventoryActionPacket extends ServerboundPacket {
    private static final int MAX_PACKET_BYTES = 128 * 1024;

    private int windowId;
    private InventoryAction action;
    private int slot;
    private long extraId;
    private ItemStack slotItem = ItemStack.EMPTY;
    @Nullable
    private AEKey key;
    private boolean invalid;

    public InventoryActionPacket() {
    }

    public InventoryActionPacket(int windowId, InventoryAction action, int slot, long id) {
        this.windowId = windowId;
        this.action = action;
        this.slot = slot;
        this.extraId = id;
    }

    public InventoryActionPacket(int windowId, InventoryAction action, int slot, ItemStack slotItem) {
        this.windowId = windowId;
        this.action = action;
        this.slot = slot;
        this.slotItem = slotItem.copy();
    }

    public InventoryActionPacket(int windowId, InventoryAction action, int slot, long id, ItemStack slotItem) {
        this.windowId = windowId;
        this.action = action;
        this.slot = slot;
        this.extraId = id;
        this.slotItem = slotItem.copy();
    }

    public InventoryActionPacket(int windowId, InventoryAction action, int slot, @Nullable AEKey key) {
        this.windowId = windowId;
        this.action = action;
        this.slot = slot;
        this.key = key;
    }

    @Nullable
    public AEKey getKey() {
        return this.key;
    }

    @Override
    protected void read(ByteBuf buf) {
        if (buf.readableBytes() > MAX_PACKET_BYTES) {
            invalidate(buf);
            return;
        }

        try {
            var packetBuffer = new PacketBuffer(buf);
            this.windowId = packetBuffer.readInt();
            this.action = NetworkPacketHelper.readEnumOrNull(packetBuffer, InventoryAction.class);
            this.slot = packetBuffer.readInt();
            this.extraId = packetBuffer.readVarLong();
            this.slotItem = packetBuffer.readItemStack();
            this.key = AEKey.readOptionalKey(packetBuffer);
            if (buf.isReadable()) {
                invalidate(buf, new IllegalArgumentException(
                    "Trailing inventory action packet payload bytes: " + buf.readableBytes()));
            }
        } catch (IOException | RuntimeException e) {
            invalidate(buf, e);
        }
    }

    private void invalidate(ByteBuf buf) {
        invalidate(buf, new IllegalArgumentException("Inventory action packet exceeds maximum size"));
    }

    private void invalidate(ByteBuf buf, Exception exception) {
        this.invalid = true;
        this.slotItem = ItemStack.EMPTY;
        this.key = null;
        invalidateMalformed(buf, exception instanceof RuntimeException runtimeException
            ? runtimeException
            : new IllegalArgumentException("Could not read inventory action packet", exception));
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeEnumValue(this.action);
        packetBuffer.writeInt(this.slot);
        packetBuffer.writeVarLong(this.extraId);
        packetBuffer.writeItemStack(this.slotItem);
        AEKey.writeOptionalKey(packetBuffer, this.key);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.invalid || this.action == null) {
            return;
        }
        if (!(player.openContainer instanceof AEBaseContainer container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }
        container.setTargetKey(this.key);
        if (this.action == InventoryAction.SET_FILTER) {
            container.setFilter(this.slot, this.slotItem, this.extraId != 0);
        } else {
            container.doAction(player, this.action, this.slot, this.extraId);
        }
        if (player.openContainer == container) {
            container.syncInventoryActionState(player);
        }
    }
}
