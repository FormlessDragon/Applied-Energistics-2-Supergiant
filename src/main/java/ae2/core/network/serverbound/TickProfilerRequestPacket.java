package ae2.core.network.serverbound;

import ae2.core.localization.PlayerMessages;
import ae2.core.network.ServerboundPacket;
import ae2.me.ticker.RequestBox;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class TickProfilerRequestPacket extends ServerboundPacket {
    private int duration;

    public TickProfilerRequestPacket() {
    }

    public TickProfilerRequestPacket(int duration) {
        this.duration = duration;
    }

    @Override
    protected void read(ByteBuf buf) {
        duration = buf.readInt();
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing bytes in tick profiler request packet");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(duration);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (duration < 0) {
            if (RequestBox.cancelProfile(player)) {
                player.sendMessage(PlayerMessages.TickAnalyserCancel.text());
            } else {
                player.sendMessage(PlayerMessages.TickAnalyserNoCancel.text());
            }
        } else {
            int clampedDuration = RequestBox.clampDurationSeconds(duration);
            switch (RequestBox.requestProfile(player, clampedDuration)) {
                case OK -> player.sendMessage(PlayerMessages.TickAnalyserBegin.text(clampedDuration));
                case WAIT -> player.sendMessage(PlayerMessages.TickAnalyserWaiting.text());
                case DENY -> player.sendMessage(PlayerMessages.TickAnalyserUserControl.text());
            }
        }
    }
}
