package ae2.tile.misc;

import ae2.api.AECapabilities;
import ae2.api.behaviors.ContainerItemContext;
import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ItemTransfer;
import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.settings.TickRates;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.text.TextComponentItemStack;
import ae2.tile.grid.AENetworkedPoweredTile;
import ae2.util.Platform;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

public class TileCaner extends AENetworkedPoweredTile
    implements IGridTickable, ICraftingMachine, InternalInventoryHost {
    private static final int POWER_MAXIMUM_AMOUNT = 3200;
    private static final int POWER_USAGE = 80;

    private final AppEngInternalInventory container = new AppEngInternalInventory(this, 1, 1);
    private final GenericStackInv stuff = new GenericStackInv(this::wake, 1);

    private ItemStack target = ItemStack.EMPTY;
    @Nullable
    private EnumFacing ejectSide;
    private CanerMode mode = CanerMode.FILL;
    @Nullable
    private AEKey emptyKey;
    private ItemStack clientContainer = ItemStack.EMPTY;

    public TileCaner() {
        this.stuff.useRegisteredCapacities();
        this.stuff.setCapacity(AEKeyType.items(), 0);
        this.setInternalMaxPower(POWER_MAXIMUM_AMOUNT);
        this.setPowerSides(getGridConnectableSides(getOrientation()));
        this.getMainNode()
            .setIdlePowerUsage(0)
            .addService(IGridTickable.class, this);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CANER.stack();
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.TOP), orientation.getSide(RelativeSide.BOTTOM));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.setPowerSides(getGridConnectableSides(orientation));
    }

    public AppEngInternalInventory getContainerInventory() {
        return this.container;
    }

    public GenericStackInv getGenericInv() {
        return this.stuff;
    }

    public CanerMode getMode() {
        return this.mode;
    }

    public void setMode(CanerMode mode) {
        this.mode = mode;
        saveChanges();
        wake();
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.container;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.container;
    }

    @Nullable
    public IItemHandler getExposedItemHandler(@Nullable EnumFacing side) {
        return this.container.toItemHandler();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.container) {
            this.target = ItemStack.EMPTY;
            this.emptyKey = null;
            wake();
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return this.world != null && this.world.isRemote;
    }

    private void wake() {
        saveChanges();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, !hasJob());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        fillInternalPower();
        if (this.mode == CanerMode.FILL) {
            fill();
        } else if (this.mode == CanerMode.EMPTY) {
            empty();
        }
        if (isDone()) {
            eject();
        }
        return hasJob() ? TickRateModulation.FASTER : TickRateModulation.SLEEP;
    }

    private void fillInternalPower() {
        if (this.getInternalCurrentPower() >= POWER_MAXIMUM_AMOUNT) {
            return;
        }
        getMainNode().ifPresent(grid -> {
            double toExtract = Math.min(POWER_USAGE, this.getInternalMaxPower() - this.getInternalCurrentPower());
            double extracted = grid.getEnergyService().extractAEPower(toExtract, Actionable.MODULATE,
                PowerMultiplier.ONE);
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        });
    }

    private boolean hasEnoughPower() {
        return this.getInternalCurrentPower() >= POWER_USAGE;
    }

    private void consumePower() {
        this.extractAEPower(POWER_USAGE, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }

    @Nullable
    private ContainerItemContext getStrategy(AEKey type, EntityPlayer player, ItemStack stack) {
        if (ContainerItemStrategies.isKeySupported(type)) {
            return ContainerItemStrategies.findOwnedItemContext(type.getType(), player, stack);
        }
        return null;
    }

    private boolean isDone() {
        return !this.target.isEmpty()
            && ItemStack.areItemStacksEqual(this.target, this.container.getStackInSlot(0));
    }

    private void eject() {
        if (this.world == null || this.world.isRemote || this.ejectSide == null) {
            return;
        }
        ItemTransfer targetInv = InternalInventory.wrapExternal(this.world, this.pos.offset(this.ejectSide),
            this.ejectSide.getOpposite());
        if (targetInv == null) {
            return;
        }

        ItemStack before = this.container.getStackInSlot(0).copy();
        ItemStack overflow = targetInv.addItems(before, false);
        this.container.setItemDirect(0, overflow);

        if (overflow.isEmpty()) {
            this.target = ItemStack.EMPTY;
            this.emptyKey = null;
            saveChanges();
        }
    }

    private void fill() {
        ItemStack stack = this.container.getStackInSlot(0);
        GenericStack obj = this.stuff.getStack(0);
        if (stack.isEmpty() || obj == null || !(this.world instanceof WorldServer) || !hasEnoughPower()) {
            return;
        }

        EntityPlayer player = Platform.getFakeEntityPlayer(this.world, null);
        player.inventory.setInventorySlotContents(0, stack);
        player.inventory.setInventorySlotContents(1, ItemStack.EMPTY);
        ContainerItemContext handler = getStrategy(obj.what(), player, stack);
        if (handler == null) {
            return;
        }

        long added = handler.insert(obj.what(), obj.amount(), Actionable.SIMULATE);
        if (added <= 0) {
            return;
        }

        this.stuff.extract(0, obj.what(), added, Actionable.MODULATE);
        handler.insert(obj.what(), added, Actionable.MODULATE);
        this.container.setItemDirect(0, getContainerResult(player));
        consumePower();
        saveChanges();
    }

    private void empty() {
        ItemStack stack = this.container.getStackInSlot(0);
        if (stack.isEmpty() || !(this.world instanceof WorldServer) || !hasEnoughPower()) {
            return;
        }

        GenericStack contents = this.emptyKey != null
            ? ContainerItemStrategies.getContainedStack(stack, this.emptyKey.getType())
            : ContainerItemStrategies.getContainedStack(stack);
        GenericStack stored = this.stuff.getStack(0);
        if (contents == null || stored != null && !stored.what().equals(contents.what())) {
            return;
        }

        EntityPlayer player = Platform.getFakeEntityPlayer(this.world, null);
        player.inventory.setInventorySlotContents(0, stack);
        player.inventory.setInventorySlotContents(1, ItemStack.EMPTY);
        ContainerItemContext handler = getStrategy(contents.what(), player, stack);
        if (handler == null) {
            return;
        }

        long toAdd = handler.extract(contents.what(), contents.amount(), Actionable.SIMULATE);
        if (toAdd <= 0) {
            return;
        }
        long canAdd = this.stuff.insert(0, contents.what(), toAdd, Actionable.SIMULATE);
        if (canAdd != toAdd) {
            return;
        }

        handler.extract(contents.what(), canAdd, Actionable.MODULATE);
        this.stuff.insert(0, contents.what(), canAdd, Actionable.MODULATE);
        this.container.setItemDirect(0, getContainerResult(player));
        consumePower();
        saveChanges();
    }

    private ItemStack getContainerResult(EntityPlayer player) {
        ItemStack first = player.inventory.getStackInSlot(0);
        if (!first.isEmpty()) {
            return first.copy();
        }
        return player.inventory.getStackInSlot(1).copy();
    }

    private boolean hasJob() {
        if (this.mode == CanerMode.FILL) {
            return this.stuff.getStack(0) != null && !this.container.getStackInSlot(0).isEmpty();
        }
        return this.mode == CanerMode.EMPTY && !this.container.getStackInSlot(0).isEmpty();
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.container.writeToNBT(data, "container");
        this.stuff.writeToChildTag(data, "stuff");
        if (!this.target.isEmpty()) {
            NBTTagCompound targetTag = new NBTTagCompound();
            this.target.writeToNBT(targetTag);
            data.setTag("target", targetTag);
        }
        if (this.ejectSide != null) {
            data.setString("ejectSide", this.ejectSide.name());
        }
        data.setByte("mode", (byte) this.mode.ordinal());
        if (this.emptyKey != null) {
            data.setTag("emptyKey", this.emptyKey.toTagGeneric());
        }
    }

    private static ItemStack readStack(NBTTagCompound tag) {
        try {
            return new ItemStack(tag);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.container.readFromNBT(data, "container");
        this.stuff.readFromChildTag(data, "stuff");
        this.target = data.hasKey("target", 10) ? readStack(data.getCompoundTag("target")) : ItemStack.EMPTY;
        this.ejectSide = data.hasKey("ejectSide") ? EnumFacing.byName(data.getString("ejectSide")) : null;
        if (data.hasKey("mode")) {
            int ordinal = data.getByte("mode");
            CanerMode[] modes = CanerMode.values();
            if (ordinal >= 0 && ordinal < modes.length) {
                this.mode = modes[ordinal];
            }
        }
        this.emptyKey = data.hasKey("emptyKey", 10) ? AEKey.fromTagGeneric(data.getCompoundTag("emptyKey")) : null;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(!this.container.getStackInSlot(0).isEmpty());
        if (!this.container.getStackInSlot(0).isEmpty()) {
            PacketBuffer packet = new PacketBuffer(data);
            packet.writeItemStack(this.container.getStackInSlot(0));
        }
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        ItemStack old = this.clientContainer;
        if (data.readBoolean()) {
            try {
                this.clientContainer = new PacketBuffer(data).readItemStack();
            } catch (IOException e) {
                AELog.warn(e, "Failed to read caner item from update stream");
                this.clientContainer = ItemStack.EMPTY;
            }
        } else {
            this.clientContainer = ItemStack.EMPTY;
        }
        return changed || !ItemStack.areItemStacksEqual(old, this.clientContainer);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        GenericStack stack = this.stuff.getStack(0);
        if (stack != null && this.world != null) {
            stack.what().addDrops(stack.amount(), drops, this.world, this.pos);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.stuff.clear();
        this.container.clear();
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(AEItemKey.of(AEBlocks.CANER.stack()),
            TextComponentItemStack.of(AEBlocks.CANER.stack()), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, int multiplier,
                               EnumFacing ejectionDirection) {
        if (!(patternDetails instanceof AEProcessingPattern) || this.stuff.getStack(0) != null
            || !this.container.getStackInSlot(0).isEmpty() || multiplier != 1) {
            return false;
        }
        return this.mode == CanerMode.FILL
            ? pushFillPattern(patternDetails, inputs, ejectionDirection)
            : pushEmptyPattern(patternDetails, inputs, ejectionDirection);
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, KeyCounter[] inputs, int maxMultiplier,
                                           EnumFacing ejectionDirection) {
        if (maxMultiplier <= 0 || !(patternDetails instanceof AEProcessingPattern) || this.stuff.getStack(0) != null
            || !this.container.getStackInSlot(0).isEmpty()) {
            return 0;
        }
        return this.mode == CanerMode.FILL
            ? canAcceptFillPattern(patternDetails, inputs) ? 1 : 0
            : canAcceptEmptyPattern(patternDetails, inputs) ? 1 : 0;
    }

    private boolean pushFillPattern(IPatternDetails patternDetails, KeyCounter[] inputs, EnumFacing ejectionDirection) {
        if (!canAcceptFillPattern(patternDetails, inputs)) {
            return false;
        }
        var first = inputs[0].getFirstEntry();
        var second = inputs[1].getFirstEntry();
        if (first == null || second == null) {
            return false;
        }
        GenericStack output = patternDetails.getPrimaryOutput();
        var content = first;
        var containerEntry = second;
        if (content.getKey() instanceof AEItemKey) {
            content = second;
            containerEntry = first;
        }
        var contentKey = content.getKey();
        var containerKey = containerEntry.getKey();
        if (!(containerKey instanceof AEItemKey containerItem) || contentKey == null
            || content.getLongValue() <= 0) {
            return false;
        }
        AEItemKey outputItem = (AEItemKey) output.what();

        this.stuff.setStack(0, new GenericStack(contentKey, content.getLongValue()));
        this.container.setItemDirect(0, containerItem.toStack());
        this.target = outputItem.toStack();
        this.ejectSide = ejectionDirection;
        return true;
    }

    private boolean canAcceptFillPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (inputs.length != 2) {
            return false;
        }
        var first = inputs[0].getFirstEntry();
        var second = inputs[1].getFirstEntry();
        GenericStack output = patternDetails.getPrimaryOutput();
        if (first == null || second == null || !(output.what() instanceof AEItemKey)
            || output.amount() != 1) {
            return false;
        }

        var content = first;
        var containerEntry = second;
        if (content.getKey() instanceof AEItemKey) {
            content = second;
            containerEntry = first;
        }
        var contentKey = content.getKey();
        var containerKey = containerEntry.getKey();
        return containerKey instanceof AEItemKey
            && containerEntry.getLongValue() == 1
            && contentKey != null
            && content.getLongValue() > 0
            && !(contentKey instanceof AEItemKey);
    }

    private boolean pushEmptyPattern(IPatternDetails patternDetails, KeyCounter[] inputs, EnumFacing ejectionDirection) {
        if (!canAcceptEmptyPattern(patternDetails, inputs)) {
            return false;
        }
        var input = inputs[0].getFirstEntry();
        if (input == null) {
            return false;
        }
        if (!(input.getKey() instanceof AEItemKey inputItem)) {
            return false;
        }

        GenericStack firstOutput = patternDetails.getOutputs().get(0);
        GenericStack secondOutput = patternDetails.getOutputs().get(1);
        GenericStack content = firstOutput;
        GenericStack output = secondOutput;
        if (content.what() instanceof AEItemKey) {
            content = secondOutput;
            output = firstOutput;
        }
        AEItemKey outputItem = (AEItemKey) output.what();

        this.container.setItemDirect(0, inputItem.toStack());
        this.target = outputItem.toStack();
        this.emptyKey = content.what();
        this.ejectSide = ejectionDirection;
        return true;
    }

    private boolean canAcceptEmptyPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (inputs.length != 1 || patternDetails.getOutputs().size() != 2) {
            return false;
        }
        var input = inputs[0].getFirstEntry();
        if (input == null || !(input.getKey() instanceof AEItemKey) || input.getLongValue() != 1) {
            return false;
        }

        GenericStack firstOutput = patternDetails.getOutputs().get(0);
        GenericStack secondOutput = patternDetails.getOutputs().get(1);
        GenericStack content = firstOutput;
        GenericStack output = secondOutput;
        if (content.what() instanceof AEItemKey) {
            content = secondOutput;
            output = firstOutput;
        }
        return !(content.what() instanceof AEItemKey)
            && output.what() instanceof AEItemKey
            && output.amount() == 1;
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == AECapabilities.GENERIC_INTERNAL_INV
            || capability == AECapabilities.CRAFTING_MACHINE
            || capability == AECapabilities.PATTERN_PROVIDER_BATCH_TARGET
            || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
            || super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            return (T) this.stuff;
        }
        if (capability == AECapabilities.CRAFTING_MACHINE) {
            return (T) this;
        }
        if (capability == AECapabilities.PATTERN_PROVIDER_BATCH_TARGET) {
            return (T) this;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) getExposedItemHandler(facing);
        }
        return super.getCapability(capability, facing);
    }

}
