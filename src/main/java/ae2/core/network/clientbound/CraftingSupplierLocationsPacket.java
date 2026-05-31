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
        var decoded = new ArrayList<CraftingSupplierLocation>(size);
        for (int i = 0; i < size; i++) {
            decoded.add(CraftingSupplierLocation.read(data));
        }
        this.locations = decoded;
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeVarInt(this.locations.size());
        for (var location : this.locations) {
            location.write(data);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        CraftingSupplierHighlightHandler.INSTANCE.showLocations(minecraft, this.locations);
    }
}
