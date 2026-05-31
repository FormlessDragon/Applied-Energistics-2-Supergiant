package ae2.items.contents;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.inventories.InternalInventory;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.api.stacks.GenericStack;
import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.items.tools.PatternModifierItem;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class PatternModifierGuiHost extends ItemGuiHost<PatternModifierItem> implements InternalInventoryHost {
    private static final String PATTERN_INVENTORY_TAG = "patternInv";
    private static final String TARGET_INVENTORY_TAG = "targetInv";
    private static final String BLANK_PATTERN_INVENTORY_TAG = "blankPatternInv";
    private static final String CLONE_PATTERN_INVENTORY_TAG = "clonePatternInv";
    private static final String REPLACE_INVENTORY_TAG = "replaceInv";

    private final AppEngInternalInventory patternInventory =
        new AppEngInternalInventory(this, PatternModifierItem.PATTERN_SLOTS,
            AEItems.CRAFTING_PATTERN.stack().getMaxStackSize(), PatternFilter.ENCODED);
    private final AppEngInternalInventory targetInventory =
        new AppEngInternalInventory(this, 1, 1, PatternFilter.ENCODED);
    private final AppEngInternalInventory blankPatternInventory =
        new AppEngInternalInventory(this, PatternModifierItem.BLANK_PATTERN_SLOTS, 64, PatternFilter.BLANK);
    private final AppEngInternalInventory clonePatternInventory =
        new AppEngInternalInventory(this, 1, 1, PatternFilter.ENCODED);
    private final AppEngInternalInventory replaceInventory =
        new AppEngInternalInventory(this, 2, 1, PatternFilter.ANY_FILTER);
    @Nullable
    private final PatternProviderLogicHost patternProvider;

    public PatternModifierGuiHost(PatternModifierItem item, EntityPlayer player, ItemGuiHostLocator locator) {
        super(item, player, locator);
        this.patternProvider = findPatternProvider(player, locator.hitResult());
        readInventories();
    }

    @Nullable
    private static PatternProviderLogicHost findPatternProvider(EntityPlayer player, @Nullable RayTraceResult hitResult) {
        if (hitResult == null || hitResult.getBlockPos() == null) {
            return null;
        }

        TileEntity tile = player.world.getTileEntity(hitResult.getBlockPos());
        if (tile instanceof PatternProviderLogicHost provider) {
            return provider;
        }
        if (tile instanceof IPartHost partHost) {
            Vec3d hit = hitResult.hitVec.subtract(
                hitResult.getBlockPos().getX(),
                hitResult.getBlockPos().getY(),
                hitResult.getBlockPos().getZ());
            SelectedPart selectedPart = partHost.selectPartLocal(hit);
            if (selectedPart.part instanceof PatternProviderLogicHost provider) {
                return provider;
            }
        }
        return null;
    }

    public AppEngInternalInventory getPatternInventory() {
        return this.patternInventory;
    }

    public AppEngInternalInventory getTargetInventory() {
        return this.targetInventory;
    }

    public AppEngInternalInventory getBlankPatternInventory() {
        return this.blankPatternInventory;
    }

    public AppEngInternalInventory getClonePatternInventory() {
        return this.clonePatternInventory;
    }

    public AppEngInternalInventory getReplaceInventory() {
        return this.replaceInventory;
    }

    @Nullable
    public PatternProviderLogicHost getPatternProvider() {
        return this.patternProvider;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        writeInventories();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        writeInventories();
    }

    @Override
    public boolean isClientSide() {
        return getPlayer().world.isRemote;
    }

    private void readInventories() {
        NBTTagCompound tag = getItemStack().getTagCompound();
        if (tag == null) {
            return;
        }

        this.patternInventory.readFromNBT(tag, PATTERN_INVENTORY_TAG);
        this.targetInventory.readFromNBT(tag, TARGET_INVENTORY_TAG);
        this.blankPatternInventory.readFromNBT(tag, BLANK_PATTERN_INVENTORY_TAG);
        this.clonePatternInventory.readFromNBT(tag, CLONE_PATTERN_INVENTORY_TAG);
        this.replaceInventory.readFromNBT(tag, REPLACE_INVENTORY_TAG);
    }

    private void writeInventories() {
        ItemStack stack = getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        this.patternInventory.writeToNBT(tag, PATTERN_INVENTORY_TAG);
        this.targetInventory.writeToNBT(tag, TARGET_INVENTORY_TAG);
        this.blankPatternInventory.writeToNBT(tag, BLANK_PATTERN_INVENTORY_TAG);
        this.clonePatternInventory.writeToNBT(tag, CLONE_PATTERN_INVENTORY_TAG);
        this.replaceInventory.writeToNBT(tag, REPLACE_INVENTORY_TAG);
    }

    private enum PatternFilter implements IAEItemFilter {
        ENCODED {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return PatternDetailsHelper.isEncodedPattern(stack);
            }
        },
        BLANK {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return AEItems.BLANK_PATTERN.is(stack);
            }
        },
        ANY_FILTER {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return stack.isEmpty() || GenericStack.unwrapItemStack(stack) == null;
            }
        }
    }
}
