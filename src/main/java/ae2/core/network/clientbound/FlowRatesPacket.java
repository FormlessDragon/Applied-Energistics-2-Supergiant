package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.api.util.FlowRate;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

public class FlowRatesPacket extends ClientboundPacket {

    private Map<AEKey, FlowRate> rates;

    private static final int MAX_RATES = 4096;
    private static final int MIN_BYTES_PER_RATE = 19;

    public FlowRatesPacket() {
    }

    public FlowRatesPacket(final Map<AEKey, FlowRate> rates) {
        this.rates = rates;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        final int size = packetBuffer.readInt();
        if (size < 0 || size > MAX_RATES || size > packetBuffer.readableBytes() / MIN_BYTES_PER_RATE) {
            this.rates = Map.of();
            buf.skipBytes(buf.readableBytes());
            return;
        }

        final Map<AEKey, FlowRate> decoded = new HashMap<>(size);
        for(int i = 0; i < size; i++) {
            final AEKey key = AEKey.readKey(packetBuffer);
            final long in = packetBuffer.readLong();
            final long out = packetBuffer.readLong();
            if (key != null) {
                decoded.put(key, new FlowRate(in, out));
            }
        }
        this.rates = decoded;
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        final int size = Math.min(this.rates.size(), MAX_RATES);
        packetBuffer.writeInt(size);

        int written = 0;
        for (Map.Entry<AEKey, FlowRate> entry : this.rates.entrySet()) {
            if (++written > size) {
                break;
            }
            AEKey.writeKey(packetBuffer, entry.getKey());
            packetBuffer.writeLong(entry.getValue().in());
            packetBuffer.writeLong(entry.getValue().out());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.world == null) {
            return;
        }

        if (minecraft.currentScreen instanceof GuiMEStorage<?> guiMEStorage) {
            guiMEStorage.updateFlowRates(this.rates);
        }
    }
}
