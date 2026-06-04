package ae2.items.tools;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.AEBaseItem;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class TickAnalyserItem extends AEBaseItem implements IGuiItem {
    private static final String CONFIG_TAG = "tickAnalyserConfig";

    public TickAnalyserItem() {
        setMaxStackSize(1);
    }

    public static TickAnalyserConfig getConfig(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(CONFIG_TAG, 10)
            ? TickAnalyserConfig.fromTag(tag.getCompoundTag(CONFIG_TAG))
            : TickAnalyserConfig.DEFAULT;
    }

    public static void setConfig(ItemStack stack, TickAnalyserConfig config) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setTag(CONFIG_TAG, config.toTag());
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote && !player.isSneaking()) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.TICK_ANALYSER, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new AnalyserItemHost<>(this, player, locator);
    }
}
