package ae2.items.tools.quartz;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.parts.SelectedPart;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.AEBaseItem;
import ae2.parts.AEBasePart;
import ae2.tile.AEBaseTile;
import ae2.tile.networking.TileCableBus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class QuartzCuttingKnifeItem extends AEBaseItem implements IGuiItem {
    public QuartzCuttingKnifeItem() {
        super();
        this.setMaxStackSize(1);
        this.setMaxDamage(50);
    }

    public static ItemStack damageKnife(ItemStack stack) {
        ItemStack damaged = stack.copy();
        if (damaged.isItemStackDamageable()) {
            damaged.setItemDamage(damaged.getItemDamage() + 1);
            if (damaged.getItemDamage() >= damaged.getMaxDamage()) {
                return ItemStack.EMPTY;
            }
        }
        return damaged;
    }

    @Nullable
    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new ItemGuiHost<>(this, player, locator);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.QUARTZ_KNIFE, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }

        if (isRenamableAeTarget(world, pos, hitX, hitY, hitZ)) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.QUARTZ_KNIFE,
                GuiHostLocators.forItemUseContext(player, hand, pos, side, hitX, hitY, hitZ));
        }
        return EnumActionResult.SUCCESS;
    }

    private static boolean isRenamableAeTarget(World world, BlockPos pos, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileCableBus cableBus) {
            SelectedPart selectedPart = cableBus.selectPartLocal(new Vec3d(hitX, hitY, hitZ));
            return selectedPart.part instanceof AEBasePart;
        }
        return tile instanceof AEBaseTile;
    }
}
