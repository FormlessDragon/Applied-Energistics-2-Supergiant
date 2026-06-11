package ae2.core.network.serverbound;

import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.AEBaseContainer;
import ae2.core.network.ServerboundPacket;
import ae2.util.EnumCycler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public class ConfigButtonPacket extends ServerboundPacket {
    private static final int MAX_SETTING_NAME_LENGTH = 64;

    private int windowId;
    private Setting<?> option;
    private String optionName;
    private boolean rotationDirection;

    public ConfigButtonPacket() {
    }

    public ConfigButtonPacket(int windowId, Setting<?> option, boolean rotationDirection) {
        this.windowId = windowId;
        this.option = option;
        this.optionName = option.getName();
        this.rotationDirection = rotationDirection;
    }

    @Override
    protected void read(ByteBuf buf) {
        this.windowId = buf.readInt();
        this.optionName = new PacketBuffer(buf).readString(MAX_SETTING_NAME_LENGTH);
        try {
            this.option = Settings.getOrThrow(this.optionName);
        } catch (IllegalArgumentException ignored) {
            this.option = null;
        }
        this.rotationDirection = buf.readBoolean();
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Trailing config button packet payload bytes: " + buf.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(this.windowId);
        ByteBufUtils.writeUTF8String(buf, getOptionName());
        buf.writeBoolean(this.rotationDirection);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.option == null || !(player.openContainer instanceof AEBaseContainer baseContainer)) {
            return;
        }
        if (baseContainer.windowId != this.windowId) {
            return;
        }

        var target = baseContainer.getTarget();
        if (target instanceof IConfigurableObject configurableObject) {
            IConfigManager configManager = configurableObject.getConfigManager();
            if (configManager.hasSetting(this.option)) {
                cycleSetting(configManager, this.option);
            }
        }
    }

    private String getOptionName() {
        if (this.optionName != null) {
            return this.optionName;
        }
        return this.option.getName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cycleSetting(IConfigManager configManager, Setting setting) {
        Enum currentValue = configManager.getSetting(setting);
        Enum nextValue = EnumCycler.rotateEnum(currentValue, this.rotationDirection, setting.getValues());
        configManager.putSetting(setting, nextValue);
    }
}
