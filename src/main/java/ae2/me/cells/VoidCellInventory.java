package ae2.me.cells;

import ae2.api.config.Actionable;
import ae2.api.config.CondenserOutput;
import ae2.api.config.IncludeExclude;
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
import ae2.tile.misc.CondenserLogic;
import ae2.tile.misc.CondenserLogicHost;
import ae2.util.CellWorkbenchFilter;
import ae2.util.prioritylist.DefaultPriorityList;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class VoidCellInventory implements StorageCell, CondenserLogicHost {
    private static final String STORED_STACKS = "void_cell_stored_stacks";

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider host;
    private final CondenserOutput mode;
    private final IncludeExclude partitionMode;
    private final IPartitionList partitionList;
    private AEKey2LongMap storedAmounts;
    private double voidEnergy;
    private boolean persisted = true;

    public VoidCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        this.stack = stack;
        this.host = host;
        if (stack.getItem() instanceof VoidCellItem voidCellItem) {
            boolean fuzzy = CellWorkbenchFilter.isFuzzy(stack, voidCellItem);
            this.mode = voidCellItem.getMode(stack);
            this.partitionMode = CellWorkbenchFilter.getMode(CellWorkbenchFilter.isInverted(stack, voidCellItem));
            this.partitionList = CellWorkbenchFilter.createPartitionList(stack, voidCellItem, fuzzy);
        } else {
            this.mode = CondenserOutput.TRASH;
            this.partitionMode = IncludeExclude.WHITELIST;
            this.partitionList = DefaultPriorityList.INSTANCE;
        }
        NBTTagCompound tag = stack.getTagCompound();
        this.voidEnergy = tag != null ? tag.getDouble(VoidCellItem.VOID_CELL_ENERGY) : 0;
        if (!Double.isFinite(this.voidEnergy) || this.voidEnergy < 0) {
            this.voidEnergy = 0;
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !matches(what)) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            CondenserLogic.addPower(this, (double) amount / what.getAmountPerUnit());
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0) {
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
                        addStoredAmount(this.storedAmounts, genericStack.what(), genericStack.amount());
                    }
                }
            }
        }
        return this.storedAmounts;
    }

    private void addStoredAmount(AEKey what, long amount) {
        if (what == null || amount <= 0) {
            return;
        }
        AEKey2LongMap cellItems = getCellItems();
        addStoredAmount(cellItems, what, amount);
    }

    private void addStoredAmount(AEKey2LongMap cellItems, AEKey what, long amount) {
        if (what == null || amount <= 0) {
            return;
        }
        if (!isValidStoredOutput(what)) {
            return;
        }
        long currentAmount = cellItems.getLong(what);
        long newAmount = Long.MAX_VALUE - currentAmount < amount ? Long.MAX_VALUE : currentAmount + amount;
        cellItems.put(what, newAmount);
    }

    private boolean isValidStoredOutput(AEKey what) {
        return what.equals(AEItemKey.of(AEItems.MATTER_BALL.item()))
            || what.equals(AEItemKey.of(AEItems.SINGULARITY.item()));
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
        if (key == null) {
            return false;
        }
        if (!(stack.getItem() instanceof VoidCellItem voidCellItem)) {
            return false;
        }

        return this.partitionList.matchesFilter(key, this.partitionMode);
    }

    @Override
    public CondenserOutput getCondenserOutput() {
        return this.mode;
    }

    @Override
    public double getStoredCondenserPower() {
        return this.voidEnergy;
    }

    @Override
    public void setStoredCondenserPower(double storedPower) {
        this.voidEnergy = storedPower;
    }

    @Override
    public double getCondenserStorageLimit() {
        return this.mode.requiredPower > 0 ? Long.MAX_VALUE * (double) this.mode.requiredPower : 0;
    }

    @Override
    public long getAvailableCondenserOutputSpace(AEItemKey output) {
        long currentAmount = getCellItems().getLong(output);
        return Long.MAX_VALUE - currentAmount;
    }

    @Override
    public void addCondenserOutput(AEItemKey output, long amount) {
        addStoredAmount(output, amount);
    }

    @Override
    public void saveCondenserChanges() {
        saveChanges();
    }
}
