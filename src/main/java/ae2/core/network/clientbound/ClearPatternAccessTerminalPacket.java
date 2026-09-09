package ae2.core.network.clientbound;

import ae2.client.gui.me.patternaccess.IPatternProviderDisplay;
import ae2.core.AELog;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClearPatternAccessTerminalPacket extends ClientboundPacket {
    private boolean singleInventory;
    private long inventoryId;
    private boolean malformed;

    public ClearPatternAccessTerminalPacket() {
    }

    public ClearPatternAccessTerminalPacket(long inventoryId) {
        this.singleInventory = true;
        this.inventoryId = inventoryId;
    }

    @Override
    protected void read(ByteBuf data) {
        try {
            int flag = data.readUnsignedByte();
            if (flag > 1) {
                throw new IllegalArgumentException("Invalid pattern directory removal flag");
            }
            this.singleInventory = flag == 1;
            if (this.singleInventory) {
                this.inventoryId = data.readLong();
            }
            if (data.isReadable()) {
                throw new IllegalArgumentException("Trailing bytes in pattern directory removal");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
            data.skipBytes(data.readableBytes());
            AELog.warn(exception, "Ignoring malformed pattern directory removal");
        }
    }

    @Override
    protected void write(ByteBuf data) {
        data.writeBoolean(this.singleInventory);
        if (this.singleInventory) {
            data.writeLong(this.inventoryId);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.malformed) {
            return;
        }
        if (minecraft.currentScreen instanceof IPatternProviderDisplay display) {
            if (this.singleInventory) {
                display.removeProvider(this.inventoryId);
            } else {
                display.clear();
            }
        }
    }
}
