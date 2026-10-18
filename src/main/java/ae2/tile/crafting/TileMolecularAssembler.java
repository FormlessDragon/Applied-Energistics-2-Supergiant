package ae2.tile.crafting;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.BaseInternalInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.VersionedInternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.client.render.crafting.AssemblerAnimationStatus;
import ae2.container.ContainerConstants;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.AssemblerAnimationPacket;
import ae2.helpers.IPriorityHost;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.me.helpers.MachineSource;
import ae2.text.TextComponentItemStack;
import ae2.tile.grid.AENetworkedTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TileMolecularAssembler extends AENetworkedTile implements IUpgradeableObject, IGridTickable,
    ICraftingProvider, PatternContainer, IPriorityHost, IConfigurableObject, InternalInventoryHost {

    private static final String NBT_PATTERNS = "patterns";
    private static final String NBT_UPGRADES = "upgrades";
    private static final String NBT_PRIORITY = "priority";
    private static final String NBT_CACHED_OUTPUTS = "cachedOutputs";
    private static final String NBT_CACHED_INPUTS = "cachedInputs";
    private static final String NBT_PENDING_CRAFTS = "pendingCrafts";
    private static final String NBT_MAIN_OUTPUT = "mainOutput";
    private static final String NBT_OUTPUT_BUFFER = "outputBuffer";
    private static final String NBT_OUTPUT_TRANSFER = "outputTransfer";
    private static final String NBT_COMPLETED_OUTPUTS = "completedOutputs";
    private static final String NBT_CURRENT_PATTERN = "currentPattern";
    private static final String NBT_PROGRESS = "progress";
    private static final List<String> CRAFT_DATA_KEYS = List.of(NBT_CACHED_INPUTS, NBT_CACHED_OUTPUTS,
        NBT_PENDING_CRAFTS, NBT_MAIN_OUTPUT, NBT_CURRENT_PATTERN, NBT_PROGRESS);
    private static final int BASE_INPUT_BUFFER_SLOTS = 9;
    private static final int OUTPUT_BUFFER_SLOTS = BASE_INPUT_BUFFER_SLOTS + 1;
    private static final int MAX_CRAFT_PROGRESS = 100;


    private final InventoryCrafting craftingInv = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
    private final IConfigManager configManager = IConfigManager.builder(this::onConfigChanged)
                                                               .registerSetting(Settings.PATTERN_ACCESS_TERMINAL,
                                                                   YesNo.YES)
                                                               .build();
    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this,
        PatternProviderCapacity.getMaxPatternSlots(AEConfig.instance().getMolecularAssemblerPatternExpansionCardLimit()),
        1,
        new PatternFilter());
    private final InternalInventory terminalPatternInventory = new ActivePatternInventory();
    private long terminalContentsVersion;
    private final CraftOutputBuffer outputBuffer = new CraftOutputBuffer();
    private int indexedPatternSlots = -1;
    private final IUpgradeInventory upgrades = new MolecularAssemblerUpgradeInventory(this);
    private Object patternGeneration = new Object();
    private ObjectList<GenericStack> cachedOutputs = new ObjectArrayList<>();
    private ObjectList<GenericStack> cachedInputs = new ObjectArrayList<>();
    private ObjectList<IAssemblerPattern> patterns = new ObjectArrayList<>();
    private ObjectOpenHashSet<AEItemKey> patternKeys = new ObjectOpenHashSet<>();
    private ArrayDeque<GenericStack> completedOutputs = new ArrayDeque<>();
    private long outputBufferRevision;
    @Nullable
    private ArrayDeque<GenericStack> blockedOutputQueue;
    private long blockedOutputRevision;
    @Nullable
    private NBTBase unresolvedCompletedOutputs;
    private final IActionSource actionSource = new MachineSource(this::getGridNode);
    private int priority;
    @Nullable
    private IAssemblerPattern currentPattern;
    private ItemStack currentPatternStack = ItemStack.EMPTY;
    @Nullable
    private GenericStack currentMainOutput;
    private int pendingCrafts;
    private Object craftGeneration = new Object();
    private boolean preparingPush;
    private boolean injectingOutput;
    private boolean preparingOutputs;
    private UUID outputGeneration = UUID.randomUUID();
    @Nullable
    private NBTBase outputTransfer;
    @Nullable
    private NBTTagCompound unresolvedCraft;
    private boolean powered;
    private boolean patternsActive;
    private double progress;
    private boolean awake;
    private boolean reboot = true;
    @Nullable
    private AssemblerAnimationStatus animationStatus;

    public TileMolecularAssembler() {
        this.getMainNode()
            .setIdlePowerUsage(0.0)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IGridTickable.class, this)
            .addService(ICraftingProvider.class, this);
    }

    private static ObjectList<GenericStack> readCraftStacks(NBTTagCompound data, String key) {
        ObjectList<GenericStack> result = new ObjectArrayList<>();
        if (!data.hasKey(key)) {
            return result;
        }
        if (!(data.getTag(key) instanceof NBTTagList list)
            || list.tagCount() > 0 && list.getTagType() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid craft list " + key);
        }
        // This is durable custody, so every saved entry must be read or the entire batch retained as unresolved.
        for (int index = 0; index < list.tagCount(); index++) {
            var tag = list.getCompoundTagAt(index);
            result.add(readCraftStack(tag));
        }
        return result;
    }

    private static void requireCraftTagType(NBTTagCompound data, String key, int type) {
        if (data.hasKey(key) && !data.hasKey(key, type)) {
            throw new IllegalArgumentException("Invalid craft metadata type " + key);
        }
    }

    private static GenericStack readCraftStack(NBTTagCompound tag) {
        var what = AEKey.fromTagGeneric(tag);
        if (what == null || !tag.hasKey(GenericStack.AMOUNT_FIELD, Constants.NBT.TAG_LONG)
            || tag.getLong(GenericStack.AMOUNT_FIELD) <= 0
            || what instanceof AEItemKey itemKey && AEItems.MISSING_CONTENT.is(itemKey)) {
            throw new IllegalArgumentException("Unresolved craft resource");
        }
        return new GenericStack(what, tag.getLong(GenericStack.AMOUNT_FIELD));
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.world != null && !this.world.isRemote) {
            this.updatePatterns();
            if (this.hasPendingCraftEscrow()) {
                this.recalculateCurrentPattern();
            }
            this.updatePoweredState();
            this.updateSleepiness();
        }
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
    public void setRemoved() {
        this.patternGeneration = new Object();
        this.outputGeneration = UUID.randomUUID();
        this.craftGeneration = new Object();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.patternGeneration = new Object();
        this.outputGeneration = UUID.randomUUID();
        this.craftGeneration = new Object();
        super.onChunkUnloaded();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        this.updateSleepiness();
        return new TickingRequest(1, 1, !this.awake);
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
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.configManager.writeToNBT(data);
        this.patternInventory.writeToNBT(data, NBT_PATTERNS);
        this.upgrades.writeToNBT(data, NBT_UPGRADES);
        data.setInteger(NBT_PRIORITY, this.priority);
        this.writeCachedOutputs(data);
        this.writeCachedInputs(data);
        data.setInteger(NBT_PENDING_CRAFTS, this.pendingCrafts);
        data.setTag(NBT_MAIN_OUTPUT, GenericStack.writeTag(this.currentMainOutput));
        this.outputBuffer.writeToChildTag(data, NBT_OUTPUT_BUFFER);
        data.removeTag(NBT_COMPLETED_OUTPUTS);
        if (this.unresolvedCompletedOutputs != null) {
            data.setTag(NBT_COMPLETED_OUTPUTS, this.unresolvedCompletedOutputs.copy());
        } else if (!this.completedOutputs.isEmpty()) {
            data.setTag(NBT_COMPLETED_OUTPUTS, GenericStack.writeList(new ArrayList<>(this.completedOutputs)));
        }
        data.removeTag(NBT_OUTPUT_TRANSFER);
        if (this.outputTransfer != null) {
            data.setTag(NBT_OUTPUT_TRANSFER, this.outputTransfer.copy());
        }
        data.setDouble(NBT_PROGRESS, this.progress);
        if (!this.currentPatternStack.isEmpty()) {
            data.setTag(NBT_CURRENT_PATTERN, this.currentPatternStack.writeToNBT(new NBTTagCompound()));
        } else {
            data.removeTag(NBT_CURRENT_PATTERN);
        }
        if (this.unresolvedCraft != null) {
            for (var key : CRAFT_DATA_KEYS) {
                data.removeTag(key);
                if (this.unresolvedCraft.hasKey(key)) {
                    data.setTag(key, this.unresolvedCraft.getTag(key).copy());
                }
            }
        }
    }

    @Override
    public List<IAssemblerPattern> getAvailablePatterns() {
        if (!this.getMainNode().isOnline()) {
            return Collections.emptyList();
        }
        return this.patterns;
    }

    @Override
    public int getPatternPriority() {
        return this.priority;
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        this.patternGeneration = new Object();
        this.outputGeneration = UUID.randomUUID();
        this.outputTransfer = data.hasKey(NBT_OUTPUT_TRANSFER) ? data.getTag(NBT_OUTPUT_TRANSFER).copy() : null;
        this.completedOutputs = new ArrayDeque<>();
        this.blockedOutputQueue = null;
        this.unresolvedCompletedOutputs = null;
        try {
            this.completedOutputs = new ArrayDeque<>(readCraftStacks(data, NBT_COMPLETED_OUTPUTS));
        } catch (RuntimeException failure) {
            this.unresolvedCompletedOutputs = data.getTag(NBT_COMPLETED_OUTPUTS).copy();
            AELog.error(failure, "Retained invalid completed molecular assembler outputs at " + this.pos);
        }
        this.craftGeneration = new Object();
        super.loadTag(data);
        this.configManager.readFromNBT(data);
        this.patternInventory.readFromNBT(data, NBT_PATTERNS);
        this.upgrades.readFromNBT(data, NBT_UPGRADES);
        this.priority = data.getInteger(NBT_PRIORITY);
        this.unresolvedCraft = null;
        this.cachedInputs.clear();
        this.cachedOutputs.clear();
        this.pendingCrafts = 0;
        this.progress = 0;
        this.currentMainOutput = null;
        this.currentPatternStack = ItemStack.EMPTY;
        try {
            var inputs = readCraftStacks(data, NBT_CACHED_INPUTS);
            var outputs = readCraftStacks(data, NBT_CACHED_OUTPUTS);
            requireCraftTagType(data, NBT_PENDING_CRAFTS, Constants.NBT.TAG_INT);
            requireCraftTagType(data, NBT_PROGRESS, Constants.NBT.TAG_DOUBLE);
            requireCraftTagType(data, NBT_MAIN_OUTPUT, Constants.NBT.TAG_COMPOUND);
            requireCraftTagType(data, NBT_CURRENT_PATTERN, Constants.NBT.TAG_COMPOUND);
            int pending = data.getInteger(NBT_PENDING_CRAFTS);
            double savedProgress = data.getDouble(NBT_PROGRESS);
            var mainOutputTag = data.getCompoundTag(NBT_MAIN_OUTPUT);
            var mainOutput = mainOutputTag.isEmpty() ? null : readCraftStack(mainOutputTag);
            var patternTag = data.getCompoundTag(NBT_CURRENT_PATTERN);
            var patternStack = patternTag.isEmpty() ? ItemStack.EMPTY : new ItemStack(patternTag);
            if (pending < 0 || !Double.isFinite(savedProgress) || savedProgress < 0
                || !patternTag.isEmpty() && patternStack.isEmpty()) {
                throw new IllegalArgumentException("Invalid craft metadata");
            }
            boolean hasResources = !inputs.isEmpty() || !outputs.isEmpty();
            if (pending == 0 && hasResources
                || pending > 0 && (outputs.isEmpty() || mainOutput == null || patternStack.isEmpty())) {
                throw new IllegalArgumentException("Inconsistent pending craft metadata");
            }
            this.cachedInputs = inputs;
            this.cachedOutputs = outputs;
            this.pendingCrafts = pending;
            this.progress = savedProgress;
            this.currentMainOutput = mainOutput;
            this.currentPatternStack = patternStack;
        } catch (RuntimeException e) {
            this.unresolvedCraft = new NBTTagCompound();
            for (var key : CRAFT_DATA_KEYS) {
                if (data.hasKey(key)) {
                    this.unresolvedCraft.setTag(key, data.getTag(key).copy());
                }
            }
            AELog.warn("Molecular assembler retained unresolved craft data at %s: %s", this.pos, e.toString());
        }
        this.outputBuffer.readFromChildTag(data, NBT_OUTPUT_BUFFER);
        this.currentPattern = null;
        this.reboot = true;
        this.updatePatterns();
        this.recalculateCurrentPattern();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.outputGeneration = UUID.randomUUID();
        this.craftGeneration = new Object();
        this.updatePoweredState();
        boolean active = this.getMainNode().isOnline();
        if (active != this.patternsActive) {
            this.patternsActive = active;
            ICraftingProvider.requestUpdate(this.getMainNode());
        }
        this.updateSleepiness();
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.getMainNode().isActive()) {
            return TickRateModulation.SLEEP;
        }

        this.refillOutputBuffer();
        boolean didOutputWork = this.injectOutputBuffer();
        if (this.outputTransfer != null || this.injectingOutput || this.unresolvedCompletedOutputs != null) {
            this.updateSleepiness();
            return TickRateModulation.SLEEP;
        }
        if (this.currentPattern == null) {
            this.updateSleepiness();
            return didOutputWork ? TickRateModulation.URGENT
                : this.awake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
        }

        if (this.reboot) {
            ticksSinceLastCall = 1;
            this.reboot = false;
        }

        int speed = this.getSpeedPerTick();
        if (this.progress < MAX_CRAFT_PROGRESS) {
            this.progress += this.usePower(ticksSinceLastCall, speed, this.getPowerMultiplier());
        }
        if (this.progress < MAX_CRAFT_PROGRESS) {
            return TickRateModulation.FASTER;
        }

        if (this.tryCompleteCraft(speed)) {
            if (this.pendingCrafts <= 0) {
                this.clearCurrentCraft();
            }
            this.saveChanges();
            this.updateSleepiness();
            return this.awake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
        }

        this.updateSleepiness();
        return TickRateModulation.SLOWER;
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int maxMultiplier) {
        if (!this.canMergePatternPush(patternDetails) || maxMultiplier <= 0) {
            return 0;
        }
        return MolecularAssemblerPushLimits.maxPushMultiplier(this.getParallelLimit(), this.pendingCrafts,
            maxMultiplier);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        if (this.preparingPush || this.injectingOutput || this.preparingOutputs || this.outputTransfer != null
            || !this.completedOutputs.isEmpty() || this.unresolvedCompletedOutputs != null) {
            return false;
        }
        this.preparingPush = true;
        try {
            return preparePatternPush(patternDetails, inputHolder, multiplier);
        } finally {
            this.preparingPush = false;
        }
    }

    @Nullable
    @Override
    public IGrid getGrid() {
        return this.getMainNode().getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        return this.configManager.getSetting(Settings.PATTERN_ACCESS_TERMINAL) == YesNo.YES;
    }

    @Override
    public boolean isAssemblerPatternContainer() {
        return true;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return this.terminalPatternInventory;
    }

    public AppEngInternalInventory getPatternInventory() {
        return this.patternInventory;
    }

    @Override
    public boolean containsPattern(AEItemKey pattern) {
        return this.patternKeys.contains(pattern);
    }

    @Override
    public long getTerminalSortOrder() {
        return ((long) this.pos.getZ() << 24) ^ ((long) this.pos.getX() << 8) ^ this.pos.getY();
    }

    @Override
    public void openTerminalPatternContainerGui(EntityPlayer player) {
        GuiOpener.openGui(player, GuiIds.GuiKey.MOLECULAR_ASSEMBLER, this);
    }

    @Override
    public boolean canEditTerminalName() {
        return true;
    }

    @Override
    public boolean canModifyTerminalVisibility() {
        return true;
    }

    @Override
    public void setTerminalVisibility(boolean visible) {
        this.configManager.putSetting(Settings.PATTERN_ACCESS_TERMINAL, visible ? YesNo.YES : YesNo.NO);
        saveChanges();
    }

    @Override
    public void setTerminalCustomName(@Nullable String name) {
        setCustomName(name);
        saveChanges();
    }

    private boolean preparePatternPush(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        var generation = this.craftGeneration;
        var sourceWorld = this.world;
        if (!this.getMainNode().isActive()
            || !(patternDetails instanceof IAssemblerPattern assemblerPattern)
            || !this.patterns.contains(assemblerPattern)
            || multiplier <= 0
            || (this.currentPattern == null && this.hasPendingCraftEscrow())) {
            return false;
        }

        if (multiplier > this.getParallelLimit() - this.pendingCrafts) {
            return false;
        }

        KeyCounter[] representativeInput = copyInputHolder(inputHolder);
        ItemStack[] sparseInputs = new ItemStack[BASE_INPUT_BUFFER_SLOTS];
        assemblerPattern.fillCraftingGrid(representativeInput, (slot, stack) -> sparseInputs[slot] = stack);
        ObjectList<GenericStack> newOutputs = collectOutputs(assemblerPattern, sparseInputs, inputHolder, multiplier);
        ObjectList<GenericStack> newInputs = collectInputs(inputHolder);
        GenericStack newMainOutput = newOutputs.isEmpty() ? null : newOutputs.getFirst();
        if (newOutputs.isEmpty()) {
            return false;
        }
        if (this.currentPattern != null && !canMergePattern(assemblerPattern, newMainOutput, multiplier)) {
            return false;
        }
        var preparedInputs = prepareCombinedStacks(this.cachedInputs, newInputs);
        var preparedOutputs = prepareCombinedStacks(this.cachedOutputs, newOutputs);
        if (preparedInputs == null || preparedOutputs == null) {
            return false;
        }
        boolean startingCraft = this.currentPattern == null;
        var patternStack = startingCraft ? patternDetails.getDefinition().toStack() : this.currentPatternStack;
        var mainOutput = startingCraft
            ? new GenericStack(newMainOutput.what(), newMainOutput.amount() / multiplier) : this.currentMainOutput;

        if (!this.getMainNode().isActive() || this.world != sourceWorld || this.craftGeneration != generation) {
            return false;
        }

        // No extension calls or aggregation after transferring custody from the caller.
        this.craftGeneration = new Object();
        if (startingCraft) {
            this.currentPattern = assemblerPattern;
            this.currentPatternStack = patternStack;
            this.currentMainOutput = mainOutput;
            this.progress = 0;
            this.reboot = true;
        }
        this.cachedInputs = preparedInputs;
        this.cachedOutputs = preparedOutputs;
        this.pendingCrafts += multiplier;
        for (KeyCounter counter : inputHolder) {
            counter.clear();
        }
        this.notifyCommittedCraft();
        return true;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.saveChanges();
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openGui(player, GuiIds.GuiKey.MOLECULAR_ASSEMBLER, this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return AEBlocks.MOLECULAR_ASSEMBLER.stack();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.patternInventory) {
            this.updatePatterns();
        }
        this.saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return this.world == null || this.world.isRemote;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isActive() {
        return this.powered;
    }

    public int getCraftingProgress() {
        return (int) this.progress;
    }

    @Override
    public boolean canMergePatternPush(IPatternDetails patternDetails) {
        return this.unresolvedCraft == null && this.outputTransfer == null && !this.injectingOutput && !this.preparingOutputs
            && this.completedOutputs.isEmpty() && this.unresolvedCompletedOutputs == null
            && this.getMainNode().isActive() && patternDetails instanceof IAssemblerPattern
            && this.patterns.contains(patternDetails);
    }

    @Override
    public boolean isBusy() {
        return this.unresolvedCraft != null || this.outputTransfer != null || this.injectingOutput || this.preparingOutputs
            || !this.completedOutputs.isEmpty() || this.unresolvedCompletedOutputs != null
            || MolecularAssemblerPushLimits.isBusy(this.getParallelLimit(), this.pendingCrafts);
    }

    @Nullable
    public AssemblerAnimationStatus getAnimationStatus() {
        return this.animationStatus;
    }

    public void setAnimationStatus(@Nullable AssemblerAnimationStatus animationStatus) {
        this.animationStatus = animationStatus;
    }

    public int getInstalledCapacityCards() {
        return this.upgrades.getInstalledUpgrades(AEItems.PATTERN_EXPANSION_CARD.item());
    }

    public int getActivePatternSlots() {
        return Math.min(this.patternInventory.size(),
            PatternProviderCapacity.getActivePatternSlots(this.getInstalledCapacityCards(),
                AEConfig.instance().getMolecularAssemblerPatternExpansionCardLimit()));
    }

    public boolean isPatternSlotEnabled(int slot) {
        return slot >= 0 && slot < this.getActivePatternSlots();
    }

    public int getPatternPageCount() {
        return PatternProviderCapacity.getPageCount(this.getActivePatternSlots());
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        ITextComponent name = hasCustomName()
            ? new TextComponentString(Objects.requireNonNull(getCustomName()))
            : TextComponentItemStack.of(AEBlocks.MOLECULAR_ASSEMBLER.stack());
        return new PatternContainerGroup(
            AEItemKey.of(AEBlocks.MOLECULAR_ASSEMBLER.item()),
            name,
            Collections.emptyList());
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (ItemStack pattern : this.patternInventory) {
            if (!pattern.isEmpty()) {
                drops.add(pattern.copy());
            }
        }
        for (ItemStack upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
        if (this.unresolvedCraft != null) {
            var recovery = AEItems.MISSING_CONTENT.stack();
            var recoveryTag = new NBTTagCompound();
            recoveryTag.setTag("assembler_pending_craft", this.unresolvedCraft.copy());
            recoveryTag.setString("missing_content_error", "Unresolved molecular assembler craft; original data retained");
            recovery.setTagCompound(recoveryTag);
            drops.add(recovery);
        } else {
            this.addGenericStackDrops(this.cachedInputs, drops);
        }
        if (this.outputTransfer != null) {
            var recovery = AEItems.MISSING_CONTENT.stack();
            var tag = new NBTTagCompound();
            tag.setTag("assembler_output_transfer", this.outputTransfer.copy());
            tag.setString("missing_content_error", "Unconfirmed assembler output transfer; do not automatically refund");
            recovery.setTagCompound(tag);
            drops.add(recovery);
        }
        if (this.unresolvedCompletedOutputs != null) {
            var recovery = AEItems.MISSING_CONTENT.stack();
            var tag = new NBTTagCompound();
            tag.setTag("assembler_completed_outputs", this.unresolvedCompletedOutputs.copy());
            tag.setString("missing_content_error", "Invalid completed assembler outputs; original data retained");
            recovery.setTagCompound(tag);
            drops.add(recovery);
        } else {
            this.addGenericStackDrops(this.completedOutputs, drops);
        }
        this.addGenericStackDrops(this.outputBuffer, drops);
    }

    @Override
    public void clearContent() {
        this.patternGeneration = new Object();
        this.outputGeneration = UUID.randomUUID();
        this.outputTransfer = null;
        this.completedOutputs.clear();
        this.blockedOutputQueue = null;
        this.unresolvedCompletedOutputs = null;
        super.clearContent();
        this.patternInventory.clear();
        this.upgrades.clear();
        this.outputBuffer.clear();
        this.clearCurrentCraft();
    }

    private void updatePatterns() {
        var generation = new Object();
        this.patternGeneration = generation;
        this.craftGeneration = new Object();
        World level = this.world;
        int activeSlots = this.getActivePatternSlots();
        var stacks = new ItemStack[activeSlots];
        for (int slot = 0; slot < activeSlots; slot++) {
            stacks[slot] = this.patternInventory.getStackInSlot(slot).copy();
        }
        ObjectList<IAssemblerPattern> preparedPatterns = new ObjectArrayList<>();
        var preparedKeys = new ObjectOpenHashSet<AEItemKey>();
        if (level != null) {
            for (var stack : stacks) {
                if (this.patternGeneration != generation || this.world != level) {
                    return;
                }
                try {
                    AEItemKey key = AEItemKey.of(stack);
                    IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
                    if (details instanceof IAssemblerPattern pattern) {
                        preparedPatterns.add(pattern);
                        if (key != null) {
                            preparedKeys.add(key);
                        }
                    }
                } catch (RuntimeException failure) {
                    AELog.error(failure, "Failed to decode molecular assembler pattern at " + this.pos);
                }
            }
        }
        if (this.patternGeneration != generation || this.world != level) {
            return;
        }
        // No extension calls between publishing the complete list and its matching identity index.
        this.patterns = preparedPatterns;
        this.patternKeys = preparedKeys;
        this.indexedPatternSlots = activeSlots;
        this.terminalContentsVersion++;
        if (level != null) {
            ICraftingProvider.requestUpdate(this.getMainNode());
        }
    }

    private void recalculateCurrentPattern() {
        if (this.unresolvedCraft != null) {
            this.preserveCurrentCraftForRecovery();
            return;
        }
        if (this.currentPatternStack.isEmpty() || this.world == null) {
            if (this.hasPendingCraftEscrow()) {
                this.preserveCurrentCraftForRecovery();
            } else {
                this.clearCurrentCraft();
            }
            return;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(this.currentPatternStack, this.world);
        if (details instanceof IAssemblerPattern assemblerPattern) {
            this.currentPattern = assemblerPattern;
        } else {
            this.preserveCurrentCraftForRecovery();
        }
    }

    private boolean tryCompleteCraft(int speed) {
        if (this.unresolvedCraft != null || this.outputTransfer != null || this.injectingOutput || this.preparingOutputs
            || !this.completedOutputs.isEmpty() || this.unresolvedCompletedOutputs != null) {
            return false;
        }
        var generation = this.craftGeneration;
        var outputGeneration = this.outputGeneration;
        long bufferRevision = this.outputBufferRevision;
        Objects.requireNonNull(this.currentPattern);
        int runs = this.pendingCrafts;
        if (runs <= 0) {
            return false;
        }

        var preparedOutputs = prepareOutputs(this.cachedOutputs);
        if (this.craftGeneration != generation || !this.outputGeneration.equals(outputGeneration)
            || this.outputBufferRevision != bufferRevision) {
            return false;
        }

        var completedOutput = this.currentMainOutput;
        this.outputBuffer.publish(preparedOutputs, true);

        if (completedOutput != null && this.world != null) {
            InitNetwork.sendToAllNearExcept(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), 32,
                this.world, new AssemblerAnimationPacket(this.pos, (byte) speed, completedOutput.what()));
        }
        this.injectOutputBuffer();
        return true;
    }

    private PreparedOutputs prepareOutputs(Iterable<GenericStack> results) {
        this.preparingOutputs = true;
        try {
            GenericStackInv simulated = new GenericStackInv(null, this.outputBuffer.size());
            for (int slot = 0; slot < this.outputBuffer.size(); slot++) {
                simulated.setStack(slot, this.outputBuffer.getStack(slot));
            }
            var remaining = new ArrayDeque<GenericStack>();
            boolean moved = false;
            for (var result : results) {
                long inserted = simulated.insert(result.what(), result.amount(), Actionable.MODULATE, this.actionSource);
                moved |= inserted > 0;
                if (inserted < result.amount()) {
                    remaining.addLast(new GenericStack(result.what(), result.amount() - inserted));
                }
            }
            return new PreparedOutputs(simulated, remaining, moved);
        } finally {
            this.preparingOutputs = false;
        }
    }

    private int usePower(int ticksPassed, int bonusValue, double acceleratorTax) {
        IGrid grid = this.getMainNode().getGrid();
        if (grid != null) {
            return (int) (grid.getEnergyService().extractAEPower(ticksPassed * bonusValue * acceleratorTax,
                Actionable.MODULATE, PowerMultiplier.CONFIG) / acceleratorTax);
        }
        return 0;
    }

    private int getSpeedPerTick() {
        return switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item())) {
            case 1 -> 13;
            case 2 -> 17;
            case 3 -> 20;
            case 4 -> 25;
            case 5 -> 50;
            default -> 10;
        };
    }

    private double getPowerMultiplier() {
        return switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item())) {
            case 1 -> 1.3;
            case 2 -> 1.7;
            case 3 -> 2.0;
            case 4 -> 2.5;
            case 5 -> 5.0;
            default -> 1.0;
        };
    }

    private int getParallelLimit() {
        return MolecularAssemblerPushLimits.parallelLimit(
            this.upgrades.getInstalledUpgrades(AEItems.PARALLEL_CARD.item()));
    }

    private void refillOutputBuffer() {
        if (this.completedOutputs.isEmpty() || this.injectingOutput || this.preparingOutputs || this.outputTransfer != null
            || this.unresolvedCompletedOutputs != null) {
            return;
        }
        var generation = this.outputGeneration;
        var queue = this.completedOutputs;
        long revision = this.outputBufferRevision;
        if (this.blockedOutputQueue == queue && this.blockedOutputRevision == revision) {
            return;
        }
        var prepared = prepareOutputs(queue);
        if (this.outputGeneration.equals(generation) && this.completedOutputs == queue
            && this.outputBufferRevision == revision && this.outputTransfer == null && !this.injectingOutput) {
            if (prepared.moved()) {
                this.outputBuffer.publish(prepared, false);
            } else {
                // The frozen queue cannot fit this exact buffer; retry after a real content change.
                this.blockedOutputQueue = queue;
                this.blockedOutputRevision = revision;
            }
        }
    }

    private void updatePoweredState() {
        if (this.world == null || this.world.isRemote) {
            return;
        }

        IGrid grid = this.getMainNode().getGrid();
        boolean newPowered = grid != null && this.getMainNode().isPowered()
            && grid.getEnergyService().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001;
        if (this.powered != newPowered) {
            this.powered = newPowered;
            this.markForUpdate();
        }
    }

    private boolean injectOutputBuffer() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        return this.injectIntoNetwork(grid.getStorageService().getInventory());
    }

    private void updateSleepiness() {
        // An offer temporarily leaves the buffer, but this synchronous call is still active.
        // Publish scheduling only after its receipt or unknown outcome has been settled.
        if (this.injectingOutput) {
            return;
        }
        boolean previousAwake = this.awake;
        this.awake = this.outputTransfer == null && this.unresolvedCompletedOutputs == null
            && (this.currentPattern != null || !this.outputBuffer.isEmpty() || !this.completedOutputs.isEmpty());
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

    private void clearCurrentCraft() {
        this.craftGeneration = new Object();
        this.unresolvedCraft = null;
        this.currentPattern = null;
        this.currentPatternStack = ItemStack.EMPTY;
        this.currentMainOutput = null;
        this.cachedOutputs.clear();
        this.cachedInputs.clear();
        this.pendingCrafts = 0;
        this.progress = 0;
    }

    private boolean hasPendingCraftEscrow() {
        return this.unresolvedCraft != null || this.pendingCrafts > 0
            || !this.cachedInputs.isEmpty() || !this.cachedOutputs.isEmpty();
    }

    private void onConfigChanged() {
        this.saveChanges();
    }

    private void preserveCurrentCraftForRecovery() {
        this.craftGeneration = new Object();
        this.currentPattern = null;
        this.updateSleepiness();
    }

    boolean isPatternSlotOccupied(int slot) {
        return slot >= 0 && slot < this.patternInventory.size() && !this.patternInventory.getStackInSlot(slot).isEmpty();
    }

    void onUpgradesChanged() {
        // Speed and parallelism changes invalidate a prepared push, but do not alter pattern definitions.
        this.craftGeneration = new Object();
        if (this.indexedPatternSlots != this.getActivePatternSlots()) {
            this.updatePatterns();
        }
        this.saveChanges();
    }

    private void onBufferChanged() {
        this.outputBufferRevision++;
        this.blockedOutputQueue = null;
        this.notifyCommittedCraft();
    }

    private void notifyCommittedCraft() {
        // Custody has already changed. A notification failure must not hide the successful resource receipt.
        try {
            this.saveChanges();
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to mark committed molecular assembler state dirty at " + this.pos);
        }
        this.notifyCraftScheduling();
    }

    private boolean canMergePattern(IAssemblerPattern assemblerPattern, GenericStack newMainOutput, int multiplier) {
        if (this.currentPattern == null || this.pendingCrafts >= this.getParallelLimit()) {
            return false;
        }
        if (this.currentMainOutput == null || newMainOutput == null) {
            return false;
        }
        if (!this.currentPattern.getDefinition().equals(assemblerPattern.getDefinition())) {
            return false;
        }

        return this.currentMainOutput.what().equals(newMainOutput.what())
            && this.currentMainOutput.amount() == newMainOutput.amount() / multiplier;
    }

    private void notifyCraftScheduling() {
        try {
            this.updateSleepiness();
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to schedule committed molecular assembler state at " + this.pos);
        }
    }

    private long getTemplateAmount(IPatternDetails.IInput input, AEKey key) {
        for (GenericStack possibleInput : input.possibleInputs()) {
            if (possibleInput.what().equals(key)) {
                return possibleInput.amount();
            }
        }
        return 1;
    }

    private ObjectList<GenericStack> collectOutputs(IAssemblerPattern assemblerPattern, ItemStack[] sparseInputs,
                                                    KeyCounter[] inputHolder, int multiplier) {
        for (int slot = 0; slot < sparseInputs.length; slot++) {
            this.craftingInv.setInventorySlotContents(slot,
                sparseInputs[slot] == null ? ItemStack.EMPTY : sparseInputs[slot]);
        }
        ItemStack output = assemblerPattern.assemble(this.craftingInv, this.world);
        GenericStack outputStack = GenericStack.fromItemStack(output);
        if (outputStack == null) {
            return new ObjectArrayList<>();
        }
        ObjectList<GenericStack> result = new ObjectArrayList<>();
        if (outputStack.amount() <= 0 || outputStack.amount() > Long.MAX_VALUE / multiplier) {
            return result;
        }
        result.add(new GenericStack(outputStack.what(), outputStack.amount() * multiplier));
        var inputs = assemblerPattern.getInputs();
        for (int i = 0; i < Math.min(inputs.length, inputHolder.length); i++) {
            for (var entry : inputHolder[i]) {
                var remainder = inputs[i].getRemainingKey(entry.getKey());
                if (remainder == null) {
                    continue;
                }
                long templateAmount = getTemplateAmount(inputs[i], entry.getKey());
                if (templateAmount > 0) {
                    long remainingAmount = entry.getLongValue() / templateAmount;
                    if (remainingAmount > 0) {
                        result.add(new GenericStack(remainder, remainingAmount));
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    private ObjectList<GenericStack> prepareCombinedStacks(List<GenericStack> existing,
                                                           List<GenericStack> additions) {
        var amounts = new AEKey2LongMap.OpenHashMap();
        for (var stacks : List.of(existing, additions)) {
            for (var stack : stacks) {
                long amount = stack.amount();
                if (amount <= 0) {
                    return null;
                }
                long previous = amounts.addTo(stack.what(), amount);
                if (previous > Long.MAX_VALUE - amount) {
                    return null;
                }
            }
        }
        ObjectList<GenericStack> result = new ObjectArrayList<>(amounts.size());
        for (var entry : amounts.object2LongEntrySet()) {
            result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    private KeyCounter[] copyInputHolder(KeyCounter[] inputHolder) {
        KeyCounter[] copy = new KeyCounter[inputHolder.length];
        for (int i = 0; i < inputHolder.length; i++) {
            copy[i] = new KeyCounter();
            copy[i].addAll(inputHolder[i]);
        }
        return copy;
    }

    private void writeCachedOutputs(NBTTagCompound data) {
        if (this.cachedOutputs.isEmpty()) {
            data.removeTag(NBT_CACHED_OUTPUTS);
            return;
        }

        data.setTag(NBT_CACHED_OUTPUTS, GenericStack.writeList(this.cachedOutputs));
    }

    private ObjectList<GenericStack> collectInputs(KeyCounter[] inputHolder) {
        ObjectList<GenericStack> result = new ObjectArrayList<>();
        for (var counter : inputHolder) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return result;
    }

    private void writeCachedInputs(NBTTagCompound data) {
        if (this.cachedInputs.isEmpty()) {
            data.removeTag(NBT_CACHED_INPUTS);
            return;
        }

        data.setTag(NBT_CACHED_INPUTS, GenericStack.writeList(this.cachedInputs));
    }

    private void addGenericStackDrops(Iterable<GenericStack> stacks, List<ItemStack> drops) {
        for (var stack : stacks) {
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, this.world, this.pos);
            }
        }
    }

    private boolean injectIntoNetwork(MEStorage storage) {
        if (this.injectingOutput || this.preparingOutputs || this.outputTransfer != null || this.unresolvedCompletedOutputs != null) {
            return false;
        }
        this.injectingOutput = true;
        boolean didSomething = false;
        try {
            var generation = this.outputGeneration;
            for (int slot = 0; slot < this.outputBuffer.size(); slot++) {
                GenericStack stack = this.outputBuffer.getStack(slot);
                if (stack == null) {
                    continue;
                }

                // Preserve the exact offered amount before any external mutation. The target token is diagnostic
                // for this runtime call, not a durable receipt issued by the target storage.
                var transfer = new NBTTagCompound();
                transfer.setInteger("schema", 2);
                transfer.setUniqueId("id", UUID.randomUUID());
                transfer.setUniqueId("sourceGeneration", generation);
                transfer.setString("targetClass", storage.getClass().getName());
                transfer.setInteger("targetInstance", System.identityHashCode(storage));
                transfer.setInteger("slot", slot);
                transfer.setString("state", "held");
                transfer.setTag("offered", GenericStack.writeTag(stack));
                if (!this.outputGeneration.equals(generation) || this.outputBuffer.getStack(slot) != stack) {
                    return didSomething;
                }
                long bufferRevision = this.outputBufferRevision;
                this.outputTransfer = transfer;
                this.outputBuffer.setTransferredSlot(slot, null);
                this.notifyCommittedCraft();
                if (!this.outputGeneration.equals(generation) || this.outputTransfer != transfer
                    || this.outputBufferRevision != bufferRevision) {
                    return didSomething;
                }

                // There is no callback between this transition and the target call. A snapshot taken
                // by the earlier save notification records held resources; one taken by the target
                // records an unconfirmed outcome. Neither persisted phase authorizes automatic replay.
                transfer.setString("state", "unconfirmed");
                final long inserted;
                try {
                    inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, this.actionSource);
                } catch (RuntimeException failure) {
                    AELog.error(failure, "Unconfirmed molecular assembler output transfer at " + this.pos);
                    return didSomething;
                }
                if (inserted < 0 || inserted > stack.amount()) {
                    AELog.warn("Invalid molecular assembler output receipt at %s: offered=%d, inserted=%d",
                        this.pos, stack.amount(), inserted);
                    return didSomething;
                }
                if (!this.outputGeneration.equals(generation) || this.outputTransfer != transfer
                    || this.outputBufferRevision != bufferRevision) {
                    // A lifecycle or inventory callback now owns the recovery record. Never write into the replacement state.
                    return didSomething;
                }

                long remaining = stack.amount() - inserted;
                this.outputBuffer.setTransferredSlot(slot,
                    remaining == 0 ? null : new GenericStack(stack.what(), remaining));
                this.outputTransfer = null;
                didSomething |= inserted > 0;
                if (inserted > 0) {
                    this.outputBufferRevision++;
                    this.blockedOutputQueue = null;
                }
                this.notifyCommittedCraft();
                if (!this.outputGeneration.equals(generation) || this.outputTransfer != null) {
                    return didSomething;
                }
            }
            return didSomething;
        } finally {
            this.injectingOutput = false;
            if (this.outputTransfer != null) {
                this.notifyCommittedCraft();
            } else {
                this.notifyCraftScheduling();
            }
        }
    }

    private void addGenericStackDrops(GenericStackInv inv, List<ItemStack> drops) {
        for (int i = 0; i < inv.size(); i++) {
            GenericStack stack = inv.getStack(i);
            if (stack != null && this.world != null) {
                stack.what().addDrops(stack.amount(), drops, this.world, this.pos);
            }
        }
    }

    private record PreparedOutputs(GenericStackInv buffer, ArrayDeque<GenericStack> remaining, boolean moved) {
    }

    public IGridNode getGridNode() {
        return this.getMainNode().getNode();
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

    private final class CraftOutputBuffer extends GenericStackInv {
        private CraftOutputBuffer() {
            super(TileMolecularAssembler.this::onBufferChanged, OUTPUT_BUFFER_SLOTS);
        }

        private void setTransferredSlot(int slot, @Nullable GenericStack stack) {
            this.stacks[slot] = stack;
        }

        private void publish(PreparedOutputs prepared, boolean finishCraft) {
            // Publish both output owners before any listener; completed work never refunds its inputs.
            for (int slot = 0; slot < this.stacks.length; slot++) {
                this.stacks[slot] = prepared.buffer().getStack(slot);
            }
            completedOutputs = prepared.remaining();
            if (finishCraft) {
                clearCurrentCraft();
            }
            onChange();
        }
    }

    private class PatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return TileMolecularAssembler.this.world != null
                && PatternDetailsHelper.decodePattern(stack, TileMolecularAssembler.this.world) instanceof IAssemblerPattern
                && !hasSamePatternInOtherSlot(inv, slot, stack);
        }
    }

    private class ActivePatternInventory extends BaseInternalInventory implements VersionedInternalInventory {
        @Override
        public long getContentsVersion() {
            return terminalContentsVersion;
        }

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
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isPatternSlotEnabled(slot)) {
                return ItemStack.EMPTY;
            }
            return super.extractItem(slot, amount, simulate);
        }
    }
}
