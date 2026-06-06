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
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.client.render.crafting.AssemblerAnimationStatus;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.AEConfig;
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
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TileMolecularAssembler extends AENetworkedTile implements IUpgradeableObject, IGridTickable,
    ICraftingProvider, PatternContainer, IPriorityHost, IConfigurableObject, InternalInventoryHost {

    private static final String NBT_PATTERNS = "patterns";
    private static final String NBT_UPGRADES = "upgrades";
    private static final String NBT_PRIORITY = "priority";
    private static final String NBT_CACHED_OUTPUTS = "cachedOutputs";
    private static final String NBT_PENDING_CRAFTS = "pendingCrafts";
    private static final String NBT_MAIN_OUTPUT = "mainOutput";
    private static final String NBT_OUTPUT_BUFFER = "outputBuffer";
    private static final String NBT_CURRENT_PATTERN = "currentPattern";
    private static final String NBT_PROGRESS = "progress";
    private static final int BASE_INPUT_BUFFER_SLOTS = 9;
    private static final int OUTPUT_BUFFER_SLOTS = BASE_INPUT_BUFFER_SLOTS + 1;
    private static final int MAX_CRAFT_PROGRESS = 100;

    private static final Container NULL_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    };

    private final InventoryCrafting craftingInv = new InventoryCrafting(NULL_CONTAINER, 3, 3);
    private final IConfigManager configManager = IConfigManager.builder(this::onConfigChanged)
                                                               .registerSetting(Settings.PATTERN_ACCESS_TERMINAL,
                                                                   YesNo.YES)
                                                               .build();
    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this,
        PatternProviderCapacity.getMaxPatternSlots(AEConfig.instance().getMolecularAssemblerPatternExpansionCardLimit()),
        1,
        new PatternFilter());
    private final InternalInventory terminalPatternInventory = new ActivePatternInventory();
    private final IUpgradeInventory upgrades = new MolecularAssemblerUpgradeInventory(this);
    private final ObjectList<GenericStack> cachedOutputs = new ObjectArrayList<>();
    private final ObjectList<IAssemblerPattern> patterns = new ObjectArrayList<>();
    private final ObjectOpenHashSet<AEItemKey> patternKeys = new ObjectOpenHashSet<>();
    private final GenericStackInv outputBuffer = new GenericStackInv(this::onBufferChanged, OUTPUT_BUFFER_SLOTS);
    private final IActionSource actionSource = new MachineSource(this::getGridNode);
    private int priority;
    @Nullable
    private IAssemblerPattern currentPattern;
    private ItemStack currentPatternStack = ItemStack.EMPTY;
    @Nullable
    private GenericStack currentMainOutput;
    private int pendingCrafts;
    private boolean powered;
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

    @Override
    public void onReady() {
        super.onReady();
        if (this.world != null && !this.world.isRemote) {
            this.updatePatterns();
            this.updatePoweredState();
            this.updateSleepiness();
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
        data.setInteger(NBT_PENDING_CRAFTS, this.pendingCrafts);
        data.setTag(NBT_MAIN_OUTPUT, GenericStack.writeTag(this.currentMainOutput));
        this.outputBuffer.writeToChildTag(data, NBT_OUTPUT_BUFFER);
        data.setDouble(NBT_PROGRESS, this.progress);
        if (!this.currentPatternStack.isEmpty()) {
            data.setTag(NBT_CURRENT_PATTERN, this.currentPatternStack.writeToNBT(new NBTTagCompound()));
        } else {
            data.removeTag(NBT_CURRENT_PATTERN);
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
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.configManager.readFromNBT(data);
        this.patternInventory.readFromNBT(data, NBT_PATTERNS);
        this.upgrades.readFromNBT(data, NBT_UPGRADES);
        this.priority = data.getInteger(NBT_PRIORITY);
        this.readCachedOutputs(data);
        this.pendingCrafts = Math.max(0, data.getInteger(NBT_PENDING_CRAFTS));
        this.currentMainOutput = data.hasKey(NBT_MAIN_OUTPUT, Constants.NBT.TAG_COMPOUND)
            ? GenericStack.readTag(data.getCompoundTag(NBT_MAIN_OUTPUT))
            : null;
        this.outputBuffer.readFromChildTag(data, NBT_OUTPUT_BUFFER);
        this.progress = data.getDouble(NBT_PROGRESS);
        this.currentPatternStack = data.hasKey(NBT_CURRENT_PATTERN, Constants.NBT.TAG_COMPOUND)
            ? new ItemStack(data.getCompoundTag(NBT_CURRENT_PATTERN))
            : ItemStack.EMPTY;
        this.currentPattern = null;
        this.reboot = true;
        this.updatePatterns();
        this.recalculateCurrentPattern();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.updatePoweredState();
        ICraftingProvider.requestUpdate(this.getMainNode());
        this.updateSleepiness();
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
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.getMainNode().isActive()) {
            return TickRateModulation.SLEEP;
        }

        boolean didOutputWork = this.injectOutputBuffer();
        if (this.currentPattern == null) {
            this.updateSleepiness();
            return didOutputWork ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
        }

        if (this.reboot) {
            ticksSinceLastCall = 1;
            this.reboot = false;
        }

        int speed = this.getSpeedPerTick();
        this.progress += this.usePower(ticksSinceLastCall, speed, this.getPowerMultiplier());
        if (this.progress < MAX_CRAFT_PROGRESS) {
            return TickRateModulation.FASTER;
        }

        this.progress = 0;
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
    public List<IAssemblerPattern> getAvailablePatterns() {
        if (!this.getMainNode().isActive()) {
            return Collections.emptyList();
        }
        return this.patterns;
    }

    @Override
    public int getPatternPriority() {
        return this.priority;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
        if (!this.getMainNode().isActive()
            || !(patternDetails instanceof IAssemblerPattern assemblerPattern)
            || !this.patterns.contains(assemblerPattern)
            || multiplier <= 0) {
            return false;
        }

        if (this.pendingCrafts + multiplier > this.getParallelLimit()) {
            return false;
        }

        KeyCounter[] representativeInput = copyInputHolder(inputHolder);
        ItemStack[] sparseInputs = new ItemStack[BASE_INPUT_BUFFER_SLOTS];
        assemblerPattern.fillCraftingGrid(representativeInput, (slot, stack) -> sparseInputs[slot] = stack);
        ObjectList<GenericStack> newOutputs = collectOutputs(assemblerPattern, sparseInputs, inputHolder, multiplier);
        GenericStack newMainOutput = newOutputs.isEmpty() ? null : newOutputs.getFirst();
        if (newOutputs.isEmpty()) {
            return false;
        }
        if (this.currentPattern != null && !canMergePattern(assemblerPattern, newMainOutput, multiplier)) {
            return false;
        }
        for (KeyCounter counter : inputHolder) {
            counter.clear();
        }
        if (this.currentPattern == null) {
            this.currentPattern = assemblerPattern;
            this.currentPatternStack = patternDetails.getDefinition().toStack();
            this.currentMainOutput = new GenericStack(newMainOutput.what(), newMainOutput.amount() / multiplier);
            this.progress = 0;
            this.reboot = true;
        }
        mergeCachedOutputs(newOutputs);
        this.pendingCrafts += multiplier;
        this.saveChanges();
        this.updateSleepiness();
        return true;
    }

    @Override
    public boolean canMergePatternPush(IPatternDetails patternDetails) {
        return this.getMainNode().isActive() && patternDetails instanceof IAssemblerPattern
            && this.patterns.contains(patternDetails);
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
    public boolean isBusy() {
        return MolecularAssemblerPushLimits.isBusy(this.getParallelLimit(), this.pendingCrafts);
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
    public void setTerminalCustomName(@Nullable String name) {
        setCustomName(name);
        saveChanges();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        ITextComponent name = hasCustomName() ? getCustomName() : TextComponentItemStack.of(AEBlocks.MOLECULAR_ASSEMBLER.stack());
        return new PatternContainerGroup(
            AEItemKey.of(AEBlocks.MOLECULAR_ASSEMBLER.item()),
            name,
            Collections.emptyList());
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
        this.addGenericStackDrops(this.outputBuffer, drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.patternInventory.clear();
        this.upgrades.clear();
        this.outputBuffer.clear();
        this.clearCurrentCraft();
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

    private void updatePatterns() {
        this.patterns.clear();
        this.patternKeys.clear();
        World level = this.world;
        if (level == null) {
            return;
        }

        for (int slot = 0; slot < this.getActivePatternSlots(); slot++) {
            ItemStack stack = this.patternInventory.getStackInSlot(slot);
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
            if (!(details instanceof IAssemblerPattern pattern)) {
                continue;
            }
            this.patterns.add(pattern);
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                this.patternKeys.add(key);
            }
        }

        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    private void recalculateCurrentPattern() {
        if (this.currentPatternStack.isEmpty() || this.world == null) {
            this.clearCurrentCraft();
            return;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(this.currentPatternStack, this.world);
        if (details instanceof IAssemblerPattern assemblerPattern) {
            this.currentPattern = assemblerPattern;
        } else {
            this.clearCurrentCraft();
        }
    }

    private boolean tryCompleteCraft(int speed) {
        Objects.requireNonNull(this.currentPattern);
        int runs = this.pendingCrafts;
        if (runs <= 0) {
            return false;
        }

        ObjectList<GenericStack> results = new ObjectArrayList<>();
        for (GenericStack cachedOutput : this.cachedOutputs) {
            results.add(cachedOutput);
        }

        if (!canFitOutputs(results)) {
            return false;
        }

        this.pendingCrafts = 0;
        this.cachedOutputs.clear();
        for (GenericStack result : results) {
            this.outputBuffer.insert(result.what(), result.amount(), Actionable.MODULATE, this.actionSource);
        }

        if (this.currentMainOutput != null && this.world != null) {
            InitNetwork.sendToAllNearExcept(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), 32,
                this.world, new AssemblerAnimationPacket(this.pos, (byte) speed, this.currentMainOutput.what()));
        }
        this.injectOutputBuffer();
        return true;
    }

    private boolean canFitOutputs(List<GenericStack> results) {
        GenericStackInv simulated = new GenericStackInv(null, this.outputBuffer.size());
        simulated.readFromList(this.outputBuffer.toList());
        for (GenericStack result : results) {
            if (simulated.insert(result.what(), result.amount(), Actionable.MODULATE, this.actionSource) < result.amount()) {
                return false;
            }
        }
        return true;
    }

    private boolean injectOutputBuffer() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        return this.injectIntoNetwork(this.outputBuffer, grid.getStorageService().getInventory());
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

    private void updateSleepiness() {
        boolean previousAwake = this.awake;
        this.awake = this.currentPattern != null || !this.outputBuffer.isEmpty();
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

    private void clearCurrentCraft() {
        this.currentPattern = null;
        this.currentPatternStack = ItemStack.EMPTY;
        this.currentMainOutput = null;
        this.cachedOutputs.clear();
        this.pendingCrafts = 0;
        this.progress = 0;
    }

    private void onConfigChanged() {
        this.saveChanges();
    }

    void onUpgradesChanged() {
        this.saveChanges();
        this.updatePatterns();
    }

    boolean isPatternSlotOccupied(int slot) {
        return slot >= 0 && slot < this.patternInventory.size() && !this.patternInventory.getStackInSlot(slot).isEmpty();
    }

    private void onBufferChanged() {
        this.saveChanges();
        this.updateSleepiness();
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
        result.add(new GenericStack(outputStack.what(), outputStack.amount() * multiplier));
        KeyCounter remainders = new KeyCounter();
        var inputs = assemblerPattern.getInputs();
        for (int i = 0; i < Math.min(inputs.length, inputHolder.length); i++) {
            for (var entry : inputHolder[i]) {
                var remainder = inputs[i].getRemainingKey(entry.getKey());
                if (remainder == null) {
                    continue;
                }
                long templateAmount = getTemplateAmount(inputs[i], entry.getKey());
                if (templateAmount > 0) {
                    remainders.add(remainder, entry.getLongValue() / templateAmount);
                }
            }
        }
        for (var entry : remainders) {
            result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    private long getTemplateAmount(IPatternDetails.IInput input, AEKey key) {
        for (GenericStack possibleInput : input.possibleInputs()) {
            if (possibleInput.what().equals(key)) {
                return possibleInput.amount();
            }
        }
        return 1;
    }

    private void mergeCachedOutputs(List<GenericStack> outputs) {
        KeyCounter merged = new KeyCounter();
        for (GenericStack cachedOutput : this.cachedOutputs) {
            merged.add(cachedOutput.what(), cachedOutput.amount());
        }
        for (GenericStack output : outputs) {
            merged.add(output.what(), output.amount());
        }
        this.cachedOutputs.clear();
        for (var entry : merged) {
            this.cachedOutputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
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

    private void readCachedOutputs(NBTTagCompound data) {
        this.cachedOutputs.clear();
        if (!data.hasKey(NBT_CACHED_OUTPUTS, Constants.NBT.TAG_LIST)) {
            return;
        }

        for (GenericStack stack : GenericStack.readList(data.getTagList(NBT_CACHED_OUTPUTS, Constants.NBT.TAG_COMPOUND))) {
            if (stack != null) {
                this.cachedOutputs.add(stack);
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

    private boolean injectIntoNetwork(GenericStackInv inv, MEStorage storage) {
        boolean didSomething = false;
        boolean changed = false;
        for (int i = 0; i < inv.size(); i++) {
            GenericStack stack = inv.getStack(i);
            if (stack == null) {
                continue;
            }

            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, this.actionSource);
            if (inserted <= 0) {
                continue;
            }

            long remaining = stack.amount() - inserted;
            inv.setStack(i, remaining <= 0 ? null : new GenericStack(stack.what(), remaining));
            didSomething = true;
            changed = true;
        }

        if (changed) {
            this.saveChanges();
        }
        return didSomething;
    }

    private class PatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return TileMolecularAssembler.this.world != null
                && PatternDetailsHelper.decodePattern(stack, TileMolecularAssembler.this.world) instanceof IAssemblerPattern
                && !hasSamePatternInOtherSlot(inv, slot, stack);
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
