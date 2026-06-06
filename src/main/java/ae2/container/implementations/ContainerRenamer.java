package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.parts.AEBasePart;
import ae2.tile.AEBaseTile;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerRenamer extends AEBaseContainer {

    private static final String ACTION_SET_NAME = "setName";
    private final Object renamableHost;
    @GuiSync(1)
    private String initialName;
    @GuiSync(2)
    private boolean initialNameReady;
    private String pendingName;

    public ContainerRenamer(InventoryPlayer playerInventory, AEBaseTile host) {
        super(playerInventory, host);
        this.renamableHost = host;
        this.initialName = getName(host.getCustomName());
        this.pendingName = this.initialName;
        this.initialNameReady = isServerSide();
        registerClientAction(ACTION_SET_NAME, String.class, this::setName);
    }

    public ContainerRenamer(InventoryPlayer playerInventory, AEBasePart host) {
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
        this.initialName = name;
        if (this.renamableHost instanceof AEBaseTile tile) {
            if (tile instanceof ICraftingCPUTileEntity craftingCpu) {
                craftingCpu.setName(name);
            } else {
                tile.setCustomName(name);
            }
            tile.saveChanges();
            tile.markForUpdate();
        } else if (this.renamableHost instanceof AEBasePart part) {
            part.setCustomName(name);
            if (part.getHost() != null) {
                part.getHost().markForSave();
                part.getHost().markForUpdate();
            }
        }
    }
}
