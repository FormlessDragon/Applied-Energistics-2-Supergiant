package ae2.container.me.items;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.StorageHelper;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.implementations.PatternModifierPanel;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.PatternTermSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.container.slot.SlotBackgroundIcon;
import ae2.core.definitions.AEItems;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.parts.encoding.EncodingMode;
import ae2.parts.encoding.PatternEncodingLogic;
import ae2.parts.encoding.ProcessingPatternAmountHelper;
import ae2.util.ConfigGuiInventory;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContainerPatternEncodingTerm extends ContainerMEStorage implements PatternModifierPanel.Host {
    private static final int CRAFTING_GRID_WIDTH = 3;
    private static final int CRAFTING_GRID_HEIGHT = 3;
    private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;

    private static final String ACTION_SET_MODE = "setMode";
    private static final String ACTION_ENCODE = "encode";
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_SET_CLEAR_ON_CLOSE = "setClearOnClose";
    private static final String ACTION_SET_SUBSTITUTION = "setSubstitution";
    private static final String ACTION_SET_FLUID_SUBSTITUTION = "setFluidSubstitution";
    private static final String ACTION_CYCLE_PROCESSING_OUTPUT = "cycleProcessingOutput";
    private static final String ACTION_PROCESSING_MULTIPLY_2 = "processingMultiply2";
    private static final String ACTION_PROCESSING_MULTIPLY_3 = "processingMultiply3";
    private static final String ACTION_PROCESSING_MULTIPLY_5 = "processingMultiply5";
    private static final String ACTION_PROCESSING_DIVIDE_2 = "processingDivide2";
    private static final String ACTION_PROCESSING_DIVIDE_3 = "processingDivide3";
    private static final String ACTION_PROCESSING_DIVIDE_5 = "processingDivide5";

    private static final Container DUMMY_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
            return false;
        }
    };
    public final IntSet slotsSupportingFluidSubstitution = new IntArraySet();
    private final PatternEncodingLogic encodingLogic;
    private final FakeSlot[] craftingGridSlots = new FakeSlot[CRAFTING_GRID_SLOTS];
    private final FakeSlot[] processingInputSlots = new FakeSlot[AEProcessingPattern.MAX_INPUT_SLOTS];
    private final FakeSlot[] processingOutputSlots = new FakeSlot[AEProcessingPattern.MAX_OUTPUT_SLOTS];
    private final PatternTermSlot craftOutputSlot;
    private final RestrictedInputSlot blankPatternSlot;
    private final RestrictedInputSlot encodedPatternSlot;
    private final ConfigInventory encodedInputsInv;
    private final ConfigInventory encodedOutputsInv;
    private final PatternModifierPanel patternModifierPanel;
    @GuiSync(97)
    public EncodingMode mode;
    @GuiSync(96)
    public boolean substitute;
    @GuiSync(95)
    public boolean substituteFluids;
    @GuiSync(94)
    public YesNo autoFillPatterns = YesNo.NO;
    @GuiSync(93)
    public boolean patternModifierPanelAvailable;
    @Nullable
    private IRecipe currentRecipe;
    private boolean clearOnClose;

    public ContainerPatternEncodingTerm(InventoryPlayer ip, IPatternTerminalGuiHost host) {
        this(GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL, ip, host, true);
    }

    public ContainerPatternEncodingTerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, IPatternTerminalGuiHost host,
                                        boolean bindInventory) {
        super(guiKey, ip, host, bindInventory);
        this.encodingLogic = host.getLogic();
        this.encodedInputsInv = this.encodingLogic.getEncodedInputInv();
        this.encodedOutputsInv = this.encodingLogic.getEncodedOutputInv();
        this.mode = this.encodingLogic.getMode();
        this.substitute = this.encodingLogic.isSubstitution();
        this.substituteFluids = this.encodingLogic.isFluidSubstitution();

        ConfigGuiInventory encodedInputs = this.encodedInputsInv.createGuiWrapper();
        ConfigGuiInventory encodedOutputs = this.encodedOutputsInv.createGuiWrapper();

        for (int i = 0; i < this.craftingGridSlots.length; i++) {
            FakeSlot slot = new FakeSlot(encodedInputs, i, 0, 0);
            slot.setHideAmount(true);
            this.addSlot(this.craftingGridSlots[i] = slot, SlotSemantics.CRAFTING_GRID);
        }
        this.addSlot(this.craftOutputSlot = new PatternTermSlot(), SlotSemantics.CRAFTING_RESULT);

        for (int i = 0; i < this.processingInputSlots.length; i++) {
            this.addSlot(this.processingInputSlots[i] = new FakeSlot(encodedInputs, i, 0, 0),
                SlotSemantics.PROCESSING_INPUTS);
        }
        for (int i = 0; i < this.processingOutputSlots.length; i++) {
            this.addSlot(this.processingOutputSlots[i] = new FakeSlot(encodedOutputs, i, 0, 0),
                SlotSemantics.PROCESSING_OUTPUTS);
        }
        this.processingOutputSlots[0].setBackgroundIcon(SlotBackgroundIcon.PRIMARY_OUTPUT);

        this.addSlot(this.blankPatternSlot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.BLANK_PATTERN,
            this.encodingLogic.getBlankPatternInv(), 0), SlotSemantics.BLANK_PATTERN);
        this.addSlot(this.encodedPatternSlot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN,
            this.encodingLogic.getEncodedPatternInv(), 0), SlotSemantics.ENCODED_PATTERN);
        this.encodedPatternSlot.setStackLimit(1);

        registerClientAction(ACTION_ENCODE, Boolean.class, this::encode);
        registerClientAction(ACTION_CLEAR, this::clear);
        registerClientAction(ACTION_SET_CLEAR_ON_CLOSE, Boolean.class, this::setClearOnClose);
        registerClientAction(ACTION_SET_MODE, EncodingMode.class, this::changeMode);
        registerClientAction(ACTION_SET_SUBSTITUTION, Boolean.class, this::changeSubstitution);
        registerClientAction(ACTION_SET_FLUID_SUBSTITUTION, Boolean.class, this::changeFluidSubstitution);
        registerClientAction(ACTION_CYCLE_PROCESSING_OUTPUT, this::cycleProcessingOutput);
        registerClientAction(ACTION_PROCESSING_MULTIPLY_2,
            () -> modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation.MULTIPLY_2));
        registerClientAction(ACTION_PROCESSING_MULTIPLY_3,
            () -> modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation.MULTIPLY_3));
        registerClientAction(ACTION_PROCESSING_MULTIPLY_5,
            () -> modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation.MULTIPLY_5));
        registerClientAction(ACTION_PROCESSING_DIVIDE_2,
            () -> modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation.DIVIDE_2));
        registerClientAction(ACTION_PROCESSING_DIVIDE_3,
            () -> modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation.DIVIDE_3));
        registerClientAction(ACTION_PROCESSING_DIVIDE_5,
            () -> modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation.DIVIDE_5));
        this.patternModifierPanel = new PatternModifierPanel(this);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();

        updateSlotVisibility();
        getAndUpdateOutput();

        tryAutoFillBlankPatterns();
    }

    private static String actionForProcessingAmountOperation(ProcessingPatternAmountHelper.Operation operation) {
        return switch (operation) {
            case MULTIPLY_2 -> ACTION_PROCESSING_MULTIPLY_2;
            case MULTIPLY_3 -> ACTION_PROCESSING_MULTIPLY_3;
            case MULTIPLY_5 -> ACTION_PROCESSING_MULTIPLY_5;
            case DIVIDE_2 -> ACTION_PROCESSING_DIVIDE_2;
            case DIVIDE_3 -> ACTION_PROCESSING_DIVIDE_3;
            case DIVIDE_5 -> ACTION_PROCESSING_DIVIDE_5;
        };
    }

    private static void collectProcessingStacks(ConfigInventory inventory, List<GenericStack> stacks) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            stacks.add(inventory.getStack(slot));
        }
    }

    private static void applyProcessingAmountOperation(ConfigInventory inventory,
                                                       ProcessingPatternAmountHelper.Operation operation) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, ProcessingPatternAmountHelper.apply(inventory.getStack(slot), operation));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide()) {
            this.mode = this.encodingLogic.getMode();
            this.substitute = this.encodingLogic.isSubstitution();
            this.substituteFluids = this.encodingLogic.isFluidSubstitution();
            this.autoFillPatterns = getHost().getConfigManager().getSetting(Settings.PATTERN_AUTO_FILL);
            this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
        }
    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        updateSlotVisibility();
        getAndUpdateOutput();
    }

    @Override
    public void onSlotChange(Slot slot) {
        if (slot == this.encodedPatternSlot && isServerSide()) {
            this.broadcastChanges();
        }
        if (slot == this.blankPatternSlot || slot == this.encodedPatternSlot || isEncodingSlot(slot)) {
            getAndUpdateOutput();
        }
    }

    private boolean isEncodingSlot(@Nullable Slot slot) {
        if (slot == null) {
            return false;
        }
        for (FakeSlot craftingGridSlot : this.craftingGridSlots) {
            if (craftingGridSlot == slot) {
                return true;
            }
        }
        for (FakeSlot processingInputSlot : this.processingInputSlots) {
            if (processingInputSlot == slot) {
                return true;
            }
        }
        for (FakeSlot processingOutputSlot : this.processingOutputSlots) {
            if (processingOutputSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private void updateSlotVisibility() {
        boolean crafting = this.mode == EncodingMode.CRAFTING;
        boolean processing = this.mode == EncodingMode.PROCESSING;
        for (FakeSlot slot : this.craftingGridSlots) {
            slot.setActive(crafting);
        }
        this.craftOutputSlot.setActive(crafting);
        for (FakeSlot slot : this.processingInputSlots) {
            slot.setActive(processing);
        }
        for (FakeSlot slot : this.processingOutputSlots) {
            slot.setActive(processing);
        }
        this.patternModifierPanel.updateSlotState(this.patternModifierPanelAvailable);
    }

    private ItemStack getAndUpdateOutput() {
        if (this.mode != EncodingMode.CRAFTING) {
            this.currentRecipe = null;
            this.craftOutputSlot.setResultItem(ItemStack.EMPTY);
            this.slotsSupportingFluidSubstitution.clear();
            return ItemStack.EMPTY;
        }

        ItemStack[] ingredients = new ItemStack[CRAFTING_GRID_SLOTS];
        boolean invalidIngredient = false;
        for (int i = 0; i < ingredients.length; i++) {
            ingredients[i] = getEncodedCraftingIngredient(i);
            if (ingredients[i] == null) {
                invalidIngredient = true;
                break;
            }
        }

        if (invalidIngredient) {
            this.currentRecipe = null;
            this.craftOutputSlot.setResultItem(ItemStack.EMPTY);
            this.slotsSupportingFluidSubstitution.clear();
            return ItemStack.EMPTY;
        }

        InventoryCrafting craftingInventory = new InventoryCrafting(DUMMY_CONTAINER, CRAFTING_GRID_WIDTH,
            CRAFTING_GRID_HEIGHT);
        for (int i = 0; i < ingredients.length; i++) {
            craftingInventory.setInventorySlotContents(i, ingredients[i]);
        }

        if (this.currentRecipe == null || !this.currentRecipe.matches(craftingInventory, this.getPlayer().world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(craftingInventory, this.getPlayer().world);
            checkFluidSubstitutionSupport();
        }

        if (this.currentRecipe == null) {
            this.craftOutputSlot.setResultItem(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        ItemStack result = this.currentRecipe.getCraftingResult(craftingInventory);
        this.craftOutputSlot.setResultItem(result);
        return result;
    }

    private void checkFluidSubstitutionSupport() {
        this.slotsSupportingFluidSubstitution.clear();
        if (this.currentRecipe == null) {
            return;
        }

        ItemStack encodedPattern = encodeCraftingPattern();
        if (encodedPattern == null) {
            return;
        }

        IPatternDetails decodedPattern = PatternDetailsHelper.decodePattern(encodedPattern, this.getPlayer().world);
        if (decodedPattern instanceof AECraftingPattern craftingPattern) {
            for (int i = 0; i < craftingPattern.getSparseInputs().size(); i++) {
                if (craftingPattern.getValidFluid(i) != null) {
                    this.slotsSupportingFluidSubstitution.add(i);
                }
            }
        }
    }

    public void encode() {
        encode(false);
    }

    public void encode(boolean preferModifierAndInventory) {
        if (isClientSide()) {
            sendClientAction(ACTION_ENCODE, preferModifierAndInventory);
            return;
        }

        tryAutoFillBlankPatterns();
        ItemStack encodedPattern = encodePattern();
        if (encodedPattern == null) {
            clearPattern();
            return;
        }

        ItemStack encodedOutput = this.encodedPatternSlot.getStack();
        if (!encodedOutput.isEmpty()
            && !PatternDetailsHelper.isEncodedPattern(encodedOutput)
            && !AEItems.BLANK_PATTERN.is(encodedOutput)) {
            return;
        }

        boolean usedOutputPattern = !encodedOutput.isEmpty();
        if (encodedOutput.isEmpty()) {
            if (!consumeBlankPatternForEncoding()) {
                return;
            }
        }

        if (preferModifierAndInventory && insertEncodedPatternIntoModifierOrInventory(encodedPattern.copy())) {
            if (usedOutputPattern) {
                this.encodedPatternSlot.putStack(ItemStack.EMPTY);
            }
            return;
        }

        insertEncodedPatternIntoOutputSlot(encodedPattern.copy());
    }

    private boolean insertEncodedPatternIntoModifierOrInventory(ItemStack encodedPattern) {
        ItemStack remaining = this.patternModifierPanel.insertPattern(encodedPattern, false);
        if (remaining.isEmpty()) {
            return true;
        }

        ItemStack inventoryRemainder = remaining.copy();
        return this.getPlayerInventory().addItemStackToInventory(inventoryRemainder);
    }

    private void insertEncodedPatternIntoOutputSlot(ItemStack encodedPattern) {
        this.encodedPatternSlot.putStack(encodedPattern);
    }

    private boolean consumeBlankPatternForEncoding() {
        ItemStack blankPattern = this.blankPatternSlot.getStack().copy();
        if (AEItems.BLANK_PATTERN.is(blankPattern)) {
            blankPattern.shrink(1);
            this.blankPatternSlot.putStack(blankPattern);
            return true;
        }
        return this.patternModifierPanel.consumeBlankPattern();
    }

    private void tryAutoFillBlankPatterns() {
        if (!shouldAutoFillBlankPatterns()) {
            return;
        }

        ItemStack currentStack = this.blankPatternSlot.getStack();
        if (!currentStack.isEmpty() && !AEItems.BLANK_PATTERN.is(currentStack)) {
            return;
        }

        ItemStack blankPatternPrototype = AEItems.BLANK_PATTERN.stack(1);
        int maxStackSize = currentStack.isEmpty() ? blankPatternPrototype.getMaxStackSize()
            : currentStack.getMaxStackSize();
        int missing = maxStackSize - currentStack.getCount();
        if (missing <= 0) {
            return;
        }

        AEItemKey blankPatternKey = AEItemKey.of(blankPatternPrototype);
        if (blankPatternKey == null) {
            return;
        }

        long extracted = StorageHelper.poweredExtraction(this.energySource, this.storage, blankPatternKey, missing,
            getActionSource());
        if (extracted <= 0) {
            return;
        }

        if (currentStack.isEmpty()) {
            this.blankPatternSlot.putStack(AEItems.BLANK_PATTERN.stack((int) extracted));
            return;
        }

        ItemStack updatedStack = currentStack.copy();
        updatedStack.grow((int) extracted);
        this.blankPatternSlot.putStack(updatedStack);
    }

    private boolean shouldAutoFillBlankPatterns() {
        return isServerSide()
            && getHost().getConfigManager().getSetting(Settings.PATTERN_AUTO_FILL) == YesNo.YES;
    }

    private void clearPattern() {
        ItemStack encodedPattern = this.encodedPatternSlot.getStack();
        if (PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            this.encodedPatternSlot.putStack(AEItems.BLANK_PATTERN.stack(encodedPattern.getCount()));
        }
    }

    @Nullable
    private ItemStack encodePattern() {
        return switch (this.mode) {
            case CRAFTING -> encodeCraftingPattern();
            case PROCESSING -> encodeProcessingPattern();
        };
    }

    @Nullable
    private ItemStack encodeCraftingPattern() {
        ItemStack[] ingredients = new ItemStack[CRAFTING_GRID_SLOTS];
        boolean valid = false;
        for (int i = 0; i < ingredients.length; i++) {
            ingredients[i] = getEncodedCraftingIngredient(i);
            if (ingredients[i] == null) {
                return null;
            }
            if (!ingredients[i].isEmpty()) {
                valid = true;
            }
        }
        if (!valid) {
            return null;
        }

        ItemStack result = getAndUpdateOutput();
        if (this.currentRecipe == null || result.isEmpty()) {
            return null;
        }

        return PatternDetailsHelper.encodeCraftingPattern(this.currentRecipe, ingredients, result, isSubstitute(),
            isSubstituteFluids());
    }

    @Nullable
    private ItemStack encodeProcessingPattern() {
        GenericStack[] inputs = new GenericStack[this.encodedInputsInv.size()];
        boolean valid = false;
        for (int slot = 0; slot < this.encodedInputsInv.size(); slot++) {
            inputs[slot] = this.encodedInputsInv.getStack(slot);
            if (inputs[slot] != null) {
                valid = true;
            }
        }
        if (!valid) {
            return null;
        }

        GenericStack[] outputs = new GenericStack[this.encodedOutputsInv.size()];
        for (int slot = 0; slot < this.encodedOutputsInv.size(); slot++) {
            outputs[slot] = this.encodedOutputsInv.getStack(slot);
        }
        if (outputs[0] == null) {
            return null;
        }

        return PatternDetailsHelper.encodeProcessingPattern(Arrays.asList(inputs), Arrays.asList(outputs));
    }

    @Nullable
    private ItemStack getEncodedCraftingIngredient(int slot) {
        AEKey what = this.encodedInputsInv.getKey(slot);
        if (what == null) {
            return ItemStack.EMPTY;
        }
        if (what instanceof AEItemKey itemKey) {
            return itemKey.toStack(1);
        }
        return null;
    }

    @Override
    public void onContainerClosed(net.minecraft.entity.player.EntityPlayer player) {
        if (isServerSide() && this.clearOnClose) {
            clear();
        }
        super.onContainerClosed(player);
    }

    public void clear() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }

        this.encodedInputsInv.clear();
        this.encodedOutputsInv.clear();
        this.currentRecipe = null;
        this.craftOutputSlot.setResultItem(ItemStack.EMPTY);
        this.broadcastChanges();
    }

    public void setClearOnClose(boolean clearOnClose) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_CLEAR_ON_CLOSE, clearOnClose);
            return;
        }

        this.clearOnClose = clearOnClose;
    }

    public EncodingMode getMode() {
        return this.mode;
    }

    public void setMode(EncodingMode mode) {
        if (this.mode == mode) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_SET_MODE, mode);
        }
        changeMode(mode);
    }

    private void changeMode(EncodingMode mode) {
        this.mode = mode;
        this.currentRecipe = null;
        updateSlotVisibility();
        getAndUpdateOutput();
        if (isServerSide()) {
            this.encodingLogic.setMode(mode);
        }
    }

    public boolean isSubstitute() {
        return this.substitute;
    }

    public void setSubstitute(boolean substitute) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_SUBSTITUTION, substitute);
        }
        changeSubstitution(substitute);
    }

    private void changeSubstitution(boolean substitute) {
        this.substitute = substitute;
        if (isServerSide()) {
            this.encodingLogic.setSubstitution(substitute);
        }
        this.currentRecipe = null;
        getAndUpdateOutput();
    }

    public boolean isSubstituteFluids() {
        return this.substituteFluids;
    }

    public void setSubstituteFluids(boolean substituteFluids) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_FLUID_SUBSTITUTION, substituteFluids);
        }
        changeFluidSubstitution(substituteFluids);
    }

    public YesNo getAutoFillPatterns() {
        return this.autoFillPatterns;
    }

    public boolean isPatternModifierPanelAvailable() {
        return this.patternModifierPanelAvailable;
    }

    public PatternModifierPanel getPatternModifierPanel() {
        return this.patternModifierPanel;
    }

    @Override
    public void registerPatternModifierPanelAction(String action, Runnable runnable) {
        registerClientAction(action, runnable);
    }

    @Override
    public void sendPatternModifierPanelAction(String action) {
        sendClientAction(action);
    }

    @Override
    public void lockPatternModifierPlayerInventorySlot(int slot) {
        lockPlayerInventorySlot(slot);
    }

    public void updatePatternModifierPanelVisibleSlots(boolean visible) {
        this.patternModifierPanel.updateSlotState(visible && this.patternModifierPanelAvailable);
    }

    private void changeFluidSubstitution(boolean substituteFluids) {
        this.substituteFluids = substituteFluids;
        if (isServerSide()) {
            this.encodingLogic.setFluidSubstitution(substituteFluids);
        }
        this.currentRecipe = null;
        getAndUpdateOutput();
    }

    @Override
    protected int transferStackToContainer(ItemStack input) {
        int initialCount = input.getCount();

        if (this.blankPatternSlot.isItemValid(input)) {
            input = this.blankPatternSlot.getSlotInv().addItems(input);
            if (input.isEmpty()) {
                return initialCount;
            }
        }

        if (this.encodedPatternSlot.isItemValid(input)) {
            input = this.encodedPatternSlot.getSlotInv().addItems(input);
            if (input.isEmpty()) {
                return initialCount;
            }
        }

        int transferred = initialCount - input.getCount();
        return transferred + super.transferStackToContainer(input);
    }

    public FakeSlot[] getCraftingGridSlots() {
        return this.craftingGridSlots;
    }

    public FakeSlot[] getProcessingInputSlots() {
        return this.processingInputSlots;
    }

    public FakeSlot[] getProcessingOutputSlots() {
        return this.processingOutputSlots;
    }

    public void cycleProcessingOutput() {
        if (isClientSide()) {
            sendClientAction(ACTION_CYCLE_PROCESSING_OUTPUT);
            return;
        }
        if (this.mode != EncodingMode.PROCESSING) {
            return;
        }

        ItemStack[] newOutputs = new ItemStack[this.processingOutputSlots.length];
        Arrays.fill(newOutputs, ItemStack.EMPTY);
        for (int i = 0; i < this.processingOutputSlots.length; i++) {
            ItemStack current = this.processingOutputSlots[i].getStack();
            if (current.isEmpty()) {
                continue;
            }
            for (int j = 1; j < this.processingOutputSlots.length; j++) {
                ItemStack next = this.processingOutputSlots[(i + j) % this.processingOutputSlots.length].getStack();
                if (!next.isEmpty()) {
                    newOutputs[i] = next.copy();
                    break;
                }
            }
        }

        for (int i = 0; i < newOutputs.length; i++) {
            this.processingOutputSlots[i].putStack(newOutputs[i]);
        }
    }

    public void modifyProcessingPatternAmounts(ProcessingPatternAmountHelper.Operation operation) {
        if (isClientSide()) {
            sendClientAction(actionForProcessingAmountOperation(operation));
            return;
        }
        if (this.mode != EncodingMode.PROCESSING) {
            return;
        }

        List<GenericStack> stacks = new ArrayList<>(this.encodedInputsInv.size() + this.encodedOutputsInv.size());
        collectProcessingStacks(this.encodedInputsInv, stacks);
        collectProcessingStacks(this.encodedOutputsInv, stacks);
        if (!ProcessingPatternAmountHelper.canApply(stacks, operation)) {
            return;
        }

        applyProcessingAmountOperation(this.encodedInputsInv, operation);
        applyProcessingAmountOperation(this.encodedOutputsInv, operation);
        broadcastChanges();
    }

    public boolean canCycleProcessingOutputs() {
        if (this.mode != EncodingMode.PROCESSING) {
            return false;
        }
        long nonEmpty = 0;
        for (FakeSlot slot : this.processingOutputSlots) {
            if (!slot.getStack().isEmpty()) {
                nonEmpty++;
                if (nonEmpty > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canModifyAmountForSlot(@Nullable Slot slot) {
        if (!isProcessingPatternSlot(slot)) {
            return false;
        }
        assert slot != null;
        return slot.getHasStack();
    }

    public boolean isProcessingPatternSlot(@Nullable Slot slot) {
        if (slot == null || this.mode != EncodingMode.PROCESSING) {
            return false;
        }

        for (FakeSlot processingInputSlot : this.processingInputSlots) {
            if (processingInputSlot == slot) {
                return true;
            }
        }
        for (FakeSlot processingOutputSlot : this.processingOutputSlots) {
            if (processingOutputSlot == slot) {
                return true;
            }
        }
        return false;
    }
}
