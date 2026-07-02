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

package ae2.helpers.patternprovider;

import ae2.api.AECapabilities;
import ae2.api.config.Actionable;
import ae2.api.config.BlockingMode;
import ae2.api.config.LockCraftingMode;
import ae2.api.config.PatternProviderBlockingType;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.config.PatternProviderOutputSideMode;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.IPatternDetails.IInput;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.api.implementations.blockentities.IPatternProviderBatchTarget;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.BaseInternalInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.IConfigManager;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.core.settings.TickRates;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.InterfaceLogicHost;
import ae2.helpers.WirelessTerminalActions;
import ae2.helpers.patternprovider.PatternProviderMergeHelper.ExternalTarget;
import ae2.helpers.patternprovider.PatternProviderMergeHelper.TargetMatch;
import ae2.me.helpers.MachineSource;
import ae2.parts.p2p.PatternProviderP2PTunnelPart;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.PlayerInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
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
import org.jetbrains.annotations.Nullable;

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
    public static final String NBT_PENDING_SEND_LIST = "pendingSendList";
    public static final String NBT_RETURN_INV = "returnInv";
    private static final String NBT_PENDING_SEND_STACK = "stack";
    private static final String NBT_PENDING_SEND_DIRECTION = "direction";
    private static final String MEMORY_CARD_SETTINGS = "settings";
    private static final String MEMORY_CARD_PRIORITY = "priority";
    private static final String MEMORY_CARD_PATTERNS = "patterns";

    private final PatternProviderLogicHost host;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private final IConfigManager configManager;
    private final AppEngInternalInventory patternInventory;
    private final InternalInventory terminalPatternInventory = new ActivePatternInventory();
    private final IUpgradeInventory upgrades;
    private final ObjectList<IPatternDetails> patterns = new ObjectArrayList<>();
    private final ObjectSet<AEItemKey> patternKeys = new ObjectOpenHashSet<>();
    private final ObjectSet<AEKey> patternInputs = new ObjectOpenHashSet<>();
    private final ObjectList<PendingSend> pendingSendList = new ObjectArrayList<>();
    private final PatternProviderReturnInventory returnInv;
    private final PatternProviderTargetCache[] targetCaches = new PatternProviderTargetCache[6];

    private int priority;
    private YesNo redstoneState = YesNo.UNDECIDED;
    private UnlockCraftingEvent unlockEvent;
    private GenericStack unlockStack;
    private int roundRobinIndex;
    private boolean wasProviderActive;
    private boolean hasLastSuccessfulPatternHash;
    private int lastSuccessfulPatternHash;
    @Nullable
    private PendingMergePush pendingMergePush;

    public PatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host) {
        this(mainNode, host, host.getMainContainerIcon().getItem(),
            PatternProviderCapacity.getMaxPatternSlots(AEConfig.instance().getPatternProviderExpansionCardLimit()));
    }

    public PatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize) {
        this(mainNode, host, host.getMainContainerIcon().getItem(), patternInventorySize);
    }

    public PatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, Item machineType,
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
                                           .registerSetting(Settings.PATTERN_PROVIDER_INSERTION_MODE,
                                               PatternProviderInsertionMode.DEFAULT)
                                           .registerSetting(Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE,
                                               PatternProviderOutputSideMode.SINGLE_SIDE)
                                           .registerSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.YES)
                                           .registerSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE)
                                           .build();
        this.patternInventory = new AppEngInternalInventory(this, patternInventorySize, 1, new PatternInventoryFilter());
        this.upgrades = new PatternProviderUpgradeInventory(machineType,
            1 + AEConfig.instance().getPatternProviderExpansionCardLimit());
        this.returnInv = new PatternProviderReturnInventory(() -> {
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            this.host.saveChanges();
        });
    }

    private static int countItem(InventoryPlayer inventory, ItemStack needle) {
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
        this.updatePatterns();
        ICraftingProvider.requestUpdate(this.mainNode);
    }

    private boolean isPatternSlotOccupied(int slot) {
        return slot >= 0 && slot < this.patternInventory.size() && !this.patternInventory.getStackInSlot(slot).isEmpty();
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

        NBTTagList pendingSendListTag = new NBTTagList();
        for (PendingSend pendingSend : this.pendingSendList) {
            NBTTagCompound pendingSendTag = new NBTTagCompound();
            pendingSendTag.setTag(NBT_PENDING_SEND_STACK, GenericStack.writeTag(pendingSend.stack()));
            pendingSendTag.setByte(NBT_PENDING_SEND_DIRECTION, (byte) pendingSend.direction().ordinal());
            pendingSendListTag.appendTag(pendingSendTag);
        }
        tag.setTag(NBT_PENDING_SEND_LIST, pendingSendListTag);
        tag.removeTag(NBT_SEND_LIST);
        tag.removeTag(NBT_SEND_DIRECTION);

        tag.setTag(NBT_RETURN_INV, this.returnInv.writeToTag());
    }

    @Nullable
    private static EnumFacing readDirection(byte ordinal) {
        return ordinal >= 0 && ordinal < EnumFacing.VALUES.length ? EnumFacing.VALUES[ordinal] : null;
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

        this.pendingSendList.clear();
        if (tag.hasKey(NBT_PENDING_SEND_LIST, Constants.NBT.TAG_LIST)) {
            NBTTagList pendingSendListTag = tag.getTagList(NBT_PENDING_SEND_LIST, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < pendingSendListTag.tagCount(); i++) {
                NBTTagCompound pendingSendTag = pendingSendListTag.getCompoundTagAt(i);
                GenericStack stack = GenericStack.readTag(pendingSendTag.getCompoundTag(NBT_PENDING_SEND_STACK));
                EnumFacing direction = readDirection(pendingSendTag.getByte(NBT_PENDING_SEND_DIRECTION));
                if (stack != null && direction != null) {
                    this.pendingSendList.add(new PendingSend(stack, direction));
                }
            }
        } else if (tag.hasKey(NBT_SEND_LIST, Constants.NBT.TAG_LIST)) {
            NBTTagList sendListTag = tag.getTagList(NBT_SEND_LIST, Constants.NBT.TAG_COMPOUND);
            EnumFacing direction = tag.hasKey(NBT_SEND_DIRECTION, Constants.NBT.TAG_BYTE)
                ? readDirection(tag.getByte(NBT_SEND_DIRECTION))
                : null;
            for (GenericStack stack : GenericStack.readList(sendListTag)) {
                if (stack != null && direction != null) {
                    this.pendingSendList.add(new PendingSend(stack, direction));
                }
            }
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
        if (inv == this.upgrades) {
            this.onUpgradesChanged();
            return;
        }
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
        this.patternKeys.clear();
        this.patternInputs.clear();

        for (int slot = 0; slot < this.getActivePatternSlots(); slot++) {
            ItemStack stack = this.patternInventory.getStackInSlot(slot);
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, this.host.getTileEntity().getWorld());
            if (details == null || details instanceof IAssemblerPattern) {
                continue;
            }

            this.patterns.add(details);
            AEItemKey patternKey = AEItemKey.of(stack);
            if (patternKey != null) {
                this.patternKeys.add(patternKey);
            }
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
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        var basePatternDetails = PseudoPatternDetails.unwrap(patternDetails);
        if (!this.pendingSendList.isEmpty() || !this.mainNode.isActive() || !this.patterns.contains(basePatternDetails)) {
            return false;
        }

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        if (this.pendingMergePush != null && this.pendingMergePush.matches(basePatternDetails)) {
            PendingMergePush mergePush = this.pendingMergePush;
            this.pendingMergePush = null;
            return mergePush.push(inputHolder, multiplier);
        }

        this.pendingMergePush = null;
        PushTargetSet targetSet = collectPushTargets();
        if (targetSet == null) {
            return false;
        }

        for (MachinePushTarget machineTarget : targetSet.machineTargets) {
            if (machineTarget.machine.pushPattern(basePatternDetails, inputHolder, 1,
                machineTarget.ejectionDirection)) {
                onPushPatternSuccess(basePatternDetails);
                return true;
            }
        }

        if (!basePatternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        if (this.configManager.getSetting(Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE)
            == PatternProviderOutputSideMode.SPLIT_BY_INGREDIENTS_TYPE) {
            return pushPatternSplitByIngredientsType(basePatternDetails, inputHolder, targetSet.externalTargets);
        }

        return pushPatternSingleSide(basePatternDetails, inputHolder, targetSet.externalTargets);
    }

    @Override
    public boolean canMergePatternPush(IPatternDetails patternDetails) {
        var basePatternDetails = PseudoPatternDetails.unwrap(patternDetails);
        if (!canMergePatternPushBasic(basePatternDetails)) {
            return false;
        }
        if (getInsertionMode() == PatternProviderInsertionMode.PREFER_EMPTY
            && shouldUseSinglePushForPreferEmpty(basePatternDetails)) {
            return false;
        }
        return !shouldUseSinglePushForPartialExternalTarget(basePatternDetails);
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier) {
        this.pendingMergePush = null;
        var basePatternDetails = PseudoPatternDetails.unwrap(patternDetails);
        if (maxMultiplier <= 0 || !canMergePatternPushBasic(basePatternDetails)) {
            return 0;
        }

        PushTargetSet targetSet = collectPushTargets();
        if (targetSet == null) {
            return 0;
        }

        for (int i = 0; i < targetSet.machineTargets.size(); i++) {
            MachinePushTarget machineTarget = targetSet.machineTargets.get(i);
            int definitionMultiplier = machineTarget.batchTarget.getMaxPatternPushMultiplier(basePatternDetails,
                buildPatternInputHolder(basePatternDetails), maxMultiplier, machineTarget.ejectionDirection);
            if (definitionMultiplier <= 0) {
                continue;
            }
            int multiplier = Math.min(definitionMultiplier, maxMultiplier);
            this.pendingMergePush = new MachineMergePush(basePatternDetails, targetSet.machineTargets, i, multiplier);
            return multiplier;
        }

        if (!basePatternDetails.supportsPushInputsToExternalInventory()) {
            return 0;
        }

        PendingMergePush mergePush;
        if (this.configManager.getSetting(Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE)
            == PatternProviderOutputSideMode.SPLIT_BY_INGREDIENTS_TYPE) {
            mergePush = prepareSplitMergePush(basePatternDetails, targetSet.externalTargets, maxMultiplier);
        } else {
            mergePush = prepareSingleSideMergePush(basePatternDetails, targetSet.externalTargets, maxMultiplier);
        }

        if (mergePush == null || mergePush.multiplier() <= 0) {
            return 0;
        }
        this.pendingMergePush = mergePush;
        return mergePush.multiplier();
    }

    private boolean canMergePatternPushBasic(IPatternDetails patternDetails) {
        if (!this.mainNode.isActive() || !this.patterns.contains(patternDetails)
            || !this.pendingSendList.isEmpty() || getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }
        if (this.configManager.getSetting(Settings.LOCK_CRAFTING_MODE) == LockCraftingMode.LOCK_UNTIL_RESULT) {
            return false;
        }
        if (!shouldBypassBlockingFor(patternDetails)
            && this.configManager.getSetting(Settings.BLOCKING_MODE) != BlockingMode.NO) {
            return false;
        }
        return getInsertionMode() != PatternProviderInsertionMode.EMPTY_ONLY;
    }

    @Nullable
    private PushTargetSet collectPushTargets() {
        ObjectList<MachinePushTarget> machineTargets = new ObjectArrayList<>();
        ObjectList<ExternalTarget> externalTargets = new ObjectArrayList<>();
        TileEntity blockEntity = this.host.getTileEntity();
        World level = blockEntity.getWorld();
        if (level == null) {
            return null;
        }

        for (EnumFacing direction : getActiveSides()) {
            BlockPos adjacentPos = blockEntity.getPos().offset(direction);
            EnumFacing adjacentSide = direction.getOpposite();

            ICraftingMachine craftingMachine = ICraftingMachine.of(level, adjacentPos, adjacentSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                IPatternProviderBatchTarget batchTarget = getBatchTarget(level, adjacentPos, adjacentSide,
                    craftingMachine);
                machineTargets.add(new MachinePushTarget(craftingMachine, batchTarget, adjacentSide));
                continue;
            }

            PatternProviderTarget adapter = findAdapter(direction);
            if (adapter != null) {
                externalTargets.add(new ExternalTarget(direction, adapter));
            }
        }

        rearrangeRoundRobin(externalTargets);
        return new PushTargetSet(machineTargets, externalTargets);
    }

    private static IPatternProviderBatchTarget getBatchTarget(World level, BlockPos pos, EnumFacing side,
                                                              ICraftingMachine fallback) {
        TileEntity blockEntity = level.getTileEntity(pos);
        if (blockEntity != null && blockEntity.hasCapability(AECapabilities.PATTERN_PROVIDER_BATCH_TARGET, side)) {
            IPatternProviderBatchTarget batchTarget = blockEntity.getCapability(
                AECapabilities.PATTERN_PROVIDER_BATCH_TARGET, side);
            if (batchTarget != null) {
                return batchTarget;
            }
        }
        return fallback;
    }

    private boolean pushPatternSingleSide(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                          ObjectList<ExternalTarget> possibleTargets) {
        TargetMatch match = PatternProviderMergeHelper.findSinglePushTarget(possibleTargets, inputHolder,
            getInsertionMode(), target -> this.isTargetBlocked(target.target(), patternDetails));
        if (match != null) {
            ExternalTarget target = match.target();
            patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                long inserted = target.target().insert(what, amount, Actionable.MODULATE, getInsertionMode());
                if (inserted < amount) {
                    this.addToSendList(what, amount - inserted, target.direction());
                }
            });
            onPushPatternSuccess(patternDetails);
            this.saveChanges();
            this.sendStacksOut();
            this.roundRobinIndex += match.matchedTargetIndex() + 1;
            return true;
        }

        return false;
    }

    @Override
    public boolean isBusy() {
        return !this.pendingSendList.isEmpty();
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

    private boolean pushPatternSplitByIngredientsType(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                                      ObjectList<ExternalTarget> possibleTargets) {
        Reference2ObjectMap<AEKeyType, KeyCounter[]> inputsByType = splitInputsByType(inputHolder);
        Reference2ObjectMap<AEKeyType, ExternalTarget> targetsByType = new Reference2ObjectOpenHashMap<>();
        int highestMatchedTargetIndex = -1;

        for (Entry<AEKeyType, KeyCounter[]> entry : inputsByType.entrySet()) {
            TargetMatch match = PatternProviderMergeHelper.findSinglePushTarget(possibleTargets, entry.getValue(),
                getInsertionMode(), target -> this.isTargetBlocked(target.target(), patternDetails));
            if (match == null) {
                return false;
            }
            targetsByType.put(entry.getKey(), match.target());
            highestMatchedTargetIndex = Math.max(highestMatchedTargetIndex, match.matchedTargetIndex());
        }

        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            ExternalTarget target = targetsByType.get(what.getType());
            long inserted = target.target().insert(what, amount, Actionable.MODULATE, getInsertionMode());
            if (inserted < amount) {
                this.addToSendList(what, amount - inserted, target.direction());
            }
        });
        onPushPatternSuccess(patternDetails);
        this.saveChanges();
        this.sendStacksOut();
        this.roundRobinIndex += highestMatchedTargetIndex + 1;
        return true;
    }

    @Nullable
    private PendingMergePush prepareSingleSideMergePush(IPatternDetails patternDetails,
                                                        ObjectList<ExternalTarget> possibleTargets,
                                                        int maxMultiplier) {
        TargetMatch match = PatternProviderMergeHelper.findMergedSinglePushTarget(possibleTargets,
            buildPatternInputHolder(patternDetails), maxMultiplier, getInsertionMode(),
            target -> this.isTargetBlocked(target.target(), patternDetails) || shouldUseSinglePushForInsertionMode(target));
        if (match != null) {
            return new SingleTargetMergePush(patternDetails, match.target(), match.matchedTargetIndex(),
                match.multiplier());
        }
        return null;
    }

    @Nullable
    private PendingMergePush prepareSplitMergePush(IPatternDetails patternDetails,
                                                   ObjectList<ExternalTarget> possibleTargets,
                                                   int maxMultiplier) {
        Reference2ObjectMap<AEKeyType, KeyCounter[]> inputsByType = splitPatternInputsByType(patternDetails);
        Reference2ObjectMap<AEKeyType, ExternalTarget> targetsByType = new Reference2ObjectOpenHashMap<>();
        int highestMatchedTargetIndex = -1;
        int multiplier = maxMultiplier;

        for (Entry<AEKeyType, KeyCounter[]> entry : inputsByType.entrySet()) {
            TargetMatch match = PatternProviderMergeHelper.findMergedSinglePushTarget(possibleTargets, entry.getValue(),
                multiplier, getInsertionMode(), target -> this.isTargetBlocked(target.target(), patternDetails)
                    || shouldUseSinglePushForInsertionMode(target));
            if (match == null) {
                return null;
            }
            targetsByType.put(entry.getKey(), match.target());
            multiplier = Math.min(multiplier, match.multiplier());
            highestMatchedTargetIndex = Math.max(highestMatchedTargetIndex, match.matchedTargetIndex());
        }

        if (multiplier <= 0) {
            return null;
        }
        return new SplitTargetMergePush(patternDetails, targetsByType, highestMatchedTargetIndex, multiplier);
    }

    private boolean shouldUseSinglePushForInsertionMode(ExternalTarget target) {
        return getInsertionMode() == PatternProviderInsertionMode.PREFER_EMPTY && target.target().hasEmptySlots();
    }

    private boolean shouldUseSinglePushForPreferEmpty(IPatternDetails patternDetails) {
        PushTargetSet targetSet = collectPushTargets();
        if (targetSet == null || !patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        if (this.configManager.getSetting(Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE)
            == PatternProviderOutputSideMode.SPLIT_BY_INGREDIENTS_TYPE) {
            Reference2ObjectMap<AEKeyType, KeyCounter[]> inputsByType = splitInputsByType(
                buildPatternInputHolder(patternDetails));
            for (KeyCounter[] inputs : inputsByType.values()) {
                ExternalTarget target = findFirstExternalTarget(patternDetails, inputs, targetSet.externalTargets);
                if (target != null && target.target().hasEmptySlots()) {
                    return true;
                }
            }
            return false;
        }

        KeyCounter[] inputHolder = buildPatternInputHolder(patternDetails);
        ExternalTarget target = findFirstExternalTarget(patternDetails, inputHolder, targetSet.externalTargets);
        return target != null && target.target().hasEmptySlots();
    }

    private boolean shouldUseSinglePushForPartialExternalTarget(IPatternDetails patternDetails) {
        PushTargetSet targetSet = collectPushTargets();
        if (targetSet == null || !patternDetails.supportsPushInputsToExternalInventory()
            || !targetSet.machineTargets.isEmpty()) {
            return false;
        }

        if (this.configManager.getSetting(Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE)
            == PatternProviderOutputSideMode.SPLIT_BY_INGREDIENTS_TYPE) {
            Reference2ObjectMap<AEKeyType, KeyCounter[]> inputsByType = splitInputsByType(
                buildPatternInputHolder(patternDetails));
            for (KeyCounter[] inputs : inputsByType.values()) {
                TargetMatch match = PatternProviderMergeHelper.findSinglePushTarget(targetSet.externalTargets, inputs,
                    getInsertionMode(), target -> this.isTargetBlocked(target.target(), patternDetails));
                if (match == null) {
                    return false;
                }
                if (!PatternProviderMergeHelper.acceptsAllFully(match.target().target(), inputs, 1,
                    getInsertionMode())) {
                    return true;
                }
            }
            return false;
        }

        KeyCounter[] inputHolder = buildPatternInputHolder(patternDetails);
        TargetMatch match = PatternProviderMergeHelper.findSinglePushTarget(targetSet.externalTargets, inputHolder,
            getInsertionMode(), target -> this.isTargetBlocked(target.target(), patternDetails));
        return match != null && !PatternProviderMergeHelper.acceptsAllFully(match.target().target(), inputHolder, 1,
            getInsertionMode());
    }

    @Nullable
    private ExternalTarget findFirstExternalTarget(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                                   ObjectList<ExternalTarget> possibleTargets) {
        TargetMatch match = PatternProviderMergeHelper.findSinglePushTarget(possibleTargets, inputHolder,
            getInsertionMode(), target -> this.isTargetBlocked(target.target(), patternDetails));
        return match == null ? null : match.target();
    }

    private Reference2ObjectMap<AEKeyType, KeyCounter[]> splitInputsByType(KeyCounter[] inputHolder) {
        Reference2ObjectMap<AEKeyType, KeyCounter[]> result = new Reference2ObjectOpenHashMap<>();
        for (int slot = 0; slot < inputHolder.length; slot++) {
            for (Object2LongMap.Entry<AEKey> input : inputHolder[slot]) {
                KeyCounter[] perTypeInput = result.computeIfAbsent(input.getKey().getType(), ignored -> {
                    KeyCounter[] counters = new KeyCounter[inputHolder.length];
                    for (int i = 0; i < counters.length; i++) {
                        counters[i] = new KeyCounter();
                    }
                    return counters;
                });
                perTypeInput[slot].add(input.getKey(), input.getLongValue());
            }
        }
        return result;
    }

    private Reference2ObjectMap<AEKeyType, KeyCounter[]> splitPatternInputsByType(IPatternDetails patternDetails) {
        return splitInputsByType(buildPatternInputHolder(patternDetails));
    }

    private KeyCounter[] buildPatternInputHolder(IPatternDetails patternDetails) {
        IInput[] inputs = patternDetails.getInputs();
        KeyCounter[] inputHolder = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            inputHolder[slot] = new KeyCounter();
            GenericStack[] possibleInputs = inputs[slot].possibleInputs();
            if (possibleInputs.length > 0) {
                GenericStack input = possibleInputs[0];
                long amount = LongMath.saturatedMultiply(input.amount(), inputs[slot].getMultiplier());
                inputHolder[slot].add(input.what(), amount);
            }
        }
        return inputHolder;
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

    private PatternProviderInsertionMode getInsertionMode() {
        return this.configManager.getSetting(Settings.PATTERN_PROVIDER_INSERTION_MODE);
    }

    private void addToSendList(AEKey what, long amount, EnumFacing direction) {
        if (amount > 0) {
            this.pendingSendList.add(new PendingSend(new GenericStack(what, amount), direction));
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    private boolean sendStacksOut() {
        TileEntity blockEntity = this.host.getTileEntity();
        World level = blockEntity.getWorld();
        if (level == null) {
            return false;
        }

        boolean didSomething = false;
        boolean changed = false;
        for (ListIterator<PendingSend> it = this.pendingSendList.listIterator(); it.hasNext(); ) {
            PendingSend pendingSend = it.next();
            GenericStack stack = pendingSend.stack();
            PatternProviderTarget adapter = findAdapter(pendingSend.direction());
            if (adapter == null) {
                continue;
            }

            long inserted = adapter.insert(stack.what(), stack.amount(), Actionable.MODULATE, getInsertionMode());
            if (inserted >= stack.amount()) {
                it.remove();
                didSomething = true;
                changed = true;
            } else if (inserted > 0) {
                it.set(new PendingSend(new GenericStack(stack.what(), stack.amount() - inserted),
                    pendingSend.direction()));
                didSomething = true;
                changed = true;
            }
        }

        if (changed) {
            this.saveChanges();
        }

        return didSomething;
    }

    private boolean hasWorkToDo() {
        return !this.pendingSendList.isEmpty() || !this.returnInv.isEmpty();
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

    public InternalInventory getTerminalPatternInv() {
        return this.terminalPatternInventory;
    }

    public int getInstalledCapacityCards() {
        return this.upgrades.getInstalledUpgrades(AEItems.PATTERN_EXPANSION_CARD.item());
    }

    public int getActivePatternSlots() {
        return Math.min(this.patternInventory.size(),
            PatternProviderCapacity.getActivePatternSlots(this.getInstalledCapacityCards(),
                AEConfig.instance().getPatternProviderExpansionCardLimit()));
    }

    public boolean containsPattern(AEItemKey pattern) {
        return this.patternKeys.contains(pattern);
    }

    public int getPatternPageCount() {
        return PatternProviderCapacity.getPageCount(this.getActivePatternSlots());
    }

    public boolean isPatternSlotEnabled(int slot) {
        return slot >= 0 && slot < this.getActivePatternSlots();
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
            for (PendingSend pendingSend : this.pendingSendList) {
                GenericStack stack = pendingSend.stack();
                stack.what().addDrops(stack.amount(), drops, level, blockEntity.getPos());
            }
            this.returnInv.addDrops(drops, level, blockEntity.getPos());
        }
    }

    public void clearContent() {
        this.patternInventory.clear();
        this.upgrades.clear();
        this.pendingSendList.clear();
        this.returnInv.clear();
    }

    public PatternProviderReturnInventory getReturnInv() {
        return this.returnInv;
    }

    public int getMaxPatternPushMultiplierThroughP2P(PatternProviderP2PTunnelPart inputTunnel,
                                                     IPatternDetails patternDetails,
                                                     KeyCounter[] inputs,
                                                     int maxMultiplier) {
        var basePatternDetails = PseudoPatternDetails.unwrap(patternDetails);
        if (maxMultiplier <= 0 || !canMergePatternPushBasic(basePatternDetails)) {
            return 0;
        }

        for (PatternProviderP2PTunnelPart outputTunnel : inputTunnel.getOutputsInAttemptOrder()) {
            PatternProviderP2PTunnelPart.RemoteMachineTarget machineTarget = outputTunnel.findRemoteMachineTarget();
            if (machineTarget != null) {
                int multiplier = machineTarget.batchTarget().getMaxPatternPushMultiplier(basePatternDetails, inputs,
                    maxMultiplier, machineTarget.ejectionDirection());
                if (multiplier > 0) {
                    inputTunnel.planOutputForPattern(basePatternDetails, outputTunnel);
                    return Math.min(multiplier, maxMultiplier);
                }
                continue;
            }

            PatternProviderTarget externalTarget = outputTunnel.findRemoteExternalTarget(this.actionSource);
            if (externalTarget == null || !basePatternDetails.supportsPushInputsToExternalInventory()
                || isTargetBlocked(externalTarget, basePatternDetails)) {
                continue;
            }

            int multiplier = PatternProviderMergeHelper.findMaxExternalMultiplier(externalTarget, inputs,
                maxMultiplier, getInsertionMode());
            if (multiplier > 0) {
                inputTunnel.planOutputForPattern(basePatternDetails, outputTunnel);
                return multiplier;
            }
        }

        return 0;
    }

    public boolean pushPatternThroughP2P(PatternProviderP2PTunnelPart inputTunnel,
                                         IPatternDetails patternDetails,
                                         KeyCounter[] inputHolder,
                                         int multiplier) {
        var basePatternDetails = PseudoPatternDetails.unwrap(patternDetails);
        PatternProviderP2PTunnelPart plannedOutput = inputTunnel.consumePlannedOutput(basePatternDetails);
        for (PatternProviderP2PTunnelPart outputTunnel : inputTunnel.getOutputsInAttemptOrder(plannedOutput)) {
            PatternProviderP2PTunnelPart.RemoteMachineTarget machineTarget = outputTunnel.findRemoteMachineTarget();
            if (machineTarget != null) {
                int acceptedMultiplier = machineTarget.batchTarget().getMaxPatternPushMultiplier(basePatternDetails,
                    inputHolder, multiplier, machineTarget.ejectionDirection());
                if (acceptedMultiplier < multiplier) {
                    continue;
                }
                if (machineTarget.machine().pushPattern(basePatternDetails, inputHolder, multiplier,
                    machineTarget.ejectionDirection())) {
                    inputTunnel.onRemotePushSucceeded(outputTunnel);
                    return true;
                }
                continue;
            }

            PatternProviderTarget externalTarget = outputTunnel.findRemoteExternalTarget(this.actionSource);
            if (externalTarget == null || isTargetBlocked(externalTarget, basePatternDetails)
                || !basePatternDetails.supportsPushInputsToExternalInventory()
                || !PatternProviderMergeHelper.acceptsAllFully(externalTarget, inputHolder, 1, getInsertionMode())
                || inputTunnel.getSide() == null) {
                continue;
            }

            basePatternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                if (amount > 0) {
                    long inserted = externalTarget.insert(what, amount, Actionable.MODULATE, getInsertionMode());
                    if (inserted < amount) {
                        addToSendList(what, amount - inserted, inputTunnel.getSide());
                    }
                }
            });
            inputTunnel.onRemotePushSucceeded(outputTunnel);
            this.saveChanges();
            this.sendStacksOut();
            return true;
        }

        return false;
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

    private static void returnToPlayer(EntityPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.copy();
        if (!player.inventory.addItemStackToInventory(remainder) && !remainder.isEmpty()) {
            AELog.warn("Failed to return %s after %s; dropping it at the player", remainder, "memory card pattern restore");
            player.dropItem(remainder, false);
        }
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

        AppEngInternalInventory desiredPatterns = new AppEngInternalInventory(this.getActivePatternSlots());
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

            if (tryInstallPatternFromWirelessNetwork(player, desiredPattern)) {
                continue;
            }

            ++blankPatternsUsed;
            if (blankPatternsAvailable >= blankPatternsUsed) {
                if (!this.terminalPatternInventory.addItems(pattern.getDefinition().toStack()).isEmpty()) {
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

    private boolean tryInstallPatternFromWirelessNetwork(EntityPlayer player, ItemStack desiredPattern) {
        if (!(player instanceof EntityPlayerMP serverPlayer)) {
            return false;
        }
        if (!this.terminalPatternInventory.addItems(desiredPattern, true).isEmpty()) {
            return false;
        }

        ItemStack extracted = WirelessTerminalActions.extractStack(serverPlayer, desiredPattern, 1);
        if (extracted.isEmpty()) {
            return false;
        }

        ItemStack overflow = this.terminalPatternInventory.addItems(extracted);
        if (overflow.isEmpty()) {
            return true;
        }

        returnToPlayer(player, overflow);
        return false;
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
            String name = this.host.getCustomName();
            if (name != null) {
                return new PatternContainerGroup(this.host.getTerminalIcon(), new TextComponentString(name),
                    Collections.emptyList());
            }
        }

        EnumSet<EnumFacing> sides = getActiveSides();
        Set<PatternContainerGroup> groups = new ObjectLinkedOpenHashSet<>(sides.size());
        World level = blockEntity.getWorld();
        if (level != null) {
            for (EnumFacing side : sides) {
                PatternContainerGroup group = getAdjacentTerminalGroup(level, blockEntity.getPos().offset(side),
                    side.getOpposite());
                if (group != null) {
                    groups.add(group);
                }
            }
        }

        if (groups.size() == 1) {
            return groups.iterator().next();
        }

        ObjectList<ITextComponent> tooltip = ObjectLists.emptyList();
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

    @Nullable
    private static PatternContainerGroup getAdjacentTerminalGroup(World level, BlockPos pos, EnumFacing side) {
        TileEntity adjacent = level.getTileEntity(pos);
        if (adjacent instanceof IPartHost partHost) {
            IPart part = partHost.getPart(side);
            if (part instanceof PatternProviderP2PTunnelPart tunnel && !tunnel.isOutput()) {
                return tunnel.getCraftingMachineInfo();
            }
        }

        return PatternContainerGroup.fromMachine(level, pos, side);
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

    private boolean hasSamePatternInOtherSlot(InternalInventory inv, int slot, ItemStack stack) {
        AEItemKey pattern = AEItemKey.of(stack);
        if (pattern == null) {
            return false;
        }

        for (int i = 0; i < inv.size(); i++) {
            if (i == slot) {
                continue;
            }

            AEItemKey otherPattern = AEItemKey.of(inv.getStackInSlot(i));
            if (pattern.equals(otherPattern)) {
                return true;
            }
        }

        return false;
    }

    private interface PendingMergePush {
        boolean matches(IPatternDetails patternDetails);

        int multiplier();

        boolean push(KeyCounter[] inputHolder, int multiplier);
    }

    private record MachinePushTarget(ICraftingMachine machine, IPatternProviderBatchTarget batchTarget,
                                     EnumFacing ejectionDirection) {
    }

    private record PushTargetSet(ObjectList<MachinePushTarget> machineTargets,
                                 ObjectList<ExternalTarget> externalTargets) {
    }

    private record PendingSend(GenericStack stack, EnumFacing direction) {
    }

    private final class MachineMergePush implements PendingMergePush {
        private final IPatternDetails patternDetails;
        private final ObjectList<MachinePushTarget> targets;
        private final int matchedTargetIndex;
        private final int multiplier;

        private MachineMergePush(IPatternDetails patternDetails, ObjectList<MachinePushTarget> targets,
                                 int matchedTargetIndex, int multiplier) {
            this.patternDetails = patternDetails;
            this.targets = new ObjectArrayList<>(targets);
            this.matchedTargetIndex = matchedTargetIndex;
            this.multiplier = multiplier;
        }

        @Override
        public boolean matches(IPatternDetails patternDetails) {
            return this.patternDetails == patternDetails || this.patternDetails.getDefinition().equals(patternDetails.getDefinition());
        }

        @Override
        public int multiplier() {
            return this.multiplier;
        }

        @Override
        public boolean push(KeyCounter[] inputHolder, int multiplier) {
            for (int i = this.matchedTargetIndex; i < this.targets.size(); i++) {
                if (tryPushToTarget(this.targets.get(i), inputHolder, multiplier)) {
                    return true;
                }
            }

            for (int i = 0; i < this.matchedTargetIndex; i++) {
                if (tryPushToTarget(this.targets.get(i), inputHolder, multiplier)) {
                    return true;
                }
            }

            return false;
        }

        private boolean tryPushToTarget(MachinePushTarget target, KeyCounter[] inputHolder, int multiplier) {
            if (!(target.machine instanceof PatternProviderP2PTunnelPart)) {
                int acceptedMultiplier = target.batchTarget.getMaxPatternPushMultiplier(this.patternDetails,
                    inputHolder, multiplier, target.ejectionDirection);
                if (acceptedMultiplier < multiplier) {
                    return false;
                }
            }
            if (target.machine.pushPattern(this.patternDetails, inputHolder, multiplier, target.ejectionDirection)) {
                onPushPatternSuccess(this.patternDetails);
                return true;
            }
            return false;
        }
    }

    private final class SingleTargetMergePush implements PendingMergePush {
        private final IPatternDetails patternDetails;
        private final ExternalTarget target;
        private final int matchedTargetIndex;
        private final int multiplier;

        private SingleTargetMergePush(IPatternDetails patternDetails, ExternalTarget target, int matchedTargetIndex,
                                      int multiplier) {
            this.patternDetails = patternDetails;
            this.target = target;
            this.matchedTargetIndex = matchedTargetIndex;
            this.multiplier = multiplier;
        }

        @Override
        public boolean matches(IPatternDetails patternDetails) {
            return this.patternDetails == patternDetails || this.patternDetails.getDefinition().equals(patternDetails.getDefinition());
        }

        @Override
        public int multiplier() {
            return this.multiplier;
        }

        @Override
        public boolean push(KeyCounter[] inputHolder, int multiplier) {
            if (multiplier > this.multiplier) {
                return false;
            }
            if (!PatternProviderMergeHelper.acceptsAllFully(this.target.target(), inputHolder, 1,
                getInsertionMode())) {
                return false;
            }
            this.patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                if (amount > 0) {
                    this.target.target().insert(what, amount, Actionable.MODULATE, getInsertionMode());
                }
            });
            onPushPatternSuccess(this.patternDetails);
            saveChanges();
            sendStacksOut();
            roundRobinIndex += this.matchedTargetIndex + 1;
            return true;
        }
    }

    private final class SplitTargetMergePush implements PendingMergePush {
        private final IPatternDetails patternDetails;
        private final Reference2ObjectMap<AEKeyType, ExternalTarget> targetsByType;
        private final int highestMatchedTargetIndex;
        private final int multiplier;

        private SplitTargetMergePush(IPatternDetails patternDetails,
                                     Reference2ObjectMap<AEKeyType, ExternalTarget> targetsByType,
                                     int highestMatchedTargetIndex,
                                     int multiplier) {
            this.patternDetails = patternDetails;
            this.targetsByType = targetsByType;
            this.highestMatchedTargetIndex = highestMatchedTargetIndex;
            this.multiplier = multiplier;
        }

        @Override
        public boolean matches(IPatternDetails patternDetails) {
            return this.patternDetails == patternDetails || this.patternDetails.getDefinition().equals(patternDetails.getDefinition());
        }

        @Override
        public int multiplier() {
            return this.multiplier;
        }

        @Override
        public boolean push(KeyCounter[] inputHolder, int multiplier) {
            if (multiplier > this.multiplier) {
                return false;
            }
            Reference2ObjectMap<AEKeyType, KeyCounter[]> inputsByType = splitInputsByType(inputHolder);
            for (Entry<AEKeyType, KeyCounter[]> entry : inputsByType.entrySet()) {
                ExternalTarget target = this.targetsByType.get(entry.getKey());
                if (target == null || !PatternProviderMergeHelper.acceptsAllFully(target.target(), entry.getValue(),
                    1, getInsertionMode())) {
                    return false;
                }
            }
            this.patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                ExternalTarget target = this.targetsByType.get(what.getType());
                if (amount > 0) {
                    target.target().insert(what, amount, Actionable.MODULATE, getInsertionMode());
                }
            });
            onPushPatternSuccess(this.patternDetails);
            saveChanges();
            sendStacksOut();
            roundRobinIndex += this.highestMatchedTargetIndex + 1;
            return true;
        }
    }

    private class PatternInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (stack.isEmpty() || PatternProviderLogic.this.host.getTileEntity().getWorld() == null) {
                return false;
            }

            IPatternDetails details = PatternDetailsHelper.decodePattern(stack,
                PatternProviderLogic.this.host.getTileEntity().getWorld());
            return details != null
                && !(details instanceof IAssemblerPattern)
                && !hasSamePatternInOtherSlot(inv, slot, stack);
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

    private class ActivePatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            return getActivePatternSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            if (!isPatternSlotEnabled(slotIndex)) {
                return ItemStack.EMPTY;
            }
            return patternInventory.getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (isPatternSlotEnabled(slotIndex)) {
                patternInventory.setItemDirect(slotIndex, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isPatternSlotEnabled(slot) && patternInventory.isItemValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return patternInventory.getSlotLimit(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isPatternSlotEnabled(slot)) {
                return stack;
            }
            return patternInventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isPatternSlotEnabled(slot)) {
                return ItemStack.EMPTY;
            }
            return patternInventory.extractItem(slot, amount, simulate);
        }
    }

    private class PatternProviderUpgradeInventory extends AppEngInternalInventory implements IUpgradeInventory {
        private final Item item;

        PatternProviderUpgradeInventory(Item item, int slots) {
            super(PatternProviderLogic.this, slots, 1);
            this.item = item;
            this.setFilter(new UpgradeInvFilter());
        }

        @Override
        public Item getUpgradableItem() {
            return this.item;
        }

        @Override
        public int getInstalledUpgrades(Item upgradeCard) {
            int installed = 0;
            for (ItemStack stack : this) {
                if (!stack.isEmpty() && stack.getItem() == upgradeCard) {
                    installed++;
                }
            }
            return Math.min(installed, this.getMaxInstalled(upgradeCard));
        }

        @Override
        public int getMaxInstalled(Item upgradeCard) {
            return Upgrades.getMaxInstallable(upgradeCard, this.item);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            ItemStack previous = this.getStackInSlot(slot);
            if (isPatternExpansionCard(previous) && !isPatternExpansionCard(stack)
                && !PatternProviderCapacity.canRemoveCapacityCards(
                this.getInstalledUpgrades(AEItems.PATTERN_EXPANSION_CARD.item()),
                previous.getCount(),
                AEConfig.instance().getPatternProviderExpansionCardLimit(),
                PatternProviderLogic.this::isPatternSlotOccupied)) {
                return;
            }
            super.setItemDirect(slot, stack);
        }

        private boolean isPatternExpansionCard(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() == AEItems.PATTERN_EXPANSION_CARD.item();
        }

        private class UpgradeInvFilter implements IAEItemFilter {
            @Override
            public boolean allowExtract(InternalInventory inv, int slot, int amount) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (!isPatternExpansionCard(stack)) {
                    return true;
                }

                int removedCards = Math.min(amount, stack.getCount());
                return PatternProviderCapacity.canRemoveCapacityCards(
                    getInstalledCapacityCards(),
                    removedCards,
                    AEConfig.instance().getPatternProviderExpansionCardLimit(),
                    PatternProviderLogic.this::isPatternSlotOccupied);
            }

            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack itemstack) {
                var cardItem = itemstack.getItem();
                return getInstalledUpgrades(cardItem) < getMaxInstalled(cardItem);
            }
        }
    }
}


