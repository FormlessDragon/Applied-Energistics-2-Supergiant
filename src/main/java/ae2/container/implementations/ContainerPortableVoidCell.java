package ae2.container.implementations;

import ae2.api.config.Actionable;
import ae2.api.config.CondenserOutput;
import ae2.api.inventories.BaseInternalInventory;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.StorageCell;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.interfaces.IProgressProvider;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.OutputSlot;
import ae2.core.definitions.AEItems;
import ae2.items.contents.PortableVoidCellGuiHost;
import ae2.items.storage.PortableVoidCellItem;
import ae2.me.helpers.PlayerSource;
import ae2.util.CellWorkbenchFilter;
import ae2.util.ConfigInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerPortableVoidCell extends AEBaseContainer implements IProgressProvider {
    private static final CondenserOutput[] CONDENSER_OUTPUTS = CondenserOutput.values();
    private static final String ACTION_SET_MODE = "setMode";

    private final PortableVoidCellGuiHost host;
    private final InputInventory inputInventory;

    @GuiSync(0)
    public int requiredEnergy;

    @GuiSync(1)
    public int storedPower;

    @GuiSync(2)
    public CondenserOutput output = CondenserOutput.TRASH;

    public ContainerPortableVoidCell(InventoryPlayer ip, PortableVoidCellGuiHost host) {
        super(ip, host);
        this.host = host;
        this.inputInventory = new InputInventory(host);
        var outputInventory = new OutputInventory(host);

        this.addSlot(new InputSlot(this.inputInventory.createGuiWrapper(), 0, 51, 52), SlotSemantics.MACHINE_INPUT);
        this.addSlot(new OutputSlot(outputInventory, 0, 105, 52), SlotSemantics.MACHINE_OUTPUT);
        this.addPlayerInventorySlots(8, 119);

        registerClientAction(ACTION_SET_MODE, Integer.class, this::setMode);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            ItemStack stack = this.host.getItemStack();
            if (stack.getItem() instanceof PortableVoidCellItem portableVoidCellItem) {
                this.output = portableVoidCellItem.getMode(stack);
                this.requiredEnergy = this.output.requiredPower;
                this.storedPower = (int) portableVoidCellItem.getStoredVoidEnergy(stack);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public int getCurrentProgress() {
        return this.storedPower;
    }

    @Override
    public int getMaxProgress() {
        return this.requiredEnergy;
    }

    public CondenserOutput getOutput() {
        return this.output;
    }

    public void setModeFromClient(CondenserOutput mode) {
        sendClientAction(ACTION_SET_MODE, mode.ordinal());
    }

    public void setMode(Integer mode) {
        if (mode == null || mode < 0 || mode >= CONDENSER_OUTPUTS.length) {
            return;
        }
        ItemStack stack = this.host.getItemStack();
        if (!(stack.getItem() instanceof PortableVoidCellItem portableVoidCellItem)) {
            return;
        }
        portableVoidCellItem.setMode(stack, CONDENSER_OUTPUTS[mode]);
        this.output = portableVoidCellItem.getMode(stack);
        this.detectAndSendChanges();
    }

    @Override
    protected int transferStackToContainer(ItemStack input) {
        AEItemKey key = AEItemKey.of(input);
        if (key == null) {
            return 0;
        }

        long inserted = this.inputInventory.insert(0, key, input.getCount(), Actionable.MODULATE);
        return (int) Math.min(inserted, input.getCount());
    }

    private static final class InputInventory extends ConfigInventory {
        private final PortableVoidCellGuiHost host;

        private InputInventory(PortableVoidCellGuiHost host) {
            super(AEKeyTypes.getAll(), null, Mode.STORAGE, 1, null, true);
            this.host = host;
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (slot != 0 || amount <= 0) {
                return 0;
            }
            if (!isAllowedIn(slot, what)) {
                return 0;
            }
            MEStorage storage = StorageCells.getCellInventory(this.host.getItemStack(), null);
            if (storage == null) {
                return 0;
            }
            long inserted = storage.insert(what, amount, mode, new PlayerSource(this.host.getPlayer()));
            if (mode == Actionable.MODULATE && inserted > 0 && storage instanceof StorageCell cell) {
                cell.persist();
            }
            return inserted;
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            return 0;
        }

        @Override
        public GenericStack getStack(int slot) {
            return null;
        }

        @Override
        public AEKey getKey(int slot) {
            return null;
        }

        @Override
        public long getAmount(int slot) {
            return 0;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return insert(0, what, amount, mode);
        }

        @Override
        public void setStack(int slot, GenericStack stack) {
            if (stack != null && isAllowedIn(slot, stack.what())) {
                insert(slot, stack.what(), stack.amount(), Actionable.MODULATE);
            }
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            if (slot != 0 || what == null) {
                return false;
            }

            ItemStack stack = this.host.getItemStack();
            if (!(stack.getItem() instanceof PortableVoidCellItem portableVoidCellItem)) {
                return false;
            }

            return CellWorkbenchFilter.matches(
                stack,
                portableVoidCellItem,
                what,
                CellWorkbenchFilter.isInverted(stack, portableVoidCellItem),
                CellWorkbenchFilter.isFuzzy(stack, portableVoidCellItem));
        }
    }

    private static final class OutputInventory extends BaseInternalInventory {
        private final PortableVoidCellGuiHost host;

        private OutputInventory(PortableVoidCellGuiHost host) {
            this.host = host;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            AEItemKey key = getOutputKey();
            if (key == null) {
                return ItemStack.EMPTY;
            }

            MEStorage storage = StorageCells.getCellInventory(this.host.getItemStack(), null);
            if (storage == null) {
                return ItemStack.EMPTY;
            }
            long amount = storage.extract(key, key.getMaxStackSize(), Actionable.SIMULATE,
                new PlayerSource(this.host.getPlayer()));
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            return key.toStack((int) Math.min(amount, key.getMaxStackSize()));
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            AEItemKey key = getOutputKey();
            if (key == null || amount <= 0) {
                return ItemStack.EMPTY;
            }

            MEStorage storage = StorageCells.getCellInventory(this.host.getItemStack(), null);
            if (storage == null) {
                return ItemStack.EMPTY;
            }
            long extracted = storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                new PlayerSource(this.host.getPlayer()));
            if (!simulate && extracted > 0 && storage instanceof StorageCell cell) {
                cell.persist();
            }
            return extracted <= 0 ? ItemStack.EMPTY : key.toStack((int) Math.min(extracted, key.getMaxStackSize()));
        }

        private AEItemKey getOutputKey() {
            ItemStack stack = this.host.getItemStack();
            if (!(stack.getItem() instanceof PortableVoidCellItem portableVoidCellItem)) {
                return null;
            }
            return switch (portableVoidCellItem.getMode(stack)) {
                case MATTER_BALLS -> AEItemKey.of(AEItems.MATTER_BALL.item());
                case SINGULARITY -> AEItemKey.of(AEItems.SINGULARITY.item());
                case TRASH -> null;
            };
        }
    }

    private final class InputSlot extends FakeSlot {
        private InputSlot(ae2.api.inventories.InternalInventory inventory, int slotIndex, int x, int y) {
            super(inventory, slotIndex, x, y);
        }

        @Override
        public void increase(ItemStack stack) {
            insertFromCarried(stack, false);
        }

        @Override
        public void decrease(ItemStack stack) {
            insertFromCarried(stack, true);
        }

        @Override
        public void setGenericFilter(GenericStack stack) {
            if (stack != null && ContainerPortableVoidCell.this.inputInventory.isAllowedIn(getSlotIndex(), stack.what())) {
                ContainerPortableVoidCell.this.inputInventory.insert(getSlotIndex(), stack.what(), stack.amount(),
                    Actionable.MODULATE);
            }
        }

        private void insertFromCarried(ItemStack requestedStack, boolean singleItem) {
            ItemStack carried = ContainerPortableVoidCell.this.getCarried();
            if (carried.isEmpty() || requestedStack.isEmpty()) {
                return;
            }

            AEItemKey key = AEItemKey.of(requestedStack);
            if (key == null) {
                return;
            }

            int amount = singleItem ? 1 : requestedStack.getCount();
            long inserted = ContainerPortableVoidCell.this.inputInventory.insert(getSlotIndex(), key, amount,
                Actionable.MODULATE);
            if (inserted <= 0) {
                return;
            }

            ItemStack updatedCarried = carried.copy();
            updatedCarried.shrink((int) Math.min(inserted, updatedCarried.getCount()));
            ContainerPortableVoidCell.this.setCarried(updatedCarried.isEmpty() ? ItemStack.EMPTY : updatedCarried);
        }
    }
}
