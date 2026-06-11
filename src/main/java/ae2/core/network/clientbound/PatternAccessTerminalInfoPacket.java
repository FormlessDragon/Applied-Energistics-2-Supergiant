package ae2.core.network.clientbound;

import ae2.client.gui.me.patternaccess.GuiPatternAccessTerm;
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

public class PatternAccessTerminalInfoPacket extends ClientboundPacket {
    private long inventoryId;
    private int dimensionId;
    private BlockPos pos;
    @Nullable
    private EnumFacing face;

    public PatternAccessTerminalInfoPacket() {
    }

    public PatternAccessTerminalInfoPacket(long inventoryId, int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
        this.inventoryId = inventoryId;
        this.dimensionId = dimensionId;
        this.pos = pos;
        this.face = face;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        this.inventoryId = data.readVarLong();
        this.dimensionId = data.readInt();
        this.pos = data.readBlockPos();
        this.face = data.readBoolean() ? NetworkPacketHelper.readEnumOrNull(data, EnumFacing.class) : null;
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeVarLong(this.inventoryId);
        data.writeInt(this.dimensionId);
        data.writeBlockPos(this.pos);
        data.writeBoolean(this.face != null);
        if (this.face != null) {
            data.writeVarInt(this.face.getIndex());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.currentScreen instanceof GuiPatternAccessTerm<?> patternAccessTerminal) {
            patternAccessTerminal.postProviderInfo(this.inventoryId, this.dimensionId, this.pos, this.face);
        }
    }
}
