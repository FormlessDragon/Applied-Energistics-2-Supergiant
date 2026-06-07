package ae2.integration.abstraction;

import ae2.tile.powersink.IExternalPowerSink;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface IIC2 {

    default IC2PowerSink createPowerSink(TileEntity tileEntity, IExternalPowerSink externalSink) {
        return IC2PowerSinkStub.INSTANCE;
    }

    default IC2P2PHost createP2PHost(World world, BlockPos pos, IC2P2PTunnel tunnel) {
        return IC2P2PHostStub.INSTANCE;
    }

    default void registerP2PAttunements() {
    }
}
