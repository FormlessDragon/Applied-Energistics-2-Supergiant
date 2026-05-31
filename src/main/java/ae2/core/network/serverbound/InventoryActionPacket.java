package ae2.core.network.serverbound;

import ae2.container.AEBaseContainer;
import ae2.core.network.ServerboundPacket;
import ae2.helpers.InventoryAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

public class InventoryActionPacket extends ServerboundPacket {
    private int windowId;
    private InventoryAction action;
    private int slot;
    private long extraId;
    private ItemStack slotItem = ItemStack.EMPTY;

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

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readInt();
        this.action = packetBuffer.readEnumValue(InventoryAction.class);
        this.slot = packetBuffer.readInt();
        this.extraId = packetBuffer.readVarLong();
        try {
            this.slotItem = packetBuffer.readItemStack();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read inventory action item", e);
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeEnumValue(this.action);
        packetBuffer.writeInt(this.slot);
        packetBuffer.writeVarLong(this.extraId);
        packetBuffer.writeItemStack(this.slotItem);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer.windowId != this.windowId) {
            return;
        }
        if (player.openContainer instanceof AEBaseContainer container) {
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
}
