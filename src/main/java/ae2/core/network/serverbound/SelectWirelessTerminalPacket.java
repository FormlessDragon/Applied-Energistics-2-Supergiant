package ae2.core.network.serverbound;

import ae2.container.AEBaseContainer;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.network.ServerboundPacket;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public class SelectWirelessTerminalPacket extends ServerboundPacket {
    private static final int MAX_TERMINAL_ID_LENGTH = 64;

    private String terminalId;
    private int windowId;

    public SelectWirelessTerminalPacket() {
    }

    public SelectWirelessTerminalPacket(int windowId, String terminalId) {
        this.windowId = windowId;
        this.terminalId = terminalId;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.terminalId = packetBuffer.readString(MAX_TERMINAL_ID_LENGTH);
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing wireless terminal selection packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        ByteBufUtils.writeUTF8String(packetBuffer, this.terminalId);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!(player.openContainer instanceof AEBaseContainer container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }
        GuiHostLocator locator = container.getLocator();
        if (!(locator instanceof ItemGuiHostLocator itemLocator)) {
            return;
        }
        ItemStack stack = itemLocator.locateItem(player);
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal)) {
            return;
        }
        if (!universalTerminal.selectTerminal(stack, this.terminalId)) {
            return;
        }
        WirelessTerminalItem selected = universalTerminal.getCurrentTerminal(stack);
        if (selected != null) {
            selected.getWirelessTerminalDefinition().open(player, itemLocator, stack, true);
        }
    }
}
