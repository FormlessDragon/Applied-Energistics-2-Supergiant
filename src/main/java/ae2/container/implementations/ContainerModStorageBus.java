package ae2.container.implementations;

import ae2.container.guisync.GuiSync;
import ae2.parts.automation.special.ModFilterHost;
import ae2.parts.automation.special.ModStorageBusPart;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerModStorageBus extends ContainerStorageBus {
    private static final String ACTION_SET = "setMod";

    @GuiSync(23)
    public String modExpression;

    public ContainerModStorageBus(InventoryPlayer ip, ModStorageBusPart host) {
        super(ip, host);
        this.modExpression = host.getModFilter();
        registerClientAction(ACTION_SET, String.class, this::setModExpression);
    }

    @Override
    protected void setupConfig() {
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.modExpression = ((ModFilterHost) getHost()).getModFilter();
        }
        super.broadcastChanges();
    }

    public void setModExpression(String expression) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET, expression);
            this.modExpression = expression;
            return;
        }
        ((ModFilterHost) getHost()).setModFilter(expression);
    }
}
