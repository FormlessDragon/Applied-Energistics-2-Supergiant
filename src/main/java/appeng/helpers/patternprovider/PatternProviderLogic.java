/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.helpers.patternprovider;

import appeng.api.config.Actionable;
import appeng.api.config.BlockingMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PatternProviderBlockingType;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.settings.TickRates;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.PlayerInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class PatternProviderLogic implements InternalInventoryHost, ICraftingProvider {
    public static final String NBT_PATTERNS = "patterns";
    public static final String NBT_UNLOCK_EVENT = "unlockEvent";
    public static final String NBT_UNLOCK_STACK = "unlockStack";
    public static final String NBT_PRIORITY = "priority";
    public static final String NBT_SEND_LIST = "sendList";
    public static final String NBT_SEND_DIRECTION = "sendDirection";
    public static final String NBT_RETURN_INV = "returnInv";
    private static final String MEMORY_CARD_SETTINGS = "settings";
    private static final String MEMORY_CARD_PRIORITY = "priority";
    private static final String MEMORY_CARD_PATTERNS = "patterns";

    private final PatternProviderLogicHost host;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private final IConfigManager configManager;
    private final AppEngInternalInventory patternInventory;
    private final IUpgradeInventory upgrades;
    private final ObjectList<IPatternDetails> patterns = new ObjectArrayList<>();
    private final ObjectSet<AEKey> patternInputs = new ObjectOpenHashSet<>();
    private final ObjectList<GenericStack> sendList = new ObjectArrayList<>();
    private final PatternProviderReturnInventory returnInv;
    private final PatternProviderTargetCache[] targetCaches = new PatternProviderTargetCache[6];

    private int priority;
    private EnumFacing sendDirection;
    private YesNo redstoneState = YesNo.UNDECIDED;
    private UnlockCraftingEvent unlockEvent;
    private GenericStack unlockStack;
    private int roundRobinIndex;
    private boolean wasProviderActive;
    private boolean hasLastSuccessfulPatternHash;
    private int lastSuccessfulPatternHash;

    public PatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host) {
        this(mainNode, host, host.getMainContainerIcon().getItem(), 9);
    }

    public PatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize) {
        this(mainNode, host, host.getMainContainerIcon().getItem(), patternInventorySize);
    }

    public PatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, net.minecraft.item.Item machineType,
                                int patternInventorySize) {
        this.host = host;
        this.mainNode = mainNode
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IGridTickable.class, new Ticker())
            .addService(ICraftingProvider.class, this);
        TileEntity hostTile = host.getTileEntity();
        IActionHost actionHost = hostTile instanceof IActionHost ? (IActionHost) hostTile : mainNode::getNode;
        this.actionSource = new MachineSource(actionHost);
        this.configManager = IConfigManager.builder(this::configChanged)
                                           .registerSetting(Settings.BLOCKING_MODE, BlockingMode.NO)
                                           .registerSetting(Settings.PATTERN_PROVIDER_BLOCKING_TYPE,
                                               PatternProviderBlockingType.NORMAL)
                                            .registerSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.YES)
                                            .registerSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE)
                                            .build();
        this.patternInventory = new AppEngInternalInventory(this, patternInventorySize, 1, new PatternInventoryFilter());
        this.upgrades = UpgradeInventories.forMachine(machineType, 1, this::onUpgradesChanged);
        this.returnInv = new PatternProviderReturnInventory(() -> {
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            this.host.saveChanges();
        });
    }

    private static int countItem(net.minecraft.entity.player.InventoryPlayer inventory, ItemStack needle) {
        int total = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, needle)
                && ItemStack.areItemStackTagsEqual(stack, needle)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static Map<String, String> readSettings(NBTTagCompound settings) {
        Object2ObjectMap<String, String> result = new Object2ObjectOpenHashMap<>();
        for (String key : settings.getKeySet()) {
            if (settings.hasKey(key, Constants.NBT.TAG_STRING)) {
                result.put(key, settings.getString(key));
            }
        }
        return result;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        this.host.saveChanges();
        ICraftingProvider.requestUpdate(this.mainNode);
    }

    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    public void saveChanges() {
        this.host.saveChanges();
    }

    private void onUpgradesChanged() {
        this.host.saveChanges();
        ICraftingProvider.requestUpdate(this.mainNode);
    }

    public void writeToNBT(NBTTagCompound tag) {
        this.configManager.writeToNBT(tag);
        this.patternInventory.writeToNBT(tag, NBT_PATTERNS);
        this.upgrades.writeToNBT(tag, "upgrades");
        tag.setInteger(NBT_PRIORITY, this.priority);

        if (this.unlockEvent == UnlockCraftingEvent.REDSTONE_POWER) {
            tag.setByte(NBT_UNLOCK_EVENT, (byte) 1);
        } else if (this.unlockEvent == UnlockCraftingEvent.RESULT && this.unlockStack != null) {
            tag.setByte(NBT_UNLOCK_EVENT, (byte) 2);
            tag.setTag(NBT_UNLOCK_STACK, GenericStack.writeTag(this.unlockStack));
        } else if (this.unlockEvent == UnlockCraftingEvent.REDSTONE_PULSE) {
            tag.setByte(NBT_UNLOCK_EVENT, (byte) 3);
        }

        NBTTagList sendListTag = new NBTTagList();
        for (GenericStack toSend : this.sendList) {
            sendListTag.appendTag(GenericStack.writeTag(toSend));
        }
        tag.setTag(NBT_SEND_LIST, sendListTag);

        if (this.sendDirection != null) {
            tag.setByte(NBT_SEND_DIRECTION, (byte) this.sendDirection.ordinal());
        } else {
            tag.removeTag(NBT_SEND_DIRECTION);
        }

        tag.setTag(NBT_RETURN_INV, this.returnInv.writeToTag());
    }

    public void readFromNBT(NBTTagCompound tag) {
        this.configManager.readFromNBT(tag);
        migrateLegacyBlockingMode(tag);
        this.patternInventory.readFromNBT(tag, NBT_PATTERNS);
        this.upgrades.readFromNBT(tag, "upgrades");
        this.priority = tag.getInteger(NBT_PRIORITY);

        byte unlockEventType = tag.getByte(NBT_UNLOCK_EVENT);
        if (unlockEventType == 1) {
            this.unlockEvent = UnlockCraftingEvent.REDSTONE_POWER;
        } else if (unlockEventType == 2) {
            this.unlockEvent = UnlockCraftingEvent.RESULT;
        } else if (unlockEventType == 3) {
            this.unlockEvent = UnlockCraftingEvent.REDSTONE_PULSE;
        } else {
            this.unlockEvent = null;
        }

        this.unlockStack = this.unlockEvent == UnlockCraftingEvent.RESULT
            ? GenericStack.readTag(tag.getCompoundTag(NBT_UNLOCK_STACK))
            : null;

        this.sendDirection = null;
        this.sendList.clear();
        if (tag.hasKey(NBT_SEND_LIST, Constants.NBT.TAG_LIST)) {
            NBTTagList sendListTag = tag.getTagList(NBT_SEND_LIST, Constants.NBT.TAG_COMPOUND);
            for (GenericStack stack : GenericStack.readList(sendListTag)) {
                if (stack != null) {
                    this.sendList.add(stack);
                }
            }
        }

        if (tag.hasKey(NBT_SEND_DIRECTION, Constants.NBT.TAG_BYTE)) {
            this.sendDirection = EnumFacing.byIndex(tag.getByte(NBT_SEND_DIRECTION));
        }

        if (this.sendList.isEmpty()) {
            this.sendDirection = null;
        }

        this.returnInv.readFromChildTag(tag, NBT_RETURN_INV);
        this.updatePatterns();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.host.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.host.saveChanges();
        this.updatePatterns();
    }

    @Override
    public boolean isClientSide() {
        World level = this.host.getTileEntity().getWorld();
        return level == null || level.isRemote;
    }

    public void updatePatterns() {
        this.patterns.clear();
        this.patternInputs.clear();

        for (ItemStack stack : this.patternInventory) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, this.host.getTileEntity().getWorld());
            if (details == null) {
                continue;
            }

            this.patterns.add(details);
            this.patternInputs.addAll(getPatternInputs(details));
        }

        if (this.hasLastSuccessfulPatternHash
            && this.patterns.stream().noneMatch(pattern -> getPatternHash(pattern) == this.lastSuccessfulPatternHash)) {
            this.hasLastSuccessfulPatternHash = false;
        }

        ICraftingProvider.requestUpdate(this.mainNode);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (!this.mainNode.isActive()) {
            return Collections.emptyList();
        }
        if (!this.upgrades.isInstalled(AEItems.PSEUDO_CRAFTING_CARD.item())) {
            return this.patterns;
        }
        return this.patterns.stream()
            .map(pattern -> pattern instanceof AEProcessingPattern ? PseudoPatternDetails.wrap(pattern) : pattern)
            .collect(ObjectArrayList.toList());
    }

    @Override
    public int getPatternPriority() {
        return this.priority;
    }

    private <T> void rearrangeRoundRobin(List<T> list) {
        if (list.isEmpty()) {
            return;
        }

        this.roundRobinIndex %= list.size();
        for (int i = 0; i < this.roundRobinIndex; ++i) {
            list.add(list.get(i));
        }
        list.subList(0, this.roundRobinIndex).clear();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        var basePatternDetails = PseudoPatternDetails.unwrap(patternDetails);
        if (!this.sendList.isEmpty() || !this.mainNode.isActive() || !this.patterns.contains(basePatternDetails)) {
            return false;
        }

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        ObjectList<PushTarget> possibleTargets = new ObjectArrayList<>();
        TileEntity blockEntity = this.host.getTileEntity();
        World level = blockEntity.getWorld();
        if (level == null) {
            return false;
        }

        for (EnumFacing direction : getActiveSides()) {
            BlockPos adjacentPos = blockEntity.getPos().offset(direction);
            EnumFacing adjacentSide = direction.getOpposite();

            ICraftingMachine craftingMachine = ICraftingMachine.of(level, adjacentPos, adjacentSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                if (craftingMachine.pushPattern(basePatternDetails, inputHolder, adjacentSide)) {
                    onPushPatternSuccess(basePatternDetails);
                    return true;
                }
                continue;
            }

            PatternProviderTarget adapter = findAdapter(direction);
            if (adapter != null) {
                possibleTargets.add(new PushTarget(direction, adapter));
            }
        }

        if (!basePatternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        rearrangeRoundRobin(possibleTargets);
        for (int i = 0; i < possibleTargets.size(); ++i) {
            PushTarget target = possibleTargets.get(i);
            if (this.isTargetBlocked(target.target, basePatternDetails)) {
                continue;
            }

            if (this.adapterAcceptsAll(target.target, inputHolder)) {
                basePatternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                    long inserted = target.target.insert(what, amount, Actionable.MODULATE);
                    if (inserted < amount) {
                        this.addToSendList(what, amount - inserted);
                    }
                });
                onPushPatternSuccess(basePatternDetails);
                this.sendDirection = target.direction;
                this.saveChanges();
                this.sendStacksOut();
                this.roundRobinIndex += i + 1;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isBusy() {
        return !this.sendList.isEmpty();
    }

    public boolean resetCraftingLock() {
        if (this.unlockEvent != null || this.unlockStack != null) {
            this.unlockEvent = null;
            this.unlockStack = null;
            this.saveChanges();
            return true;
        }
        return false;
    }

    private void onPushPatternSuccess(IPatternDetails pattern) {
        this.resetCraftingLock();
        this.hasLastSuccessfulPatternHash = true;
        this.lastSuccessfulPatternHash = getPatternHash(pattern);

        LockCraftingMode lockMode = this.configManager.getSetting(Settings.LOCK_CRAFTING_MODE);
        if (lockMode == LockCraftingMode.LOCK_UNTIL_PULSE) {
            if (getRedstoneState()) {
                this.unlockEvent = UnlockCraftingEvent.REDSTONE_PULSE;
            } else {
                this.unlockEvent = UnlockCraftingEvent.REDSTONE_POWER;
            }
            this.redstoneState = YesNo.UNDECIDED;
            this.saveChanges();
        } else if (lockMode == LockCraftingMode.LOCK_UNTIL_RESULT) {
            this.unlockEvent = UnlockCraftingEvent.RESULT;
            this.unlockStack = pattern.getPrimaryOutput();
            this.saveChanges();
        }
    }

    public LockCraftingMode getCraftingLockedReason() {
        LockCraftingMode lockMode = this.configManager.getSetting(Settings.LOCK_CRAFTING_MODE);
        if (lockMode == LockCraftingMode.LOCK_WHILE_LOW && !getRedstoneState()) {
            return LockCraftingMode.LOCK_WHILE_LOW;
        }
        if (lockMode == LockCraftingMode.LOCK_WHILE_HIGH && getRedstoneState()) {
            return LockCraftingMode.LOCK_WHILE_HIGH;
        }
        if (this.unlockEvent == UnlockCraftingEvent.REDSTONE_POWER
            || this.unlockEvent == UnlockCraftingEvent.REDSTONE_PULSE) {
            return LockCraftingMode.LOCK_UNTIL_PULSE;
        }
        if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
            return LockCraftingMode.LOCK_UNTIL_RESULT;
        }
        return LockCraftingMode.NONE;
    }

    @Nullable
    public GenericStack getUnlockStack() {
        return this.unlockStack;
    }

    private EnumSet<EnumFacing> getActiveSides() {
        EnumSet<EnumFacing> sides = this.host.getTargets();

        IGridNode node = this.mainNode.getNode();
        if (node != null) {
            for (Entry<EnumFacing, IGridConnection> entry : node.getInWorldConnections().entrySet()) {
                IGridNode otherNode = entry.getValue().getOtherSide(node);
                if (otherNode.getOwner() instanceof PatternProviderLogicHost
                    || (otherNode.getOwner() instanceof InterfaceLogicHost
                    && otherNode.grid().equals(this.mainNode.getGrid()))) {
                    sides.remove(entry.getKey());
                }
            }
        }

        return sides;
    }

    private boolean isTargetBlocked(PatternProviderTarget target, IPatternDetails patternDetails) {
        if (shouldBypassBlockingFor(patternDetails)) {
            return false;
        }

        return switch (this.configManager.getSetting(Settings.BLOCKING_MODE)) {
            case NO -> false;
            case STRONG -> target.containsAnyStack();
            case YES -> target.containsPatternInput(this.patternInputs);
        };
    }

    private boolean shouldBypassBlockingFor(IPatternDetails patternDetails) {
        return this.configManager.getSetting(Settings.PATTERN_PROVIDER_BLOCKING_TYPE) == PatternProviderBlockingType.SMART
            && this.hasLastSuccessfulPatternHash
            && this.lastSuccessfulPatternHash == getPatternHash(patternDetails);
    }

    private Set<AEKey> getPatternInputs(IPatternDetails patternDetails) {
        ObjectSet<AEKey> result = new ObjectOpenHashSet<>();
        for (IInput input : patternDetails.getInputs()) {
            for (GenericStack candidate : input.possibleInputs()) {
                result.add(candidate.what().dropSecondary());
            }
        }
        return result;
    }

    private int getPatternHash(IPatternDetails patternDetails) {
        return patternDetails.getDefinition().hashCode();
    }

    @Nullable
    private PatternProviderTarget findAdapter(EnumFacing side) {
        if (this.targetCaches[side.ordinal()] == null) {
            TileEntity blockEntity = this.host.getTileEntity();
            World level = blockEntity.getWorld();
            if (!(level instanceof WorldServer)) {
                return null;
            }

            this.targetCaches[side.ordinal()] = new PatternProviderTargetCache((WorldServer) level,
                blockEntity.getPos().offset(side), side.getOpposite(), this.actionSource);
        }

        return this.targetCaches[side.ordinal()].find();
    }

    public void invalidateTargetCaches() {
        Arrays.fill(this.targetCaches, null);
    }

    private boolean adapterAcceptsAll(PatternProviderTarget target, KeyCounter[] inputHolder) {
        for (KeyCounter inputList : inputHolder) {
            for (Object2LongMap.Entry<AEKey> input : inputList) {
                if (target.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private void addToSendList(AEKey what, long amount) {
        if (amount > 0) {
            this.sendList.add(new GenericStack(what, amount));
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    private boolean sendStacksOut() {
        if (this.sendDirection == null) {
            if (!this.sendList.isEmpty()) {
                throw new IllegalStateException("Invalid pattern provider state, this is a bug.");
            }
            return false;
        }

        TileEntity blockEntity = this.host.getTileEntity();
        World level = blockEntity.getWorld();
        if (level == null) {
            return false;
        }

        PatternProviderTarget adapter = findAdapter(this.sendDirection);
        if (adapter == null) {
            return false;
        }

        boolean didSomething = false;
        boolean changed = false;
        for (ListIterator<GenericStack> it = this.sendList.listIterator(); it.hasNext(); ) {
            GenericStack stack = it.next();
            long inserted = adapter.insert(stack.what(), stack.amount(), Actionable.MODULATE);
            if (inserted >= stack.amount()) {
                it.remove();
                didSomething = true;
                changed = true;
            } else if (inserted > 0) {
                it.set(new GenericStack(stack.what(), stack.amount() - inserted));
                didSomething = true;
                changed = true;
            }
        }

        if (this.sendList.isEmpty()) {
            if (this.sendDirection != null) {
                this.sendDirection = null;
                changed = true;
            }
        }

        if (changed) {
            this.saveChanges();
        }

        return didSomething;
    }

    private boolean hasWorkToDo() {
        return !this.sendList.isEmpty() || !this.returnInv.isEmpty();
    }

    private boolean doWork() {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return false;
        }

        return this.returnInv.injectIntoNetwork(grid.getStorageService().getInventory(), this.actionSource,
            this::onStackReturnedToNetwork)
            | this.sendStacksOut();
    }

    public AppEngInternalInventory getPatternInv() {
        return this.patternInventory;
    }

    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    public void onMainNodeStateChanged() {
        boolean providerActive = this.mainNode.isActive();
        if (this.wasProviderActive != providerActive) {
            this.wasProviderActive = providerActive;
            ICraftingProvider.requestUpdate(this.mainNode);
        }

        if (providerActive) {
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    public void addDrops(List<ItemStack> drops) {
        for (ItemStack stack : this.patternInventory) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }

        TileEntity blockEntity = this.host.getTileEntity();
        World level = blockEntity.getWorld();
        if (level != null) {
            for (GenericStack stack : this.sendList) {
                stack.what().addDrops(stack.amount(), drops, level, blockEntity.getPos());
            }
            this.returnInv.addDrops(drops, level, blockEntity.getPos());
        }
    }

    public void clearContent() {
        this.patternInventory.clear();
        this.upgrades.clear();
        this.sendList.clear();
        this.returnInv.clear();
    }

    void testSetPatterns(List<IPatternDetails> patterns) {
        this.patterns.clear();
        this.patterns.addAll(patterns);
    }

    public PatternProviderReturnInventory getReturnInv() {
        return this.returnInv;
    }

    public void exportSettings(NBTTagCompound output) {
        NBTTagCompound settings = new NBTTagCompound();
        for (Entry<String, String> entry : this.configManager.exportSettings().entrySet()) {
            settings.setString(entry.getKey(), entry.getValue());
        }

        output.setTag(MEMORY_CARD_SETTINGS, settings);
        output.setInteger(MEMORY_CARD_PRIORITY, this.priority);
        this.patternInventory.writeToNBT(output, MEMORY_CARD_PATTERNS);
    }

    public void importSettings(NBTTagCompound input, @Nullable EntityPlayer player) {
        if (input.hasKey(MEMORY_CARD_SETTINGS, Constants.NBT.TAG_COMPOUND)) {
            var settings = readSettings(input.getCompoundTag(MEMORY_CARD_SETTINGS));
            migrateLegacyBlockingMode(settings);
            this.configManager.importSettings(settings);
        }

        if (input.hasKey(MEMORY_CARD_PRIORITY, Constants.NBT.TAG_INT)) {
            this.setPriority(input.getInteger(MEMORY_CARD_PRIORITY));
        }

        if (player == null || player.world.isRemote || !input.hasKey(MEMORY_CARD_PATTERNS, Constants.NBT.TAG_LIST)) {
            return;
        }

        clearPatternInventory(player);

        AppEngInternalInventory desiredPatterns = new AppEngInternalInventory(this.patternInventory.size());
        desiredPatterns.readFromNBT(input, MEMORY_CARD_PATTERNS);

        int blankPatternsAvailable = player.capabilities.isCreativeMode
            ? Integer.MAX_VALUE
            : countItem(player.inventory, AEItems.BLANK_PATTERN.stack());
        int blankPatternsUsed = 0;
        for (int i = 0; i < desiredPatterns.size(); i++) {
            ItemStack desiredPattern = desiredPatterns.getStackInSlot(i);
            if (desiredPattern.isEmpty()) {
                continue;
            }

            IPatternDetails pattern = PatternDetailsHelper.decodePattern(desiredPattern, this.host.getTileEntity().getWorld());
            if (pattern == null) {
                continue;
            }

            ++blankPatternsUsed;
            if (blankPatternsAvailable >= blankPatternsUsed) {
                if (!this.patternInventory.addItems(pattern.getDefinition().toStack()).isEmpty()) {
                    blankPatternsUsed--;
                }
            }
        }

        if (blankPatternsUsed > 0 && !player.capabilities.isCreativeMode) {
            new PlayerInternalInventory(player.inventory).removeItems(blankPatternsUsed, AEItems.BLANK_PATTERN.stack(),
                null);
        }

        if (blankPatternsUsed > blankPatternsAvailable) {
            player.sendMessage(PlayerMessages.MissingBlankPatterns.text(blankPatternsUsed - blankPatternsAvailable));
        }

        this.updatePatterns();
        this.saveChanges();
    }

    private void migrateLegacyBlockingMode(NBTTagCompound tag) {
        if (tag.hasKey(Settings.BLOCKING_MODE.getName(), Constants.NBT.TAG_STRING)
            && "YES".equals(tag.getString(Settings.BLOCKING_MODE.getName()))) {
            this.configManager.putSetting(Settings.BLOCKING_MODE, BlockingMode.YES);
        }
    }

    private void migrateLegacyBlockingMode(Map<String, String> settings) {
        if ("YES".equals(settings.get(Settings.BLOCKING_MODE.getName()))) {
            settings.put(Settings.BLOCKING_MODE.getName(), BlockingMode.YES.name());
        }
    }

    public PatternContainerGroup getTerminalGroup() {
        TileEntity blockEntity = this.host.getTileEntity();
        if (this.host.hasCustomName()) {
            ITextComponent name = this.host.getCustomName();
            if (name != null) {
                return new PatternContainerGroup(this.host.getTerminalIcon(), name.createCopy(),
                    Collections.emptyList());
            }
        }

        EnumSet<EnumFacing> sides = getActiveSides();
        Set<PatternContainerGroup> groups = new it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet<>(sides.size());
        World level = blockEntity.getWorld();
        if (level != null) {
            for (EnumFacing side : sides) {
                PatternContainerGroup group = PatternContainerGroup.fromMachine(level, blockEntity.getPos().offset(side),
                    side.getOpposite());
                if (group != null) {
                    groups.add(group);
                }
            }
        }

        if (groups.size() == 1) {
            return groups.iterator().next();
        }

        ObjectList<ITextComponent> tooltip = it.unimi.dsi.fastutil.objects.ObjectLists.emptyList();
        if (groups.size() > 1) {
            tooltip = new ObjectArrayList<>();
            tooltip.add(GuiText.AdjacentToDifferentMachines.text()
                                                           .setStyle(new Style().setBold(true).setColor(TextFormatting.WHITE)));
            for (PatternContainerGroup group : groups) {
                tooltip.add(group.name().createCopy());
                for (ITextComponent line : group.tooltip()) {
                    tooltip.add(new TextComponentString("  ").appendSibling(line.createCopy()));
                }
            }
        }

        AEItemKey hostIcon = this.host.getTerminalIcon();
        return new PatternContainerGroup(hostIcon, hostIcon.getDisplayName(), tooltip);
    }

    public long getSortValue() {
        BlockPos pos = this.host.getTileEntity().getPos();
        return ((long) pos.getZ() << 24) ^ ((long) pos.getX() << 8) ^ pos.getY();
    }

    @Nullable
    public IGrid getGrid() {
        return this.mainNode.getGrid();
    }

    public void updateRedstoneState() {
        if (this.unlockEvent == UnlockCraftingEvent.REDSTONE_POWER && this.getRedstoneState()) {
            this.unlockEvent = null;
            this.saveChanges();
        } else if (this.unlockEvent == UnlockCraftingEvent.REDSTONE_PULSE && !this.getRedstoneState()) {
            this.unlockEvent = UnlockCraftingEvent.REDSTONE_POWER;
            this.redstoneState = YesNo.UNDECIDED;
            this.saveChanges();
        } else {
            this.redstoneState = YesNo.UNDECIDED;
        }
    }

    private void onStackReturnedToNetwork(GenericStack genericStack) {
        if (this.unlockEvent != UnlockCraftingEvent.RESULT) {
            return;
        }

        if (this.unlockStack == null) {
            this.unlockEvent = null;
            this.saveChanges();
            return;
        }

        if (this.unlockStack.what().equals(genericStack.what())) {
            long remainingAmount = this.unlockStack.amount() - genericStack.amount();
            if (remainingAmount <= 0) {
                this.unlockEvent = null;
                this.unlockStack = null;
            } else {
                this.unlockStack = new GenericStack(this.unlockStack.what(), remainingAmount);
            }
            this.saveChanges();
        }
    }

    private void configChanged(IConfigManager manager, Setting<?> setting) {
        if (setting == Settings.LOCK_CRAFTING_MODE) {
            if (!this.resetCraftingLock()) {
                this.saveChanges();
            }
        } else {
            this.saveChanges();
        }
    }

    private boolean getRedstoneState() {
        if (this.redstoneState == YesNo.UNDECIDED) {
            TileEntity blockEntity = this.host.getTileEntity();
            World level = blockEntity.getWorld();
            this.redstoneState = level != null && level.isBlockPowered(blockEntity.getPos()) ? YesNo.YES : YesNo.NO;
        }
        return this.redstoneState == YesNo.YES;
    }

    private void clearPatternInventory(EntityPlayer player) {
        if (player.capabilities.isCreativeMode) {
            for (int i = 0; i < this.patternInventory.size(); i++) {
                this.patternInventory.setItemDirect(i, ItemStack.EMPTY);
            }
            return;
        }

        int blankPatternCount = 0;
        for (int i = 0; i < this.patternInventory.size(); i++) {
            ItemStack pattern = this.patternInventory.getStackInSlot(i);
            if (pattern.isEmpty()) {
                continue;
            }

            if (pattern.getItem() == AEItems.CRAFTING_PATTERN.item()
                || pattern.getItem() == AEItems.PROCESSING_PATTERN.item()
                || pattern.getItem() == AEItems.BLANK_PATTERN.item()) {
                blankPatternCount += pattern.getCount();
            } else if (!player.inventory.addItemStackToInventory(pattern.copy())) {
                player.dropItem(pattern.copy(), false);
            }

            this.patternInventory.setItemDirect(i, ItemStack.EMPTY);
        }

        if (blankPatternCount > 0) {
            ItemStack stack = AEItems.BLANK_PATTERN.stack(blankPatternCount);
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropItem(stack, false);
            }
        }
    }

    private record PushTarget(EnumFacing direction, PatternProviderTarget target) {
    }

    private static class PatternInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
            return PatternDetailsHelper.isEncodedPattern(stack);
        }
    }

    private class Ticker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(TickRates.Interface, !hasWorkToDo());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            boolean couldDoWork = doWork();
            if (!hasWorkToDo()) {
                return TickRateModulation.SLEEP;
            }
            return couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }
}


