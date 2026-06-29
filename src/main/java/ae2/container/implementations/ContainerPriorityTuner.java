package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.items.contents.PriorityTunerGuiHost;
import ae2.items.tools.PriorityTunerItem;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerPriorityTuner extends AEBaseContainer {
    private static final String ACTION_SET_MODE = "setMode";
    private static final String ACTION_SET_PRIORITY = "setPriority";

    private final PriorityTunerGuiHost host;

    @GuiSync(1)
    private PriorityTunerItem.Mode mode = PriorityTunerItem.Mode.OUTPUT;
    @GuiSync(2)
    private int priority;

    public ContainerPriorityTuner(InventoryPlayer ip, PriorityTunerGuiHost host) {
        super(ip, host);
        this.host = host;
        PriorityTunerItem.Settings settings = this.host.getSettings();
        this.mode = settings.mode();
        this.priority = settings.priority();
        registerClientAction(ACTION_SET_MODE, PriorityTunerItem.Mode.class, this::setMode);
        registerClientAction(ACTION_SET_PRIORITY, Integer.class, this::setPriority);
    }

    @Override
    public void broadcastChanges() {
        PriorityTunerItem.Settings settings = this.host.getSettings();
        this.mode = settings.mode();
        this.priority = settings.priority();
        super.broadcastChanges();
    }

    public PriorityTunerItem.Mode getMode() {
        return this.mode;
    }

    public void setMode(PriorityTunerItem.Mode mode) {
        if (isClientSide()) {
            this.mode = mode;
            sendClientAction(ACTION_SET_MODE, mode);
        } else {
            this.host.setMode(mode);
        }
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        if (isClientSide()) {
            this.priority = priority;
            sendClientAction(ACTION_SET_PRIORITY, priority);
        } else {
            this.host.setPriority(priority);
        }
    }
}
