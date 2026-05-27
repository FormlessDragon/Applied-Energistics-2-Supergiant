package appeng.container.implementations;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.container.AEBaseContainer;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.InaccessibleSlot;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.inv.AppEngInternalInventory;
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
