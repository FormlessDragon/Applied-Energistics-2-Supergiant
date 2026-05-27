package appeng.container.implementations;

import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.OutputSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.tile.networking.TileWirelessAccessPoint;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import java.util.Collections;

public class ContainerWirelessAccessPoint extends AEBaseContainer implements InternalInventoryHost {
    private final TileWirelessAccessPoint accessPoint;
    private final RestrictedInputSlot boosterSlot;
    private final RestrictedInputSlot linkableIn;
    private final OutputSlot linkableOut;
    private final AppEngInternalInventory gridLinkingInv;

    @GuiSync(1)
    public long range = 0;

    @GuiSync(2)
    public long drain = 0;

    public ContainerWirelessAccessPoint(InventoryPlayer ip, TileWirelessAccessPoint accessPoint) {
        super(ip, accessPoint);
        this.accessPoint = accessPoint;

        this.addSlot(this.boosterSlot = new RestrictedInputSlot(
            RestrictedInputSlot.PlacableItemType.RANGE_BOOSTER,
            accessPoint.getInternalInventory(),
            0), SlotSemantics.STORAGE);
        this.boosterSlot.setEmptyTooltip(() -> Collections.singletonList(Tooltips.of(ButtonToolTips.PlaceWirelessBooster)));

        this.gridLinkingInv = new AppEngInternalInventory(this, 2);
        this.gridLinkingInv.setEnableClientEvents(true);

        this.addSlot(this.linkableIn = new RestrictedInputSlot(
            RestrictedInputSlot.PlacableItemType.GRID_LINKABLE_ITEM,
            this.gridLinkingInv,
            0), SlotSemantics.MACHINE_INPUT);
        this.linkableIn.setEmptyTooltip(() -> Collections.singletonList(Tooltips.of(ButtonToolTips.LinkWirelessTerminal)));
        this.addSlot(this.linkableOut = new OutputSlot(this.gridLinkingInv, 1, 0, 0), SlotSemantics.MACHINE_OUTPUT);

        this.addPlayerInventorySlots(8, 84);
    }

    @Override
    public void broadcastChanges() {
        final ItemStack boosters = this.boosterSlot.getStack();
        final int installedBoosters = boosters.isEmpty() ? 0 : boosters.getCount();

        this.setRange((long) (10 * AEConfig.instance().wireless_getMaxRange(installedBoosters)));
        this.setDrain((long) (100 * AEConfig.instance().wireless_getPowerDrain(installedBoosters)));

        super.broadcastChanges();
    }

    public long getRange() {
        return this.range;
    }

    private void setRange(long range) {
        this.range = range;
    }

    public long getDrain() {
        return this.drain;
    }

    private void setDrain(long drain) {
        this.drain = drain;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);

        if (!player.world.isRemote) {
            this.returnSlotToPlayer(player, this.linkableIn);
            this.returnSlotToPlayer(player, this.linkableOut);
        }
    }

    private void returnSlotToPlayer(EntityPlayer player, OutputSlot slot) {
        if (!slot.getHasStack()) {
            return;
        }

        final ItemStack stack = slot.getStack().copy();
        slot.putStack(ItemStack.EMPTY);

        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    private void returnSlotToPlayer(EntityPlayer player, RestrictedInputSlot slot) {
        if (!slot.getHasStack()) {
            return;
        }

        final ItemStack stack = slot.getStack().copy();
        slot.putStack(ItemStack.EMPTY);

        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv != this.gridLinkingInv || this.linkableOut.getHasStack() || !this.linkableIn.getHasStack()) {
            return;
        }

        final ItemStack terminal = this.linkableIn.getStack().copy();
        final IGridLinkableHandler handler = GridLinkables.get(terminal.getItem());

        if (handler != null && handler.canLink(terminal)) {
            handler.link(terminal, this.accessPoint.getWorld(), this.accessPoint.getPos());
            this.linkableIn.putStack(ItemStack.EMPTY);
            this.linkableOut.putStack(terminal);
        }
    }

    @Override
    public boolean isClientSide() {
        return this.getPlayer().world.isRemote;
    }
}
