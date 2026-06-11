package ae2.tile.misc;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.AECableType;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.settings.TickRates;
import ae2.recipes.handlers.CrystalFixerRecipe;
import ae2.tile.grid.AENetworkedPoweredTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.EnumSet;

public class TileCrystalFixer extends AENetworkedPoweredTile implements IGridTickable, InternalInventoryHost {
    public static final int POWER_MAXIMUM_AMOUNT = 8000;
    public static final int MAX_PROGRESS = 100;
    private static final int ENERGY_MULTIPLIER = 50;

    private final AppEngInternalInventory fuel = new AppEngInternalInventory(this, 1);
    private int progress;
    private CrystalFixerRecipe cachedRecipe;
    private ItemStack clientFuel = ItemStack.EMPTY;

    public TileCrystalFixer() {
        this.setInternalMaxPower(POWER_MAXIMUM_AMOUNT);
        this.setPowerSides(getGridConnectableSides(getOrientation()));
        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CRYSTAL_FIXER.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.setPowerSides(getGridConnectableSides(orientation));
        this.cachedRecipe = null;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.fuel;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.cachedRecipe = null;
        wake();
        markForUpdate();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return this.world != null && this.world.isRemote;
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.fuel.writeToNBT(data, "fuel");
        data.setInteger("progress", this.progress);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.fuel.readFromNBT(data, "fuel");
        this.progress = data.getInteger("progress");
        this.cachedRecipe = null;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        new PacketBuffer(data).writeItemStack(this.fuel.getStackInSlot(0));
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        ItemStack oldFuel = this.clientFuel;
        try {
            this.clientFuel = new PacketBuffer(data).readItemStack();
        } catch (IOException e) {
            AELog.warn(e, "Failed to read crystal fixer fuel from update stream");
            this.clientFuel = ItemStack.EMPTY;
        }
        return changed || !ItemStack.areItemStacksEqual(oldFuel, this.clientFuel);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, !hasTask());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        fillInternalPower();
        CrystalFixerRecipe task = getTask();
        if (task == null) {
            this.progress = 0;
            return TickRateModulation.SLEEP;
        }

        int speed = genProgress();
        int powerConsumption = ENERGY_MULTIPLIER * speed;
        IEnergySource source = selectEnergySource(powerConsumption);
        if (source == null || source.extractAEPower(powerConsumption, Actionable.SIMULATE,
            PowerMultiplier.CONFIG) <= powerConsumption - 0.01) {
            return TickRateModulation.SLOWER;
        }

        source.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
        this.progress = Math.min(MAX_PROGRESS, this.progress + speed);

        if (this.progress == MAX_PROGRESS) {
            finishRecipe(task);
        }
        return TickRateModulation.URGENT;
    }

    private int genProgress() {
        return this.world == null ? 0 : 2 + this.world.rand.nextInt(2);
    }

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

    private void fillInternalPower() {
        if (this.getInternalCurrentPower() >= POWER_MAXIMUM_AMOUNT) {
            return;
        }
        getMainNode().ifPresent(grid -> {
            double toExtract = Math.min(800.0, this.getInternalMaxPower() - this.getInternalCurrentPower());
            double extracted = grid.getEnergyService().extractAEPower(toExtract, Actionable.MODULATE,
                PowerMultiplier.ONE);
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        });
    }

    private void finishRecipe(CrystalFixerRecipe recipe) {
        this.progress = 0;
        ItemStack storedFuel = this.fuel.getStackInSlot(0);
        if (!recipe.matches(getFacingBlock(), storedFuel)) {
            this.cachedRecipe = null;
            return;
        }

        storedFuel.shrink(recipe.fuelAmount());
        if (storedFuel.getCount() <= 0) {
            this.fuel.setItemDirect(0, ItemStack.EMPTY);
        } else {
            this.fuel.setItemDirect(0, storedFuel);
        }

        if (this.world != null && this.world.rand.nextInt(CrystalFixerRecipe.FULL_CHANCE) < recipe.chance()) {
            this.world.setBlockState(getFacingPos(), recipe.output().getDefaultState(), 3);
        }
        this.cachedRecipe = null;
        markForUpdate();
    }

    private BlockPos getFacingPos() {
        return this.pos.offset(this.getOrientation().getSide(RelativeSide.FRONT));
    }

    private Block getFacingBlock() {
        if (this.world == null) {
            return Blocks.AIR;
        }
        return this.world.getBlockState(getFacingPos()).getBlock();
    }

    private boolean hasTask() {
        return getTask() != null;
    }

    private CrystalFixerRecipe getTask() {
        if (this.cachedRecipe != null && this.cachedRecipe.matches(getFacingBlock(), this.fuel.getStackInSlot(0))) {
            return this.cachedRecipe;
        }
        this.cachedRecipe = CrystalFixerRecipe.findRecipe(getFacingBlock(), this.fuel.getStackInSlot(0));
        return this.cachedRecipe;
    }

    private void wake() {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    public void onFacingBlockChanged() {
        this.cachedRecipe = null;
        wake();
    }

    public void refuel(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            ItemStack extracted = this.fuel.extractItem(0, Integer.MAX_VALUE, false);
            if (!extracted.isEmpty() && !player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }
            return;
        }

        ItemStack notAdded = this.fuel.insertItem(0, held, false);
        player.setHeldItem(hand, notAdded);
    }

    public int getProgress() {
        return this.progress;
    }

    public ItemStack getClientDisplayFuel() {
        return this.isClientSide() ? this.clientFuel : this.fuel.getStackInSlot(0);
    }

}
