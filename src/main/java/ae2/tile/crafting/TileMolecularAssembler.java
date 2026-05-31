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

package ae2.tile.crafting;

import ae2.api.AECapabilities;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.IPowerChannelState;
import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ItemTransfer;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.AECableType;
import ae2.client.render.crafting.AssemblerAnimationStatus;
import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.AssemblerAnimationPacket;
import ae2.text.TextComponentItemStack;
import ae2.tile.grid.AENetworkedInvTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.CombinedInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public class TileMolecularAssembler extends AENetworkedInvTile
    implements IUpgradeableObject, IGridTickable, ICraftingMachine, IPowerChannelState, ISegmentedInventory {
    public static final ResourceLocation INV_MAIN = AppEng.makeId("molecular_assembler");

    private static final Container NULL_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
            return false;
        }
    };

    private final InventoryCrafting craftingInv = new InventoryCrafting(NULL_CONTAINER, 3, 3);
    private final AppEngInternalInventory gridInv = new AppEngInternalInventory(this, 10, 1);
    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, 1, 1, new PatternFilter());
    private final InternalInventory gridInvExt = new FilteredInternalInventory(this.gridInv, new CraftingGridFilter());
    private final InternalInventory internalInv = new CombinedInternalInventory(this.gridInv, this.patternInv);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AEBlocks.MOLECULAR_ASSEMBLER.item(), 5,
        this::saveChanges);
    private final EnumMap<EnumFacing, ItemTransfer> neighbors = new EnumMap<>(EnumFacing.class);
    private boolean powered;
    @Nullable
    private EnumFacing pushDirection;
    private ItemStack myPattern = ItemStack.EMPTY;
    @Nullable
    private IMolecularAssemblerSupportedPattern myPlan;
    private double progress;
    private boolean awake;
    private boolean forcePlan;
    private boolean reboot = true;
    @Nullable
    private AssemblerAnimationStatus animationStatus;

    public TileMolecularAssembler() {
        this.getMainNode().setIdlePowerUsage(0.0).addService(IGridTickable.class, this);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.MOLECULAR_ASSEMBLER.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        ITextComponent name;
        name = hasCustomName() ? getCustomName() : TextComponentItemStack.of(AEBlocks.MOLECULAR_ASSEMBLER.stack());
        List<ITextComponent> tooltip;
        int accelerationCards = this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item());
        if (accelerationCards == 0) {
            tooltip = Collections.emptyList();
        } else {
            tooltip = new ObjectArrayList<>(1);
            tooltip.add(Tooltips.of(GuiText.CompatibleUpgrade.text(
                Tooltips.of(TextComponentItemStack.of(AEItems.SPEED_CARD.stack())),
                Tooltips.ofUnformattedNumber(accelerationCards))));
        }
        return new PatternContainerGroup(AEItemKey.of(AEBlocks.MOLECULAR_ASSEMBLER.item()), name, tooltip);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, EnumFacing ejectionDirection) {
        if (this.forcePlan || !this.myPattern.isEmpty()) {
            return false;
        }

        boolean isEmpty = this.gridInv.isEmpty() && this.patternInv.isEmpty();
        if (isEmpty && patternDetails instanceof IMolecularAssemblerSupportedPattern pattern) {
            this.forcePlan = true;
            this.myPattern = patternDetails.getDefinition().toStack();
            this.myPlan = pattern;
            this.pushDirection = ejectionDirection;
            this.fillGrid(inputs, pattern);
            this.updateSleepiness();
            this.saveChanges();
            return true;
        }
        return false;
    }

    private void fillGrid(KeyCounter[] table, IMolecularAssemblerSupportedPattern pattern) {
        pattern.fillCraftingGrid(table, this.gridInv::setItemDirect);
        for (KeyCounter inputs : table) {
            inputs.removeZeros();
            if (!inputs.isEmpty()) {
                throw new IllegalStateException("Could not fill crafting grid for molecular assembler.");
            }
        }
    }

    @Override
    public boolean acceptsPlans() {
        return !this.forcePlan && this.patternInv.isEmpty();
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.gridInv.writeToNBT(data, "gridInv");
        this.patternInv.writeToNBT(data, "patternInv");
        if (this.forcePlan) {
            ItemStack pattern = this.myPlan != null ? this.myPlan.getDefinition().toStack() : this.myPattern;
            if (!pattern.isEmpty()) {
                data.setTag("myPlan", pattern.writeToNBT(new NBTTagCompound()));
                data.setInteger("pushDirection", this.pushDirection == null ? -1 : this.pushDirection.ordinal());
            }
        }
        this.upgrades.writeToNBT(data, "upgrades");
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.gridInv.readFromNBT(data, "gridInv");
        this.patternInv.readFromNBT(data, "patternInv");

        this.forcePlan = false;
        this.myPattern = ItemStack.EMPTY;
        this.myPlan = null;
        this.pushDirection = null;

        if (data.hasKey("myPlan", 10)) {
            this.forcePlan = true;
            this.myPattern = new ItemStack(data.getCompoundTag("myPlan"));
            int pushDirectionOrdinal = data.getInteger("pushDirection");
            if (pushDirectionOrdinal >= 0 && pushDirectionOrdinal < EnumFacing.values().length) {
                this.pushDirection = EnumFacing.values()[pushDirectionOrdinal];
            }
        }

        this.upgrades.readFromNBT(data, "upgrades");
        this.recalculatePlan();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.world != null && !this.world.isRemote) {
            this.updateNeighbors();
            this.updatePoweredState();
        }
    }

    public void onNeighborChanged(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                  net.minecraft.util.math.BlockPos neighbor) {
        if (world == null || pos == null || neighbor == null) {
            return;
        }

        EnumFacing side = null;
        for (EnumFacing candidate : EnumFacing.values()) {
            if (pos.offset(candidate).equals(neighbor)) {
                side = candidate;
                break;
            }
        }

        if (side != null) {
            this.updateNeighbor(side);
        }
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);
        data.setBoolean("powered", this.powered);
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);
        if (data.hasKey("powered")) {
            this.powered = data.getBoolean("powered");
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.powered);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean oldPowered = this.powered;
        this.powered = data.readBoolean();
        return changed || oldPowered != this.powered;
    }

    private void recalculatePlan() {
        this.reboot = true;

        if (this.forcePlan) {
            if (this.myPlan == null && this.world != null && !this.myPattern.isEmpty()) {
                IPatternDetails details = PatternDetailsHelper.decodePattern(this.myPattern, this.world);
                if (details instanceof IMolecularAssemblerSupportedPattern) {
                    this.myPlan = (IMolecularAssemblerSupportedPattern) details;
                } else {
                    this.forcePlan = false;
                    this.myPattern = ItemStack.EMPTY;
                    this.pushDirection = null;
                }
            }
            this.updateSleepiness();
            return;
        }

        ItemStack pattern = this.patternInv.getStackInSlot(0);
        boolean reset = true;

        if (!pattern.isEmpty()) {
            if (ItemStack.areItemStacksEqual(pattern, this.myPattern)) {
                reset = false;
            } else if (this.world != null) {
                IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, this.world);
                if (details instanceof IMolecularAssemblerSupportedPattern) {
                    reset = false;
                    this.progress = 0;
                    this.myPattern = pattern.copy();
                    this.myPlan = (IMolecularAssemblerSupportedPattern) details;
                }
            }
        }

        if (reset) {
            this.progress = 0;
            this.forcePlan = false;
            this.myPlan = null;
            this.myPattern = ItemStack.EMPTY;
            this.pushDirection = null;
        }

        this.updateSleepiness();
    }

    private void updateSleepiness() {
        boolean previousAwake = this.awake;
        this.awake = this.canPush() || this.myPlan != null && this.hasMats();
        if (previousAwake != this.awake) {
            this.getMainNode().ifPresent((grid, node) -> {
                if (this.awake) {
                    grid.getTickManager().wakeDevice(node);
                } else {
                    grid.getTickManager().sleepDevice(node);
                }
            });
        }
    }

    private boolean canPush() {
        return !this.gridInv.getStackInSlot(9).isEmpty();
    }

    private boolean hasMats() {
        if (this.myPlan == null || this.world == null) {
            return false;
        }

        for (int slot = 0; slot < this.craftingInv.getSizeInventory(); slot++) {
            this.craftingInv.setInventorySlotContents(slot, this.gridInv.getStackInSlot(slot));
        }

        return !this.myPlan.assemble(this.craftingInv, this.world).isEmpty();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        } else if (INV_MAIN.equals(id)) {
            return this.internalInv;
        }
        return null;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.internalInv;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.gridInvExt;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.gridInv || inv == this.patternInv) {
            this.recalculatePlan();
        }
    }

    public int getCraftingProgress() {
        return (int) this.progress;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (ItemStack upgrade : this.upgrades) {
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
    public TickingRequest getTickingRequest(IGridNode node) {
        this.recalculatePlan();
        this.updateSleepiness();
        return new TickingRequest(1, 1, !this.awake);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.gridInv.getStackInSlot(9).isEmpty()) {
            this.pushOut(this.gridInv.getStackInSlot(9));
            if (this.gridInv.getStackInSlot(9).isEmpty()) {
                this.saveChanges();
            }

            this.ejectHeldItems();
            this.updateSleepiness();
            this.progress = 0;
            return this.awake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
        }

        if (this.myPlan == null) {
            this.updateSleepiness();
            return TickRateModulation.SLEEP;
        }

        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        if (!this.awake) {
            return TickRateModulation.SLEEP;
        }

        this.reboot = false;
        int speed = 10;
        switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item())) {
            case 0 -> this.progress += this.usePower(ticksSinceLastCall, speed, 1.0);
            case 1 -> {
                speed = 13;
                this.progress += this.usePower(ticksSinceLastCall, speed, 1.3);
            }
            case 2 -> {
                speed = 17;
                this.progress += this.usePower(ticksSinceLastCall, speed, 1.7);
            }
            case 3 -> {
                speed = 20;
                this.progress += this.usePower(ticksSinceLastCall, speed, 2.0);
            }
            case 4 -> {
                speed = 25;
                this.progress += this.usePower(ticksSinceLastCall, speed, 2.5);
            }
            case 5 -> this.progress += this.usePower(ticksSinceLastCall, speed = 50, 5.0);
            default -> {
            }
        }

        if (this.progress >= 100 && this.world != null) {
            for (int slot = 0; slot < this.craftingInv.getSizeInventory(); slot++) {
                this.craftingInv.setInventorySlotContents(slot, this.gridInv.getStackInSlot(slot));
            }

            this.progress = 0;
            ItemStack output = this.myPlan.assemble(this.craftingInv, this.world);
            if (!output.isEmpty()) {
                ItemStack result = output.copy();
                List<ItemStack> remainingItems = this.myPlan.getRemainingItems(this.craftingInv);
                this.pushOut(result);

                for (int slot = 0; slot < this.craftingInv.getSizeInventory(); slot++) {
                    ItemStack remainder = remainingItems.get(slot);
                    this.gridInv.setItemDirect(slot, remainder);
                }

                if (this.patternInv.isEmpty()) {
                    this.forcePlan = false;
                    this.myPlan = null;
                    this.pushDirection = null;
                }

                this.ejectHeldItems();
                AEItemKey item = AEItemKey.of(output);
                if (item != null) {
                    InitNetwork.sendToAllNearExcept(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), 32,
                        this.world, new AssemblerAnimationPacket(this.pos, (byte) speed, item));
                }
                this.saveChanges();
                this.updateSleepiness();
                return this.awake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
            }
        }

        return TickRateModulation.FASTER;
    }

    private void ejectHeldItems() {
        if (this.gridInv.getStackInSlot(9).isEmpty()) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = this.gridInv.getStackInSlot(slot);
                if (!stack.isEmpty() && (this.myPlan == null
                    || !this.myPlan.isItemValid(slot, AEItemKey.of(stack), this.world))) {
                    this.gridInv.setItemDirect(9, stack);
                    this.gridInv.setItemDirect(slot, ItemStack.EMPTY);
                    this.saveChanges();
                    return;
                }
            }
        }
    }

    private int usePower(int ticksPassed, int bonusValue, double acceleratorTax) {
        ae2.api.networking.IGrid grid = this.getMainNode().getGrid();
        if (grid != null) {
            return (int) (grid.getEnergyService().extractAEPower(ticksPassed * bonusValue * acceleratorTax,
                Actionable.MODULATE, PowerMultiplier.CONFIG) / acceleratorTax);
        }
        return 0;
    }

    private void pushOut(ItemStack output) {
        if (this.pushDirection == null) {
            for (EnumFacing side : this.neighbors.keySet()) {
                output = this.pushTo(output, side);
                if (output.isEmpty()) {
                    break;
                }
            }
        } else {
            output = this.pushTo(output, this.pushDirection);
        }

        if (output.isEmpty() && this.forcePlan) {
            this.forcePlan = false;
            this.recalculatePlan();
        }

        this.gridInv.setItemDirect(9, output);
    }

    private ItemStack pushTo(ItemStack output, EnumFacing side) {
        if (output.isEmpty() || this.world == null) {
            return output;
        }

        var target = this.neighbors.get(side);
        if (target == null) {
            return output;
        }

        int before = output.getCount();
        output = target.addItems(output);
        if ((output.isEmpty() ? 0 : output.getCount()) != before) {
            this.saveChanges();
        }
        return output;
    }

    private void updateNeighbors() {
        for (EnumFacing side : EnumFacing.values()) {
            this.updateNeighbor(side);
        }
    }

    private void updateNeighbor(EnumFacing side) {
        if (this.world == null || this.pos == null) {
            this.neighbors.remove(side);
            return;
        }

        ItemTransfer target = InternalInventory.wrapExternal(this.world, this.pos.offset(side), side.getOpposite());
        if (target != null) {
            this.neighbors.put(side, target);
        } else {
            this.neighbors.remove(side);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        updatePoweredState();
    }

    private void updatePoweredState() {
        if (this.world == null || this.world.isRemote) {
            return;
        }

        boolean newPowered = false;
        ae2.api.networking.IGrid grid = this.getMainNode().getGrid();
        if (grid != null) {
            newPowered = this.getMainNode().isPowered()
                && grid.getEnergyService().extractAEPower(1, Actionable.SIMULATE,
                PowerMultiplier.CONFIG) > 0.0001;
        }

        if (this.powered != newPowered) {
            this.powered = newPowered;
            this.markForUpdate();
        }
    }

    @Override
    public boolean isPowered() {
        return this.powered;
    }

    @Override
    public boolean isActive() {
        return this.powered;
    }

    @Nullable
    public AssemblerAnimationStatus getAnimationStatus() {
        return this.animationStatus;
    }

    public void setAnimationStatus(@Nullable AssemblerAnimationStatus animationStatus) {
        this.animationStatus = animationStatus;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Nullable
    public IMolecularAssemblerSupportedPattern getCurrentPattern() {
        if (this.isClientSide()) {
            if (this.world == null) {
                return null;
            }
            ItemStack pattern = this.patternInv.getStackInSlot(0);
            if (pattern.isEmpty()) {
                pattern = this.myPattern;
            }
            if (pattern.isEmpty()) {
                return null;
            }
            IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, this.world);
            if (details instanceof IMolecularAssemblerSupportedPattern) {
                return (IMolecularAssemblerSupportedPattern) details;
            }
            return null;
        }
        return this.myPlan;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.CRAFTING_MACHINE) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.CRAFTING_MACHINE) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }

    private class CraftingGridFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return slot == 9;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return slot < 9 && TileMolecularAssembler.this.myPlan != null
                && !TileMolecularAssembler.this.patternInv.isEmpty()
                && TileMolecularAssembler.this.world != null
                && TileMolecularAssembler.this.myPlan.isItemValid(slot, AEItemKey.of(stack),
                TileMolecularAssembler.this.world);
        }
    }

    private class PatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (TileMolecularAssembler.this.world == null) {
                return true;
            }
            return PatternDetailsHelper.decodePattern(stack, TileMolecularAssembler.this.world)
                instanceof IMolecularAssemblerSupportedPattern;
        }
    }
}
