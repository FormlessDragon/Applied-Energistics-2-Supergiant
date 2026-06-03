package ae2.core.network.serverbound;

import ae2.core.network.ServerboundPacket;
import ae2.me.ticker.RequestBox;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

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
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(duration);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (duration < 0) {
            if (RequestBox.cancelProfile(player)) {
                player.sendMessage(new TextComponentTranslation("chat.ae2.tick_analyser.cannel"));
            } else {
                player.sendMessage(new TextComponentTranslation("chat.ae2.tick_analyser.no_cannel"));
            }
        } else {
            switch (RequestBox.requestProfile(player, duration)) {
                case OK -> player.sendMessage(new TextComponentTranslation("chat.ae2.tick_analyser.begin", duration));
                case WAIT -> player.sendMessage(new TextComponentTranslation("chat.ae2.tick_analyser.waiting"));
                case DENY -> player.sendMessage(new TextComponentTranslation("chat.ae2.tick_analyser.user_control"));
            }
        }
    }
}
