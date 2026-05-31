package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.items.contents.ConfigModifierGuiHost;
import ae2.items.tools.ConfigModifierItem;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerConfigModifier extends AEBaseContainer {
    private static final String ACTION_SET_MODE = "setMode";
    private static final String ACTION_SET_DATA = "setData";

    private final ConfigModifierGuiHost host;

    @GuiSync(1)
    private ConfigModifierItem.Mode mode = ConfigModifierItem.Mode.MUL;
    @GuiSync(2)
    private long data = 1;

    public ContainerConfigModifier(InventoryPlayer ip, ConfigModifierGuiHost host) {
        super(ip, host);
        this.host = host;
        registerClientAction(ACTION_SET_MODE, ConfigModifierItem.Mode.class, this::setMode);
        registerClientAction(ACTION_SET_DATA, Long.class, this::setData);
    }

    @Override
    public void broadcastChanges() {
        ConfigModifierItem.Settings settings = this.host.getSettings();
        this.mode = settings.mode();
        this.data = settings.data();
        super.broadcastChanges();
    }

    public ConfigModifierItem.Mode getMode() {
        return this.mode;
    }

    public void setMode(ConfigModifierItem.Mode mode) {
        if (isClientSide()) {
            this.mode = mode;
            sendClientAction(ACTION_SET_MODE, mode);
        } else {
            this.host.setMode(mode);
        }
    }

    public long getData() {
        return this.data;
    }

    public void setData(long data) {
        if (isClientSide()) {
            this.data = data;
            sendClientAction(ACTION_SET_DATA, data);
        } else {
            this.host.setData(data);
        }
    }
}
