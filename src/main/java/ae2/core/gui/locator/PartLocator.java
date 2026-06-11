package ae2.core.gui.locator;

import ae2.api.parts.PartHelper;
import ae2.core.AELog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class PartLocator implements GuiHostLocator {
    private final BlockPos pos;
    @Nullable
    private final EnumFacing side;
    private final boolean valid;

    public PartLocator(BlockPos pos, @Nullable EnumFacing side) {
        this(pos, side, true);
    }

    private PartLocator(BlockPos pos, @Nullable EnumFacing side, boolean valid) {
        this.pos = pos;
        this.side = side;
        this.valid = valid;
    }

    public static PartLocator readFromPacket(PacketBuffer buf) {
        var pos = buf.readBlockPos();
        EnumFacing side = null;
        if (buf.readBoolean()) {
            int sideIndex = buf.readByte();
            if (sideIndex < 0 || sideIndex >= EnumFacing.VALUES.length) {
                return new PartLocator(pos, null, false);
            }
            side = EnumFacing.VALUES[sideIndex];
        }
        return new PartLocator(pos, side);
    }

    @Override
    @Nullable
    public <T> T locate(EntityPlayer player, Class<T> hostInterface) {
        if (!valid) {
            return null;
        }

        var part = PartHelper.getPart(player.world, pos, side);
        if (hostInterface.isInstance(part)) {
            return hostInterface.cast(part);
        } else if (part != null) {
            AELog.debug("Part at %s does not implement host interface %s", part, hostInterface);
        }

        return null;
    }

    public void writeToPacket(PacketBuffer buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(side != null);
        if (side != null) {
            buf.writeByte(side.ordinal());
        }
    }
}

