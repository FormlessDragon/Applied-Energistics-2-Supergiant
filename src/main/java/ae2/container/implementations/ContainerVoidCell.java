package ae2.container.implementations;

import ae2.api.config.CondenserOutput;
import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.items.contents.VoidCellGuiHost;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerVoidCell extends AEBaseContainer {
    private static final CondenserOutput[] CONDENSER_OUTPUTS = CondenserOutput.values();

    private final VoidCellGuiHost host;

    @GuiSync(0)
    public CondenserOutput output = CondenserOutput.TRASH;

    public ContainerVoidCell(InventoryPlayer ip, VoidCellGuiHost host) {
        super(ip, host);
        this.host = host;

        registerClientAction("setMode", Integer.class, this::setMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.output = this.host.getMode();
        }

        super.broadcastChanges();
    }

    public CondenserOutput getOutput() {
        return this.output;
    }

    public void setModeFromClient(CondenserOutput mode) {
        sendClientAction("setMode", mode.ordinal());
    }

    public void setMode(Integer mode) {
        if (mode == null || mode < 0 || mode >= CONDENSER_OUTPUTS.length) {
            return;
        }

        this.host.setMode(CONDENSER_OUTPUTS[mode]);
        this.output = this.host.getMode();
        this.detectAndSendChanges();
    }
}
