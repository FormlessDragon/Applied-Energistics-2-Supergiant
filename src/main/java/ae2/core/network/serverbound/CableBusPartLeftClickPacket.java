package ae2.core.network.serverbound;

import ae2.block.networking.CableBusBlock;
import ae2.core.network.ServerboundPacket;
import ae2.tile.networking.TileCableBus;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class CableBusPartLeftClickPacket extends ServerboundPacket {
    private BlockPos pos;
    private Vec3d localHit;

    public CableBusPartLeftClickPacket() {
    }

    public CableBusPartLeftClickPacket(BlockPos pos, Vec3d localHit) {
        this.pos = pos;
        this.localHit = localHit;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.pos = packetBuffer.readBlockPos();
        this.localHit = new Vec3d(packetBuffer.readDouble(), packetBuffer.readDouble(), packetBuffer.readDouble());
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeBlockPos(this.pos);
        packetBuffer.writeDouble(this.localHit.x);
        packetBuffer.writeDouble(this.localHit.y);
        packetBuffer.writeDouble(this.localHit.z);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.pos == null || this.localHit == null || player.getDistanceSqToCenter(this.pos) > 64.0) {
            return;
        }

        IBlockState state = player.world.getBlockState(this.pos);
        if (!(state.getBlock() instanceof CableBusBlock)) {
            return;
        }

        TileEntity tile = player.world.getTileEntity(this.pos);
        if (tile instanceof TileCableBus cableBus) {
            cableBus.onClicked(player, this.localHit);
        }
    }
}
