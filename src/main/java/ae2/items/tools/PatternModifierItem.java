package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.AEBaseItem;
import ae2.items.contents.PatternModifierGuiHost;
import ae2.util.InteractionUtil;
import baubles.api.BaubleType;
import baubles.api.IBauble;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import org.jetbrains.annotations.Nullable;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class PatternModifierItem extends AEBaseItem implements IGuiItem, IBauble {
    public static final int PATTERN_SLOTS = 27;
    public static final int BLANK_PATTERN_SLOTS = 4;

    public PatternModifierItem() {
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.PATTERN_MODIFIER, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            return GuiOpener.openItemGui(player, GuiIds.GuiKey.PATTERN_MODIFIER,
                GuiHostLocators.forItemUseContext(player, hand, pos, side, hitX, hitY, hitZ))
                ? EnumActionResult.SUCCESS
                : EnumActionResult.FAIL;
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new PatternModifierGuiHost(this, player, locator);
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.TRINKET;
    }
}
