package ae2.core.network.serverbound;

import ae2.container.AEBaseContainer;
import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.network.ServerboundPacket;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.items.tools.powered.WirelessTerminalMagnetMode;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

public class WirelessTerminalSettingsPacket extends ServerboundPacket {
    private int windowId;
    private boolean pickBlock;
    private boolean craftIfMissing;
    private boolean restock;
    private boolean magnet;
    private boolean pickupToME;

    public WirelessTerminalSettingsPacket() {
    }

    public WirelessTerminalSettingsPacket(int windowId, boolean pickBlock, boolean craftIfMissing, boolean restock,
                                          boolean magnet, boolean pickupToME) {
        this.windowId = windowId;
        this.pickBlock = pickBlock;
        this.craftIfMissing = craftIfMissing;
        this.restock = restock;
        this.magnet = magnet;
        this.pickupToME = pickupToME;
    }

    private static WirelessTerminalItem getTargetTerminal(ItemStack stack) {
        if (stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal) {
            return universalTerminal.getCurrentTerminal(stack);
        }
        if (stack.getItem() instanceof WirelessTerminalItem terminal) {
            return terminal;
        }
        return null;
    }

    private static boolean hasMagnetCard(EntityPlayerMP player, ItemGuiHostLocator locator) {
        WirelessTerminalGuiHost<?> host = locator.locate(player, WirelessTerminalGuiHost.class);
        return host != null && host.getUpgrades().isInstalled(AEItems.MAGNET_CARD.item());
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.pickBlock = packetBuffer.readBoolean();
        this.craftIfMissing = packetBuffer.readBoolean();
        this.restock = packetBuffer.readBoolean();
        this.magnet = packetBuffer.readBoolean();
        this.pickupToME = packetBuffer.readBoolean();
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing wireless terminal settings payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        packetBuffer.writeBoolean(this.pickBlock);
        packetBuffer.writeBoolean(this.craftIfMissing);
        packetBuffer.writeBoolean(this.restock);
        packetBuffer.writeBoolean(this.magnet);
        packetBuffer.writeBoolean(this.pickupToME);
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
        WirelessTerminalItem terminal = getTargetTerminal(stack);
        if (terminal == null) {
            return;
        }

        if ((this.magnet || this.pickupToME) && !hasMagnetCard(player, itemLocator)) {
            this.magnet = false;
            this.pickupToME = false;
        }

        WirelessTerminals.setPickBlockEnabled(stack, terminal, this.pickBlock);
        WirelessTerminals.setCraftIfMissingEnabled(stack, terminal, this.craftIfMissing);
        WirelessTerminals.setRestockEnabled(stack, terminal, this.restock);
        WirelessTerminals.setMagnetMode(stack, terminal, WirelessTerminalMagnetMode.from(this.magnet, this.pickupToME));
    }
}
