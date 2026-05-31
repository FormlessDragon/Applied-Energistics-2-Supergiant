package ae2.container.implementations;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.InaccessibleSlot;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.helpers.InterfaceLogicHost;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.util.inv.AppEngInternalInventory;
import com.google.common.primitives.Ints;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import javax.annotation.Nullable;
import java.util.Objects;

public class ContainerSetStockAmount extends AEBaseContainer implements ISubGui {

    public static final String ACTION_SET_STOCK_AMOUNT = "setStockAmount";

    private final Slot stockedItem;
    private final InterfaceLogicHost host;

    private AEKey whatToStock;
    @GuiSync(1)
    private int initialAmount = -1;
    @GuiSync(2)
    private int maxAmount = -1;
    @GuiSync(3)
    private int slot = -1;

    public ContainerSetStockAmount(InventoryPlayer ip, InterfaceLogicHost host) {
        super(ip, host);
        registerClientAction(ACTION_SET_STOCK_AMOUNT, Integer.class, this::confirm);
        this.host = host;
        this.stockedItem = new InaccessibleSlot(new AppEngInternalInventory(1), 0);
        this.addSlot(this.stockedItem, SlotSemantics.MACHINE_OUTPUT);
    }

    public static void open(EntityPlayerMP player, GuiHostLocator locator, int slot, AEKey whatToStock,
                            int initialAmount) {
        SwitchGuisPacket.openSubGui(player, locator, GuiIds.GuiKey.SET_STOCK_AMOUNT);

        if (player.openContainer instanceof ContainerSetStockAmount container) {
            container.setWhatToStock(slot, whatToStock, initialAmount);
            container.broadcastChanges();
        }
    }

    @Override
    public InterfaceLogicHost getHost() {
        return this.host;
    }

    private void setWhatToStock(int slot, AEKey whatToStock, int initialAmount) {
        this.slot = slot;
        this.whatToStock = Objects.requireNonNull(whatToStock, "whatToStock");
        this.initialAmount = initialAmount;
        this.maxAmount = Ints.saturatedCast(host.getConfig().getMaxAmount(whatToStock));
        this.stockedItem.putStack(whatToStock.wrapForDisplayOrFilter());
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public void confirm(int amount) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_STOCK_AMOUNT, amount);
            return;
        }

        GenericStackInv config = host.getConfig();
        if (!Objects.equals(config.getKey(this.slot), whatToStock)) {
            host.returnToMainContainer(getPlayer(), this);
            return;
        }

        amount = (int) Math.min(amount, config.getMaxAmount(whatToStock));
        if (amount <= 0) {
            config.setStack(slot, null);
        } else {
            config.setStack(slot, new GenericStack(whatToStock, amount));
        }
        host.returnToMainContainer(getPlayer(), this);
    }

    public int getInitialAmount() {
        return initialAmount;
    }

    @Nullable
    public AEKey getWhatToStock() {
        GenericStack stack = GenericStack.fromItemStack(stockedItem.getStack());
        return stack != null ? stack.what() : null;
    }
}
