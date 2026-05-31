package ae2.core.network.serverbound;

import ae2.api.features.HotkeyAction;
import ae2.client.Hotkey;
import ae2.core.AELog;
import ae2.core.network.ServerboundPacket;
import ae2.hotkeys.HotkeyActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class HotkeyPacket extends ServerboundPacket {
    private String hotkey;

    public HotkeyPacket() {
    }

    public HotkeyPacket(String hotkey) {
        this.hotkey = hotkey;
    }

    public HotkeyPacket(Hotkey hotkey) {
        this(hotkey.name());
    }

    @Override
    protected void read(ByteBuf buf) {
        this.hotkey = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.hotkey);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        List<HotkeyAction> actions = HotkeyActions.REGISTRY.get(this.hotkey);
        if (actions == null) {
            AELog.warn("Player %s tried using unknown hotkey \"%s\"", player, this.hotkey);
            return;
        }

        for (HotkeyAction action : actions) {
            if (action.run(player)) {
                break;
            }
        }
    }

    public static final class Handler implements IMessageHandler<HotkeyPacket, IMessage> {
        @Override
        public @Nullable IMessage onMessage(HotkeyPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> message.handleServer(player));
            return null;
        }
    }
}
