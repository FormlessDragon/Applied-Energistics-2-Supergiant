package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.api.util.FlowSearchDTO;
import ae2.client.render.overlay.IngredientFlowHighlightHandler;
import ae2.core.network.ClientboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public class FlowLocationsPacket extends ClientboundPacket {

    private static final int MAX_COMPRESSED_BYTES = 512 * 1024;

    private List<FlowSearchDTO> locations = List.of();
    private AEKey what;

    public FlowLocationsPacket() {
    }

    public FlowLocationsPacket(List<FlowSearchDTO> locations, AEKey what) {
        this.locations = List.copyOf(locations);
        this.what = what;
    }

    @Override
    protected void read(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        this.what = AEKey.readKey(data);

        final int size = data.readInt();
        if (size < 0 || size > MAX_COMPRESSED_BYTES || size > data.readableBytes()) {
            this.locations = List.of();
            buf.skipBytes(buf.readableBytes());
            return;
        }

        final byte[] compressed = new byte[size];
        data.readBytes(compressed);

        try (var bytes = new ByteArrayInputStream(compressed);
             var input = new DataInputStream(bytes)) {
            this.locations = FlowSearchDTO.readAsListFromNBT(CompressedStreamTools.read(input));
        } catch (IOException | RuntimeException e) {
            this.locations = List.of();
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        AEKey.writeKey(data, this.what);

        final byte[] compressed = compress(this.locations);

        data.writeInt(compressed.length);
        data.writeBytes(compressed);
    }

    private static byte[] compress(List<FlowSearchDTO> locations) {
        var tag = new NBTTagCompound();
        FlowSearchDTO.writeListToNBT(tag, locations);

        try (var bytes = new ByteArrayOutputStream();
             var output = new DataOutputStream(bytes)) {
            CompressedStreamTools.write(tag, output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode item flow locations", e);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        IngredientFlowHighlightHandler.INSTANCE.showFlowLocations(minecraft, this.locations,
            this.what == null ? new TextComponentString("") : this.what.getDisplayName());
    }
}
