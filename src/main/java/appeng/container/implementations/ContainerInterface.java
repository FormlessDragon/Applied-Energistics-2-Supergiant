package appeng.container.implementations;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.container.SlotSemantics;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.FakeSlot;
import appeng.helpers.InterfaceLogicHost;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerInterface extends UpgradeableContainer<InterfaceLogicHost> {

    public static final String ACTION_OPEN_SET_AMOUNT = "setAmount";

    public ContainerInterface(InventoryPlayer ip, InterfaceLogicHost host) {
        super(ip, host);

        registerClientAction(ACTION_OPEN_SET_AMOUNT, Integer.class, this::openSetAmountGui);

        var logic = host.getInterfaceLogic();
        var config = logic.getConfig().createGuiWrapper();
        for (int x = 0; x < config.size(); x++) {
            this.addSlot(new FakeSlot(config, x, 0, 0), SlotSemantics.CONFIG);
        }

        var storage = logic.getStorage().createGuiWrapper();
        for (int x = 0; x < storage.size(); x++) {
            this.addSlot(new AppEngSlot(storage, x), SlotSemantics.STORAGE);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
    }

    public void openSetAmountGui(int configSlot) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_SET_AMOUNT, configSlot);
            return;
        }

        var stack = getHost().getConfig().getStack(configSlot);
        if (stack != null) {
            ContainerSetStockAmount.open((EntityPlayerMP) getPlayer(), getLocator(), configSlot, stack.what(),
                (int) stack.amount());
        }
    }
}
