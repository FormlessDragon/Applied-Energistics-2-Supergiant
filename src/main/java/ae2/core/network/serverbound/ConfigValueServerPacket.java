package ae2.core.network.serverbound;

import ae2.api.config.Setting;
import ae2.api.util.IConfigurableObject;
import ae2.container.AEBaseContainer;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public class ConfigValueServerPacket extends ServerboundPacket {
    private static final int MAX_SETTING_NAME_LENGTH = 64;
    private static final int MAX_SETTING_VALUE_LENGTH = 64;

    private int windowId;
    private String name;
    private String value;

    public ConfigValueServerPacket() {
    }

    public ConfigValueServerPacket(int windowId, String name, String value) {
        this.windowId = windowId;
        this.name = name;
        this.value = value;
    }

    public <T extends Enum<T>> ConfigValueServerPacket(int windowId, Setting<T> setting, T value) {
        this(windowId, setting.getName(), value.name());
        if (!setting.getValues().contains(value)) {
            throw new IllegalStateException(value + " not a valid value for " + setting);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        this.name = packetBuffer.readString(MAX_SETTING_NAME_LENGTH);
        this.value = packetBuffer.readString(MAX_SETTING_VALUE_LENGTH);
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing config value packet payload bytes: " + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(this.windowId);
        ByteBufUtils.writeUTF8String(packetBuffer, this.name);
        ByteBufUtils.writeUTF8String(packetBuffer, this.value);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (!(player.openContainer instanceof AEBaseContainer container)) {
            return;
        }
        if (container.windowId != this.windowId) {
            return;
        }
        if (container instanceof IConfigurableObject configurableObject) {
            loadSetting(configurableObject, player);
            return;
        }
        if (container.getTarget() instanceof IConfigurableObject configurableObject) {
            loadSetting(configurableObject, player);
        }
    }

    private void loadSetting(IConfigurableObject configurableObject, EntityPlayerMP player) {
        var configManager = configurableObject.getConfigManager();
        for (var setting : configManager.getSettings()) {
            if (setting.getName().equals(this.name)) {
                try {
                    setting.setFromString(configManager, this.value);
                } catch (IllegalArgumentException e) {
                    NetworkPacketHelper.warnFailedPacket(e, getClass().getSimpleName() + ":" + player.getUniqueID(),
                        "Ignoring invalid config value packet from %s", player.getName());
                }
                break;
            }
        }
    }
}
