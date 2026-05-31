package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.AppEngSlot;
import ae2.tile.misc.CanerMode;
import ae2.tile.misc.TileCaner;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerCaner extends AEBaseContainer {
    private static final String ACTION_SET_MODE = "setMode";

    private final TileCaner host;

    @GuiSync(0)
    public CanerMode mode = CanerMode.FILL;

    public ContainerCaner(InventoryPlayer ip, TileCaner host) {
        super(ip, host);
        this.host = host;
        registerClientAction(ACTION_SET_MODE, CanerMode.class, this::setMode);

        addSlot(new AppEngSlot(host.getGenericInv().createGuiWrapper(), 0), SlotSemantics.CANER_CONTENT);
        addSlot(new AppEngSlot(host.getContainerInventory(), 0), SlotSemantics.CANER_CONTAINER);
        addPlayerInventorySlots(8, 89);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.mode = this.host.getMode();
        }
        super.broadcastChanges();
    }

    public CanerMode getMode() {
        return this.mode;
    }

    private void setMode(CanerMode mode) {
        this.host.setMode(mode);
        this.mode = mode;
    }

    public void switchMode() {
        sendClientAction(ACTION_SET_MODE, this.mode == CanerMode.FILL ? CanerMode.EMPTY : CanerMode.FILL);
    }
}
