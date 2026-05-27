package appeng.container.slot;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEItemIds;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.implementations.items.ISpatialStorageCell;
import appeng.api.implementations.items.IStorageComponent;
import appeng.api.inventories.InternalInventory;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.tile.crafting.IMolecularAssemblerSupportedPattern;
import appeng.tile.misc.InscriberRecipes;
import appeng.tile.qnb.TileQuantumBridge;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

public class RestrictedInputSlot extends AppEngSlot {
    private final PlacableItemType type;
    private boolean allowEdit = true;
    private int stackLimit = -1;

    public RestrictedInputSlot(PlacableItemType type, InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
        this.type = type;
        this.setBackgroundIcon(type.backgroundIcon);
    }

    public RestrictedInputSlot(PlacableItemType type, InternalInventory inventory, int slotIndex) {
        this(type, inventory, slotIndex, 0, 0);
    }

    @Override
    public int getSlotStackLimit() {
        return this.stackLimit >= 0 ? this.stackLimit : super.getSlotStackLimit();
    }

    public Slot setStackLimit(int stackLimit) {
        this.stackLimit = stackLimit;
        return this;
    }

    private boolean matchesItem(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && stack.getItem().getRegistryName() != null && stack.getItem().getRegistryName().equals(id);
    }

    private boolean hasOrePrefix(ItemStack stack, String prefix) {
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName != null && oreName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMolecularAssemblerPattern(ItemStack stack) {
        if (!PatternDetailsHelper.isEncodedPattern(stack) || getContainer() == null || getContainer().getPlayer().world == null) {
            return false;
        }

        try {
            Object pattern = PatternDetailsHelper.decodePattern(stack, getContainer().getPlayer().world);
            return pattern instanceof IMolecularAssemblerSupportedPattern;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isSpatialStorageCell(ItemStack stack) {
        if (!(stack.getItem() instanceof ISpatialStorageCell spatialStorageCell)) {
            return false;
        }
        return spatialStorageCell.isSpatialStorage(stack);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        if (getContainer() != null && !getContainer().isValidForSlot(this, stack)) {
            return false;
        }

        if (!this.allowEdit || stack.isEmpty() || !super.isItemValid(stack)) {
            return false;
        }

        return switch (this.type) {
            case STORAGE_CELLS -> StorageCells.isCellHandled(stack);
            case STORAGE_COMPONENT -> stack.getItem() instanceof IStorageComponent storageComponent
                && storageComponent.isStorageComponent(stack);
            case GRID_LINKABLE_ITEM -> {
                IGridLinkableHandler handler = GridLinkables.get(stack.getItem());
                yield handler != null && handler.canLink(stack);
            }
            case TRASH -> {
                if (StorageCells.isCellHandled(stack)) {
                    yield false;
                }
                if (stack.getItem() instanceof IStorageComponent storageComponent) {
                    yield !storageComponent.isStorageComponent(stack);
                }
                yield true;
            }
            case ENCODED_AE_PATTERN -> matchesItem(stack, AEItemIds.CRAFTING_PATTERN)
                || matchesItem(stack, AEItemIds.PROCESSING_PATTERN);
            case MOLECULAR_ASSEMBLER_PATTERN -> isMolecularAssemblerPattern(stack);
            case PROVIDER_PATTERN, ENCODED_PATTERN -> PatternDetailsHelper.isEncodedPattern(stack);
            case BLANK_PATTERN -> AEItems.BLANK_PATTERN.item() == stack.getItem()
                || matchesItem(stack, AEItemIds.BLANK_PATTERN);
            case POWERED_TOOL -> stack.getItem() instanceof IAEItemPowerStorage;
            case RANGE_BOOSTER -> matchesItem(stack, AEItemIds.WIRELESS_BOOSTER);
            case QE_SINGULARITY -> TileQuantumBridge.isValidEntangledSingularity(stack);
            case SPATIAL_STORAGE_CELLS, SPATIAL_STORAGE_CELLS_NO_SHADOW -> isSpatialStorageCell(stack);
            case FUEL -> TileEntityFurnace.isItemFuel(stack);
            case UPGRADES -> Upgrades.isUpgradeCardItem(stack);
            case WORKBENCH_CELL -> stack.getItem() instanceof ICellWorkbenchItem cellWorkbenchItem
                && cellWorkbenchItem.isEditable(stack);
            case VIEW_CELL -> matchesItem(stack, AEItemIds.VIEW_CELL);
            case INSCRIBER_PLATE -> {
                if (AEItems.NAME_PRESS.is(stack)) {
                    yield true;
                }
                yield getContainer() != null && InscriberRecipes.isValidOptionalIngredient(stack);
            }
            case INSCRIBER_INPUT -> true;
            case METAL_INGOTS -> hasOrePrefix(stack, "ingot");
            case ORE -> hasOrePrefix(stack, "ore");
            case PATTERN -> matchesItem(stack, AEItemIds.BLANK_PATTERN) || PatternDetailsHelper.isEncodedPattern(stack);
        };
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return this.allowEdit && super.canTakeStack(player);
    }

    @Override
    public ItemStack getDisplayStack() {
        if (isRemote() && (this.type == PlacableItemType.ENCODED_PATTERN
            || this.type == PlacableItemType.PROVIDER_PATTERN)) {
            ItemStack stack = super.getDisplayStack();
            if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem<?> encodedPatternItem
                && getContainer() != null) {
                ItemStack output = encodedPatternItem.getOutput(stack, getContainer().getPlayer().world);
                if (!output.isEmpty()) {
                    return output;
                }
            }
        }
        return super.getDisplayStack();
    }

    @SuppressWarnings("unused")
    public void setAllowEdit(boolean allowEdit) {
        this.allowEdit = allowEdit;
    }

    public enum PlacableItemType {
        STORAGE_CELLS(SlotBackgroundIcon.STORAGE_CELL),
        ORE(SlotBackgroundIcon.ORE),
        STORAGE_COMPONENT(SlotBackgroundIcon.STORAGE_COMPONENT),
        GRID_LINKABLE_ITEM(SlotBackgroundIcon.WIRELESS_TERM),
        TRASH(SlotBackgroundIcon.TRASH),
        ENCODED_AE_PATTERN(SlotBackgroundIcon.ENCODED_PATTERN),
        MOLECULAR_ASSEMBLER_PATTERN(SlotBackgroundIcon.BLANK_PATTERN),
        PROVIDER_PATTERN(SlotBackgroundIcon.BLANK_PATTERN),
        ENCODED_PATTERN(SlotBackgroundIcon.ENCODED_PATTERN),
        PATTERN(SlotBackgroundIcon.BLANK_PATTERN),
        BLANK_PATTERN(SlotBackgroundIcon.BLANK_PATTERN),
        POWERED_TOOL(SlotBackgroundIcon.CHARGABLE),
        RANGE_BOOSTER(SlotBackgroundIcon.WIRELESS_BOOSTER),
        QE_SINGULARITY(SlotBackgroundIcon.SINGULARITY),
        SPATIAL_STORAGE_CELLS(SlotBackgroundIcon.SPATIAL_CELL),
        SPATIAL_STORAGE_CELLS_NO_SHADOW(SlotBackgroundIcon.SPATIAL_CELL_NO_SHADOW),
        FUEL(SlotBackgroundIcon.FUEL),
        UPGRADES(SlotBackgroundIcon.UPGRADE),
        WORKBENCH_CELL(SlotBackgroundIcon.STORAGE_CELL),
        VIEW_CELL(SlotBackgroundIcon.VIEW_CELL),
        INSCRIBER_PLATE(SlotBackgroundIcon.PLATE),
        INSCRIBER_INPUT(SlotBackgroundIcon.INGOT),
        METAL_INGOTS(SlotBackgroundIcon.INGOT);

        public final SlotBackgroundIcon backgroundIcon;

        PlacableItemType(SlotBackgroundIcon backgroundIcon) {
            this.backgroundIcon = backgroundIcon;
        }
    }
}
