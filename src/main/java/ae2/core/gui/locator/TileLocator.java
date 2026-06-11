package ae2.core.gui.locator;

import ae2.core.AELog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class TileLocator implements GuiHostLocator {
    private final BlockPos pos;

    public TileLocator(BlockPos pos) {
        this.pos = pos;
    }

    public static TileLocator readFromPacket(PacketBuffer buf) {
        return new TileLocator(buf.readBlockPos());
    }

    @Override
    @Nullable
    public <T> T locate(EntityPlayer player, Class<T> hostInterface) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (hostInterface.isInstance(tile)) {
            return hostInterface.cast(tile);
        }
        if (tile != null) {
            AELog.debug("Cannot locate container host @ %s, %s does not implement %s", pos, tile, hostInterface);
        }
        return null;
    }

    public void writeToPacket(PacketBuffer buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public String toString() {
        return "Tile{pos=" + pos + '}';
    }
}

