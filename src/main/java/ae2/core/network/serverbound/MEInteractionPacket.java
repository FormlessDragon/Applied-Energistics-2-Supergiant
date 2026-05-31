package ae2.core.network.serverbound;

import ae2.container.AEBaseContainer;
import ae2.container.me.common.IMEInteractionHandler;
import ae2.core.network.ServerboundPacket;
import ae2.helpers.InventoryAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;

/**
 * Packet sent by clients to interact with an ME inventory such as an item terminal.
 */
public class MEInteractionPacket extends ServerboundPacket {
    private int windowId;
    private long serial;
    private InventoryAction action;

    public MEInteractionPacket() {
    }

    public MEInteractionPacket(int windowId, long serial, InventoryAction action) {
        this.windowId = windowId;
        this.serial = serial;
        this.action = action;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readInt();
        this.serial = packetBuffer.readVarLong();
        this.action = packetBuffer.readEnumValue(InventoryAction.class);
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.windowId);
        packetBuffer.writeVarLong(this.serial);
        packetBuffer.writeEnumValue(this.action);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer.windowId != this.windowId) {
            return;
        }
        if (player.openContainer instanceof IMEInteractionHandler handler) {
            Container openContainer = player.openContainer;
            handler.handleInteraction(this.serial, this.action);
            if (player.openContainer == openContainer && openContainer instanceof AEBaseContainer container) {
                container.syncInventoryActionState(player);
            }
        }
    }
}
