package ae2.container.implementations;

import ae2.container.guisync.GuiSync;
import ae2.parts.automation.UpgradeablePart;
import ae2.parts.automation.special.ModFilterHost;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerModFilterBus<T extends UpgradeablePart & ModFilterHost> extends UpgradeableContainer<T> {
    private static final int MAX_MOD_EXPRESSION_LENGTH = 1024;

    private static final String ACTION_SET = "setMod";

    @GuiSync(23)
    public String modExpression;

    public ContainerModFilterBus(InventoryPlayer ip, T host) {
        super(ip, host);
        this.modExpression = host.getModFilter();
        registerClientAction(ACTION_SET, String.class, this::setModExpression);
    }

    @Override
    protected void setupConfig() {
        super.setupConfig();
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.modExpression = getHost().getModFilter();
        }
        super.broadcastChanges();
    }

    private static boolean isModExpressionAllowed(String expression) {
        return expression == null || expression.length() <= MAX_MOD_EXPRESSION_LENGTH;
    }

    public void setModExpression(String expression) {
        if (!isModExpressionAllowed(expression)) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_SET, expression);
            this.modExpression = expression;
            return;
        }
        getHost().setModFilter(expression);
    }
}
