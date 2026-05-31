package ae2.parts.automation.special;

import ae2.api.behaviors.StackTransferContext;
import ae2.api.config.Actionable;
import ae2.api.config.RedstoneMode;
import ae2.api.config.Settings;
import ae2.api.networking.IGrid;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKey;
import ae2.api.storage.StorageHelper;
import ae2.core.AppEng;
import ae2.core.definitions.AEItems;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.parts.automation.ExportBusPart;
import ae2.util.ConfigInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class PreciseExportBusPart extends ExportBusPart {
    private static final ResourceLocation MODEL_BASE = AppEng.makeId("part/precise_export_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, ExportBusPartModels.OFF);

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, ExportBusPartModels.ON);

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ExportBusPartModels.HAS_CHANNEL);

    private ConfigInventory stackConfig;

    public PreciseExportBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public ConfigInventory getConfig() {
        if (this.stackConfig == null) {
            this.stackConfig = ConfigInventory.configStacks(63)
                                              .supportedTypes(ae2.parts.automation.StackWorldBehaviors.withExportStrategy())
                                              .changeListener(this::onStackConfigChanged)
                                              .allowOverstacking(true)
                                              .build();
        }
        return this.stackConfig;
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        getConfig().readFromChildTag(extra, "config2");
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        getConfig().writeToChildTag(extra, "config2");
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        var storageService = grid.getStorageService();
        var craftingService = grid.getCraftingService();
        var schedulingMode = this.getConfigManager().getSetting(Settings.SCHEDULING_MODE);
        var context = createTransferContext(storageService, grid.getEnergyService());

        int x;
        for (x = 0; x < this.availableSlots() && context.hasOperationsLeft(); x++) {
            int slotToExport = this.getStartingSlot(schedulingMode, x);
            var stack = getConfig().getStack(slotToExport);
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            AEKey what = stack.what();
            long amount = stack.amount();
            long operations = Math.max(1, amount / what.getAmountPerOperation());
            if (context.getOperationsRemaining() < operations) {
                break;
            }

            if (craftOnly()) {
                attemptCrafting(context, craftingService, slotToExport, what, amount);
                continue;
            }

            long before = context.getOperationsRemaining();
            long extracted = isPulseMode() ? simulateExtract(context, what, amount)
                : simulateExtract(context, what, before * what.getAmountPerOperation()) / amount * amount;
            long canHold = getExportStrategy().push(what, extracted, Actionable.SIMULATE) / amount * amount;
            if (canHold > 0) {
                long moved = getExportStrategy().transfer(context, what, canHold);
                if (moved > 0) {
                    context.reduceOperationsRemaining(Math.max(1, moved / what.getAmountPerOperation()));
                }
            }
            if (before == context.getOperationsRemaining() && isCraftingEnabled()) {
                attemptCrafting(context, craftingService, slotToExport, what, amount);
            }
        }

        if (context.hasDoneWork()) {
            this.updateSchedulingMode(schedulingMode, x);
        }
        return context.hasDoneWork();
    }

    protected void attemptCrafting(StackTransferContext context, ae2.api.networking.crafting.ICraftingService cg,
                                   int slotToExport, AEKey what, long targetAmount) {
        long amount = getExportStrategy().push(what, targetAmount, Actionable.SIMULATE);
        if (amount == targetAmount) {
            requestCrafting(cg, slotToExport, what, amount);
            context.reduceOperationsRemaining(Math.max(1, amount / what.getAmountPerOperation()));
        }
    }

    private long simulateExtract(StackTransferContext context, AEKey what, long amount) {
        return StorageHelper.poweredExtraction(context.getEnergySource(), context.getInternalStorage().getInventory(),
            what, amount, context.getActionSource(), Actionable.SIMULATE);
    }

    private boolean isPulseMode() {
        return this.isUpgradedWith(AEItems.REDSTONE_CARD) && this.getRSMode() == RedstoneMode.SIGNAL_PULSE;
    }

    private void onStackConfigChanged() {
        getHost().markForSave();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    @Override
    protected ae2.container.GuiIds.GuiKey getGuiKey() {
        return ae2.container.GuiIds.GuiKey.PRECISE_EXPORT_BUS;
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        }
        return MODELS_OFF;
    }
}
