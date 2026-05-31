package ae2.core.network.clientbound;

import ae2.api.stacks.AEKey;
import ae2.client.render.crafting.AssemblerAnimationStatus;
import ae2.core.network.ClientboundPacket;
import ae2.tile.crafting.TileMolecularAssembler;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AssemblerAnimationPacket extends ClientboundPacket {
    private BlockPos pos = BlockPos.ORIGIN;
    private byte speed;
    private AEKey what;

    public AssemblerAnimationPacket() {
    }

    public AssemblerAnimationPacket(BlockPos pos, byte speed, AEKey what) {
        this.pos = pos;
        this.speed = speed;
        this.what = what;
    }

    @Override
    protected void read(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        this.pos = packetBuffer.readBlockPos();
        this.speed = packetBuffer.readByte();
        this.what = AEKey.readKey(packetBuffer);
    }

    @Override
    protected void write(ByteBuf buf) {
        var packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeBlockPos(this.pos);
        packetBuffer.writeByte(this.speed);
        AEKey.writeKey(packetBuffer, this.what);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.world == null || this.what == null) {
            return;
        }

        TileEntity te = minecraft.world.getTileEntity(this.pos);
        if (te instanceof TileMolecularAssembler molecularAssembler) {
            molecularAssembler.setAnimationStatus(new AssemblerAnimationStatus(this.speed,
                this.what.wrapForDisplayOrFilter()));
        }
    }
}
