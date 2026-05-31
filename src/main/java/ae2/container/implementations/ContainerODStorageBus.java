package ae2.container.implementations;

import ae2.container.guisync.GuiSync;
import ae2.parts.automation.special.ODFilterHost;
import ae2.parts.automation.special.ODStorageBusPart;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerODStorageBus extends ContainerStorageBus {
    private static final String ACTION_SET_WHITE = "setWhite";
    private static final String ACTION_SET_BLACK = "setBlack";

    @GuiSync(21)
    public String whiteExpression;

    @GuiSync(22)
    public String blackExpression;

    public ContainerODStorageBus(InventoryPlayer ip, ODStorageBusPart host) {
        super(ip, host);
        this.whiteExpression = host.getODFilter(true);
        this.blackExpression = host.getODFilter(false);
        registerClientAction(ACTION_SET_WHITE, String.class, this::setWhiteExpression);
        registerClientAction(ACTION_SET_BLACK, String.class, this::setBlackExpression);
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
            ODFilterHost host = (ODFilterHost) getHost();
            this.whiteExpression = host.getODFilter(true);
            this.blackExpression = host.getODFilter(false);
        }
        super.broadcastChanges();
    }

    public void setWhiteExpression(String expression) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_WHITE, expression);
            this.whiteExpression = expression;
            return;
        }
        ((ODFilterHost) getHost()).setODFilter(expression, true);
    }

    public void setBlackExpression(String expression) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_BLACK, expression);
            this.blackExpression = expression;
            return;
        }
        ((ODFilterHost) getHost()).setODFilter(expression, false);
    }
}
