package ae2.core.network.clientbound;

import ae2.client.render.overlay.CraftingSupplierHighlightHandler;
import ae2.core.network.ClientboundPacket;
import ae2.crafting.execution.CraftingSupplierLocation;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class CraftingSupplierLocationsPacket extends ClientboundPacket {
    private static final int BYTES_PER_LOCATION = Integer.BYTES * 4;
    private static final int MAX_LOCATIONS = 512;

    private List<CraftingSupplierLocation> locations = List.of();

    public CraftingSupplierLocationsPacket() {
    }

    public CraftingSupplierLocationsPacket(List<CraftingSupplierLocation> locations) {
        this.locations = List.copyOf(locations);
    }

    @Override
    protected void read(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        int size = data.readVarInt();
        if (size < 0 || size > MAX_LOCATIONS || size > data.readableBytes() / BYTES_PER_LOCATION) {
            this.locations = List.of();
            buf.skipBytes(buf.readableBytes());
            return;
        }

        var decoded = new ArrayList<CraftingSupplierLocation>(size);
        for (int i = 0; i < size; i++) {
            decoded.add(CraftingSupplierLocation.read(data));
        }
        this.locations = decoded;
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        int size = Math.min(this.locations.size(), MAX_LOCATIONS);
        data.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            this.locations.get(i).write(data);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        CraftingSupplierHighlightHandler.INSTANCE.showLocations(minecraft, this.locations);
    }
}
