package ae2.items.storage;

import ae2.api.config.CondenserOutput;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.items.contents.PortableVoidCellGuiHost;
import ae2.items.tools.powered.PortableItemCellAutoPickup;
import ae2.me.cells.VoidCellHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PortableVoidCellItem extends VoidCellItem {

    private static String createUsageLine() {
        return GuiText.BytesUsed.getLocal(numberText())
            + TextFormatting.RESET + " / "
            + obfuscatedMaxText();
    }

    private static String createTypesLine() {
        return TextFormatting.GREEN + "0 "
            + GuiText.Of.getLocal() + " "
            + obfuscatedMaxText()
            + TextFormatting.LIGHT_PURPLE + " "
            + GuiText.Types.getLocal()
            + TextFormatting.RESET;
    }

    private static String numberText() {
        return TextFormatting.GREEN + "0" + TextFormatting.RESET;
    }

    private static String obfuscatedMaxText() {
        return TextFormatting.DARK_RED.toString()
            + TextFormatting.OBFUSCATED
            + "9999"
            + TextFormatting.RESET;
    }

    @Override
    public void setMode(ItemStack stack, CondenserOutput mode) {
        super.setMode(stack, mode);
        PortableItemCellAutoPickup.invalidateCachedCell(stack);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.PORTABLE_VOID_CELL, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Nullable
    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new PortableVoidCellGuiHost(this, player, locator);
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        addToTooltip(stack, lines);
    }

    @Override
    public void addToTooltip(ItemStack stack, List<String> lines) {
        lines.add(TextFormatting.RESET + TextFormatting.GREEN.toString()
            + GuiText.voidCellMode(getMode(stack)).getLocal());
        lines.add(createUsageLine());
        lines.add(createTypesLine());
        VoidCellHandler.INSTANCE.addPartitionInformation(stack, lines);
        PortableItemCellAutoPickup.addInformationToTooltip(stack, lines);
    }
}
