package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.container.guisync.GuiSync;
import ae2.helpers.IWorkIntervalHost;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerWorkInterval extends AEBaseContainer implements ISubGui {

    private static final String ACTION_SET_WORK_INTERVAL = "setWorkInterval";

    private final IWorkIntervalHost host;

    @GuiSync(1)
    private long workInterval;

    public ContainerWorkInterval(InventoryPlayer ip, IWorkIntervalHost host) {
        super(ip, host);
        this.host = host;
        this.workInterval = host.getWorkInterval();

        registerClientAction(ACTION_SET_WORK_INTERVAL, Long.class, this::setWorkInterval);
    }

    @Override
    public IWorkIntervalHost getHost() {
        return this.host;
    }

    public long getWorkInterval() {
        return this.workInterval;
    }

    public void setWorkInterval(long newValue) {
        long clamped = Math.max(1L, newValue);
        if (isClientSide()) {
            if (clamped != this.workInterval) {
                this.workInterval = clamped;
                sendClientAction(ACTION_SET_WORK_INTERVAL, clamped);
            }
        } else {
            this.workInterval = clamped;
            this.host.setWorkInterval(clamped);
        }
    }
}
