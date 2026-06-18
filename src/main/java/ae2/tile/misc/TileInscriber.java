/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package ae2.tile.misc;

import ae2.api.AECapabilities;
import ae2.api.config.Actionable;
import ae2.api.config.InscriberInputCapacity;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.api.implementations.blockentities.ICrankable;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.core.settings.TickRates;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.IOutputSideConfigHost;
import ae2.recipes.handlers.InscriberProcessType;
import ae2.recipes.handlers.InscriberRecipe;
import ae2.text.TextComponentItemStack;
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
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

public class TileInscriber extends AENetworkedPoweredTile
    implements IGridTickable, IUpgradeableObject, IConfigurableObject, ICraftingMachine, IOutputSideConfigHost {
    private static final int MAX_PROCESSING_STEPS = 200;
    private static final int POWER_PER_CRANK_TURN = 160;
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AEBlocks.INSCRIBER.item(), 5,
        this::saveChanges);
    private final AppEngInternalInventory topItemHandler = new AppEngInternalInventory(this, 1, 64, new BaseFilter());
    private final AppEngInternalInventory bottomItemHandler = new AppEngInternalInventory(this, 1, 64,
        new BaseFilter());
    private final AppEngInternalInventory sideItemHandler = new AppEngInternalInventory(this, 2, 64, new BaseFilter());
    private final InternalInventory inv = new CombinedInternalInventory(this.topItemHandler, this.bottomItemHandler,
        this.sideItemHandler);
    private final InternalInventory topItemHandlerExtern = new FilteredInternalInventory(this.topItemHandler,
        new AutomationFilter());
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private final InternalInventory bottomItemHandlerExtern = new FilteredInternalInventory(this.bottomItemHandler,
        new AutomationFilter());
    private final InternalInventory sideItemHandlerExtern = new FilteredInternalInventory(this.sideItemHandler,
        new AutomationFilter());
    private final InternalInventory combinedItemHandlerExtern = new CombinedInternalInventory(this.topItemHandlerExtern,
        this.bottomItemHandlerExtern, this.sideItemHandlerExtern);
    private final EnumSet<EnumFacing> outputSides = EnumSet.allOf(EnumFacing.class);
    private final ICrankable crankable = new Crankable();
    private final ItemStack[] clientVisualStacks = new ItemStack[]{
        ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };
    private int processingTime;
    private boolean smash;
    private int finalStep;
    private long clientStart;
    @Nullable
    private InscriberRecipe cachedTask;
    public TileInscriber() {
        this.setInternalMaxPower(1600);
        this.setPowerSides(getGridConnectableSides(getOrientation()));
        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);

        this.configManager.registerSetting(Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.configManager.registerSetting(Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.SIXTY_FOUR);
        applyInputCapacity();
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.INSCRIBER.stack();
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.topItemHandler.writeToNBT(data, "topItemHandler");
        this.bottomItemHandler.writeToNBT(data, "bottomItemHandler");
        this.sideItemHandler.writeToNBT(data, "sideItemHandler");
        this.upgrades.writeToNBT(data, "upgrades");
        this.configManager.writeToNBT(data);
        data.setInteger("processingTime", this.processingTime);
        data.setBoolean("smash", this.smash);
        data.setInteger("finalStep", this.finalStep);
        data.setInteger("outputSides", encodeOutputSides());
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.topItemHandler.readFromNBT(data, "topItemHandler");
        this.bottomItemHandler.readFromNBT(data, "bottomItemHandler");
        this.sideItemHandler.readFromNBT(data, "sideItemHandler");
        this.upgrades.readFromNBT(data, "upgrades");
        this.configManager.readFromNBT(data);
        this.processingTime = data.getInteger("processingTime");
        this.smash = data.getBoolean("smash");
        this.finalStep = data.getInteger("finalStep");
        decodeOutputSides(data.hasKey("outputSides", Constants.NBT.TAG_ANY_NUMERIC)
            ? data.getInteger("outputSides")
            : 0x3F);
        this.cachedTask = null;
        applyInputCapacity();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.setPowerSides(getGridConnectableSides(orientation));
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        if (isSeparateSides()) {
            EnumFacing top = this.getOrientation().getSide(RelativeSide.TOP);
            if (side == top) {
                return this.topItemHandlerExtern;
            } else if (side == top.getOpposite()) {
                return this.bottomItemHandlerExtern;
            }
        }
        return this.combinedItemHandlerExtern;
    }

    @Nullable
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.STORAGE.equals(id)) {
            return this.inv;
        } else if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return null;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (var upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.topItemHandler || inv == this.bottomItemHandler || inv == this.sideItemHandler && slot == 0) {
            this.processingTime = 0;
            this.cachedTask = null;
        }

        if (!this.smash) {
            this.markForUpdate();
        }

        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        var packetBuffer = new PacketBuffer(data);
        int slotMask = this.smash ? 64 : 0;
        for (int slot = 0; slot < 4; slot++) {
            if (!getVisualStack(slot).isEmpty()) {
                slotMask |= 1 << slot;
            }
        }

        packetBuffer.writeByte(slotMask);
        for (int slot = 0; slot < 4; slot++) {
            if ((slotMask & (1 << slot)) != 0) {
                packetBuffer.writeItemStack(getVisualStack(slot));
            }
        }
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        var packetBuffer = new PacketBuffer(data);
        int slotMask = packetBuffer.readUnsignedByte();
        boolean newSmash = (slotMask & 64) != 0;
        if (this.smash != newSmash) {
            this.smash = newSmash;
            changed = true;
            if (newSmash) {
                this.clientStart = System.currentTimeMillis();
            }
        }

        for (int slot = 0; slot < this.clientVisualStacks.length; slot++) {
            ItemStack oldStack = this.clientVisualStacks[slot];
            ItemStack newStack = ItemStack.EMPTY;
            if ((slotMask & (1 << slot)) != 0) {
                try {
                    newStack = packetBuffer.readItemStack();
                } catch (IOException e) {
                    AELog.warn(e, "Failed to read inscriber item from update stream");
                }
            }
            this.clientVisualStacks[slot] = newStack;
            switch (slot) {
                case 0 -> this.topItemHandler.setItemDirect(0, newStack);
                case 1 -> this.bottomItemHandler.setItemDirect(0, newStack);
                case 2 -> this.sideItemHandler.setItemDirect(0, newStack);
                case 3 -> this.sideItemHandler.setItemDirect(1, newStack);
                default -> {
                }
            }
            if (!ItemStack.areItemStacksEqual(oldStack, newStack)) {
                changed = true;
            }
        }

        this.cachedTask = null;
        return changed;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, !this.hasAutoExportWork() && !this.hasCraftWork());
    }

    private boolean hasAutoExportWork() {
        return !this.sideItemHandler.getStackInSlot(1).isEmpty()
            && this.configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.smash) {
            this.finalStep++;
            if (this.finalStep == 8) {
                var task = this.getTask();
                if (task != null) {
                    int runs = this.getParallelRuns(task);
                    if (runs > 0) {
                        var output = task.getResultItem().copy();
                        output.setCount(output.getCount() * runs);
                        this.sideItemHandler.insertItem(1, output, false);
                        this.processingTime = 0;
                        if (task.getProcessType() == InscriberProcessType.PRESS) {
                            this.topItemHandler.extractItem(0, runs, false);
                            this.bottomItemHandler.extractItem(0, runs, false);
                        }
                        this.sideItemHandler.extractItem(0, runs, false);
                    }
                }
                this.saveChanges();
            } else if (this.finalStep >= 16) {
                this.finalStep = 0;
                this.smash = false;
                this.markForUpdate();
            }
        } else if (this.hasCraftWork()) {
            final int speedFactor = switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item())) {
                case 1 -> 3;
                case 2 -> 5;
                case 3 -> 10;
                case 4 -> 50;
                default -> 2;
            };
            final int powerConsumption = 10 * speedFactor;
            final double powerThreshold = powerConsumption - 0.01;

            IEnergySource source = selectEnergySource(powerConsumption);
            if (source != null) {
                double available = source.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (available > powerThreshold) {
                    source.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    this.processingTime += speedFactor;
                    this.saveChanges();
                }
            }

            if (this.processingTime > MAX_PROCESSING_STEPS) {
                this.processingTime = MAX_PROCESSING_STEPS;
                var task = this.getTask();
                if (task != null && this.getParallelRuns(task) > 0) {
                    this.smash = true;
                    this.finalStep = 0;
                    this.markForUpdate();
                }
            }
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return this.hasCraftWork() ? TickRateModulation.URGENT
            : this.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    @Nullable
    private IEnergySource selectEnergySource(double powerConsumption) {
        double internal = this.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (internal > powerConsumption - 0.01) {
            return this;
        }

        var grid = this.getMainNode().getGrid();
        if (grid != null) {
            return grid.getEnergyService();
        }

        if (internal > 0) {
            return this;
        }

        return null;
    }

    private boolean hasCraftWork() {
        var task = this.getTask();
        if (task != null) {
            return this.getParallelRuns(task) > 0;
        }

        this.processingTime = 0;
        return this.smash;
    }

    @Nullable
    public InscriberRecipe getTask() {
        if (this.cachedTask == null) {
            ItemStack input = this.sideItemHandler.getStackInSlot(0);
            if (!input.isEmpty()) {
                this.cachedTask = InscriberRecipes.findRecipe(input, this.topItemHandler.getStackInSlot(0),
                    this.bottomItemHandler.getStackInSlot(0), true);
            }
        }
        return this.cachedTask;
    }

    private int getParallelLimit() {
        return switch (this.upgrades.getInstalledUpgrades(AEItems.PARALLEL_CARD.item())) {
            case 1 -> 4;
            case 2 -> 16;
            case 3 -> 64;
            default -> 1;
        };
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(AEItemKey.of(AEBlocks.INSCRIBER.stack()),
            TextComponentItemStack.of(AEBlocks.INSCRIBER.stack()), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, int multiplier,
                               EnumFacing ejectionDirection) {
        if (!(patternDetails instanceof AEProcessingPattern processingPattern) || multiplier <= 0) {
            return false;
        }
        InscriberCraftingPush.Plan plan = createCraftingPushPlan(processingPattern, inputs, multiplier);
        if (plan == null || plan.maxMultiplier() < multiplier) {
            return false;
        }

        insertPlannedStack(this.topItemHandler, plan.top(), multiplier);
        insertPlannedStack(this.sideItemHandler, plan.middle(), multiplier);
        insertPlannedStack(this.bottomItemHandler, plan.bottom(), multiplier);
        this.cachedTask = null;
        this.processingTime = 0;
        this.saveChanges();
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        return true;
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, KeyCounter[] inputs, int maxMultiplier,
                                           EnumFacing ejectionDirection) {
        if (!(patternDetails instanceof AEProcessingPattern processingPattern)) {
            return 0;
        }
        InscriberCraftingPush.Plan plan = createCraftingPushPlan(processingPattern, inputs, maxMultiplier);
        return plan == null ? 0 : plan.maxMultiplier();
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    private InscriberCraftingPush.Plan createCraftingPushPlan(AEProcessingPattern pattern, KeyCounter[] inputs,
                                                              int maxMultiplier) {
        return InscriberCraftingPush.plan(pattern, inputs, new InscriberCraftingPush.State(
            this.topItemHandler.getStackInSlot(0),
            this.sideItemHandler.getStackInSlot(0),
            this.bottomItemHandler.getStackInSlot(0),
            this.sideItemHandler.getStackInSlot(1),
            this.configManager.getSetting(Settings.INSCRIBER_INPUT_CAPACITY).capacity,
            this.smash), this.getParallelLimit(), maxMultiplier);
    }

    private void insertPlannedStack(AppEngInternalInventory inventory, InscriberCraftingPush.SlotPlan slotPlan,
                                    int multiplier) {
        ItemStack stack = slotPlan.stackForRuns(multiplier);
        if (!stack.isEmpty()) {
            inventory.insertItem(0, stack, false);
        }
    }

    private int getParallelRuns(InscriberRecipe task) {
        int runs = Math.min(this.getParallelLimit(), this.sideItemHandler.getStackInSlot(0).getCount());
        if (task.getProcessType() == InscriberProcessType.PRESS) {
            runs = Math.min(runs, this.topItemHandler.getStackInSlot(0).getCount());
            runs = Math.min(runs, this.bottomItemHandler.getStackInSlot(0).getCount());
        }
        if (runs <= 0) {
            return 0;
        }

        ItemStack output = task.getResultItem().copy();
        int outputCount = output.getCount();
        for (int i = runs; i > 0; i--) {
            output.setCount(outputCount * i);
            if (this.sideItemHandler.insertItem(1, output, true).isEmpty()) {
                return i;
            }
        }
        return 0;
    }

    private boolean pushOutResult() {
        if (!this.hasAutoExportWork() || this.world == null) {
            return false;
        }

        EnumSet<EnumFacing> allowedOutputSides = getAllowedOutputSides();
        for (var side : this.outputSides) {
            if (!allowedOutputSides.contains(side)) {
                continue;
            }
            var external = InternalInventory.wrapExternal(this.world, this.pos.offset(side), side.getOpposite());
            if (external == null) {
                continue;
            }

            int startItems = this.sideItemHandler.getStackInSlot(1).getCount();
            var overflow = external.addItems(this.sideItemHandler.extractItem(1, 64, false));
            this.sideItemHandler.insertItem(1, overflow, false);
            if (this.sideItemHandler.getStackInSlot(1).getCount() != startItems) {
                return true;
            }
        }

        return false;
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
        if (capability == AECapabilities.CRAFTING_MACHINE
            || capability == AECapabilities.PATTERN_PROVIDER_BATCH_TARGET) {
            return true;
        }
        if (capability == AECapabilities.CRANKABLE && getCrankable(facing) != null) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.CRAFTING_MACHINE
            || capability == AECapabilities.PATTERN_PROVIDER_BATCH_TARGET) {
            return (T) this;
        }
        if (capability == AECapabilities.CRANKABLE) {
            return (T) getCrankable(facing);
        }
        return super.getCapability(capability, facing);
    }

    @Nullable
    protected ICrankable getCrankable(@Nullable EnumFacing side) {
        if (side != null && side != this.getOrientation().getSide(RelativeSide.FRONT)) {
            return this.crankable;
        }
        return null;
    }

    public int getMaxProcessingTime() {
        return MAX_PROCESSING_STEPS;
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    public long getClientStart() {
        return this.clientStart;
    }

    public boolean isSmash() {
        return this.smash;
    }

    public ItemStack getVisualStack(int slot) {
        if (this.isClientSide()) {
            return slot >= 0 && slot < this.clientVisualStacks.length ? this.clientVisualStacks[slot] : ItemStack.EMPTY;
        }

        return switch (slot) {
            case 0 -> this.topItemHandler.getStackInSlot(0);
            case 1 -> this.bottomItemHandler.getStackInSlot(0);
            case 2 -> this.sideItemHandler.getStackInSlot(0);
            case 3 -> this.sideItemHandler.getStackInSlot(1);
            default -> ItemStack.EMPTY;
        };
    }

    public boolean isSeparateSides() {
        return this.configManager.getSetting(Settings.INSCRIBER_SEPARATE_SIDES) == YesNo.YES;
    }

    @Override
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
        EnumSet<EnumFacing> allowed = EnumSet.allOf(EnumFacing.class);
        if (isSeparateSides()) {
            EnumFacing top = this.getOrientation().getSide(RelativeSide.TOP);
            allowed.remove(top);
            allowed.remove(top.getOpposite());
        }
        return allowed;
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openGui(player, GuiIds.GuiKey.INSCRIBER, this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return AEBlocks.INSCRIBER.stack();
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        if (setting == Settings.AUTO_EXPORT) {
            wake();
        }

        if (setting == Settings.INSCRIBER_SEPARATE_SIDES && this.world != null) {
            this.markForUpdate();
            var state = this.getBlockState();
            if (state != null) {
                this.world.notifyNeighborsOfStateChange(this.pos, state.getBlock(), true);
            }
        }

        if (setting == Settings.INSCRIBER_INPUT_CAPACITY) {
            applyInputCapacity();
        }

        saveChanges();
        wake();
    }

    private void applyInputCapacity() {
        int capacity = this.configManager.getSetting(Settings.INSCRIBER_INPUT_CAPACITY).capacity;
        this.topItemHandler.setMaxStackSize(0, capacity);
        this.bottomItemHandler.setMaxStackSize(0, capacity);
        this.sideItemHandler.setMaxStackSize(0, capacity);
    }

    private void wake() {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
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

    private final class Crankable implements ICrankable {
        @Override
        public boolean canTurn() {
            return getInternalCurrentPower() < getInternalMaxPower();
        }

        @Override
        public void applyTurn() {
            injectExternalPower(PowerUnit.AE, POWER_PER_CRANK_TURN, Actionable.MODULATE);
        }
    }

    private final class BaseFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (inv == TileInscriber.this.sideItemHandler && slot == 1) {
                return true;
            }

            if ((inv == TileInscriber.this.topItemHandler || inv == TileInscriber.this.bottomItemHandler)
                && InscriberRecipes.isValidOptionalIngredient(stack)) {
                return true;
            }

            if (inv == TileInscriber.this.sideItemHandler
                && (InscriberRecipes.isValidOptionalIngredient(TileInscriber.this.topItemHandler.getStackInSlot(0))
                || InscriberRecipes.isValidOptionalIngredient(
                TileInscriber.this.bottomItemHandler.getStackInSlot(0)))) {
                return true;
            }

            ItemStack top = TileInscriber.this.topItemHandler.getStackInSlot(0);
            ItemStack bottom = TileInscriber.this.bottomItemHandler.getStackInSlot(0);
            ItemStack middle = TileInscriber.this.sideItemHandler.getStackInSlot(0);

            if (inv == TileInscriber.this.topItemHandler) {
                top = stack;
            } else if (inv == TileInscriber.this.bottomItemHandler) {
                bottom = stack;
            } else if (inv == TileInscriber.this.sideItemHandler && slot == 0) {
                middle = stack;
            }

            if (!middle.isEmpty() && InscriberRecipes.findRecipe(middle, top, bottom, true) != null) {
                return true;
            }

            if (middle.isEmpty()) {
                if (top.isEmpty() && bottom.isEmpty()) {
                    return inv == TileInscriber.this.sideItemHandler && slot == 0;
                }
                if (top.isEmpty()) {
                    return InscriberRecipes.isValidOptionalIngredientCombination(stack, bottom);
                }
                if (bottom.isEmpty()) {
                    return InscriberRecipes.isValidOptionalIngredientCombination(top, stack);
                }
                return InscriberRecipes.isValidOptionalIngredientCombination(top, bottom);
            }

            return false;
        }
    }

    private final class AutomationFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            if (inv == TileInscriber.this.sideItemHandler && slot == 1) {
                return true;
            }

            if (TileInscriber.this.smash) {
                return false;
            }

            return isSeparateSides() && (inv == TileInscriber.this.topItemHandler || inv == TileInscriber.this.bottomItemHandler);
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return (inv != TileInscriber.this.sideItemHandler || slot != 1) && !TileInscriber.this.smash;
        }
    }




}
