package ae2.tile.misc;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ItemTransfer;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.core.settings.TickRates;
import ae2.helpers.IOutputSideConfigHost;
import ae2.helpers.externalstorage.GenericStackFluidStorage;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.recipes.handlers.CrystalAssemblerRecipe;
import ae2.tile.grid.AENetworkedPoweredTile;
import ae2.util.ConfigManager;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.CombinedInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class TileCrystalAssembler extends AENetworkedPoweredTile
    implements IGridTickable, IUpgradeableObject, IConfigurableObject, ISegmentedInventory, IOutputSideConfigHost {
    public static final int INPUT_SLOTS = 9;
    public static final int TANK_CAPACITY = 16000;
    public static final int MAX_PROCESSING_TIME = 200;
    public static final ResourceLocation INV_MAIN = AppEng.makeId("crystal_assembler");

    private final AppEngInternalInventory input = new AppEngInternalInventory(this, INPUT_SLOTS, 64,
        new AutomationFilter());
    private final AppEngInternalInventory output = new AppEngInternalInventory(this, 1, 64);
    private final InternalInventory internalInventory = new CombinedInternalInventory(this.input, this.output);
    private final InternalInventory exposedInventory = new CombinedInternalInventory(
        new FilteredInternalInventory(this.input, new AutomationFilter()),
        new FilteredInternalInventory(this.output, new OutputFilter()));
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AEBlocks.CRYSTAL_ASSEMBLER.item(), 5,
        this::saveChanges);
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private final EnumMap<EnumFacing, ItemTransfer> neighbors = new EnumMap<>(EnumFacing.class);
    private final EnumSet<EnumFacing> outputSides = EnumSet.allOf(EnumFacing.class);
    private int processingTime;
    private boolean working;
    private boolean powered;
    @Nullable
    private CrystalAssemblerRecipe cachedTask;
    private final GenericStackInv tank = new GenericStackInv(Set.of(AEKeyType.fluids()), this::onTankChanged,
        GenericStackInv.Mode.STORAGE, 1) {
        @Override
        public boolean canExtract() {
            return false;
        }
    };
    private final IFluidHandler fluidHandler = new GenericStackFluidStorage(this.tank);

    public TileCrystalAssembler() {
        this.setInternalMaxPower(8000);
        this.setPowerSides(getGridConnectableSides(getOrientation()));
        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.tank.setCapacity(AEKeyType.fluids(), TANK_CAPACITY);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CRYSTAL_ASSEMBLER.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.TOP)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.setPowerSides(getGridConnectableSides(orientation));
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.internalInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.exposedInventory;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        if (INV_MAIN.equals(id)) {
            return this.internalInventory;
        }
        return null;
    }

    @Override
    public void onReady() {
        super.onReady();
        updateNeighbors();
    }

    public void updateNeighbors() {
        if (this.world == null) {
            return;
        }
        this.neighbors.clear();
        for (EnumFacing side : EnumFacing.values()) {
            ItemTransfer target = InternalInventory.wrapExternal(this.world, this.pos.offset(side), side.getOpposite());
            if (target != null) {
                this.neighbors.put(side, target);
            }
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.input.writeToNBT(data, "input");
        this.output.writeToNBT(data, "output");
        this.tank.writeToChildTag(data, "tank");
        this.upgrades.writeToNBT(data, "upgrades");
        this.configManager.writeToNBT(data);
        data.setInteger("processingTime", this.processingTime);
        data.setInteger("outputSides", encodeOutputSides());
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.input.readFromNBT(data, "input");
        this.output.readFromNBT(data, "output");
        this.tank.readFromChildTag(data, "tank");
        this.upgrades.readFromNBT(data, "upgrades");
        this.configManager.readFromNBT(data);
        this.processingTime = data.getInteger("processingTime");
        decodeOutputSides(data.hasKey("outputSides") ? data.getInteger("outputSides") : 0x3F);
        this.cachedTask = null;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.working);
        data.writeBoolean(this.isPowered());
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean oldWorking = this.working;
        this.working = data.readBoolean();
        boolean oldPowered = this.powered;
        this.powered = data.readBoolean();
        return changed || oldWorking != this.working || oldPowered != this.powered;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.markForUpdate();
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (ItemStack upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
        GenericStack fluid = this.tank.getStack(0);
        if (fluid != null && this.world != null) {
            fluid.what().addDrops(fluid.amount(), drops, this.world, this.pos);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
        this.tank.clear();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.cachedTask = null;
        this.processingTime = 0;
        wake();
    }

    private void onTankChanged() {
        this.cachedTask = null;
        this.processingTime = 0;
        saveChanges();
        wake();
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        saveChanges();
        wake();
    }

    private void wake() {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, !hasCraftWork() && !hasAutoExportWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        CrystalAssemblerRecipe task = getTask();
        if (task == null || !canOutput(task.getResultItem())) {
            setWorking(false);
            this.processingTime = 0;
            return hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }

        int speedFactor = switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item())) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 50;
            default -> 2;
        };
        int powerConsumption = 10 * speedFactor;
        IEnergySource source = selectEnergySource(powerConsumption);
        if (source != null && source.extractAEPower(powerConsumption, Actionable.SIMULATE,
            PowerMultiplier.CONFIG) > powerConsumption - 0.01) {
            source.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
            this.processingTime = Math.min(MAX_PROCESSING_TIME, this.processingTime + speedFactor);
            setWorking(true);
            saveChanges();
        }

        if (this.processingTime >= MAX_PROCESSING_TIME) {
            int runs = getParallelRuns(task);
            ItemStack output = task.getResultItem();
            output.setCount(output.getCount() * runs);
            task.consume(this.input, this.tank, runs);
            this.output.insertItem(0, output, false);
            this.processingTime = 0;
            this.cachedTask = null;
            saveChanges();
        }

        return TickRateModulation.URGENT;
    }

    @Nullable
    private IEnergySource selectEnergySource(double powerConsumption) {
        double internal = extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (internal > powerConsumption - 0.01) {
            return this;
        }
        var grid = this.getMainNode().getGrid();
        if (grid != null) {
            return grid.getEnergyService();
        }
        return internal > 0 ? this : null;
    }

    private boolean hasCraftWork() {
        return getTask() != null;
    }

    private boolean hasAutoExportWork() {
        return !this.output.getStackInSlot(0).isEmpty()
            && this.configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    @Nullable
    public CrystalAssemblerRecipe getTask() {
        if (this.cachedTask == null) {
            this.cachedTask = CrystalAssemblerRecipe.findRecipe(this.input, this.tank, this::canOutput);
        }
        return this.cachedTask;
    }

    private boolean canOutput(ItemStack stack) {
        return this.output.insertItem(0, stack.copy(), true).isEmpty();
    }

    private int getParallelLimit() {
        return switch (this.upgrades.getInstalledUpgrades(AEItems.PARALLEL_CARD.item())) {
            case 1 -> 4;
            case 2 -> 16;
            case 3 -> 64;
            default -> 1;
        };
    }

    private int getParallelRuns(CrystalAssemblerRecipe task) {
        int runs = task.getMaxRuns(this.input, this.tank, this.getParallelLimit());
        ItemStack output = task.getResultItem();
        int outputCount = output.getCount();
        for (int i = runs; i > 0; i--) {
            output.setCount(outputCount * i);
            if (canOutput(output)) {
                return i;
            }
        }
        return 0;
    }

    private boolean pushOutResult() {
        if (!hasAutoExportWork() || this.world == null) {
            return false;
        }
        ItemStack stack = this.output.getStackInSlot(0);
        for (EnumFacing side : this.outputSides) {
            ItemTransfer target = this.neighbors.get(side);
            if (target == null) {
                continue;
            }
            ItemStack before = stack.copy();
            stack = target.addItems(stack);
            this.output.setItemDirect(0, stack);
            if (!ItemStack.areItemStacksEqual(before, stack)) {
                saveChanges();
                return true;
            }
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public AppEngInternalInventory getInput() {
        return this.input;
    }

    public AppEngInternalInventory getOutput() {
        return this.output;
    }

    public GenericStackInv getTank() {
        return this.tank;
    }

    public boolean isWorking() {
        return this.working;
    }

    public boolean onPlayerUse(EntityPlayer player, EnumHand hand) {
        return FluidUtil.interactWithFluidHandler(player, hand, this.fluidHandler);
    }

    private void setWorking(boolean working) {
        if (this.working != working) {
            this.working = working;
            markForUpdate();
        }
    }

    public boolean isPowered() {
        if (this.world != null && !this.world.isRemote) {
            return this.getMainNode().isOnline();
        }
        return this.powered;
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    public int getMaxProcessingTime() {
        return MAX_PROCESSING_TIME;
    }

    public EnumSet<EnumFacing> getOutputSides() {
        return this.outputSides;
    }

    @Override
    public void setOutputSideEnabled(EnumFacing side, boolean enabled) {
        boolean changed = enabled ? this.outputSides.add(side) : this.outputSides.remove(side);
        if (changed) {
            saveChanges();
            wake();
        }
    }

    @Override
    public BlockOrientation getBlockOrientation() {
        return getOrientation();
    }

    @Override
    public EnumSet<EnumFacing> getAllowedOutputSides() {
        return EnumSet.allOf(EnumFacing.class);
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openGui(player, GuiIds.GuiKey.CRYSTAL_ASSEMBLER, this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return AEBlocks.CRYSTAL_ASSEMBLER.stack();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.fluidHandler;
        }
        return super.getCapability(capability, facing);
    }

    private int encodeOutputSides() {
        int mask = 0;
        for (EnumFacing side : this.outputSides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private void decodeOutputSides(int mask) {
        this.outputSides.clear();
        for (EnumFacing side : EnumFacing.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                this.outputSides.add(side);
            }
        }
    }

    private static final class AutomationFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return IAEItemFilter.super.allowInsert(inv, slot, stack);
        }
    }

    private static final class OutputFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return false;
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return IAEItemFilter.super.allowExtract(inv, slot, amount);
        }
    }
}
