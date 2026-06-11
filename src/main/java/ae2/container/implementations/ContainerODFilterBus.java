package ae2.container.implementations;

import ae2.api.upgrades.IUpgradeableObject;
import ae2.container.guisync.GuiSync;
import ae2.parts.automation.special.ODFilterHost;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerODFilterBus<T extends IUpgradeableObject & ODFilterHost> extends UpgradeableContainer<T> {
    private static final int MAX_OD_EXPRESSION_LENGTH = 1024;
    private static final int MAX_OD_EXPRESSION_TOKENS = 128;

    private static final String ACTION_SET_WHITE = "setWhite";
    private static final String ACTION_SET_BLACK = "setBlack";

    @GuiSync(21)
    public String whiteExpression;

    @GuiSync(22)
    public String blackExpression;

    public ContainerODFilterBus(InventoryPlayer ip, T host) {
        super(ip, host);
        this.whiteExpression = host.getODFilter(true);
        this.blackExpression = host.getODFilter(false);
        registerClientAction(ACTION_SET_WHITE, String.class, this::setWhiteExpression);
        registerClientAction(ACTION_SET_BLACK, String.class, this::setBlackExpression);
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
            this.whiteExpression = getHost().getODFilter(true);
            this.blackExpression = getHost().getODFilter(false);
        }
        super.broadcastChanges();
    }

    private static boolean isODExpressionAllowed(String expression) {
        return expression == null
            || expression.length() <= MAX_OD_EXPRESSION_LENGTH
            && countODExpressionTokens(expression) <= MAX_OD_EXPRESSION_TOKENS;
    }

    private static int countODExpressionTokens(String expression) {
        int tokens = 0;
        boolean inTag = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (isODTagChar(c)) {
                inTag = true;
                continue;
            }
            if (inTag) {
                tokens++;
                inTag = false;
            }
            if (!Character.isWhitespace(c)) {
                tokens++;
            }
        }
        return inTag ? tokens + 1 : tokens;
    }

    private static boolean isODTagChar(char c) {
        return c == ':' || c == '*' || c == '_' || c == '-' || c == '/' || c == '.'
            || Character.isLetterOrDigit(c);
    }

    public void setWhiteExpression(String expression) {
        if (!isODExpressionAllowed(expression)) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_SET_WHITE, expression);
            this.whiteExpression = expression;
            return;
        }
        getHost().setODFilter(expression, true);
    }

    public void setBlackExpression(String expression) {
        if (!isODExpressionAllowed(expression)) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_SET_BLACK, expression);
            this.blackExpression = expression;
            return;
        }
        getHost().setODFilter(expression, false);
    }
}
