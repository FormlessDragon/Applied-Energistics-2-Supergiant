package ae2.container.implementations;

import ae2.api.config.Settings;
import ae2.api.util.IConfigManager;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.FakeSlot;
import ae2.helpers.InterfaceLogic;
import ae2.helpers.InterfaceLogicHost;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerInterface extends UpgradeableContainer<InterfaceLogicHost> {

    public static final String ACTION_OPEN_SET_AMOUNT = "setAmount";
    public static final String ACTION_SET_PAGE = "setPage";
    private final InterfacePageInventory configPageInventory;
    private final InterfacePageInventory storagePageInventory;

    @GuiSync(10)
    public int currentPage;
    @GuiSync(11)
    public int pageCount;

    public ContainerInterface(InventoryPlayer ip, InterfaceLogicHost host) {
        super(ip, host);

        registerClientAction(ACTION_OPEN_SET_AMOUNT, Integer.class, this::openSetAmountGui);
        registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);

        var logic = host.getInterfaceLogic();
        this.pageCount = logic.getPageCount();
        this.configPageInventory = new InterfacePageInventory(logic.getConfig().createGuiWrapper(),
            InterfaceLogic.SLOTS_PER_PAGE);
        for (int x = 0; x < this.configPageInventory.size(); x++) {
            this.addSlot(new FakeSlot(this.configPageInventory, x, 0, 0), SlotSemantics.CONFIG);
        }

        this.storagePageInventory = new InterfacePageInventory(logic.getStorage().createGuiWrapper(),
            InterfaceLogic.SLOTS_PER_PAGE);
        for (int x = 0; x < this.storagePageInventory.size(); x++) {
            this.addSlot(new AppEngSlot(this.storagePageInventory, x), SlotSemantics.STORAGE);
        }
    }

    private static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        return Math.clamp(page, 0, pageCount - 1);
    }

    @Override
    public void onClientDataSync(ShortSet updatedFields) {
        super.onClientDataSync(updatedFields);
        this.configPageInventory.setPage(this.currentPage);
        this.storagePageInventory.setPage(this.currentPage);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setFuzzyMode(cm.getSetting(Settings.FUZZY_MODE));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.pageCount = this.getHost().getInterfaceLogic().getPageCount();
            this.currentPage = clampPage(this.currentPage, this.pageCount);
        }
        this.configPageInventory.setPage(this.currentPage);
        this.storagePageInventory.setPage(this.currentPage);
        super.broadcastChanges();
    }

    public void openSetAmountGui(int configSlot) {
        if (isClientSide()) {
            sendClientAction(ACTION_OPEN_SET_AMOUNT, configSlot);
            return;
        }

        int actualSlot = this.currentPage * InterfaceLogic.SLOTS_PER_PAGE + configSlot;
        if (configSlot < 0 || configSlot >= InterfaceLogic.SLOTS_PER_PAGE
            || actualSlot < 0 || actualSlot >= getHost().getConfig().size()) {
            return;
        }
        var stack = getHost().getConfig().getStack(actualSlot);
        if (stack != null) {
            ContainerSetStockAmount.open((EntityPlayerMP) getPlayer(), getLocator(), actualSlot, stack.what(),
                (int) stack.amount());
        }
    }

    public void setPage(int page) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, page);
            return;
        }
        this.currentPage = clampPage(page, this.getHost().getInterfaceLogic().getPageCount());
        this.configPageInventory.setPage(this.currentPage);
        this.storagePageInventory.setPage(this.currentPage);
        this.detectAndSendChanges();
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public int getPageCount() {
        return this.pageCount;
    }
}
