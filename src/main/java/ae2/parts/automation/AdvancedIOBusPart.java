package ae2.parts.automation;

import ae2.api.behaviors.StackImportStrategy;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.networking.IGrid;
import ae2.api.parts.IPartItem;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.IConfigManagerBuilder;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.GuiIds;
import ae2.core.definitions.AEItems;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

public class AdvancedIOBusPart extends StockExportBusPart implements KeyTypeSelectionHost {
    private final KeyTypeSelection keyTypeSelection;
    @Nullable
    private StackImportStrategy importStrategy;

    public AdvancedIOBusPart(IPartItem<?> partItem) {
        super(partItem);
        this.keyTypeSelection = new KeyTypeSelection(() -> {
            getHost().markForSave();
            importStrategy = null;
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }, StackWorldBehaviors.hasImportStrategyTypeFilter());
    }

    @Override
    protected int getUpgradeSlots() {
        return 8;
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.REGULATE_STOCK, YesNo.YES);
    }

    protected StackImportStrategy getImportStrategy() {
        if (this.importStrategy == null) {
            var self = this.getHost().getTileEntity();
            var fromPos = self.getPos().offset(this.getSide());
            var fromSide = getSide().getOpposite();
            importStrategy = StackWorldBehaviors.createImportFacade((WorldServer) getLevel(), fromPos, fromSide,
                keyTypeSelection.enabledPredicate());
        }
        return this.importStrategy;
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        boolean exportWork = super.doBusWork(grid);
        var strategy = getImportStrategy();
        boolean importWork = false;
        int operationsLeft = getOperationsPerTick();
        boolean regulate = getConfigManager().getSetting(Settings.REGULATE_STOCK) == YesNo.YES;

        if (regulate) {
            var schedulingMode = this.getConfigManager().getSetting(Settings.SCHEDULING_MODE);
            for (int x = 0; x < this.availableSlots() && operationsLeft > 0; x++) {
                int slotToExport = this.getStartingSlot(schedulingMode, x);
                GenericStack stack = this.getConfig().getStack(slotToExport);
                if (stack == null || stack.what() == null) {
                    continue;
                }
                AEKey what = stack.what();
                long targetAmount = stack.amount();
                long stock = getCurrentStock(what);
                if (stock > targetAmount) {
                    long transferFactor = what.getAmountPerOperation();
                    int maxAmount = (int) Math.min((long) operationsLeft * transferFactor, stock - targetAmount);
                    int operations = Math.max(1, maxAmount / (int) transferFactor);
                    var context = new FilteredImportStackTransferContext(grid.getStorageService(),
                        grid.getEnergyService(), this.source, operations, makeFilter(what));
                    strategy.transfer(context);
                    operationsLeft -= operations - context.getOperationsRemaining();
                    importWork |= context.hasDoneWork();
                }
            }
        }

        if (operationsLeft > 0) {
            var context = new FilteredImportStackTransferContext(grid.getStorageService(), grid.getEnergyService(),
                this.source, operationsLeft, getFilter());
            context.setInverted(this.isUpgradedWith(AEItems.INVERTER_CARD));
            strategy.transfer(context);
            importWork |= context.hasDoneWork();
        }

        return exportWork || importWork;
    }

    private IPartitionList makeFilter(AEKey what) {
        var builder = IPartitionList.builder();
        builder.add(what);
        if (isUpgradedWith(AEItems.FUZZY_CARD)) {
            builder.fuzzyMode(this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }
        return builder.build();
    }

    @Override
    protected int getOperationsPerTick() {
        return super.getOperationsPerTick() * 8;
    }

    @Override
    protected GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.ADVANCED_IO_BUS;
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        keyTypeSelection.readFromNBT(extra);
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        keyTypeSelection.writeToNBT(extra);
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return keyTypeSelection;
    }
}
