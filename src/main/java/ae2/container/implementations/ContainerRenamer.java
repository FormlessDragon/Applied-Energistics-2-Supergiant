package ae2.container.implementations;

import ae2.api.util.ICustomName;
import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerRenamer extends AEBaseContainer {

    private static final String ACTION_SET_NAME = "setName";
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private final ICustomName renamableHost;
    @GuiSync(1)
    private String initialName;
    @GuiSync(2)
    private boolean initialNameReady;
    private String pendingName;

    public ContainerRenamer(InventoryPlayer playerInventory, ICustomName host) {
        super(playerInventory, host);
        this.renamableHost = host;
        this.initialName = getName(host.getCustomName());
        this.pendingName = this.initialName;
        this.initialNameReady = isServerSide();
        registerClientAction(ACTION_SET_NAME, String.class, this::setName);
    }

    private static String getName(String name) {
        return name == null ? "" : name;
    }

    public String getInitialName() {
        return this.initialName;
    }

    public boolean isInitialNameReady() {
        return this.initialNameReady;
    }

    public void setName(String name) {
        if (name != null && name.length() > MAX_CUSTOM_NAME_LENGTH) {
            return;
        }
        this.pendingName = name;
        if (isClientSide()) {
            sendClientAction(ACTION_SET_NAME, name);
        } else {
            applyName(name);
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (isServerSide()) {
            applyName(this.pendingName);
        }
    }

    private void applyName(String name) {
        if (name != null && name.length() > MAX_CUSTOM_NAME_LENGTH) {
            return;
        }
        this.initialName = name;
        this.renamableHost.setCustomNameFromRenamer(name);
    }
}
