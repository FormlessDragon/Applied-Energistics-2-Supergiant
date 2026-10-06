package ae2.core.network.serverbound;

import ae2.api.implementations.items.WirelessTerminalApi;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEKey;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.NetworkAmountResultPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.Nullable;

/**
 * Asks the server how much of a key the network behind one of the wireless terminals the player carries stores.
 * <p>
 * The client reports the slots it found a terminal in, because it can look at its own inventory for free, while only
 * the server can tell whether a terminal is usable. The slots travel as two bitmaps: they are a fixed size whatever
 * the player carries, their bit order is the slot order, and a receiver can make no use of a slot number that is not
 * one of its own anyway.
 */
public class NetworkAmountQueryPacket extends ServerboundPacket {

    private AEKey key;
    /**
     * Bit i is set while slot i of the player's inventory held a wireless terminal when the client looked.
     */
    private long inventoryTerminals;
    /**
     * Bit i is set while bauble slot i held a wireless terminal when the client looked.
     */
    private long baubleTerminals;

    public NetworkAmountQueryPacket() {}

    public NetworkAmountQueryPacket(AEKey what, long inventoryTerminals, long baubleTerminals) {
        this.key = what;
        this.inventoryTerminals = inventoryTerminals;
        this.baubleTerminals = baubleTerminals;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.key = AEKey.readKey(packetBuffer);
        this.inventoryTerminals = packetBuffer.readLong();
        this.baubleTerminals = packetBuffer.readLong();
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        AEKey.writeKey(packetBuffer, this.key);
        packetBuffer.writeLong(this.inventoryTerminals);
        packetBuffer.writeLong(this.baubleTerminals);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.key == null) {
            return;
        }

        WirelessTerminalApi.TerminalContext context = findUsableTerminal(player, this.inventoryTerminals, false);
        if (context == null) {
            context = findUsableTerminal(player, this.baubleTerminals, true);
        }
        if (context == null) {
            InitNetwork.sendToClient(player,
                new NetworkAmountResultPacket(this.key, NetworkAmountResultPacket.NO_ANSWER));
            return;
        }

        IStorageService service = context.host().getGridStorageService();
        if (service == null) {
            InitNetwork.sendToClient(player,
                new NetworkAmountResultPacket(this.key, NetworkAmountResultPacket.NO_ANSWER));
            return;
        }

        long amount = service.getCachedInventory().get(this.key);
        InitNetwork.sendToClient(player, new NetworkAmountResultPacket(this.key, amount));
    }

    /**
     * The first usable terminal among the given slots, lowest slot first. The candidates are a hint from the client:
     * a slot number that is out of the player's range holds nothing, so walking them costs a lookup each at most.
     */
    @Nullable
    private static WirelessTerminalApi.TerminalContext findUsableTerminal(EntityPlayerMP player, long terminals,
                                                                          boolean isBaubleSlot) {
        for (long bits = terminals; bits != 0; bits &= bits - 1) {
            int slot = Long.numberOfTrailingZeros(bits);
            WirelessTerminalApi.TerminalContext context =
                WirelessTerminalApi.findUsableWirelessTermOfPlayer(player, _ -> true, slot, isBaubleSlot);
            if (context != null) {
                return context;
            }
        }
        return null;
    }
}
