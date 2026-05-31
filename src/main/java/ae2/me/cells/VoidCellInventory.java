package ae2.me.cells;

import ae2.api.config.Actionable;
import ae2.api.config.CondenserOutput;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.ISaveProvider;
import ae2.api.storage.cells.StorageCell;
import ae2.core.definitions.AEItems;
import ae2.items.storage.VoidCellItem;
import ae2.text.TextComponentItemStack;
import ae2.util.CellWorkbenchFilter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class VoidCellInventory implements StorageCell {
    private static final String STORED_STACKS = "void_cell_stored_stacks";

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider host;
    private final CondenserOutput mode;
    private AEKey2LongMap storedAmounts;
    private double voidEnergy;
    private boolean persisted = true;

    public VoidCellInventory(ItemStack stack) {
        this(stack, null);
    }

    public VoidCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        this.stack = stack;
        this.host = host;
        this.mode = stack.getItem() instanceof VoidCellItem voidCellItem
            ? voidCellItem.getMode(stack)
            : CondenserOutput.TRASH;
        NBTTagCompound tag = stack.getTagCompound();
        this.voidEnergy = tag != null ? tag.getDouble(VoidCellItem.VOID_CELL_ENERGY) : 0;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !matches(what)) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            this.voidEnergy += (double) amount / what.getAmountPerUnit();
            fillOutput();
            saveChanges();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) {
            return 0;
        }

        long currentAmount = getCellItems().getLong(what);
        if (currentAmount <= 0) {
            return 0;
        }

        long extracted = Math.min(amount, currentAmount);
        if (mode == Actionable.MODULATE) {
            if (extracted == currentAmount) {
                getCellItems().removeLong(what);
            } else {
                getCellItems().put(what, currentAmount - extracted);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : getCellItems().object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                out.add(entry.getKey(), amount);
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return matches(what);
    }

    @Override
    public CellState getStatus() {
        return getCellItems().isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 1.0d;
    }

    @Override
    public boolean canFitInsideCell() {
        return false;
    }

    @Override
    public ITextComponent getDescription() {
        return TextComponentItemStack.of(stack);
    }

    @Override
    public void persist() {
        if (this.persisted) {
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (getCellItems().isEmpty()) {
            tag.removeTag(STORED_STACKS);
        } else {
            var stacks = new ArrayList<GenericStack>(getCellItems().size());
            for (var entry : getCellItems().object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
            tag.setTag(STORED_STACKS, GenericStack.writeList(stacks));
        }

        if (this.voidEnergy <= 0) {
            tag.removeTag(VoidCellItem.VOID_CELL_ENERGY);
        } else {
            tag.setDouble(VoidCellItem.VOID_CELL_ENERGY, this.voidEnergy);
        }
        this.persisted = true;
    }

    private AEKey2LongMap getCellItems() {
        if (this.storedAmounts == null) {
            this.storedAmounts = new AEKey2LongMap.OpenHashMap();
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey(STORED_STACKS, 9)) {
                for (var genericStack : GenericStack.readList(tag.getTagList(STORED_STACKS, 10))) {
                    if (genericStack != null && genericStack.amount() > 0) {
                        this.storedAmounts.addTo(genericStack.what(), genericStack.amount());
                    }
                }
            }
        }
        return this.storedAmounts;
    }

    private void fillOutput() {
        AEItemKey output = switch (this.mode) {
            case MATTER_BALLS -> AEItemKey.of(AEItems.MATTER_BALL.item());
            case SINGULARITY -> AEItemKey.of(AEItems.SINGULARITY.item());
            case TRASH -> null;
        };
        if (output == null || this.mode.requiredPower <= 0) {
            this.voidEnergy = 0;
            return;
        }

        long amount = (long) (this.voidEnergy / this.mode.requiredPower);
        if (amount > 0) {
            getCellItems().addTo(output, amount);
            this.voidEnergy -= amount * this.mode.requiredPower;
        }
    }

    private void saveChanges() {
        this.persisted = false;
        if (this.host != null) {
            this.host.saveChanges();
        } else {
            persist();
        }
    }

    private boolean matches(AEKey key) {
        if (!(stack.getItem() instanceof VoidCellItem voidCellItem)) {
            return false;
        }

        return CellWorkbenchFilter.matches(
            stack,
            voidCellItem,
            key,
            CellWorkbenchFilter.isInverted(stack, voidCellItem),
            CellWorkbenchFilter.isFuzzy(stack, voidCellItem));
    }
}
