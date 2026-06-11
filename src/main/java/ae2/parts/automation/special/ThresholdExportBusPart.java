package ae2.parts.automation.special;

import ae2.api.config.Settings;
import ae2.api.networking.IGrid;
import ae2.api.networking.storage.IStorageService;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class ThresholdExportBusPart extends PreciseExportBusPart implements ThresholdModeHost {
    private static final ResourceLocation MODEL_BASE = AppEng.makeId("part/threshold_export_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, ExportBusPartModels.OFF);

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, ExportBusPartModels.ON);

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ExportBusPartModels.HAS_CHANNEL);

    private ThresholdMode mode = ThresholdMode.GREATER;

    public ThresholdExportBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        int modeIndex = extra.getByte("thresholdMode");
        var modes = ThresholdMode.values();
        if (modeIndex >= 0 && modeIndex < modes.length) {
            this.mode = modes[modeIndex];
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        extra.setByte("thresholdMode", (byte) this.mode.ordinal());
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        var storageService = grid.getStorageService();
        var schedulingMode = this.getConfigManager().getSetting(Settings.SCHEDULING_MODE);
        var context = createTransferContext(storageService, grid.getEnergyService());

        int x;
        for (x = 0; x < this.availableSlots() && context.hasOperationsLeft(); x++) {
            int slotToExport = this.getStartingSlot(schedulingMode, x);
            GenericStack stack = getConfig().getStack(slotToExport);
            if (stack == null || stack.amount() <= 0 || !passesThreshold(stack, storageService)) {
                continue;
            }
            AEKey what = stack.what();
            long amount = Math.min((long) context.getOperationsRemaining() * what.getAmountPerOperation(),
                getMaxOutput(stack, storageService));
            amount = getExportStrategy().transfer(context, what, amount);
            if (amount > 0) {
                context.reduceOperationsRemaining(Math.max(1, amount / what.getAmountPerOperation()));
            }
        }

        if (context.hasDoneWork()) {
            this.updateSchedulingMode(schedulingMode, x);
        }
        return context.hasDoneWork();
    }

    private boolean passesThreshold(GenericStack stack, IStorageService storageService) {
        long stored = storageService.getCachedInventory().get(stack.what());
        return (this.mode == ThresholdMode.GREATER) == (stored > stack.amount());
    }

    private long getMaxOutput(GenericStack stack, IStorageService storageService) {
        if (this.mode == ThresholdMode.GREATER) {
            return storageService.getCachedInventory().get(stack.what()) - stack.amount();
        }
        return Long.MAX_VALUE;
    }

    @Override
    public ThresholdMode getThresholdMode() {
        return this.mode;
    }

    @Override
    public void setThresholdMode(ThresholdMode mode) {
        if (mode != null && mode != this.mode) {
            this.mode = mode;
            getHost().markForSave();
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    @Override
    protected GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.THRESHOLD_EXPORT_BUS;
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
