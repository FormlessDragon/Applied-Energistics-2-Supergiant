package ae2.items.contents;

import ae2.api.config.Actionable;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.stacks.AEKey;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.StorageCell;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.storage.PortableVoidCellItem;
import ae2.me.helpers.PlayerSource;
import ae2.util.CellWorkbenchFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public class PortableVoidCellGuiHost extends ItemGuiHost<PortableVoidCellItem> implements ISubGuiHost {

    public PortableVoidCellGuiHost(PortableVoidCellItem item, EntityPlayer player, ItemGuiHostLocator locator) {
        super(item, player, locator);
    }

    @Override
    public long insert(EntityPlayer player, AEKey what, long amount, Actionable mode) {
        ItemStack stack = getItemStack();
        if (!CellWorkbenchFilter.matches(stack, getItem(), what, CellWorkbenchFilter.isInverted(stack, getItem()),
            CellWorkbenchFilter.isFuzzy(stack, getItem()))) {
            return 0;
        }

        MEStorage storage = StorageCells.getCellInventory(getItemStack(), null);
        if (storage == null) {
            return 0;
        }
        long inserted = storage.insert(what, amount, mode, new PlayerSource(player));
        if (mode == Actionable.MODULATE && storage instanceof StorageCell cell) {
            cell.persist();
        }
        return inserted;
    }

    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openItemGui(player, GuiIds.GuiKey.PORTABLE_VOID_CELL, Objects.requireNonNull(getLocator()), true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getItemStack();
    }
}
