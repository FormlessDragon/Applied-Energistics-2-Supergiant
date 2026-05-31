package ae2.container.implementations;

import ae2.container.guisync.GuiSync;
import ae2.parts.automation.special.ThresholdExportBusPart;
import ae2.parts.automation.special.ThresholdMode;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerThresholdExportBus extends ContainerIOBus {
    private static final String ACTION_SET_MODE = "setMode";

    @GuiSync(24)
    public ThresholdMode mode;

    public ContainerThresholdExportBus(InventoryPlayer ip, ThresholdExportBusPart host) {
        super(ip, host);
        this.mode = host.getThresholdMode();
        registerClientAction(ACTION_SET_MODE, ThresholdMode.class, this::setMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.mode = ((ThresholdExportBusPart) getHost()).getThresholdMode();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return super.isSlotEnabled(idx);
    }

    public void setMode(ThresholdMode mode) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_MODE, mode);
            this.mode = mode;
            return;
        }
        ((ThresholdExportBusPart) getHost()).setThresholdMode(mode);
    }
}
