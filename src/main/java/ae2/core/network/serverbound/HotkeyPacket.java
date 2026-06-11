package ae2.core.network.serverbound;

import ae2.api.features.HotkeyAction;
import ae2.client.Hotkey;
import ae2.core.network.ServerboundPacket;
import ae2.hotkeys.HotkeyActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.util.List;

public class HotkeyPacket extends ServerboundPacket {
    private static final int MAX_HOTKEY_NAME_LENGTH = 64;

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
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.hotkey = packetBuffer.readString(MAX_HOTKEY_NAME_LENGTH);
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing hotkey packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.hotkey);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        List<HotkeyAction> actions = HotkeyActions.REGISTRY.get(this.hotkey);
        if (actions == null) {
            return;
        }

        for (HotkeyAction action : actions) {
            if (action.run(player)) {
                break;
            }
        }
    }
}
