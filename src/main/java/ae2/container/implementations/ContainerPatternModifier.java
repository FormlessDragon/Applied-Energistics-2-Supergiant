package ae2.container.implementations;

import ae2.api.inventories.InternalInventory;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantic;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.OutputSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.definitions.AEItems;
import ae2.helpers.patternmodifier.PatternModifierActions;
import ae2.helpers.patternmodifier.PatternModifierLogic;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.items.contents.PatternModifierGuiHost;
import ae2.items.tools.PatternModifierItem;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ContainerPatternModifier extends AEBaseContainer {
    public static final SlotSemantic PATTERN_MODIFIER_TARGET = SlotSemantics.register("PATTERN_MODIFIER_TARGET", false);
    public static final SlotSemantic PATTERN_MODIFIER_CLONE = SlotSemantics.register("PATTERN_MODIFIER_CLONE", false);
    public static final SlotSemantic PATTERN_MODIFIER_BLANKS = SlotSemantics.register("PATTERN_MODIFIER_BLANKS", false);
    public static final SlotSemantic PATTERN_MODIFIER_REPLACE_TARGET =
        SlotSemantics.register("PATTERN_MODIFIER_REPLACE_TARGET", false);
    public static final SlotSemantic PATTERN_MODIFIER_REPLACE_WITH =
        SlotSemantics.register("PATTERN_MODIFIER_REPLACE_WITH", false);

    private static final String ACTION_NEXT_PAGE = "nextPage";
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_CLONE = "clone";
    private static final String ACTION_REPLACE = "replace";
    private static final String ACTION_MULTIPLY_2 = "multiply2";
    private static final String ACTION_MULTIPLY_3 = "multiply3";
    private static final String ACTION_MULTIPLY_5 = "multiply5";
    private static final String ACTION_MULTIPLY_8 = "multiply8";
    private static final String ACTION_DIVIDE_2 = "divide2";
    private static final String ACTION_DIVIDE_3 = "divide3";
    private static final String ACTION_DIVIDE_5 = "divide5";
    private static final String ACTION_DIVIDE_8 = "divide8";
    private static final String ACTION_SET_SUBSTITUTE_TRUE = "substituteTrue";
    private static final String ACTION_SET_SUBSTITUTE_FALSE = "substituteFalse";
    private static final String ACTION_SET_FLUID_SUBSTITUTE_TRUE = "fluidSubstituteTrue";
    private static final String ACTION_SET_FLUID_SUBSTITUTE_FALSE = "fluidSubstituteFalse";
    private static final String ACTION_TOGGLE_PATTERN_INVENTORY = "togglePatternInventory";
    private static final String ACTION_SET_PROVIDER_PAGE = "setProviderPage";

    private final PatternModifierGuiHost host;
    @Nullable
    private final PatternProviderLogicHost patternProvider;
    @GuiSync(33)
    private final boolean providerInventoryAvailable;
    @GuiSync(31)
    private int page;
    @GuiSync(32)
    private boolean providerInventoryMode;
    @GuiSync(34)
    private int activePatternSlots;
    @GuiSync(35)
    private int providerPage;
    @GuiSync(36)
    private int providerPageCount = 1;

    public ContainerPatternModifier(InventoryPlayer ip, PatternModifierGuiHost host) {
        super(ip, host);
        this.host = host;
        this.patternProvider = host.getPatternProvider();
        this.providerInventoryAvailable = this.patternProvider != null;
        this.providerInventoryMode = this.providerInventoryAvailable;
        this.activePatternSlots = getCurrentPatternSlotCount();
        this.providerPageCount = getCurrentProviderPageCount();

        for (int slot = 0; slot < PatternProviderCapacity.PATTERN_MODIFIER_SLOTS_PER_PAGE; slot++) {
            PatternTargetInventory patternTargetInventory = new PatternTargetInventory();
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN,
                patternTargetInventory, slot), SlotSemantics.ENCODED_PATTERN);
        }

        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN,
            host.getTargetInventory(), 0), PATTERN_MODIFIER_TARGET);
        this.addSlot(new OutputSlot(host.getClonePatternInventory(), 0, 0, 0), PATTERN_MODIFIER_CLONE);
        for (int slot = 0; slot < host.getBlankPatternInventory().size(); slot++) {
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.BLANK_PATTERN,
                host.getBlankPatternInventory(), slot), PATTERN_MODIFIER_BLANKS);
        }
        this.addSlot(new FakeSlot(host.getReplaceInventory(), 0, 0, 0), PATTERN_MODIFIER_REPLACE_TARGET);
        this.addSlot(new FakeSlot(host.getReplaceInventory(), 1, 0, 0), PATTERN_MODIFIER_REPLACE_WITH);
        this.addPlayerInventorySlots(8, 128);

        registerActions();
        updateVisibleSlots();
    }

    private static String actionForAmountModification(int factor, boolean divide) {
        if (!PatternModifierActions.isSupportedStandaloneFactor(factor)) {
            throw new IllegalArgumentException("Unsupported Pattern Modifier factor: " + factor);
        }
        return switch (factor) {
            case 2 -> divide ? ACTION_DIVIDE_2 : ACTION_MULTIPLY_2;
            case 3 -> divide ? ACTION_DIVIDE_3 : ACTION_MULTIPLY_3;
            case 5 -> divide ? ACTION_DIVIDE_5 : ACTION_MULTIPLY_5;
            case 8 -> divide ? ACTION_DIVIDE_8 : ACTION_MULTIPLY_8;
            default -> throw new IllegalArgumentException("Unsupported Pattern Modifier factor: " + factor);
        };
    }

    private static String actionForCraftingProperty(PatternModifierLogic.CraftingProperty property, boolean value) {
        return switch (property) {
            case SUBSTITUTE -> value ? ACTION_SET_SUBSTITUTE_TRUE : ACTION_SET_SUBSTITUTE_FALSE;
            case FLUID_SUBSTITUTE -> value ? ACTION_SET_FLUID_SUBSTITUTE_TRUE : ACTION_SET_FLUID_SUBSTITUTE_FALSE;
        };
    }

    public int getPage() {
        return this.page;
    }

    public boolean isProviderInventoryMode() {
        return this.providerInventoryMode;
    }

    public boolean isProviderInventoryAvailable() {
        return this.providerInventoryAvailable;
    }

    public int getActivePatternSlots() {
        return this.activePatternSlots;
    }

    public int getProviderPage() {
        return this.providerPage;
    }

    public void setProviderPage(int page) {
        if (!this.providerInventoryAvailable) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PROVIDER_PAGE, page);
            return;
        }
        this.providerPage = Math.clamp(page, 0, getCurrentProviderPageCount() - 1);
        this.providerPageCount = getCurrentProviderPageCount();
        this.activePatternSlots = getCurrentPatternSlotCount();
        updateVisibleSlots();
        detectAndSendChanges();
    }

    public int getProviderPageCount() {
        return this.providerPageCount;
    }

    public void togglePatternInventory() {
        if (!this.providerInventoryAvailable) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PATTERN_INVENTORY);
            return;
        }
        this.providerInventoryMode = !this.providerInventoryMode;
        this.activePatternSlots = getCurrentPatternSlotCount();
        this.providerPage = Math.min(this.providerPage, getCurrentProviderPageCount() - 1);
        this.providerPageCount = getCurrentProviderPageCount();
        updateVisibleSlots();
        detectAndSendChanges();
    }

    public void nextPage() {
        if (isClientSide()) {
            this.page = (this.page + 1) % 4;
            updateVisibleSlots();
            sendClientAction(ACTION_NEXT_PAGE);
        } else {
            this.page = (this.page + 1) % 4;
            updateVisibleSlots();
            detectAndSendChanges();
        }
    }

    public void clearPatterns() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }

        forEachPatternSlot(slot -> setPatternStackForOperation(slot,
            PatternModifierLogic.clearPattern(getPatternStackForOperation(slot))));
    }

    public void modifyAmounts(int factor, boolean divide) {
        if (isClientSide()) {
            sendClientAction(actionForAmountModification(factor, divide));
            return;
        }

        forEachPatternSlot(slot -> setPatternStackForOperation(slot,
            PatternModifierLogic.modifyAmounts(getPatternStackForOperation(slot), getPlayer().world, factor, divide)));
    }

    public void replace() {
        if (isClientSide()) {
            sendClientAction(ACTION_REPLACE);
            return;
        }

        ItemStack replaceTarget = this.host.getReplaceInventory().getStackInSlot(0);
        ItemStack replaceWith = this.host.getReplaceInventory().getStackInSlot(1);
        forEachPatternSlot(slot -> setPatternStackForOperation(slot,
            PatternModifierLogic.replace(getPatternStackForOperation(slot), getPlayer().world, replaceTarget,
                replaceWith)));
    }

    public void setCraftingProperty(PatternModifierLogic.CraftingProperty property, boolean value) {
        if (isClientSide()) {
            sendClientAction(actionForCraftingProperty(property, value));
            return;
        }

        forEachPatternSlot(slot -> setPatternStackForOperation(slot,
            PatternModifierLogic.setCraftingProperty(getPatternStackForOperation(slot), getPlayer().world, property,
                value)));
    }

    public void clonePattern() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLONE);
            return;
        }

        ItemStack target = this.host.getTargetInventory().getStackInSlot(0);
        if (!PatternModifierLogic.isEncodedPattern(target, getPlayer().world)) {
            return;
        }

        ItemStack existingClone = this.host.getClonePatternInventory().getStackInSlot(0);
        if (existingClone.isEmpty()) {
            if (!consumeBlankPattern()) {
                return;
            }
            this.host.getClonePatternInventory().setItemDirect(0, target.copy());
        } else if (PatternModifierLogic.isEncodedPattern(existingClone, getPlayer().world)) {
            this.host.getClonePatternInventory().setItemDirect(0, target.copy());
        }
    }

    public void updateVisibleSlots() {
        if (isServerSide()) {
            this.providerPage = Math.min(this.providerPage, getCurrentProviderPageCount() - 1);
            this.providerPageCount = getCurrentProviderPageCount();
            this.activePatternSlots = getCurrentPatternSlotCount();
        }
        boolean patternPage = this.page == 0 || this.page == 1 || this.page == 2;
        for (var slot : getSlots(SlotSemantics.ENCODED_PATTERN)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(patternPage && appEngSlot.getSlotIndex() < this.activePatternSlots);
                appEngSlot.setActive(patternPage && appEngSlot.getSlotIndex() < this.activePatternSlots);
            }
        }
        setSlotsEnabled(PATTERN_MODIFIER_REPLACE_TARGET, this.page == 1);
        setSlotsEnabled(PATTERN_MODIFIER_REPLACE_WITH, this.page == 1);
        setSlotsEnabled(PATTERN_MODIFIER_TARGET, this.page == 3);
        setSlotsEnabled(PATTERN_MODIFIER_CLONE, this.page == 3);
        setSlotsEnabled(PATTERN_MODIFIER_BLANKS, this.page == 3);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.providerPage = Math.min(this.providerPage, getCurrentProviderPageCount() - 1);
            this.providerPageCount = getCurrentProviderPageCount();
            this.activePatternSlots = getCurrentPatternSlotCount();
        }
        updateVisibleSlots();
        super.broadcastChanges();
    }

    @Override
    public void onClientDataSync(ShortSet updatedFields) {
        super.onClientDataSync(updatedFields);
        updateVisibleSlots();
    }

    private void registerActions() {
        registerClientAction(ACTION_NEXT_PAGE, this::nextPage);
        registerClientAction(ACTION_CLEAR, this::clearPatterns);
        registerClientAction(ACTION_CLONE, this::clonePattern);
        registerClientAction(ACTION_REPLACE, this::replace);
        registerClientAction(ACTION_MULTIPLY_2, () -> modifyAmounts(2, false));
        registerClientAction(ACTION_MULTIPLY_3, () -> modifyAmounts(3, false));
        registerClientAction(ACTION_MULTIPLY_5, () -> modifyAmounts(5, false));
        registerClientAction(ACTION_MULTIPLY_8, () -> modifyAmounts(8, false));
        registerClientAction(ACTION_DIVIDE_2, () -> modifyAmounts(2, true));
        registerClientAction(ACTION_DIVIDE_3, () -> modifyAmounts(3, true));
        registerClientAction(ACTION_DIVIDE_5, () -> modifyAmounts(5, true));
        registerClientAction(ACTION_DIVIDE_8, () -> modifyAmounts(8, true));
        registerClientAction(ACTION_SET_SUBSTITUTE_TRUE,
            () -> setCraftingProperty(PatternModifierLogic.CraftingProperty.SUBSTITUTE, true));
        registerClientAction(ACTION_SET_SUBSTITUTE_FALSE,
            () -> setCraftingProperty(PatternModifierLogic.CraftingProperty.SUBSTITUTE, false));
        registerClientAction(ACTION_SET_FLUID_SUBSTITUTE_TRUE,
            () -> setCraftingProperty(PatternModifierLogic.CraftingProperty.FLUID_SUBSTITUTE, true));
        registerClientAction(ACTION_SET_FLUID_SUBSTITUTE_FALSE,
            () -> setCraftingProperty(PatternModifierLogic.CraftingProperty.FLUID_SUBSTITUTE, false));
        registerClientAction(ACTION_TOGGLE_PATTERN_INVENTORY, this::togglePatternInventory);
        registerClientAction(ACTION_SET_PROVIDER_PAGE, Integer.class, this::setProviderPage);
    }

    private void setSlotsEnabled(SlotSemantic semantic, boolean enabled) {
        for (var slot : getSlots(semantic)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(enabled);
            }
        }
    }

    private void forEachPatternSlot(SlotConsumer consumer) {
        int slots = this.providerInventoryMode && this.patternProvider != null
            ? this.patternProvider.getLogic().getActivePatternSlots()
            : PatternModifierItem.PATTERN_SLOTS;
        for (int slot = 0; slot < slots; slot++) {
            consumer.accept(slot);
        }
    }

    private ItemStack getPatternStackForOperation(int slot) {
        if (this.providerInventoryMode && this.patternProvider != null) {
            return this.patternProvider.getLogic().getPatternInv().getStackInSlot(slot);
        }
        return this.host.getPatternInventory().getStackInSlot(slot);
    }

    private void setPatternStackForOperation(int slot, ItemStack stack) {
        if (this.providerInventoryMode && this.patternProvider != null) {
            this.patternProvider.getLogic().getPatternInv().setItemDirect(slot, stack);
        } else {
            this.host.getPatternInventory().setItemDirect(slot, stack);
        }
    }

    private int getCurrentPatternSlotCount() {
        if (this.providerInventoryMode && this.patternProvider != null) {
            int firstSlot = PatternProviderCapacity.getFirstPatternModifierSlotOnPage(this.providerPage);
            return Math.clamp(this.patternProvider.getLogic().getActivePatternSlots() - firstSlot, 0,
                PatternProviderCapacity.PATTERN_MODIFIER_SLOTS_PER_PAGE);
        }
        return PatternModifierItem.PATTERN_SLOTS;
    }

    private int getCurrentProviderPageCount() {
        if (this.patternProvider == null) {
            return 1;
        }
        return PatternProviderCapacity.getPatternModifierPageCount(
            this.patternProvider.getLogic().getActivePatternSlots());
    }

    private boolean consumeBlankPattern() {
        for (int slot = 0; slot < this.host.getBlankPatternInventory().size(); slot++) {
            ItemStack stack = this.host.getBlankPatternInventory().getStackInSlot(slot);
            if (!AEItems.BLANK_PATTERN.is(stack)) {
                continue;
            }
            ItemStack copy = stack.copy();
            copy.shrink(1);
            this.host.getBlankPatternInventory().setItemDirect(slot, copy.isEmpty() ? ItemStack.EMPTY : copy);
            return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface SlotConsumer {
        void accept(int slot);
    }

    private final class PatternTargetInventory implements InternalInventory {
        @Override
        public int size() {
            return PatternProviderCapacity.PATTERN_MODIFIER_SLOTS_PER_PAGE;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (!isSlotAvailable(slot)) {
                return 0;
            }
            return getDelegate().getSlotLimit(getDelegateSlot(slot));
        }

        private InternalInventory getDelegate() {
            if (providerInventoryMode && patternProvider != null) {
                return patternProvider.getLogic().getPatternInv();
            }
            return host.getPatternInventory();
        }

        private int getDelegateSlot(int slotIndex) {
            if (providerInventoryMode && patternProvider != null) {
                return PatternProviderCapacity.getFirstPatternModifierSlotOnPage(providerPage) + slotIndex;
            }
            return slotIndex;
        }

        private boolean isSlotAvailable(int slotIndex) {
            return slotIndex >= 0 && slotIndex < activePatternSlots;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            if (!isSlotAvailable(slotIndex)) {
                return ItemStack.EMPTY;
            }
            return getDelegate().getStackInSlot(getDelegateSlot(slotIndex));
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (isSlotAvailable(slotIndex)) {
                getDelegate().setItemDirect(getDelegateSlot(slotIndex), stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isSlotAvailable(slot) && getDelegate().isItemValid(getDelegateSlot(slot), stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isSlotAvailable(slot)) {
                return stack;
            }
            return getDelegate().insertItem(getDelegateSlot(slot), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isSlotAvailable(slot)) {
                return ItemStack.EMPTY;
            }
            return getDelegate().extractItem(getDelegateSlot(slot), amount, simulate);
        }
    }
}
