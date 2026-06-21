package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.AEBaseItem;
import ae2.items.contents.PortableCellWorkbenchGuiHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PortableCellWorkbenchItem extends AEBaseItem implements IGuiItem {

    public PortableCellWorkbenchItem() {
        this.setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.PORTABLE_CELL_WORKBENCH, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Nullable
    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new PortableCellWorkbenchGuiHost(this, player, locator);
    }
}
