package ae2.core.network.clientbound;

import ae2.client.render.overlay.CraftingSupplierHighlightHandler;
import ae2.client.render.overlay.OverlayHighlightLocation;
import ae2.client.render.overlay.OverlayHighlightShape;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CellTerminalSubnetHighlightPacket extends ClientboundPacket {
    private int dimensionId;
    private BlockPos pos = BlockPos.ORIGIN;
    @Nullable
    private EnumFacing side;

    public CellTerminalSubnetHighlightPacket() {
    }

    public CellTerminalSubnetHighlightPacket(int dimensionId, BlockPos pos, @Nullable EnumFacing side) {
        this.dimensionId = dimensionId;
        this.pos = pos.toImmutable();
        this.side = side;
    }

    @Override
    protected void read(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        this.dimensionId = data.readVarInt();
        this.pos = data.readBlockPos();
        this.side = NetworkPacketHelper.readEnumOrNull(data, EnumFacing.class);
    }

    @Override
    protected void write(ByteBuf buf) {
        var data = new PacketBuffer(buf);
        data.writeVarInt(this.dimensionId);
        data.writeBlockPos(this.pos);
        data.writeVarInt(this.side == null ? -1 : this.side.ordinal());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        OverlayHighlightShape shape = this.side == null
            ? OverlayHighlightShape.WHOLE_BLOCK
            : OverlayHighlightShape.STORAGE_BUS;
        CraftingSupplierHighlightHandler.INSTANCE.showCellTerminalHighlightLocations(minecraft,
            List.of(new OverlayHighlightLocation(this.dimensionId, this.pos, this.side,
                shape)));
    }
}
