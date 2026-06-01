package ae2.parts.automation;

import ae2.api.behaviors.StackTransferContext;
import ae2.api.config.Actionable;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.storage.IStorageService;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.container.GuiIds;
import ae2.core.definitions.AEItems;
import ae2.util.ConfigInventory;
import ae2.util.prioritylist.DefaultPriorityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.NotNull;

public class StockExportBusPart extends ExportBusPart {
    private ConfigInventory stackConfig;
    private StorageReader storageReader;

    public StockExportBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected int getUpgradeSlots() {
        return 6;
    }

    @Override
    public ConfigInventory getConfig() {
        if (this.stackConfig == null) {
            this.stackConfig = ConfigInventory.configStacks(63)
                                              .supportedTypes(StackWorldBehaviors.withExportStrategy())
                                              .changeListener(this::onStackConfigChanged)
                                              .allowOverstacking(true)
                                              .build();
        }
        return this.stackConfig;
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        getConfig().readFromChildTag(extra, "extraConfig");
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        getConfig().writeToChildTag(extra, "extraConfig");
    }

    private void onStackConfigChanged() {
        getHost().markForSave();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    protected StorageReader getStorageReader() {
        if (storageReader == null) {
            var self = this.getHost().getTileEntity();
            var fromPos = self.getPos().offset(this.getSide());
            var fromSide = getSide().getOpposite();
            storageReader = new ExternalStorageReader((WorldServer) getLevel(), fromPos, fromSide);
        }
        return storageReader;
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        IStorageService storageService = grid.getStorageService();
        ICraftingService craftingService = grid.getCraftingService();
        var schedulingMode = this.getConfigManager().getSetting(Settings.SCHEDULING_MODE);
        StackTransferContext context = createTransferContext(storageService, grid.getEnergyService());

        int x;
        for (x = 0; x < this.availableSlots() && context.hasOperationsLeft(); x++) {
            int slotToExport = this.getStartingSlot(schedulingMode, x);
            GenericStack stack = this.getConfig().getStack(slotToExport);
            if (stack == null || stack.what() == null) {
                continue;
            }

            AEKey what = stack.what();
            long targetAmount = stack.amount();
            if (this.craftOnly()) {
                attemptCrafting(context, craftingService, slotToExport, what, targetAmount);
                continue;
            }

            int before = context.getOperationsRemaining();
            long transferFactor = what.getAmountPerOperation();
            long maxAmount = (long) context.getOperationsRemaining() * transferFactor;
            long currentAmount = getCurrentStock(what);
            maxAmount = Math.min(maxAmount, targetAmount - currentAmount);
            if (maxAmount <= 0) {
                continue;
            }

            long transferred = getExportStrategy().transfer(context, what, maxAmount);
            if (transferred > 0) {
                context.reduceOperationsRemaining(Math.max(1, transferred / transferFactor));
            }

            if (before == context.getOperationsRemaining() && this.isCraftingEnabled()) {
                attemptCrafting(context, craftingService, slotToExport, what, targetAmount);
            }
        }

        if (context.hasDoneWork()) {
            this.updateSchedulingMode(schedulingMode, x);
        }
        return context.hasDoneWork();
    }

    protected void attemptCrafting(StackTransferContext context, ICraftingService craftingService, int slotToExport,
                                   AEKey what, long targetAmount) {
        long transferFactor = what.getAmountPerOperation();
        long maxAmount = (long) context.getOperationsRemaining() * transferFactor;
        long currentAmount = getCurrentStock(what);
        maxAmount = Math.min(maxAmount, targetAmount - currentAmount);
        if (maxAmount <= 0) {
            return;
        }
        long amount = getExportStrategy().push(what, maxAmount, Actionable.SIMULATE);
        if (amount > 0) {
            requestCrafting(craftingService, slotToExport, what, amount);
            context.reduceOperationsRemaining(Math.max(1, amount / transferFactor));
        }
    }

    @Override
    @NotNull
    protected StackTransferContext createTransferContext(IStorageService storageService, IEnergyService energyService) {
        return new StackTransferContextImpl(storageService, energyService, this.source, getOperationsPerTick(),
            DefaultPriorityList.INSTANCE);
    }

    protected long getCurrentStock(AEKey what) {
        return getStorageReader().getCurrentStock(what);
    }

    @Override
    protected boolean craftOnly() {
        return isCraftingEnabled() && this.getConfigManager().getSetting(Settings.CRAFT_ONLY) == YesNo.YES;
    }

    @Override
    protected boolean isCraftingEnabled() {
        return isUpgradedWith(AEItems.CRAFTING_CARD);
    }

    @Override
    protected GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.STOCK_EXPORT_BUS;
    }

    @Override
    public IPartModel getStaticModels() {
        return super.getStaticModels();
    }
}
