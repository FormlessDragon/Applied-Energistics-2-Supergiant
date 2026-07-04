package ae2.items.storage;

import ae2.api.config.CondenserOutput;
import ae2.api.config.FuzzyMode;
import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.IStackTooltipDataProvider;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.items.AEBaseItem;
import ae2.items.contents.CellConfig;
import ae2.items.contents.VoidCellGuiHost;
import ae2.me.cells.VoidCellHandler;
import ae2.util.ConfigInventory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class VoidCellItem extends AEBaseItem implements ICellWorkbenchItem, IStackTooltipDataProvider, IGuiItem {
    public static final String VOID_CELL_MODE = "void_cell_mode";
    public static final String VOID_CELL_ENERGY = "void_cell_energy";
    private static final String STORAGE_CELL_FUZZY_MODE = "storage_cell_fuzzy_mode";

    public VoidCellItem() {
        this.setMaxStackSize(1);
    }

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
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 2);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(is);
    }

    @Override
    public boolean supportsAutoPartition(ItemStack is) {
        return false;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORAGE_CELL_FUZZY_MODE, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(STORAGE_CELL_FUZZY_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            is.setTagCompound(tag);
        }
        tag.setString(STORAGE_CELL_FUZZY_MODE, fzMode.name());
    }

    public CondenserOutput getMode(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(VOID_CELL_MODE, 8)) {
            try {
                return CondenserOutput.valueOf(tag.getString(VOID_CELL_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return CondenserOutput.TRASH;
    }

    public void setMode(ItemStack stack, CondenserOutput mode) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (mode == CondenserOutput.TRASH) {
            tag.removeTag(VOID_CELL_MODE);
        } else {
            tag.setString(VOID_CELL_MODE, mode.name());
        }
    }

    public double getStoredVoidEnergy(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getDouble(VOID_CELL_ENERGY) : 0;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            GuiOpener.openItemGui(player, GuiIds.GuiKey.VOID_CELL, GuiHostLocators.forHand(player, hand));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Nullable
    @Override
    public ItemGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable RayTraceResult hitResult) {
        return new VoidCellGuiHost(this, player, locator);
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        addToTooltip(stack, lines);
    }

    @Override
    public Optional<StorageCellTooltipComponent> getStackTooltipData(ItemStack stack) {
        return VoidCellHandler.INSTANCE.getTooltipData(stack);
    }

    @Override
    public void addToTooltip(ItemStack stack, List<String> lines) {
        CondenserOutput mode = getMode(stack);
        lines.add(TextFormatting.RESET + TextFormatting.GREEN.toString() + GuiText.voidCellMode(mode).getLocal());
        lines.add(TextFormatting.RESET + TextFormatting.GRAY.toString() + GuiText.VoidCellOpenGui.getLocal());
        lines.add(createUsageLine());
        lines.add(createTypesLine());
        VoidCellHandler.INSTANCE.addPartitionInformation(stack, lines);
    }
}
