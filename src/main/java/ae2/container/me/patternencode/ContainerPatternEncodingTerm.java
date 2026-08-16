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
import ae2.container.me.patternencode.PatternProviderUploadService.ProcessingPatternUploadPreparation;
import ae2.container.me.patternencode.PatternProviderUploadService.ProcessingPatternUploadResult;
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
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.me.service.ActivePatternProviderDirectory.ProviderKey;
import ae2.parts.encoding.EncodingMode;
import ae2.parts.encoding.PatternEncodingLogic;
import ae2.parts.encoding.ProcessingPatternAmountHelper;
import ae2.util.ConfigGuiInventory;
import ae2.util.ConfigInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
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
    implements PatternModifierPanel.Host, IProviderSelectionEndpoint {
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
    private static final String ACTION_REBUILD_MAPPINGS_FROM_ACTIVE_PROVIDERS = "rebuildMappingsFromActiveProviders";
    private static final int MAX_RENAME_PROCESSING_PATTERN_ITEM_PAYLOAD_LENGTH = 512;
    private static final int MAX_SET_HEI_PROCESSING_RECIPE_PAYLOAD_LENGTH = 16384;
    static final int MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH = MAX_SET_HEI_PROCESSING_RECIPE_PAYLOAD_LENGTH;
    // Fixed JSON syntax, field names and the longest decimal forms in a valid focused request.
    private static final int MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_FIXED_JSON_LENGTH = 158;
    private static final int MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_PAYLOAD_LENGTH =
        MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_FIXED_JSON_LENGTH
            + ProviderPageLimits.MAX_QUERY_UTF16_LENGTH * 6;
    private static final int MAX_PROVIDER_MAPPING_PAGE_REQUEST_PAYLOAD_LENGTH = 128;
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
    private final ProviderSelectionSession providerSelectionSession;
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
    private int providerSelectionOverlayRequestNonce;
    @GuiSync(84)
    private String providerSelectionOverlaySearchText = "";
    @GuiSync(83)
    private String providerSelectionOverlayMappingText = "";
    @Nullable
    private IRecipe currentRecipe;
    @Nullable
    private HeiProcessingRecipeSnapshot heiProcessingRecipeSnapshot;
    private boolean changingEncodedPatternSlotInternally;
    private boolean clearOnClose;

    public ContainerPatternEncodingTerm(InventoryPlayer ip, IPatternTerminalGuiHost host) {
        this(GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL, ip, host, true);
    }

    public ContainerPatternEncodingTerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, IPatternTerminalGuiHost host,
                                        boolean bindInventory) {
        this(guiKey, ip, host, bindInventory, true);
    }

    protected ContainerPatternEncodingTerm(GuiIds.GuiKey guiKey, InventoryPlayer ip, IPatternTerminalGuiHost host,
                                           boolean bindInventory, boolean registerProviderSelectionActions) {
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
        this.providerSelectionSession = new ProviderSelectionSession(new ProviderSelectionSession.Host() {
            @Override
            public boolean isLinkConnected() {
                return ContainerPatternEncodingTerm.this.getHost().getLinkStatus().connected();
            }

            @Override
            public IGrid getProviderSelectionGrid() {
                return ContainerPatternEncodingTerm.this.getProviderSelectionGrid();
            }

            @Override
            public IGrid requireProviderSelectionGrid() {
                return ContainerPatternEncodingTerm.this.requireProviderSelectionGrid();
            }

            @Override
            public PatternProviderMappingData getProviderMappingData() {
                return ContainerPatternEncodingTerm.this.getPatternProviderMappingData();
            }

            @Override
            public EntityPlayer getPlayer() {
                return ContainerPatternEncodingTerm.this.getPlayer();
            }

            @Override
            public World getWorld() {
                return ContainerPatternEncodingTerm.this.getPlayer().world;
            }

            @Override
            public int getWindowId() {
                return ContainerPatternEncodingTerm.this.windowId;
            }

            @Override
            public ItemStack getEncodedPattern() {
                return ContainerPatternEncodingTerm.this.encodedPatternSlot.getStack().copy();
            }

            @Override
            public void setEncodedPattern(ItemStack encodedPattern) {
                ContainerPatternEncodingTerm.this.putEncodedPatternStackFromContainer(encodedPattern);
            }

            @Override
            public ProcessingPatternUploadResult uploadProcessingPattern(ItemStack encodedPattern, IGrid grid,
                                                                          ProviderKey target) {
                return PatternProviderUploadService.tryUploadProcessingPatternToProvider(
                    ContainerPatternEncodingTerm.this.getPlayer(),
                    (IPatternTerminalGuiHost) ContainerPatternEncodingTerm.this.getHost(), grid, target, encodedPattern);
            }

            @Override
            public ProcessingPatternUploadPreparation prepareProcessingPatternUpload(ItemStack encodedPattern,
                                                                                      IGrid grid,
                                                                                      ProviderKey target) {
                return PatternProviderUploadService.prepareProcessingPatternUpload(
                    ContainerPatternEncodingTerm.this.getPlayer(),
                    (IPatternTerminalGuiHost) ContainerPatternEncodingTerm.this.getHost(), grid, target, encodedPattern);
            }

            @Override
            public void sendProviderDirectoryPage(ProviderDirectoryPage page) {
                ContainerPatternEncodingTerm.this.sendProviderDirectoryPage(page);
            }

            @Override
            public void sendProviderMappingPage(ProviderMappingPage page) {
                ContainerPatternEncodingTerm.this.sendProviderMappingPage(page);
            }

            @Override
            public void syncProviderDirectoryRevision(long revision) {
                ContainerPatternEncodingTerm.this.providerDirectoryRevision = revision;
            }

            @Override
            public void syncProviderSelectionOverlay(ProviderSelectionSession.ProviderSelectionOverlayOpenRequest request) {
                ContainerPatternEncodingTerm.this.providerSelectionOverlayRequestNonce = request.nonce();
                ContainerPatternEncodingTerm.this.providerSelectionOverlaySearchText = request.searchText();
                ContainerPatternEncodingTerm.this.providerSelectionOverlayMappingText = request.mappingText();
            }
        });

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
        if (registerProviderSelectionActions) {
            registerProviderSelectionActions();
        }
        this.patternModifierPanel = new PatternModifierPanel(this);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();

        updateSlotVisibility();
        getAndUpdateOutput();

        tryAutoFillBlankPatterns();
    }

    private void registerProviderSelectionActions() {
        registerClientAction(ACTION_UPLOAD_PROCESSING_PATTERN_TO_PROVIDER,
            ProviderSelectionSession.ProviderEntryAction.class, this::handleProviderEntryUpload);
        registerClientAction(ACTION_BIND_PROVIDER_MAPPING, ProviderSelectionSession.ProviderMappingAction.class,
            MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH, this::handleProviderMappingBind);
        registerClientAction(ACTION_BIND_AND_UPLOAD_PROVIDER_MAPPING, ProviderSelectionSession.ProviderMappingAction.class,
            MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH, this::handleProviderMappingBindAndUpload);
        registerClientAction(ACTION_UNBIND_PROVIDER_MAPPING, ProviderSelectionSession.ProviderMappingAction.class,
            MAX_PROVIDER_MAPPING_ACTION_PAYLOAD_LENGTH, this::handleProviderMappingUnbind);
        registerClientAction(ACTION_REQUEST_PROVIDER_DIRECTORY_PAGE, ProviderDirectoryPageRequest.class,
            MAX_PROVIDER_DIRECTORY_PAGE_REQUEST_PAYLOAD_LENGTH, this::handleProviderDirectoryPageRequest);
        registerClientAction(ACTION_REQUEST_PROVIDER_MAPPING_PAGE, ProviderMappingPageRequest.class,
            MAX_PROVIDER_MAPPING_PAGE_REQUEST_PAYLOAD_LENGTH, this::handleProviderMappingPageRequest);
        registerClientAction(ACTION_REBUILD_MAPPINGS_FROM_ACTIVE_PROVIDERS,
            this::handleRebuildMappingsFromActiveProviders);
    }

    private void handleProviderEntryUpload(ProviderSelectionSession.ProviderEntryAction action) {
        if (isServerSide()) {
            this.providerSelectionSession.uploadProcessingPatternToProvider(action);
        }
    }

    private void handleProviderMappingBind(ProviderSelectionSession.ProviderMappingAction action) {
        if (isServerSide()) {
            this.providerSelectionSession.bindProviderMapping(action);
        }
    }

    private void handleProviderMappingBindAndUpload(ProviderSelectionSession.ProviderMappingAction action) {
        if (isServerSide()) {
            this.providerSelectionSession.bindAndUploadProcessingPatternToProvider(action);
        }
    }

    private void handleProviderMappingUnbind(ProviderSelectionSession.ProviderMappingAction action) {
        if (isServerSide()) {
            this.providerSelectionSession.unbindProviderMapping(action);
        }
    }

    private void handleProviderDirectoryPageRequest(ProviderDirectoryPageRequest request) {
        if (isServerSide()) {
            this.providerSelectionSession.requestProviderDirectoryPage(request);
        }
    }

    private void handleProviderMappingPageRequest(ProviderMappingPageRequest request) {
        if (isServerSide()) {
            this.providerSelectionSession.requestProviderMappingPage(request);
        }
    }

    private void handleRebuildMappingsFromActiveProviders() {
        if (isServerSide()) {
            this.providerSelectionSession.rebuildMappingsFromActiveProviders();
        }
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
            this.providerSelectionSession.onBroadcastChanges();
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
            openProcessingPatternProviderSelection("", getValidHeiProcessingRecipeTypeUid());
            return;
        }

        ItemStack encodedPattern = this.encodedPatternSlot.getStack();
        if (!PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(encodedPattern, getPlayer().world);
        IGrid grid = getProviderSelectionGrid();
        if (this.mode == EncodingMode.PROCESSING) {
            if (!(details instanceof AEProcessingPattern processingPattern)) {
                getPlayer().sendStatusMessage(PlayerMessages.PatternUploadProcessingOnly.text(), false);
                return;
            }

            uploadProcessingPattern(encodedPattern, processingPattern, grid);
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
                                         @Nullable IGrid grid) {
        uploadProcessingPattern(encodedPattern, grid, processingPattern.getRecipeTypeUid(),
            processingPattern.getRecipeType(), new ProcessingPatternUploadActions() {
                @Override
                public List<PatternContainer> findProcessingPatternUploadTargets(IGrid grid, String recipeTypeUid) {
                    return PatternProviderUploadService.findProcessingPatternUploadTargets(
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
                public void openProcessingPatternProviderSelection(String initialSearchText, String initialMappingText) {
                    ContainerPatternEncodingTerm.this.openProcessingPatternProviderSelection(initialSearchText,
                        initialMappingText);
                }

                @Override
                public void sendNoProviderTargetMessage() {
                    getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
                }
            });
    }

    static void uploadProcessingPattern(ItemStack encodedPattern, @Nullable IGrid grid,
                                        @Nullable String recipeTypeUid, @Nullable String recipeTypeTitle,
                                        ProcessingPatternUploadActions actions) {
        Objects.requireNonNull(actions, "actions");
        PatternProviderUploadService.UploadPlan uploadPlan =
            PatternProviderUploadService.getImmediateProcessingPatternUploadPlan(recipeTypeUid, recipeTypeTitle);
        if (uploadPlan.openProviderSelection()) {
            actions.openProcessingPatternProviderSelection(uploadPlan.initialSearchText(),
                uploadPlan.initialMappingText());
            return;
        }

        String automaticRecipeTypeUid = uploadPlan.recipeTypeUid();

        if (grid == null) {
            PatternProviderUploadService.UploadPlan fallbackPlan =
                PatternProviderUploadService.getProcessingPatternFallbackPlan(recipeTypeUid, recipeTypeTitle, true);
            actions.openProcessingPatternProviderSelection(fallbackPlan.initialSearchText(),
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

    protected void openProcessingPatternProviderSelection(@Nullable String initialSearchText,
                                                       @Nullable String initialMappingText) {
        openProviderSelection(initialSearchText, initialMappingText);
    }

    private static void handleNoProcessingPatternAutomaticTarget(@Nullable IGrid grid, String recipeTypeUid,
                                                                 @Nullable String recipeTypeTitle,
                                                                 ProcessingPatternUploadActions actions) {
        PatternProviderUploadService.UploadPlan fallbackPlan =
            PatternProviderUploadService.getProcessingPatternUploadTargetPlan(0, recipeTypeUid, recipeTypeTitle,
                grid != null && PatternProviderUploadService.hasAvailableProvider(grid));
        if (!fallbackPlan.openProviderSelection()) {
            actions.sendNoProviderTargetMessage();
            return;
        }
        actions.openProcessingPatternProviderSelection(fallbackPlan.initialSearchText(),
            fallbackPlan.initialMappingText());
    }

    private static void uploadProcessingPatternToProviders(ItemStack encodedPattern, IGrid grid,
                                                           List<PatternContainer> uploadTargets,
                                                           String recipeTypeUid,
                                                           @Nullable String recipeTypeTitle,
                                                           ProcessingPatternUploadActions actions) {
        PatternProviderUploadService.UploadPlan uploadPlan =
            PatternProviderUploadService.getProcessingPatternUploadTargetPlan(uploadTargets.size(), recipeTypeUid,
                recipeTypeTitle, PatternProviderUploadService.hasAvailableProvider(grid));
        if (uploadPlan.openProviderSelection()) {
            actions.openProcessingPatternProviderSelection(uploadPlan.initialSearchText(),
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
            && PatternProviderUploadService.hasAvailableProvider(grid)) {
            PatternProviderUploadService.UploadPlan fallbackPlan =
                PatternProviderUploadService.getProcessingPatternFallbackPlan(recipeTypeUid, recipeTypeTitle, true);
            actions.openProcessingPatternProviderSelection(fallbackPlan.initialSearchText(),
                fallbackPlan.initialMappingText());
        }
    }

    protected ProcessingPatternUploadResult uploadProcessingPatternToProvider(ItemStack encodedPattern, IGrid grid,
                                                                              PatternContainer uploadTarget) {
        return PatternProviderUploadService.tryUploadProcessingPatternToProvider(getPlayer(),
            (IPatternTerminalGuiHost) getHost(), grid, uploadTarget, encodedPattern);
    }

    @Override
    public void uploadProcessingPatternToProvider(long directoryRevision, long providerEntryId) {
        ProviderSelectionSession.ProviderEntryAction action =
            new ProviderSelectionSession.ProviderEntryAction(directoryRevision, providerEntryId);
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_PROCESSING_PATTERN_TO_PROVIDER, action);
            return;
        }
        this.providerSelectionSession.uploadProcessingPatternToProvider(action);
    }

    @Override
    public void bindProviderMapping(long directoryRevision, long providerEntryId, String mappingText) {
        ProviderSelectionSession.ProviderMappingAction action =
            new ProviderSelectionSession.ProviderMappingAction(directoryRevision, providerEntryId, mappingText);
        if (isClientSide()) {
            sendClientAction(ACTION_BIND_PROVIDER_MAPPING, action);
            return;
        }
        this.providerSelectionSession.bindProviderMapping(action);
    }

    @Override
    public void bindAndUploadProcessingPatternToProvider(long directoryRevision, long providerEntryId,
                                                          String recipeTypeUid) {
        ProviderSelectionSession.ProviderMappingAction action =
            new ProviderSelectionSession.ProviderMappingAction(directoryRevision, providerEntryId, recipeTypeUid);
        if (isClientSide()) {
            sendClientAction(ACTION_BIND_AND_UPLOAD_PROVIDER_MAPPING, action);
            return;
        }
        this.providerSelectionSession.bindAndUploadProcessingPatternToProvider(action);
    }

    @Override
    public void unbindProviderMapping(long directoryRevision, long providerEntryId) {
        unbindProviderMapping(directoryRevision, providerEntryId, null);
    }

    @Override
    public void unbindProviderMapping(long directoryRevision, long providerEntryId, @Nullable String recipeType) {
        ProviderSelectionSession.ProviderMappingAction action =
            new ProviderSelectionSession.ProviderMappingAction(directoryRevision, providerEntryId, recipeType);
        if (isClientSide()) {
            sendClientAction(ACTION_UNBIND_PROVIDER_MAPPING, action);
            return;
        }
        this.providerSelectionSession.unbindProviderMapping(action);
    }

    @Override
    public void rebuildMappingsFromActiveProviders() {
        if (isClientSide()) {
            sendClientAction(ACTION_REBUILD_MAPPINGS_FROM_ACTIVE_PROVIDERS);
            return;
        }
        this.providerSelectionSession.rebuildMappingsFromActiveProviders();
    }

    @Override
    public void requestProviderDirectoryPage(long nonce, String query, int page,
                                             @Nullable ProviderDirectoryPageRequest.Focus focus) {
        ProviderDirectoryPageRequest request = new ProviderDirectoryPageRequest(nonce, query, page, focus);
        if (isClientSide()) {
            sendClientAction(ACTION_REQUEST_PROVIDER_DIRECTORY_PAGE, request);
            return;
        }
        this.providerSelectionSession.requestProviderDirectoryPage(request);
    }

    @Override
    public void requestProviderMappingPage(long nonce, long directoryRevision, long providerEntryId, int page) {
        ProviderMappingPageRequest request =
            new ProviderMappingPageRequest(nonce, directoryRevision, providerEntryId, page);
        if (isClientSide()) {
            sendClientAction(ACTION_REQUEST_PROVIDER_MAPPING_PAGE, request);
            return;
        }
        this.providerSelectionSession.requestProviderMappingPage(request);
    }

    private void openProviderSelection(@Nullable String initialSearchText, @Nullable String initialMappingText) {
        if (isClientSide()) {
            return;
        }

        this.providerSelectionSession.open(initialSearchText, initialMappingText);
    }

    @Nullable
    protected IGrid getProviderSelectionGrid() {
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
    private IGrid requireProviderSelectionGrid() {
        ILinkStatus linkStatus = getHost().getLinkStatus();
        if (!linkStatus.connected()) {
            getPlayer().sendStatusMessage(linkStatus.statusDescription() != null
                ? linkStatus.statusDescription()
                : PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return null;
        }
        IGrid grid = getProviderSelectionGrid();
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

        String recipeTypeUid = getValidHeiProcessingRecipeTypeUid();
        return PatternDetailsHelper.encodeProcessingPattern(Arrays.asList(inputs), Arrays.asList(outputs),
            recipeTypeUid);
    }

    @Nullable
    private String getValidHeiProcessingRecipeTypeUid() {
        if (this.mode != EncodingMode.PROCESSING || this.heiProcessingRecipeSnapshot == null) {
            return null;
        }

        List<List<AEKey>> candidatesBySlot = this.heiProcessingRecipeSnapshot.inputCandidatesBySlot;
        int nonEmptyInputs = 0;
        for (int slot = 0; slot < this.encodedInputsInv.size(); slot++) {
            AEKey currentKey = this.encodedInputsInv.getKey(slot);
            if (currentKey == null) {
                continue;
            }

            if (slot >= candidatesBySlot.size() || !candidatesBySlot.get(slot).contains(currentKey)) {
                return null;
            }
            nonEmptyInputs++;
        }

        return nonEmptyInputs == candidatesBySlot.size() ? this.heiProcessingRecipeSnapshot.recipeTypeUid : null;
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
        if (request == null || request.recipeTypeUid == null || request.recipeTypeUid.isEmpty()
            || request.inputCandidateKeyTags == null || request.inputCandidateKeyTags.isEmpty()) {
            return null;
        }
        if (request.inputCandidateKeyTags.size() > MAX_HEI_PROCESSING_RECIPE_INPUT_SLOTS) {
            return null;
        }

        List<List<AEKey>> candidatesBySlot = new ArrayList<>(request.inputCandidateKeyTags.size());
        int totalCandidates = 0;
        for (List<String> candidateTags : request.inputCandidateKeyTags) {
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
            candidatesBySlot.add(candidates);
        }

        return new HeiProcessingRecipeSnapshot(request.recipeTypeUid, candidatesBySlot);
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
    public int getProviderSelectionOverlayRequestNonce() {
        return this.providerSelectionOverlayRequestNonce;
    }

    @Override
    public String getProviderSelectionOverlaySearchText() {
        return this.providerSelectionOverlaySearchText;
    }

    @Override
    public String getProviderSelectionOverlayMappingText() {
        return this.providerSelectionOverlayMappingText;
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
            if (recipeTypeUid == null || recipeTypeUid.isEmpty()) {
                return;
            }
            sendClientAction(ACTION_SET_HEI_PROCESSING_RECIPE,
                new HeiProcessingRecipeRequest(recipeTypeUid, inputCandidateKeyTags));
        }
    }

    private void setHeiProcessingRecipe(HeiProcessingRecipeRequest request) {
        if (isClientSide()) {
            return;
        }

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

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (isServerSide()) {
            this.providerSelectionSession.close();
        }
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

        void openProcessingPatternProviderSelection(String initialSearchText, String initialMappingText);

        void sendNoProviderTargetMessage();
    }

    private record HeiProcessingRecipeSnapshot(String recipeTypeUid, List<List<AEKey>> inputCandidatesBySlot) {
    }
}
