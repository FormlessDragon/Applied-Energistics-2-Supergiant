package ae2.container.me.patternencode;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.StorageHelper;
import ae2.container.GuiIds;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.implementations.PatternModifierPanel;
import ae2.container.me.patternaccess.PatternAccessSupport;
import ae2.container.me.patternencode.PatternProviderSelectionSupport.ProcessingPatternUploadPreparation;
import ae2.container.me.patternencode.PatternProviderSelectionSupport.ProcessingPatternUploadResult;
import ae2.container.me.patternencode.PatternProviderSelectionSupport.ProviderDirectoryEntry;
import ae2.container.me.patternencode.PatternProviderSelectionSupport.ProviderMappingValidationResult;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.PatternTermSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.container.slot.SlotBackgroundIcon;
import ae2.core.definitions.AEItems;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.network.clientbound.ProviderDirectoryPagePacket;
import ae2.core.network.clientbound.ProviderMappingPagePacket;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.BindResult;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.crafting.pattern.RecipeTypeUid;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.parts.encoding.EncodingMode;
import ae2.parts.encoding.PatternEncodingLogic;
import ae2.parts.encoding.ProcessingPatternAmountHelper;
import ae2.util.ConfigGuiInventory;
import ae2.util.ConfigInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ContainerPatternEncodingTerm extends ContainerMEStorage
    implements PatternModifierPanel.Host, IPatternProviderSelection {
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
    private static final String ACTION_CLEAR_PROCESSING_SECONDARY_OUTPUTS = "clearProcessingSecondaryOutputs";
    private static final String ACTION_PROCESSING_MULTIPLY_2 = "processingMultiply2";
    private static final String ACTION_PROCESSING_MULTIPLY_3 = "processingMultiply3";
    private static final String ACTION_PROCESSING_MULTIPLY_5 = "processingMultiply5";
    private static final String ACTION_PROCESSING_DIVIDE_2 = "processingDivide2";
    private static final String ACTION_PROCESSING_DIVIDE_3 = "processingDivide3";
    private static final String ACTION_PROCESSING_DIVIDE_5 = "processingDivide5";
    private static final String ACTION_RENAME_PROCESSING_PATTERN_ITEM = "renameProcessingPatternItem";
    private static final String ACTION_SET_HEI_PROCESSING_RECIPE = "setHeiProcessingRecipe";
    private static final String ACTION_UPLOAD_PATTERN = "uploadPattern";
    private static final String ACTION_SET_PATTERN_MODIFIER_PANEL_VISIBLE = "setPatternModifierPanelVisible";
    private static final String ACTION_UPLOAD_PROCESSING_PATTERN_TO_PROVIDER = "uploadProcessingPatternToProvider";
    private static final String ACTION_BIND_PROVIDER_MAPPING = "bindProviderMapping";
    private static final String ACTION_BIND_AND_UPLOAD_PROVIDER_MAPPING = "bindAndUploadProviderMapping";
    private static final String ACTION_UNBIND_PROVIDER_MAPPING = "unbindProviderMapping";
    private static final String ACTION_REQUEST_PROVIDER_DIRECTORY_PAGE = "requestProviderDirectoryPage";
    private static final String ACTION_REQUEST_PROVIDER_MAPPING_PAGE = "requestProviderMappingPage";
    private static final String ACTION_RELOAD_ALL_CURRENT_PROVIDERS = "reloadAllCurrentProviders";
    private static final int MAX_RENAME_PROCESSING_PATTERN_ITEM_PAYLOAD_LENGTH = 512;
    private static final int MAX_SET_HEI_PROCESSING_RECIPE_PAYLOAD_LENGTH = 16384;
    // Gson may expand one UTF-16 code unit to six JSON characters.
    static final int MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH =
        128 + RecipeTypeUid.MAX_UTF16_LENGTH * 6;
    // Fixed JSON syntax, field names and the longest decimal forms in a valid focused request.
    private static final int MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_FIXED_JSON_LENGTH = 158;
    private static final int MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_PAYLOAD_LENGTH =
        MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_FIXED_JSON_LENGTH
            + ProviderPageLimits.MAX_QUERY_UTF16_LENGTH * 6;
    private static final int MAX_PROVIDER_MAPPING_PAGE_REQUEST_PAYLOAD_LENGTH = 128;
    private static final int PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private static final int MAX_HEI_PROCESSING_RECIPE_INPUT_SLOTS = AEProcessingPattern.MAX_INPUT_SLOTS;
    private static final int MAX_HEI_PROCESSING_RECIPE_CANDIDATES_PER_SLOT = 256;
    private static final int MAX_HEI_PROCESSING_RECIPE_TOTAL_CANDIDATES =
        MAX_HEI_PROCESSING_RECIPE_INPUT_SLOTS * MAX_HEI_PROCESSING_RECIPE_CANDIDATES_PER_SLOT;
    private static final int MAX_HEI_PROCESSING_RECIPE_KEY_TAG_LENGTH = 4096;

    private static final Container DUMMY_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
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
    private final Long2ObjectMap<ProviderSelectEntry> providerSelectEntriesById = new Long2ObjectLinkedOpenHashMap<>();
    private final Reference2LongMap<PatternContainer> providerSelectIdsByContainer = new Reference2LongOpenHashMap<>();
    private final Reference2LongMap<PatternContainer> providerIdentityOrdinals = new Reference2LongOpenHashMap<>();
    private List<ProviderStamp> providerDirectorySignature = List.of();
    private List<ProviderSelectEntry> providerDirectorySnapshot = List.of();
    @Nullable
    private IGrid observedProviderGrid;
    private long observedMappingRevision = Long.MIN_VALUE;
    private long nextProviderIdentityOrdinal;
    private long nextProviderSelectEntryId;
    private int ticksUntilProviderDirectoryScan;
    private boolean observedProviderLinkConnected;
    private boolean providerDirectoryInitialized;
    private boolean patternModifierPanelVisible;
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
    @GuiSync(92)
    public long networkBlankPatternCount;
    @GuiSync(86)
    private long providerDirectoryRevision;
    @GuiSync(85)
    private int providerSelectOverlayRequestNonce;
    @GuiSync(84)
    private String providerSelectOverlaySearchText = "";
    @GuiSync(83)
    private String providerSelectOverlayMappingText = "";
    @Nullable
    private IRecipe currentRecipe;
    @Nullable
    private HeiProcessingRecipeSnapshot heiProcessingRecipeSnapshot;
    @Nullable
    private String currentHeiProcessingRecipeTypeUid;
    private boolean changingEncodedPatternSlotInternally;
    private boolean clearOnClose;
    @Nullable
    private PatternAccessSupport.ProviderDiscoverySnapshot providerDiscoverySnapshot;

    public ContainerPatternEncodingTerm(InventoryPlayer ip, IPatternTerminalGuiHost host) {
        this(GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL, ip, host, true);
    }

    public ContainerPatternEncodingTerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, IPatternTerminalGuiHost host,
                                        boolean bindInventory) {
        this(guiKey, ip, host, bindInventory, true);
    }

    protected ContainerPatternEncodingTerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, IPatternTerminalGuiHost host,
                                           boolean bindInventory, boolean registerProviderSelectActions) {
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
        registerClientAction(ACTION_CLEAR_PROCESSING_SECONDARY_OUTPUTS, this::clearProcessingSecondaryOutputs);
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
        registerClientAction(ACTION_RENAME_PROCESSING_PATTERN_ITEM, RenamePatternItemRequest.class,
            MAX_RENAME_PROCESSING_PATTERN_ITEM_PAYLOAD_LENGTH,
            this::renameProcessingPatternItem);
        registerClientAction(ACTION_SET_HEI_PROCESSING_RECIPE, HeiProcessingRecipeRequest.class,
            MAX_SET_HEI_PROCESSING_RECIPE_PAYLOAD_LENGTH,
            this::setHeiProcessingRecipe);
        registerClientAction(ACTION_UPLOAD_PATTERN, Boolean.class, this::uploadPattern);
        registerClientAction(ACTION_SET_PATTERN_MODIFIER_PANEL_VISIBLE, Boolean.class,
            this::setPatternModifierPanelVisibleFromClient);
        if (registerProviderSelectActions) {
            registerProviderSelectActions();
        }
        this.patternModifierPanel = new PatternModifierPanel(this);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();

        updateSlotVisibility();
        getAndUpdateOutput();

        tryAutoFillBlankPatterns();
    }

    private void registerProviderSelectActions() {
        registerClientAction(ACTION_UPLOAD_PROCESSING_PATTERN_TO_PROVIDER, Long.class,
            this::uploadProcessingPatternToProvider);
        registerClientAction(ACTION_BIND_PROVIDER_MAPPING, ProviderMappingByIdAction.class,
            MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH, this::bindProviderMapping);
        registerClientAction(ACTION_BIND_AND_UPLOAD_PROVIDER_MAPPING, ProviderMappingByIdAction.class,
            MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH, this::bindAndUploadProcessingPatternToProvider);
        registerClientAction(ACTION_UNBIND_PROVIDER_MAPPING, ProviderMappingByIdAction.class,
            MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH, this::unbindProviderMapping);
        registerClientAction(ACTION_REQUEST_PROVIDER_DIRECTORY_PAGE, ProviderDirectoryPageRequest.class,
            MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_PAYLOAD_LENGTH, this::requestProviderDirectoryPage);
        registerClientAction(ACTION_REQUEST_PROVIDER_MAPPING_PAGE, ProviderMappingPageRequest.class,
            MAX_PROVIDER_MAPPING_PAGE_REQUEST_PAYLOAD_LENGTH, this::requestProviderMappingPage);
        registerClientAction(ACTION_RELOAD_ALL_CURRENT_PROVIDERS, this::reloadAllCurrentProviders);
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
        if (isServerSide()) {
            refreshProviderDirectory(false, true);
        }
        super.broadcastChanges();
        if (isServerSide()) {
            this.mode = this.encodingLogic.getMode();
            this.substitute = this.encodingLogic.isSubstitution();
            this.substituteFluids = this.encodingLogic.isFluidSubstitution();
            this.autoFillPatterns = getHost().getConfigManager().getSetting(Settings.PATTERN_AUTO_FILL);
            this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
            this.networkBlankPatternCount = this.autoFillPatterns == YesNo.YES ? computeNetworkBlankPatternCount() : 0;
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
        boolean userChangedEncodedPattern = slot == this.encodedPatternSlot
            && !this.changingEncodedPatternSlotInternally;
        if (isServerSide() && (userChangedEncodedPattern || isProcessingPatternSlot(slot))) {
            invalidateHeiProcessingRecipeSnapshot();
        }
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
        this.patternModifierPanel.updateSlotState(this.patternModifierPanelVisible && this.patternModifierPanelAvailable);
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
                putEncodedPatternStackFromContainer(ItemStack.EMPTY);
            }
            return;
        }

        insertEncodedPatternIntoOutputSlot(encodedPattern.copy());
    }

    private static boolean isAcceptedByContainer(PatternContainer container, @Nullable IPatternDetails details) {
        return details != null && (details instanceof IAssemblerPattern) == container.isAssemblerPatternContainer();
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
        putEncodedPatternStackFromContainer(encodedPattern);
    }

    public void uploadPattern(boolean shiftDown) {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_PATTERN, shiftDown);
            return;
        }

        ILinkStatus linkStatus = getHost().getLinkStatus();
        if (!linkStatus.connected()) {
            if (linkStatus.statusDescription() != null) {
                getPlayer().sendStatusMessage(linkStatus.statusDescription(), false);
            }
            return;
        }

        if (this.mode == EncodingMode.PROCESSING && shiftDown) {
            openProcessingPatternProviderSelect("", getValidHeiProcessingRecipeTypeUid());
            return;
        }

        ItemStack encodedPattern = this.encodedPatternSlot.getStack();
        if (!PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            MissingEncodedPatternUploadPlan missingPatternPlan =
                getMissingEncodedPatternUploadPlan(this.mode, shiftDown, getValidHeiProcessingRecipeTypeUid());
            if (missingPatternPlan.openProviderSelect()) {
                openProcessingPatternProviderSelect(missingPatternPlan.initialSearchText(),
                    missingPatternPlan.initialMappingText());
                return;
            }

            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(encodedPattern, getPlayer().world);
        IGrid grid = getProviderSelectGrid();
        if (this.mode == EncodingMode.PROCESSING) {
            if (!(details instanceof AEProcessingPattern processingPattern)) {
                getPlayer().sendStatusMessage(PlayerMessages.PatternUploadProcessingOnly.text(), false);
                return;
            }

            uploadProcessingPattern(encodedPattern, processingPattern, grid, shiftDown);
            return;
        }

        if (!(details instanceof IAssemblerPattern)) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadAssemblerOnly.text(), false);
            return;
        }

        if (grid == null) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoTarget.text(), false);
            return;
        }

        uploadAssemblerPattern(encodedPattern, grid, shiftDown);
    }

    private void uploadAssemblerPattern(ItemStack encodedPattern, IGrid grid, boolean shiftDown) {
        AEItemKey patternKey = AEItemKey.of(encodedPattern);
        if (patternKey == null) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return;
        }

        if (!shiftDown && grid.getCraftingService().isKnownPattern(patternKey)) {
            handleDuplicateUploadFailure(PlayerMessages.PatternUploadDuplicateInNetwork);
            return;
        }

        List<PatternContainer> candidates = collectAssemblerPatternContainers(grid);
        boolean duplicateInContainer = false;
        boolean nonDuplicateCandidateFound = false;
        for (PatternContainer container : candidates) {
            if (container.containsPattern(patternKey)) {
                duplicateInContainer = true;
                continue;
            }

            nonDuplicateCandidateFound = true;
            if (movePatternToFirstAvailableSlot(container, encodedPattern.copy())) {
                putEncodedPatternStackFromContainer(ItemStack.EMPTY);
                return;
            }
        }

        if (duplicateInContainer && !nonDuplicateCandidateFound) {
            handleDuplicateUploadFailure(PlayerMessages.PatternUploadDuplicateInContainer);
            return;
        }

        getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoTarget.text(), false);
    }

    private void uploadProcessingPattern(ItemStack encodedPattern, AEProcessingPattern processingPattern,
                                         @Nullable IGrid grid, boolean shiftDown) {
        uploadProcessingPattern(encodedPattern, grid, shiftDown, processingPattern.getRecipeTypeUid(),
            processingPattern.getRecipeType(), new ProcessingPatternUploadActions() {
                @Override
                public List<PatternContainer> findProcessingPatternUploadTargets(IGrid grid, String recipeTypeUid) {
                    return PatternProviderSelectionSupport.findProcessingPatternUploadTargets(
                        getPatternProviderMappingData(), grid, recipeTypeUid);
                }

                @Override
                public ProcessingPatternUploadResult uploadProcessingPatternToProvider(ItemStack encodedPattern,
                                                                                       IGrid grid,
                                                                                       PatternContainer uploadTarget) {
                    return ContainerPatternEncodingTerm.this.uploadProcessingPatternToProvider(encodedPattern, grid,
                        uploadTarget);
                }

                @Override
                public void openProcessingPatternProviderSelect(String initialSearchText, String initialMappingText) {
                    ContainerPatternEncodingTerm.this.openProcessingPatternProviderSelect(initialSearchText,
                        initialMappingText);
                }

                @Override
                public void sendNoProviderTargetMessage() {
                    getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
                }
            });
    }

    static void uploadProcessingPattern(ItemStack encodedPattern, @Nullable IGrid grid, boolean shiftDown,
                                        @Nullable String recipeTypeUid, @Nullable String recipeTypeTitle,
                                        ProcessingPatternUploadActions actions) {
        Objects.requireNonNull(actions, "actions");
        ProcessingPatternUploadPlan uploadPlan =
            getProcessingPatternUploadPlan(shiftDown, recipeTypeUid, recipeTypeTitle);
        if (uploadPlan.openProviderSelect()) {
            actions.openProcessingPatternProviderSelect(uploadPlan.initialSearchText(),
                uploadPlan.initialMappingText());
            return;
        }

        String automaticRecipeTypeUid = uploadPlan.recipeTypeUid();

        if (grid == null) {
            ProcessingPatternUploadPlan fallbackPlan = getProcessingPatternFallbackPlan(recipeTypeUid, recipeTypeTitle,
                true);
            actions.openProcessingPatternProviderSelect(fallbackPlan.initialSearchText(),
                fallbackPlan.initialMappingText());
            return;
        }

        List<PatternContainer> uploadTargets = actions.findProcessingPatternUploadTargets(grid,
            automaticRecipeTypeUid);
        if (uploadTargets.isEmpty()) {
            handleNoProcessingPatternAutomaticTarget(grid, automaticRecipeTypeUid, recipeTypeTitle, actions);
            return;
        }

        uploadProcessingPatternToProviders(encodedPattern, grid, uploadTargets, automaticRecipeTypeUid,
            recipeTypeTitle, actions);
    }

    static ProcessingPatternUploadPlan getProcessingPatternUploadPlan(boolean shiftDown,
                                                                      @Nullable String recipeTypeUid,
                                                                      @Nullable String recipeTypeTitle) {
        return getImmediateProcessingPatternUploadPlan(shiftDown, recipeTypeUid, recipeTypeTitle);
    }

    protected void openProcessingPatternProviderSelect(@Nullable String initialSearchText,
                                                       @Nullable String initialMappingText) {
        openProviderSelect(initialSearchText, initialMappingText);
    }

    private static void handleNoProcessingPatternAutomaticTarget(@Nullable IGrid grid, String recipeTypeUid,
                                                                 @Nullable String recipeTypeTitle,
                                                                 ProcessingPatternUploadActions actions) {
        ProcessingPatternUploadPlan fallbackPlan = getProcessingPatternUploadTargetPlan(0, recipeTypeUid,
            recipeTypeTitle,
            grid != null && PatternProviderSelectionSupport.hasAvailableProvider(grid));
        if (!fallbackPlan.openProviderSelect()) {
            actions.sendNoProviderTargetMessage();
            return;
        }
        actions.openProcessingPatternProviderSelect(fallbackPlan.initialSearchText(),
            fallbackPlan.initialMappingText());
    }

    private static void uploadProcessingPatternToProviders(ItemStack encodedPattern, IGrid grid,
                                                           List<PatternContainer> uploadTargets,
                                                           String recipeTypeUid,
                                                           @Nullable String recipeTypeTitle,
                                                           ProcessingPatternUploadActions actions) {
        ProcessingPatternUploadPlan uploadPlan = getProcessingPatternUploadTargetPlan(uploadTargets.size(),
            recipeTypeUid, recipeTypeTitle,
            PatternProviderSelectionSupport.hasAvailableProvider(grid));
        if (uploadPlan.openProviderSelect()) {
            actions.openProcessingPatternProviderSelect(uploadPlan.initialSearchText(),
                uploadPlan.initialMappingText());
            return;
        }

        if (uploadTargets.size() != 1) {
            actions.sendNoProviderTargetMessage();
            return;
        }

        ProcessingPatternUploadResult result = actions.uploadProcessingPatternToProvider(encodedPattern, grid,
            uploadTargets.getFirst());
        if (result == ProcessingPatternUploadResult.NO_PROVIDER_TARGET
            && PatternProviderSelectionSupport.hasAvailableProvider(grid)) {
            ProcessingPatternUploadPlan fallbackPlan = getProcessingPatternFallbackPlan(recipeTypeUid, recipeTypeTitle,
                true);
            actions.openProcessingPatternProviderSelect(fallbackPlan.initialSearchText(),
                fallbackPlan.initialMappingText());
        }
    }

    protected ProcessingPatternUploadResult uploadProcessingPatternToProvider(ItemStack encodedPattern, IGrid grid,
                                                                              PatternContainer uploadTarget) {
        return PatternProviderSelectionSupport.tryUploadProcessingPatternToProvider(getPlayer(),
            (IPatternTerminalGuiHost) getHost(), grid, uploadTarget, encodedPattern);
    }

    @Override
    public void uploadProcessingPatternToProvider(long inventoryId) {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_PROCESSING_PATTERN_TO_PROVIDER, inventoryId);
            return;
        }

        IGrid grid = requireProviderSelectGrid();
        if (grid == null) {
            return;
        }
        refreshProviderDirectory(true);
        ProviderSelectEntry entry = getProviderSelectActionEntry(inventoryId, "upload processing pattern to");
        if (entry == null) {
            return;
        }

        ProcessingPatternUploadResult result = PatternProviderSelectionSupport.tryUploadProcessingPatternToProvider(
            getPlayer(), (IPatternTerminalGuiHost) getHost(), grid, entry.container, this.encodedPatternSlot.getStack());
        if (result == ProcessingPatternUploadResult.SUCCESS) {
            refreshProviderDirectory(true);
        }
    }

    static ProcessingPatternUploadPlan getImmediateProcessingPatternUploadPlan(boolean shiftDown,
                                                                               @Nullable String recipeTypeUid,
                                                                               @Nullable String recipeTypeTitle) {
        String trimmedRecipeTypeUid = normalizeProcessingRecipeTypeUid(recipeTypeUid, "processing upload");
        String initialSearchText = getProcessingProviderSelectInitialSearchText(recipeTypeUid, recipeTypeTitle);
        if (trimmedRecipeTypeUid.isEmpty()) {
            return ProcessingPatternUploadPlan.openProviderSelect(trimmedRecipeTypeUid, initialSearchText);
        }
        if (shiftDown) {
            return ProcessingPatternUploadPlan.openProviderSelect(trimmedRecipeTypeUid, initialSearchText);
        }
        return ProcessingPatternUploadPlan.continueAutomaticUpload(trimmedRecipeTypeUid, initialSearchText);
    }

    static ProcessingPatternUploadPlan getProcessingPatternFallbackPlan(@Nullable String recipeTypeUid,
                                                                        @Nullable String recipeTypeTitle,
                                                                        boolean hasAvailableProvider) {
        String trimmedRecipeTypeUid = normalizeProcessingRecipeTypeUid(recipeTypeUid, "processing upload fallback");
        String initialSearchText = getProcessingProviderSelectInitialSearchText(recipeTypeUid, recipeTypeTitle);
        if (!hasAvailableProvider) {
            return ProcessingPatternUploadPlan.noProviderTarget(trimmedRecipeTypeUid, initialSearchText);
        }
        return ProcessingPatternUploadPlan.openProviderSelect(trimmedRecipeTypeUid, initialSearchText,
            getProcessingProviderSelectMappingText(recipeTypeUid));
    }

    static ProcessingPatternUploadPlan getProcessingPatternUploadTargetPlan(int uploadTargetCount,
                                                                            @Nullable String recipeTypeUid,
                                                                            @Nullable String recipeTypeTitle,
                                                                            boolean hasAvailableProvider) {
        if (uploadTargetCount < 0) {
            throw new IllegalArgumentException("uploadTargetCount must not be negative");
        }

        String trimmedRecipeTypeUid = normalizeProcessingRecipeTypeUid(recipeTypeUid, "processing upload target");
        if (uploadTargetCount == 0) {
            return getProcessingPatternFallbackPlan(recipeTypeUid, recipeTypeTitle, hasAvailableProvider);
        }

        String initialSearchText = getProcessingProviderSelectInitialSearchText(recipeTypeUid, recipeTypeTitle);
        if (uploadTargetCount == 1) {
            return ProcessingPatternUploadPlan.continueAutomaticUpload(trimmedRecipeTypeUid, initialSearchText);
        }
        if (!hasAvailableProvider) {
            return ProcessingPatternUploadPlan.noProviderTarget(trimmedRecipeTypeUid, initialSearchText);
        }
        return ProcessingPatternUploadPlan.openProviderSelect(trimmedRecipeTypeUid,
            getProcessingProviderSelectRecipeTypeUidSearchText(recipeTypeUid, recipeTypeTitle), "");
    }

    private static String getProcessingProviderSelectInitialSearchText(@Nullable String recipeTypeUid,
                                                                       @Nullable String recipeTypeTitle) {
        String trimmedRecipeTypeTitle = trimProcessingRecipeText(recipeTypeTitle);
        if (!trimmedRecipeTypeTitle.isEmpty()) {
            return trimmedRecipeTypeTitle;
        }
        return normalizeProcessingRecipeTypeUid(recipeTypeUid, "provider-select search");
    }

    private static String getProcessingProviderSelectRecipeTypeUidSearchText(@Nullable String recipeTypeUid,
                                                                             @Nullable String recipeTypeTitle) {
        String trimmedRecipeTypeUid = normalizeProcessingRecipeTypeUid(recipeTypeUid, "provider-select search");
        if (!trimmedRecipeTypeUid.isEmpty()) {
            return trimmedRecipeTypeUid;
        }
        return trimProcessingRecipeText(recipeTypeTitle);
    }

    private static String getProcessingProviderSelectMappingText(@Nullable String recipeTypeUid) {
        return normalizeProcessingRecipeTypeUid(recipeTypeUid, "provider-select mapping");
    }

    private static String normalizeProcessingRecipeTypeUid(@Nullable String value, String source) {
        String normalized = RecipeTypeUid.normalize(value);
        if (normalized != null) {
            return normalized;
        }
        if (value != null && !value.isEmpty()) {
            PatternProviderSelectionSupport.warnInvalidRecipeTypeUid(source, value);
        }
        return "";
    }

    private static String trimProcessingRecipeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    static MissingEncodedPatternUploadPlan getMissingEncodedPatternUploadPlan(EncodingMode mode, boolean shiftDown,
                                                                              @Nullable String heiRecipeTypeUid) {
        String initialSearchText = normalizeProcessingRecipeTypeUid(heiRecipeTypeUid, "HEI provider-select search");
        if (mode == EncodingMode.PROCESSING && shiftDown) {
            return MissingEncodedPatternUploadPlan.openProviderSelect(initialSearchText, "");
        }
        return MissingEncodedPatternUploadPlan.noEncodedPattern(initialSearchText);
    }

    @Override
    public void bindProviderMapping(long inventoryId, String mappingText) {
        ProviderMappingByIdAction action = new ProviderMappingByIdAction(inventoryId, mappingText);
        if (isClientSide()) {
            sendClientAction(ACTION_BIND_PROVIDER_MAPPING, action);
            return;
        }

        bindProviderMapping(action);
    }

    private void bindProviderMapping(ProviderMappingByIdAction action) {
        if (isClientSide()) {
            sendClientAction(ACTION_BIND_PROVIDER_MAPPING, action);
            return;
        }
        if (action == null || !action.hasInventoryId()) {
            PatternProviderSelectionSupport.warnProviderAction("bind-mapping:missing-id",
                "Ignoring provider mapping action without a provider-select target id");
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return;
        }

        String mappingText = normalizeProviderMappingText(action.mappingText());
        if (mappingText == null) {
            return;
        }

        IGrid grid = requireProviderSelectGrid();
        if (grid == null) {
            return;
        }
        refreshProviderDirectory(true);
        ProviderSelectEntry entry = getProviderSelectActionEntry(action.inventoryId(), "bind mapping for");
        if (entry == null) {
            return;
        }

        PatternProviderMappingData mappingData = getPatternProviderMappingData();
        ProviderReference reference = requireProviderReference(entry, "bind mapping for");
        if (reference == null) {
            return;
        }
        ProviderMappingValidationResult validation = PatternProviderSelectionSupport.validateProviderMapping(
            mappingData, entry.container, reference, mappingText);
        if (validation != ProviderMappingValidationResult.SUCCESS) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
            return;
        }
        BindResult bindResult = mappingData.bind(mappingText, reference);
        if (bindResult == BindResult.ADDED) {
            refreshProviderDirectory(false);
        }
    }

    @Override
    public void bindAndUploadProcessingPatternToProvider(long inventoryId, String recipeTypeUid) {
        ProviderMappingByIdAction action = new ProviderMappingByIdAction(inventoryId, recipeTypeUid);
        if (isClientSide()) {
            sendClientAction(ACTION_BIND_AND_UPLOAD_PROVIDER_MAPPING, action);
            return;
        }
        bindAndUploadProcessingPatternToProvider(action);
    }

    private void bindAndUploadProcessingPatternToProvider(ProviderMappingByIdAction action) {
        if (action == null || !action.hasInventoryId()) {
            PatternProviderSelectionSupport.warnProviderAction("bind-and-upload:missing-id",
                "Ignoring provider bind-and-upload action without a provider-select target id");
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return;
        }
        String recipeTypeUid = normalizeProviderMappingText(action.mappingText());
        if (recipeTypeUid == null) {
            return;
        }

        IGrid grid = requireProviderSelectGrid();
        if (grid == null) {
            return;
        }
        refreshProviderDirectory(true);
        ProviderSelectEntry entry = getProviderSelectActionEntry(action.inventoryId(), "bind mapping and upload to");
        if (entry == null) {
            return;
        }

        PatternProviderMappingData mappingData = getPatternProviderMappingData();
        ProviderReference reference = requireProviderReference(entry, "bind mapping and upload to");
        if (reference == null) {
            return;
        }
        ProviderMappingValidationResult mappingValidation = PatternProviderSelectionSupport.validateProviderMapping(
            mappingData, entry.container, reference, recipeTypeUid);
        if (mappingValidation != ProviderMappingValidationResult.SUCCESS) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
            return;
        }

        mappingData.getRecipeTypes(reference);

        IPatternTerminalGuiHost host = (IPatternTerminalGuiHost) getHost();
        ProcessingPatternUploadPreparation upload = PatternProviderSelectionSupport.prepareProcessingPatternUpload(
            getPlayer(), host, grid, entry.container, this.encodedPatternSlot.getStack());
        if (!upload.ready()) {
            return;
        }

        ItemStack sourceBeforeCommit = host.getLogic().getEncodedPatternInv().getStackInSlot(0).copy();
        try {
            if (!upload.commit()) {
                throw new IllegalStateException(
                    "Provider rejected a processing pattern after accepting the simulated insertion");
            }
            host.getLogic().getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
            mappingData.bind(recipeTypeUid, reference);
        } catch (RuntimeException e) {
            upload.restoreTargetSlotAfterFailure(e);
            try {
                host.getLogic().getEncodedPatternInv().setItemDirect(0, sourceBeforeCommit);
            } catch (RuntimeException restoreFailure) {
                e.addSuppressed(restoreFailure);
            }
            NetworkPacketHelper.warnFailedPacket(e, "provider-bind-and-upload",
                "Failed to atomically bind and upload a processing pattern to provider %s",
                reference);
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderBindAndUploadFailed.text(), false);
            refreshProviderDirectory(true);
            return;
        }
        refreshProviderDirectory(true);
    }

    @Nullable
    private String normalizeProviderMappingText(@Nullable String mappingText) {
        if (mappingText == null || mappingText.trim().isEmpty()) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingBlank.text(), false);
            return null;
        }
        String normalized = RecipeTypeUid.normalize(mappingText);
        if (normalized == null) {
            PatternProviderSelectionSupport.warnInvalidRecipeTypeUid("provider mapping action", mappingText);
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
        }
        return normalized;
    }

    @Override
    public void unbindProviderMapping(long inventoryId) {
        unbindProviderMapping(inventoryId, null);
    }

    @Override
    public void unbindProviderMapping(long inventoryId, @Nullable String recipeType) {
        ProviderMappingByIdAction action = new ProviderMappingByIdAction(inventoryId, recipeType);
        if (isClientSide()) {
            sendClientAction(ACTION_UNBIND_PROVIDER_MAPPING, action);
            return;
        }
        unbindProviderMapping(action);
    }

    private void unbindProviderMapping(ProviderMappingByIdAction action) {
        if (action == null || !action.hasInventoryId()) {
            PatternProviderSelectionSupport.warnProviderAction("unbind-mapping:missing-id",
                "Ignoring provider mapping unbind action without a provider-select target id");
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return;
        }
        IGrid grid = requireProviderSelectGrid();
        if (grid == null) {
            return;
        }
        refreshProviderDirectory(true);
        ProviderSelectEntry entry = getProviderSelectActionEntry(action.inventoryId(), "unbind mapping for");
        if (entry == null) {
            return;
        }

        PatternProviderMappingData mappingData = getPatternProviderMappingData();
        ProviderReference reference = requireProviderReference(entry, "unbind mapping for");
        if (reference == null) {
            return;
        }
        String requestedRecipeType = action.mappingText();
        boolean unbindAll = requestedRecipeType == null;
        String recipeType = unbindAll ? null : RecipeTypeUid.normalize(requestedRecipeType);
        if (!unbindAll && recipeType == null) {
            PatternProviderSelectionSupport.warnInvalidRecipeTypeUid("provider mapping unbind", requestedRecipeType);
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
            return;
        }
        boolean changed = unbindAll
            ? mappingData.unbindAll(reference)
            : mappingData.unbind(reference, Objects.requireNonNull(recipeType, "recipeType"));
        if (!changed) {
            PatternProviderSelectionSupport.warnProviderAction("unbind-mapping:no-match:" + action.inventoryId(),
                "Cannot unbind provider mapping for provider-select target without matching mappings: %d",
                action.inventoryId());
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return;
        }
        refreshProviderDirectory(false);
    }

    @Override
    public void reloadAllCurrentProviders() {
        if (isClientSide()) {
            sendClientAction(ACTION_RELOAD_ALL_CURRENT_PROVIDERS);
            return;
        }
        if (requireProviderSelectGrid() == null) {
            return;
        }

        refreshProviderDirectory(true);
        List<PatternProviderSelectionSupport.ProviderMappingReloadTarget> targets = new ObjectArrayList<>();
        for (ProviderSelectEntry entry : this.providerDirectorySnapshot) {
            if (entry.reference != null) {
                targets.add(new PatternProviderSelectionSupport.ProviderMappingReloadTarget(
                    entry.container, entry.reference));
            }
        }
        PatternProviderSelectionSupport.reloadProviderMappings(
            getPatternProviderMappingData(), getPlayer().world, targets);
        refreshProviderDirectory(false);
    }

    private void openProviderSelect(@Nullable String initialSearchText, @Nullable String initialMappingText) {
        if (isClientSide()) {
            return;
        }

        refreshProviderDirectory(true);
        ProviderSelectOverlayOpenRequest request = createProviderSelectOverlayOpenRequest(
            initialSearchText,
            initialMappingText,
            this.providerSelectOverlayRequestNonce);
        this.providerSelectOverlayRequestNonce = request.nonce();
        this.providerSelectOverlaySearchText = request.searchText();
        this.providerSelectOverlayMappingText = request.mappingText();
    }

    @Nullable
    protected IGrid getProviderSelectGrid() {
        if (!getHost().getLinkStatus().connected()) {
            return null;
        }
        IGridNode node = getGridNode();
        if (node == null || !node.isActive()) {
            return null;
        }

        return node.grid();
    }

    @Nullable
    private IGrid requireProviderSelectGrid() {
        ILinkStatus linkStatus = getHost().getLinkStatus();
        if (!linkStatus.connected()) {
            getPlayer().sendStatusMessage(linkStatus.statusDescription() != null
                ? linkStatus.statusDescription()
                : PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return null;
        }
        IGrid grid = getProviderSelectGrid();
        if (grid == null) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
        }
        return grid;
    }

    protected PatternProviderMappingData getPatternProviderMappingData() {
        return PatternProviderMappingData.get(getPlayer().world);
    }

    protected void sendProviderDirectoryPage(ProviderDirectoryPage page) {
        sendPacketToClient(new ProviderDirectoryPagePacket(page));
    }

    protected void sendProviderMappingPage(ProviderMappingPage page) {
        sendPacketToClient(new ProviderMappingPagePacket(page));
    }

    private void refreshProviderDirectory(boolean forceProviderScan) {
        refreshProviderDirectory(forceProviderScan, false);
    }

    private void refreshProviderDirectory(boolean forceProviderScan, boolean advanceScheduledScan) {
        boolean linkConnected = getHost().getLinkStatus().connected();
        IGrid grid = linkConnected ? getProviderSelectGrid() : null;
        boolean contextChanged = !this.providerDirectoryInitialized
            || linkConnected != this.observedProviderLinkConnected
            || grid != this.observedProviderGrid;

        if (grid == null) {
            updateDisconnectedProviderDirectory(linkConnected, contextChanged);
            return;
        }

        PatternProviderMappingData mappingData = getPatternProviderMappingData();
        long mappingRevision = mappingData.getRevision();
        boolean mappingChanged = mappingRevision != this.observedMappingRevision;
        boolean scheduledScan = forceProviderScan
            || advanceScheduledScan && --this.ticksUntilProviderDirectoryScan <= 0;
        if (!contextChanged && !mappingChanged && !scheduledScan) {
            return;
        }

        boolean scannedProviders = contextChanged || scheduledScan;
        List<ProviderSelectEntry> currentSnapshot = this.providerDirectorySnapshot;
        List<ProviderStamp> currentSignature;
        if (scannedProviders) {
            currentSnapshot = collectProviderDirectorySnapshot(grid);
        }
        currentSignature = createProviderSignature(currentSnapshot, mappingData);

        boolean directoryChanged = contextChanged
            || !this.providerDirectorySignature.equals(currentSignature);
        this.observedProviderLinkConnected = linkConnected;
        this.observedProviderGrid = grid;
        this.observedMappingRevision = mappingRevision;
        this.providerDirectoryInitialized = true;
        if (scannedProviders) {
            this.ticksUntilProviderDirectoryScan = PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS;
        }

        if (!directoryChanged) {
            return;
        }

        if (scannedProviders) {
            rebuildProviderDirectorySnapshot(currentSnapshot);
        }
        this.providerDirectorySignature = currentSignature;
        incrementProviderDirectoryRevision();
    }

    private void updateDisconnectedProviderDirectory(boolean linkConnected, boolean contextChanged) {
        boolean directoryChanged = this.providerDirectoryInitialized
            && (contextChanged || !this.providerDirectorySnapshot.isEmpty());
        this.observedProviderLinkConnected = linkConnected;
        this.observedProviderGrid = null;
        this.observedMappingRevision = Long.MIN_VALUE;
        this.ticksUntilProviderDirectoryScan = PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS;
        this.providerDirectoryInitialized = true;
        if (!directoryChanged) {
            return;
        }

        this.providerDirectorySignature = List.of();
        this.providerDirectorySnapshot = List.of();
        this.providerSelectEntriesById.clear();
        this.providerSelectIdsByContainer.clear();
        this.providerIdentityOrdinals.clear();
        incrementProviderDirectoryRevision();
    }

    private List<ProviderSelectEntry> collectProviderDirectorySnapshot(IGrid grid) {
        PatternAccessSupport.ProviderDiscoverySnapshot discovery = this.providerDiscoverySnapshot;
        List<ProviderDirectoryEntry> providers = discovery == null
            ? PatternProviderSelectionSupport.collectProcessingPatternUploadProviders(grid)
            : discovery.providers().stream().filter(PatternProviderSelectionSupport::isSelectableProvider)
                .map(ProviderDirectoryEntry::of).toList();
        ReferenceOpenHashSet<PatternContainer> currentProviders = new ReferenceOpenHashSet<>();
        for (ProviderDirectoryEntry provider : providers) {
            currentProviders.add(provider.container());
        }
        this.providerSelectIdsByContainer.keySet().removeIf(container -> !currentProviders.contains(container));
        this.providerIdentityOrdinals.keySet().removeIf(container -> !currentProviders.contains(container));

        List<ProviderSelectEntry> entries = new ObjectArrayList<>(providers.size());
        for (ProviderDirectoryEntry provider : providers) {
            PatternContainer container = provider.container();
            entries.add(new ProviderSelectEntry(
                getOrCreateProviderSelectEntryId(container),
                container,
                provider.reference(),
                getOrCreateProviderIdentityOrdinal(container),
                provider));
        }
        entries.sort(ContainerPatternEncodingTerm::compareProviderDirectoryEntries);
        return List.copyOf(entries);
    }

    protected void setProviderDiscoverySnapshot(@Nullable PatternAccessSupport.ProviderDiscoverySnapshot snapshot) {
        this.providerDiscoverySnapshot = snapshot;
    }

    private void rebuildProviderDirectorySnapshot(List<ProviderSelectEntry> snapshot) {
        this.providerDirectorySnapshot = List.copyOf(snapshot);
        this.providerSelectEntriesById.clear();
        for (ProviderSelectEntry entry : snapshot) {
            this.providerSelectEntriesById.put(entry.id, entry);
        }
    }

    private void incrementProviderDirectoryRevision() {
        this.providerDirectoryRevision = Math.incrementExact(this.providerDirectoryRevision);
    }

    private long getOrCreateProviderIdentityOrdinal(PatternContainer container) {
        if (this.providerIdentityOrdinals.containsKey(container)) {
            return this.providerIdentityOrdinals.getLong(container);
        }
        long ordinal = this.nextProviderIdentityOrdinal;
        this.nextProviderIdentityOrdinal = Math.incrementExact(this.nextProviderIdentityOrdinal);
        this.providerIdentityOrdinals.put(container, ordinal);
        return ordinal;
    }

    private static int compareProviderDirectoryEntries(ProviderSelectEntry left, ProviderSelectEntry right) {
        int comparison = Long.compare(left.provider.sortBy(), right.provider.sortBy());
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareProviderReferences(left.reference, right.reference);
        return comparison != 0 ? comparison : Long.compare(left.identityOrdinal, right.identityOrdinal);
    }

    private static int compareProviderReferences(@Nullable ProviderReference left,
                                                 @Nullable ProviderReference right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int comparison = Integer.compare(left.dimension(), right.dimension());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(left.pos(), right.pos());
        return comparison != 0 ? comparison : Integer.compare(left.side(), right.side());
    }

    private static List<ProviderStamp> createProviderSignature(List<ProviderSelectEntry> entries,
                                                               PatternProviderMappingData mappingData) {
        Objects.requireNonNull(mappingData, "mappingData");
        List<ProviderStamp> signature = new ObjectArrayList<>(entries.size());
        for (ProviderSelectEntry entry : entries) {
            signature.add(new ProviderStamp(entry, mappingData));
        }
        return List.copyOf(signature);
    }

    @Override
    public void requestProviderDirectoryPage(long nonce, String query, int page,
                                             @Nullable ProviderDirectoryPageRequest.Focus focus) {
        ProviderDirectoryPageRequest request = new ProviderDirectoryPageRequest(nonce, query, page, focus);
        if (isClientSide()) {
            sendClientAction(ACTION_REQUEST_PROVIDER_DIRECTORY_PAGE, request);
            return;
        }
        requestProviderDirectoryPage(request);
    }

    private void requestProviderDirectoryPage(@Nullable ProviderDirectoryPageRequest request) {
        if (request == null || request.nonce() == null || request.query() == null || request.page() == null) {
            PatternProviderSelectionSupport.warnProviderAction("directory-page-request:missing-field",
                "Ignoring provider directory page request with missing fields");
            return;
        }
        long nonce = request.nonce();
        int page = request.page();
        if (nonce <= 0 || page < 0 || request.focus() != null && page != 0) {
            PatternProviderSelectionSupport.warnProviderAction("directory-page-request:invalid-number",
                "Ignoring provider directory page request with invalid nonce, page or focus page");
            return;
        }

        String query;
        ProviderDirectoryPageRequest.Focus focus;
        try {
            query = ProviderPageLimits.requireBoundedText("provider directory query", request.query().trim(),
                ProviderPageLimits.MAX_QUERY_UTF16_LENGTH, ProviderPageLimits.MAX_QUERY_UTF8_BYTES);
            focus = validateDirectoryFocus(request.focus());
        } catch (RuntimeException e) {
            PatternProviderSelectionSupport.warnProviderAction("directory-page-request:invalid-content",
                "Ignoring provider directory page request with invalid query or focus: %s", e.getMessage());
            return;
        }

        refreshProviderDirectory(false);
        PatternProviderMappingData mappingData = getPatternProviderMappingData();
        List<ProviderSelectEntry> matches = new ObjectArrayList<>();
        List<ProviderSelectEntry> unmatched = new ObjectArrayList<>();
        for (ProviderSelectEntry entry : this.providerDirectorySnapshot) {
            if (PatternProviderSelectionSupport.matchesProviderDirectoryQuery(entry.provider, mappingData, query)) {
                matches.add(entry);
            } else {
                unmatched.add(entry);
            }
        }
        matches.addAll(unmatched);
        promoteFocusedProvider(matches, focus);
        List<ProviderDirectoryPage.Entry> pageEntries = new ObjectArrayList<>();
        for (ProviderSelectEntry entry : getPage(matches, page)) {
            pageEntries.add(PatternProviderSelectionSupport.createProviderDirectoryPageEntry(
                entry.id, entry.provider, mappingData, query));
        }
        sendProviderDirectoryPage(new ProviderDirectoryPage(
            this.windowId, nonce, this.providerDirectoryRevision, page,
            this.providerDirectorySnapshot.size(), pageEntries));
    }

    @Override
    public void requestProviderMappingPage(long nonce, long directoryRevision, long providerId, int page) {
        ProviderMappingPageRequest request = new ProviderMappingPageRequest(nonce, directoryRevision, providerId, page);
        if (isClientSide()) {
            sendClientAction(ACTION_REQUEST_PROVIDER_MAPPING_PAGE, request);
            return;
        }
        requestProviderMappingPage(request);
    }

    private void requestProviderMappingPage(@Nullable ProviderMappingPageRequest request) {
        if (request == null || request.nonce() == null || request.directoryRevision() == null
            || request.providerId() == null || request.page() == null) {
            return;
        }
        refreshProviderDirectory(false);
        if (request.nonce() <= 0 || request.directoryRevision() != this.providerDirectoryRevision
            || request.page() < 0 || request.page() > Integer.MAX_VALUE / ProviderPageLimits.PAGE_SIZE) {
            return;
        }
        ProviderSelectEntry entry = this.providerSelectEntriesById.get(request.providerId().longValue());
        if (entry == null || entry.reference == null) {
            return;
        }
        PatternProviderMappingData data = getPatternProviderMappingData();
        int total = data.getRecipeTypeCount(entry.reference);
        int first = request.page() * ProviderPageLimits.PAGE_SIZE;
        if (first > total || first == total && total != 0) {
            return;
        }
        sendProviderMappingPage(new ProviderMappingPage(this.windowId, request.nonce(), this.providerDirectoryRevision,
            request.providerId(), request.page(), total,
            data.getRecipeTypePage(entry.reference, request.page(), ProviderPageLimits.PAGE_SIZE)));
    }

    @Nullable
    private static ProviderDirectoryPageRequest.Focus validateDirectoryFocus(
        @Nullable ProviderDirectoryPageRequest.Focus focus) {
        if (focus == null) {
            return null;
        }
        return new ProviderDirectoryPageRequest.Focus(
            focus.providerId(), focus.dimension(), focus.position(), focus.side());
    }

    private static void promoteFocusedProvider(List<ProviderSelectEntry> entries,
                                               @Nullable ProviderDirectoryPageRequest.Focus focus) {
        if (focus == null || entries.isEmpty()) {
            return;
        }

        int focusedIndex = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id == focus.providerId()) {
                focusedIndex = index;
                break;
            }
        }
        if (focusedIndex < 0) {
            ProviderReference focusedReference =
                new ProviderReference(focus.dimension(), focus.position(), focus.side());
            for (int index = 0; index < entries.size(); index++) {
                if (Objects.equals(entries.get(index).reference, focusedReference)) {
                    focusedIndex = index;
                    break;
                }
            }
        }
        if (focusedIndex > 0) {
            entries.addFirst(entries.remove(focusedIndex));
        }
    }

    private static <T> List<T> getPage(List<T> values, int page) {
        long start = (long) page * ProviderPageLimits.PAGE_SIZE;
        if (start >= values.size()) {
            return List.of();
        }
        int fromIndex = (int) start;
        int toIndex = Math.min(values.size(), fromIndex + ProviderPageLimits.PAGE_SIZE);
        return List.copyOf(values.subList(fromIndex, toIndex));
    }

    private long getOrCreateProviderSelectEntryId(PatternContainer container) {
        if (this.providerSelectIdsByContainer.containsKey(container)) {
            return this.providerSelectIdsByContainer.getLong(container);
        }
        if (this.nextProviderSelectEntryId == Long.MAX_VALUE) {
            PatternProviderSelectionSupport.warnProviderAction("directory-id-exhausted:" + this.windowId,
                "Provider-select target id space exhausted for container window %d", this.windowId);
            throw new IllegalStateException("Provider-select target id space exhausted");
        }
        long providerId = this.nextProviderSelectEntryId++;
        this.providerSelectIdsByContainer.put(container, providerId);
        return providerId;
    }

    @Nullable
    private ProviderSelectEntry getProviderSelectActionEntry(long inventoryId, String actionDescription) {
        ProviderSelectEntry entry = this.providerSelectEntriesById.get(inventoryId);
        if (entry != null) {
            return entry;
        }

        PatternProviderSelectionSupport.warnProviderAction(
            "provider-action:unknown-id:" + actionDescription + ":" + inventoryId,
            "Cannot %s unknown provider-select target id: %d", actionDescription, inventoryId);
        getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
        return null;
    }

    @Nullable
    private ProviderReference requireProviderReference(ProviderSelectEntry entry, String actionDescription) {
        if (entry.reference != null) {
            return entry.reference;
        }
        PatternProviderSelectionSupport.warnProviderAction(
            "provider-action:missing-reference:" + actionDescription + ":" + entry.id,
            "Cannot %s provider-select target without a stable provider reference: %d",
            actionDescription, entry.id);
        getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingInvalid.text(), false);
        return null;
    }

    private List<PatternContainer> collectAssemblerPatternContainers(IGrid grid) {
        List<PatternContainer> containers = new ArrayList<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }

            Class<? extends PatternContainer> containerClass = machineClass.asSubclass(PatternContainer.class);
            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (container.isVisibleInTerminal() && container.isAssemblerPatternContainer()) {
                    containers.add(container);
                }
            }
        }
        containers.sort(Comparator.comparingLong(PatternContainer::getTerminalSortOrder));
        return containers;
    }

    private boolean movePatternToFirstAvailableSlot(PatternContainer container, ItemStack encodedPattern) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }

            InternalInventory targetSlot = new FilteredInternalInventory(inventory.getSlotInv(slot),
                new PatternSlotFilter(container, getPlayer().world));
            ItemStack remainder = targetSlot.addItems(encodedPattern.copy());
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void handleDuplicateUploadFailure(PlayerMessages message) {
        ItemStack encodedPattern = this.encodedPatternSlot.getStack();
        if (encodedPattern.isEmpty()) {
            getPlayer().sendStatusMessage(message.text(), false);
            return;
        }

        ItemStack blankPattern = AEItems.BLANK_PATTERN.stack(encodedPattern.getCount());
        putEncodedPatternStackFromContainer(ItemStack.EMPTY);
        blankPattern = insertBlankPatternIntoBlankSlot(blankPattern);
        blankPattern = insertBlankPatternIntoPlayerInventory(blankPattern);
        blankPattern = insertBlankPatternIntoNetwork(blankPattern);
        if (!blankPattern.isEmpty()) {
            dropBlankPattern(blankPattern);
        }
        getPlayer().sendStatusMessage(message.text(), false);
    }

    private ItemStack insertBlankPatternIntoBlankSlot(ItemStack blankPattern) {
        return this.blankPatternSlot.getSlotInv().addItems(blankPattern);
    }

    private ItemStack insertBlankPatternIntoPlayerInventory(ItemStack blankPattern) {
        if (blankPattern.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = blankPattern.copy();
        if (getPlayerInventory().addItemStackToInventory(remaining)) {
            return ItemStack.EMPTY;
        }
        return remaining;
    }

    private ItemStack insertBlankPatternIntoNetwork(ItemStack blankPattern) {
        if (blankPattern.isEmpty()) {
            return ItemStack.EMPTY;
        }

        AEItemKey blankPatternKey = AEItemKey.of(blankPattern);
        if (blankPatternKey == null) {
            return blankPattern;
        }

        long inserted = StorageHelper.poweredInsert(this.energySource, this.storage, blankPatternKey,
            blankPattern.getCount(), getActionSource());
        if (inserted <= 0) {
            return blankPattern;
        }

        ItemStack remaining = blankPattern.copy();
        remaining.shrink((int) inserted);
        return remaining;
    }

    private void dropBlankPattern(ItemStack blankPattern) {
        if (blankPattern.isEmpty() || getPlayer().world == null) {
            return;
        }

        EntityItem drop = getPlayer().dropItem(blankPattern, false);
        if (drop != null) {
            drop.setNoPickupDelay();
        }
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
            putEncodedPatternStackFromContainer(AEItems.BLANK_PATTERN.stack(encodedPattern.getCount()));
        }
    }

    private void putEncodedPatternStackFromContainer(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (this.changingEncodedPatternSlotInternally) {
            throw new IllegalStateException("Nested encoded pattern slot mutation");
        }
        this.changingEncodedPatternSlotInternally = true;
        try {
            this.encodedPatternSlot.putStack(stack);
        } finally {
            this.changingEncodedPatternSlotInternally = false;
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

        String recipeTypeUid = getCurrentHeiProcessingRecipeTypeUid();
        return PatternDetailsHelper.encodeProcessingPattern(Arrays.asList(inputs), Arrays.asList(outputs),
            recipeTypeUid);
    }

    @Nullable
    private String getValidHeiProcessingRecipeTypeUid() {
        if (this.mode != EncodingMode.PROCESSING || this.heiProcessingRecipeSnapshot == null) {
            return null;
        }

        List<HeiProcessingInputSnapshot> inputsBySlot = this.heiProcessingRecipeSnapshot.inputsBySlot;
        int nonEmptyInputs = 0;
        for (int slot = 0; slot < this.encodedInputsInv.size(); slot++) {
            GenericStack currentInput = this.encodedInputsInv.getStack(slot);
            if (currentInput == null) {
                continue;
            }

            if (slot >= inputsBySlot.size()) {
                return null;
            }
            HeiProcessingInputSnapshot importedInput = inputsBySlot.get(slot);
            if (currentInput.amount() != importedInput.amount()
                || !importedInput.candidates().contains(currentInput.what())) {
                return null;
            }
            nonEmptyInputs++;
        }

        if (nonEmptyInputs != inputsBySlot.size()) {
            return null;
        }
        for (int slot = 0; slot < this.encodedOutputsInv.size(); slot++) {
            if (!Objects.equals(this.encodedOutputsInv.getStack(slot),
                this.heiProcessingRecipeSnapshot.outputsBySlot.get(slot))) {
                return null;
            }
        }
        return this.heiProcessingRecipeSnapshot.recipeTypeUid;
    }

    @Nullable
    private String getCurrentHeiProcessingRecipeTypeUid() {
        return this.mode == EncodingMode.PROCESSING
            ? this.currentHeiProcessingRecipeTypeUid
            : null;
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

    @Nullable
    private HeiProcessingRecipeSnapshot parseHeiProcessingRecipeSnapshot(@Nullable HeiProcessingRecipeRequest request) {
        if (request == null || request.inputCandidateKeyTags == null || request.inputCandidateKeyTags.isEmpty()) {
            return null;
        }
        String recipeTypeUid = RecipeTypeUid.normalize(request.recipeTypeUid);
        if (recipeTypeUid == null) {
            if (request.recipeTypeUid != null && !request.recipeTypeUid.isEmpty()) {
                PatternProviderSelectionSupport.warnInvalidRecipeTypeUid("HEI processing recipe", request.recipeTypeUid);
            }
            return null;
        }
        if (request.inputCandidateKeyTags.size() > MAX_HEI_PROCESSING_RECIPE_INPUT_SLOTS) {
            return null;
        }

        List<HeiProcessingInputSnapshot> inputsBySlot = new ArrayList<>(request.inputCandidateKeyTags.size());
        int totalCandidates = 0;
        for (int slot = 0; slot < request.inputCandidateKeyTags.size(); slot++) {
            List<String> candidateTags = request.inputCandidateKeyTags.get(slot);
            if (candidateTags == null || candidateTags.isEmpty()) {
                return null;
            }
            if (candidateTags.size() > MAX_HEI_PROCESSING_RECIPE_CANDIDATES_PER_SLOT) {
                return null;
            }
            totalCandidates += candidateTags.size();
            if (totalCandidates > MAX_HEI_PROCESSING_RECIPE_TOTAL_CANDIDATES) {
                return null;
            }

            List<AEKey> candidates = new ArrayList<>(candidateTags.size());
            for (String candidateTag : candidateTags) {
                AEKey key = parseKey(candidateTag);
                if (key != null && !candidates.contains(key)) {
                    candidates.add(key);
                }
            }
            if (candidates.isEmpty()) {
                return null;
            }
            GenericStack importedInput = this.encodedInputsInv.getStack(slot);
            if (importedInput == null || importedInput.amount() <= 0 || !candidates.contains(importedInput.what())) {
                return null;
            }
            inputsBySlot.add(new HeiProcessingInputSnapshot(candidates, importedInput.amount()));
        }

        List<GenericStack> outputsBySlot = new ArrayList<>(this.encodedOutputsInv.size());
        for (int slot = 0; slot < this.encodedOutputsInv.size(); slot++) {
            outputsBySlot.add(this.encodedOutputsInv.getStack(slot));
        }
        return new HeiProcessingRecipeSnapshot(recipeTypeUid, inputsBySlot, outputsBySlot);
    }

    public void clear() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }

        this.encodedInputsInv.clear();
        this.encodedOutputsInv.clear();
        invalidateHeiProcessingRecipeSnapshot();
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
        invalidateHeiProcessingRecipeSnapshot();
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

    public boolean isBlankPatternSlot(@Nullable Slot slot) {
        return slot == this.blankPatternSlot;
    }

    public long getSyncedNetworkBlankPatternCount() {
        return this.networkBlankPatternCount;
    }

    @Override
    public long getProviderDirectoryRevision() {
        return this.providerDirectoryRevision;
    }

    @Override
    public int getProviderSelectOverlayRequestNonce() {
        return this.providerSelectOverlayRequestNonce;
    }

    @Override
    public String getProviderSelectOverlaySearchText() {
        return this.providerSelectOverlaySearchText;
    }

    @Override
    public String getProviderSelectOverlayMappingText() {
        return this.providerSelectOverlayMappingText;
    }

    private record PatternSlotFilter(PatternContainer container, World level) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty()
                && isAcceptedByContainer(this.container, PatternDetailsHelper.decodePattern(stack, this.level));
        }
    }

    private long computeNetworkBlankPatternCount() {
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.stack(1));
        if (blankPatternKey == null) {
            return 0;
        }
        return getPreviousAvailableStacks().get(blankPatternKey);
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
        if (isClientSide() && this.patternModifierPanelVisible != visible) {
            sendClientAction(ACTION_SET_PATTERN_MODIFIER_PANEL_VISIBLE, visible);
        }
        applyPatternModifierPanelVisible(visible);
    }

    private void setPatternModifierPanelVisibleFromClient(boolean visible) {
        applyPatternModifierPanelVisible(visible);
    }

    private void applyPatternModifierPanelVisible(boolean visible) {
        this.patternModifierPanelVisible = visible;
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

    public void setHeiProcessingRecipe(String recipeTypeUid, List<List<String>> inputCandidateKeyTags) {
        if (isClientSide()) {
            String normalizedRecipeTypeUid = RecipeTypeUid.normalize(recipeTypeUid);
            if (normalizedRecipeTypeUid == null) {
                if (recipeTypeUid != null && !recipeTypeUid.isEmpty()) {
                    PatternProviderSelectionSupport.warnInvalidRecipeTypeUid("HEI processing recipe", recipeTypeUid);
                }
                return;
            }
            sendClientAction(ACTION_SET_HEI_PROCESSING_RECIPE,
                new HeiProcessingRecipeRequest(normalizedRecipeTypeUid, inputCandidateKeyTags));
        }
    }

    private void setHeiProcessingRecipe(HeiProcessingRecipeRequest request) {
        if (isClientSide()) {
            return;
        }

        this.currentHeiProcessingRecipeTypeUid = request == null ? null : RecipeTypeUid.normalize(request.recipeTypeUid);
        this.heiProcessingRecipeSnapshot = parseHeiProcessingRecipeSnapshot(request);
    }

    @Nullable
    private static AEKey parseKey(@Nullable String serializedKey) {
        if (serializedKey == null || serializedKey.isEmpty()
            || serializedKey.length() > MAX_HEI_PROCESSING_RECIPE_KEY_TAG_LENGTH) {
            return null;
        }

        try {
            return AEKey.fromTagGeneric(JsonToNBT.getTagFromJson(serializedKey));
        } catch (NBTException | RuntimeException e) {
            NetworkPacketHelper.warnMalformedPacket(e, "hei-processing-recipe-key",
                "Ignoring malformed HEI processing recipe key");
            return null;
        }
    }

    private void invalidateHeiProcessingRecipeSnapshot() {
        this.heiProcessingRecipeSnapshot = null;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (isServerSide() && this.clearOnClose) {
            clear();
        }
        super.onContainerClosed(player);
    }

    public void renameProcessingPatternItem(int slotNumber, String name) {
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME_PROCESSING_PATTERN_ITEM, new RenamePatternItemRequest(slotNumber, name));
        }
    }

    private void renameProcessingPatternItem(RenamePatternItemRequest request) {
        if (isClientSide() || request == null) {
            return;
        }

        Slot slot = getSlotByNumber(request.slotNumber);
        if (slot == null) {
            return;
        }
        if (!isProcessingPatternItemSlot(slot)) {
            return;
        }

        GenericStack stack = getProcessingStack(slot);
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
            return;
        }

        ItemStack renamed = itemKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
        if (request.name != null && request.name.length() > MAX_CUSTOM_NAME_LENGTH) {
            return;
        }
        if (request.name == null || request.name.isEmpty()) {
            renamed.clearCustomName();
        } else {
            renamed.setStackDisplayName(request.name);
        }
        setProcessingStack(slot, GenericStack.fromItemStack(renamed));
        getAndUpdateOutput();
        broadcastChanges();
    }

    public boolean isProcessingPatternItemSlot(@Nullable Slot slot) {
        if (slot == null || this.mode != EncodingMode.PROCESSING) {
            return false;
        }

        GenericStack stack = getProcessingStack(slot);
        return stack != null && stack.what() instanceof AEItemKey;
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
        invalidateHeiProcessingRecipeSnapshot();

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

    public void clearProcessingSecondaryOutputs() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_PROCESSING_SECONDARY_OUTPUTS);
            return;
        }
        if (this.mode != EncodingMode.PROCESSING) {
            return;
        }
        invalidateHeiProcessingRecipeSnapshot();

        for (int slot = 1; slot < this.encodedOutputsInv.size(); slot++) {
            this.encodedOutputsInv.setStack(slot, null);
        }
        broadcastChanges();
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

        invalidateHeiProcessingRecipeSnapshot();
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

    @Nullable
    private GenericStack getProcessingStack(Slot slot) {
        for (int i = 0; i < this.processingInputSlots.length; i++) {
            if (this.processingInputSlots[i] == slot) {
                return this.encodedInputsInv.getStack(i);
            }
        }
        for (int i = 0; i < this.processingOutputSlots.length; i++) {
            if (this.processingOutputSlots[i] == slot) {
                return this.encodedOutputsInv.getStack(i);
            }
        }
        return null;
    }

    private void setProcessingStack(Slot slot, @Nullable GenericStack stack) {
        invalidateHeiProcessingRecipeSnapshot();
        for (int i = 0; i < this.processingInputSlots.length; i++) {
            if (this.processingInputSlots[i] == slot) {
                this.encodedInputsInv.setStack(i, stack);
                return;
            }
        }
        for (int i = 0; i < this.processingOutputSlots.length; i++) {
            if (this.processingOutputSlots[i] == slot) {
                this.encodedOutputsInv.setStack(i, stack);
                return;
            }
        }
    }

    private Slot getSlotByNumber(int slotNumber) {
        for (Slot slot : this.inventorySlots) {
            if (slot.slotNumber == slotNumber) {
                return slot;
            }
        }
        return null;
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class RenamePatternItemRequest {
        private final int slotNumber;
        private final String name;

        private RenamePatternItemRequest(int slotNumber, String name) {
            this.slotNumber = slotNumber;
            this.name = name;
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class HeiProcessingRecipeRequest {
        private final String recipeTypeUid;
        private final List<List<String>> inputCandidateKeyTags;

        private HeiProcessingRecipeRequest(String recipeTypeUid, List<List<String>> inputCandidateKeyTags) {
            this.recipeTypeUid = recipeTypeUid;
            this.inputCandidateKeyTags = inputCandidateKeyTags;
        }
    }

    interface ProcessingPatternUploadActions {
        List<PatternContainer> findProcessingPatternUploadTargets(IGrid grid, String recipeTypeUid);

        ProcessingPatternUploadResult uploadProcessingPatternToProvider(ItemStack encodedPattern, IGrid grid,
                                                                        PatternContainer uploadTarget);

        void openProcessingPatternProviderSelect(String initialSearchText, String initialMappingText);

        void sendNoProviderTargetMessage();
    }

    protected record ProcessingPatternUploadPlan(String recipeTypeUid, String initialSearchText,
                                                 String initialMappingText, boolean openProviderSelect) {
        public ProcessingPatternUploadPlan {
            Objects.requireNonNull(recipeTypeUid, "recipeTypeUid");
            Objects.requireNonNull(initialSearchText, "initialSearchText");
            Objects.requireNonNull(initialMappingText, "initialMappingText");
            if (!openProviderSelect && !initialMappingText.isEmpty()) {
                throw new IllegalArgumentException("Automatic processing upload cannot carry provider mapping text");
            }
        }

        private static ProcessingPatternUploadPlan openProviderSelect(String recipeTypeUid, String initialSearchText) {
            return openProviderSelect(recipeTypeUid, initialSearchText, "");
        }

        private static ProcessingPatternUploadPlan openProviderSelect(String recipeTypeUid, String initialSearchText,
                                                                      String initialMappingText) {
            return new ProcessingPatternUploadPlan(recipeTypeUid, initialSearchText, initialMappingText, true);
        }

        private static ProcessingPatternUploadPlan continueAutomaticUpload(String recipeTypeUid,
                                                                           String initialSearchText) {
            return new ProcessingPatternUploadPlan(recipeTypeUid, initialSearchText, "", false);
        }

        private static ProcessingPatternUploadPlan noProviderTarget(String recipeTypeUid, String initialSearchText) {
            return new ProcessingPatternUploadPlan(recipeTypeUid, initialSearchText, "", false);
        }
    }

    record MissingEncodedPatternUploadPlan(String initialSearchText, String initialMappingText,
                                           boolean openProviderSelect) {
        public MissingEncodedPatternUploadPlan {
            Objects.requireNonNull(initialSearchText, "initialSearchText");
            Objects.requireNonNull(initialMappingText, "initialMappingText");
            if (!openProviderSelect && !initialMappingText.isEmpty()) {
                throw new IllegalArgumentException("Missing encoded pattern handling cannot carry mapping text");
            }
        }

        private static MissingEncodedPatternUploadPlan openProviderSelect(String initialSearchText,
                                                                          String initialMappingText) {
            return new MissingEncodedPatternUploadPlan(initialSearchText, initialMappingText, true);
        }

        private static MissingEncodedPatternUploadPlan noEncodedPattern(String initialSearchText) {
            return new MissingEncodedPatternUploadPlan(initialSearchText, "", false);
        }
    }

    public static ProviderSelectOverlayOpenRequest createProviderSelectOverlayOpenRequest(@Nullable String searchText,
                                                                                          @Nullable String mappingText,
                                                                                          int currentNonce) {
        int nextNonce = currentNonce + 1;
        return new ProviderSelectOverlayOpenRequest(nextNonce, searchText, mappingText);
    }

    public record ProviderSelectOverlayOpenRequest(int nonce, String searchText, String mappingText) {
        public ProviderSelectOverlayOpenRequest {
            if (nonce == 0) {
                throw new IllegalArgumentException("Provider select overlay nonce must not be zero");
            }
            searchText = trimProcessingRecipeText(searchText);
            mappingText = normalizeProcessingRecipeTypeUid(mappingText, "provider-select open request");
        }
    }

    public static final class ProviderMappingByIdAction {
        private Long inventoryId;
        private String mappingText;

        @SuppressWarnings("unused")
        public ProviderMappingByIdAction() {
        }

        public ProviderMappingByIdAction(long inventoryId, String mappingText) {
            this.inventoryId = inventoryId;
            this.mappingText = mappingText;
        }

        public boolean hasInventoryId() {
            return this.inventoryId != null;
        }

        public long inventoryId() {
            if (!hasInventoryId()) {
                PatternProviderSelectionSupport.warnProviderAction("mapping-action-accessor:missing-id",
                    "Provider mapping action has no provider-select target id");
                return Long.MIN_VALUE;
            }
            return this.inventoryId;
        }

        public String mappingText() {
            return this.mappingText;
        }
    }

    private record ProviderSelectEntry(long id, PatternContainer container, @Nullable ProviderReference reference,
                                       long identityOrdinal, ProviderDirectoryEntry provider) {
        private ProviderSelectEntry {
            if (id < 0) {
                throw new IllegalArgumentException("Provider-select entry id must not be negative");
            }
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(provider, "provider");
            if (provider.container() != container || !Objects.equals(provider.reference(), reference)) {
                throw new IllegalArgumentException("Provider-select entry does not match its directory metadata");
            }
        }
    }

    private static final class ProviderStamp {
        private final ProviderSelectEntry entry;
        private final int recipeTypeCount;
        private final List<String> recipeTypePreview;

        private ProviderStamp(ProviderSelectEntry entry, PatternProviderMappingData mappingData) {
            this.entry = Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(mappingData, "mappingData");
            this.recipeTypeCount = entry.reference == null ? 0 : mappingData.getRecipeTypeCount(entry.reference);
            this.recipeTypePreview = entry.reference == null ? List.of() : mappingData.getRecipeTypePreview(entry.reference);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProviderStamp that)) {
                return false;
            }
            ProviderSelectEntry leftEntry = this.entry;
            ProviderSelectEntry rightEntry = that.entry;
            ProviderDirectoryEntry left = leftEntry.provider;
            ProviderDirectoryEntry right = rightEntry.provider;
            return leftEntry.id == rightEntry.id
                && leftEntry.container == rightEntry.container
                && leftEntry.identityOrdinal == rightEntry.identityOrdinal
                && left.sortBy() == right.sortBy()
                && left.inventorySize() == right.inventorySize()
                && left.emptySlots() == right.emptySlots()
                && left.acceptsProcessingPatterns() == right.acceptsProcessingPatterns()
                && left.canEditTerminalName() == right.canEditTerminalName()
                && left.canModifyTerminalVisibility() == right.canModifyTerminalVisibility()
                && left.hasLocation() == right.hasLocation()
                && left.locationDimension() == right.locationDimension()
                && left.locationPos() == right.locationPos()
                && left.locationSide() == right.locationSide()
                && Objects.equals(left.group(), right.group())
                && Objects.equals(left.reference(), right.reference())
                && this.recipeTypeCount == that.recipeTypeCount
                && this.recipeTypePreview.equals(that.recipeTypePreview);
        }

        @Override
        public int hashCode() {
            ProviderSelectEntry valueEntry = this.entry;
            ProviderDirectoryEntry value = valueEntry.provider;
            return Objects.hash(valueEntry.id, System.identityHashCode(valueEntry.container),
                valueEntry.identityOrdinal, value.sortBy(), value.group(), value.inventorySize(), value.emptySlots(),
                value.acceptsProcessingPatterns(), value.canEditTerminalName(), value.canModifyTerminalVisibility(),
                value.reference(), value.hasLocation(), value.locationDimension(), value.locationPos(),
                value.locationSide(), this.recipeTypeCount, this.recipeTypePreview);
        }
    }

    private record HeiProcessingInputSnapshot(List<AEKey> candidates, long amount) {
        private HeiProcessingInputSnapshot {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("HEI processing input candidates must not be empty");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("HEI processing input amount must be positive");
            }
        }
    }

    private record HeiProcessingRecipeSnapshot(String recipeTypeUid,
                                               List<HeiProcessingInputSnapshot> inputsBySlot,
                                               List<GenericStack> outputsBySlot) {
        private HeiProcessingRecipeSnapshot {
            recipeTypeUid = RecipeTypeUid.requireValid(recipeTypeUid);
            inputsBySlot = List.copyOf(Objects.requireNonNull(inputsBySlot, "inputsBySlot"));
            outputsBySlot = List.copyOf(Objects.requireNonNull(outputsBySlot, "outputsBySlot"));
        }
    }
}
