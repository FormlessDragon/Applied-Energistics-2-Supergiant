package ae2.items.tools.powered;

import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.upgrades.Upgrades;
import ae2.container.GuiIds;
import ae2.core.definitions.AEBlocks;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.PlayerMessages;
import ae2.items.contents.PortableCellGuiHost;
import ae2.recipes.game.StorageCellDisassemblyRecipe;
import ae2.util.InteractionUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractPortableCell extends PoweredContainerItem implements ICellWorkbenchItem, IGuiItem {
    private static final String COLOR_TAG = "portableCellColor";

    private final GuiIds.GuiKey guiKey;
    private final int defaultColor;
    private final double powerCapacity;

    public AbstractPortableCell(GuiIds.GuiKey guiKey, double powerCapacity, int defaultColor) {
        super(powerCapacity);
        this.guiKey = guiKey;
        this.defaultColor = defaultColor;
        this.powerCapacity = powerCapacity;
    }

    public static int getColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 1 && stack.getItem() instanceof AbstractPortableCell portableCell) {
            if (portableCell.getAECurrentPower(stack) <= 0) {
                return CellState.ABSENT.getStateColor();
            }

            var cellInv = StorageCells.getCellInventory(stack, null);
            var cellStatus = cellInv != null ? cellInv.getStatus() : CellState.EMPTY;
            return cellStatus.getStateColor();
        } else if (tintIndex == 2 && stack.getItem() instanceof AbstractPortableCell portableCell) {
            return portableCell.getColor(stack);
        } else {
            return 0xFFFFFF;
        }
    }

    public abstract ResourceLocation getRecipeId();

    @Override
    public abstract double getChargeRate(ItemStack stack);

    public boolean openFromInventory(EntityPlayer player, ItemGuiHostLocator locator) {
        return openFromInventory(player, locator, false);
    }

    protected boolean openFromInventory(EntityPlayer player, ItemGuiHostLocator locator, boolean returningFromSubmenu) {
        var is = locator.locateItem(player);
        if (is.getItem() == this) {
            return GuiOpener.openItemGui(player, this.guiKey, locator, returningFromSubmenu);
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public PortableCellGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                             @Nullable RayTraceResult hitResult) {
        return new PortableCellGuiHost<>(this, player, locator, (p, sm) -> openFromInventory(p, locator, true));
    }

    public int getColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(COLOR_TAG) ? tag.getInteger(COLOR_TAG) : defaultColor;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        return player.isSneaking() && this.disassembleDrive(stack, world, player)
            ? EnumActionResult.SUCCESS
            : EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World level, EntityPlayer player, EnumHand hand) {
        if (!InteractionUtil.isInAlternateUseMode(player)
            || !disassembleDrive(player.getHeldItem(hand), level, player)) {
            if (!level.isRemote) {
                GuiOpener.openItemGui(player, this.guiKey, GuiHostLocators.forHand(player, hand));
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private boolean disassembleDrive(ItemStack stack, World level, EntityPlayer player) {
        var playerInventory = player.inventory;
        var disassemblyItems = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassemblyItems.isEmpty() || playerInventory.getCurrentItem() != stack || stack.getCount() != 1) {
            return false;
        }

        if (level.isRemote) {
            return true;
        }

        var inv = StorageCells.getCellInventory(stack, null);
        if (inv != null && !inv.getAvailableStacks().isEmpty()) {
            player.sendStatusMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return true;
        }

        playerInventory.setInventorySlotContents(playerInventory.currentItem, ItemStack.EMPTY);

        double remainingEnergy = getAECurrentPower(stack);
        for (var recipeStack : disassemblyItems) {
            var droppedStack = recipeStack.copy();
            remainingEnergy = transferEnergyToResult(droppedStack, remainingEnergy);
            playerInventory.placeItemBackInInventory(level, droppedStack);
        }

        getUpgrades(stack).forEach(upgrade -> playerInventory.placeItemBackInInventory(level, upgrade));

        return true;
    }

    private double transferEnergyToResult(ItemStack stack, double remainingEnergy) {
        if (remainingEnergy <= 0 || stack.getItem() != AEBlocks.ENERGY_CELL.item()) {
            return remainingEnergy;
        }

        var energyCell = AEBlocks.ENERGY_CELL.block();
        if (energyCell == null) {
            return remainingEnergy;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        NBTTagCompound blockEntityTag = tag.hasKey("BlockEntityTag", 10)
            ? tag.getCompoundTag("BlockEntityTag")
            : new NBTTagCompound();

        double maxPower = energyCell.getMaxPower();
        double storedPower = blockEntityTag.getDouble("internalCurrentPower");
        double insertedPower = Math.clamp(maxPower - storedPower, 0, remainingEnergy);
        if (insertedPower > 0) {
            blockEntityTag.setDouble("internalCurrentPower", storedPower + insertedPower);
            blockEntityTag.setDouble("internalMaxPower", maxPower);
            tag.setTag("BlockEntityTag", blockEntityTag);
        }

        return remainingEnergy - insertedPower;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 2, this::onUpgradesChanged);
    }

    public void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPower(stack, powerCapacity * (1 + Upgrades.getEnergyCardMultiplier(upgrades) * 8));
    }
}
