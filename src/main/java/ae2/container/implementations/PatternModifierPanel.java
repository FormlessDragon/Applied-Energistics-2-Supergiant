package ae2.container.implementations;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.container.SlotSemantic;
import ae2.container.SlotSemantics;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.definitions.AEItems;
import ae2.helpers.patternmodifier.PatternModifierActions;
import ae2.helpers.patternmodifier.PatternModifierLocator;
import ae2.helpers.patternmodifier.PatternModifierLogic;
import ae2.items.contents.PatternModifierGuiHost;
import ae2.items.tools.PatternModifierItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PatternModifierPanel {
    public static final SlotSemantic PATTERN_MODIFIER_PANEL =
        SlotSemantics.register("PATTERN_MODIFIER_PANEL", false);

    private static final String ACTION_PANEL_CLEAR = "patternModifierPanelClear";
    private static final String ACTION_PANEL_MULTIPLY_2 = "patternModifierPanelMultiply2";
    private static final String ACTION_PANEL_MULTIPLY_3 = "patternModifierPanelMultiply3";
    private static final String ACTION_PANEL_MULTIPLY_5 = "patternModifierPanelMultiply5";
    private static final String ACTION_PANEL_DIVIDE_2 = "patternModifierPanelDivide2";
    private static final String ACTION_PANEL_DIVIDE_3 = "patternModifierPanelDivide3";
    private static final String ACTION_PANEL_DIVIDE_5 = "patternModifierPanelDivide5";

    private final Host container;
    private final InternalInventory emptyInventory = new EmptyPatternInventory();
    @Nullable
    private final PatternModifierGuiHost host;

    public PatternModifierPanel(Host container) {
        this.container = container;
        this.host = findHost();
        for (int slot = 0; slot < PatternModifierItem.PATTERN_SLOTS; slot++) {
            this.container.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN,
                getPatternInventory(), slot), PATTERN_MODIFIER_PANEL);
        }
        registerActions();
        updateSlotState(false);
    }

    private static String actionForAmountModification(int factor, boolean divide) {
        if (!PatternModifierActions.isSupportedToolboxFactor(factor)) {
            throw new IllegalArgumentException("Unsupported Pattern Modifier toolbox factor: " + factor);
        }
        return switch (factor) {
            case 2 -> divide ? ACTION_PANEL_DIVIDE_2 : ACTION_PANEL_MULTIPLY_2;
            case 3 -> divide ? ACTION_PANEL_DIVIDE_3 : ACTION_PANEL_MULTIPLY_3;
            case 5 -> divide ? ACTION_PANEL_DIVIDE_5 : ACTION_PANEL_MULTIPLY_5;
            default -> throw new IllegalArgumentException("Unsupported Pattern Modifier factor: " + factor);
        };
    }

    public boolean isAvailable() {
        return this.host != null;
    }

    public ItemStack insertPattern(ItemStack stack, boolean simulate) {
        if (!isAvailable()) {
            return stack;
        }
        ItemStack remaining = stack;
        InternalInventory inventory = getHost().getPatternInventory();
        for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    public boolean consumeBlankPattern() {
        if (!isAvailable()) {
            return false;
        }
        InternalInventory inventory = getHost().getPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!AEItems.BLANK_PATTERN.is(stack)) {
                continue;
            }
            ItemStack copy = stack.copy();
            copy.shrink(1);
            inventory.setItemDirect(slot, copy.isEmpty() ? ItemStack.EMPTY : copy);
            return true;
        }
        return false;
    }

    public void updateSlotState(boolean visible) {
        boolean enabled = visible && isAvailable();
        for (var slot : this.container.getSlots(PATTERN_MODIFIER_PANEL)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setSlotEnabled(enabled);
                appEngSlot.setActive(enabled);
            }
        }
    }

    public void clearPatterns() {
        if (this.container.isClientSide()) {
            this.container.sendPatternModifierPanelAction(ACTION_PANEL_CLEAR);
            return;
        }
        forEachPatternSlot(slot -> getHost().getPatternInventory().setItemDirect(slot,
            PatternModifierLogic.clearPattern(getHost().getPatternInventory().getStackInSlot(slot))));
    }

    public void modifyAmounts(int factor, boolean divide) {
        if (this.container.isClientSide()) {
            this.container.sendPatternModifierPanelAction(actionForAmountModification(factor, divide));
            return;
        }
        forEachPatternSlot(slot -> getHost().getPatternInventory().setItemDirect(slot,
            PatternModifierLogic.modifyAmounts(getHost().getPatternInventory().getStackInSlot(slot),
                this.container.getPlayer().world, factor, divide)));
    }

    private void registerActions() {
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_CLEAR, this::clearPatterns);
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_MULTIPLY_2, () -> modifyAmounts(2, false));
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_MULTIPLY_3, () -> modifyAmounts(3, false));
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_MULTIPLY_5, () -> modifyAmounts(5, false));
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_DIVIDE_2, () -> modifyAmounts(2, true));
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_DIVIDE_3, () -> modifyAmounts(3, true));
        this.container.registerPatternModifierPanelAction(ACTION_PANEL_DIVIDE_5, () -> modifyAmounts(5, true));
    }

    private void forEachPatternSlot(SlotConsumer consumer) {
        if (!isAvailable()) {
            return;
        }
        for (int slot = 0; slot < getHost().getPatternInventory().size(); slot++) {
            ItemStack pattern = getHost().getPatternInventory().getStackInSlot(slot);
            if (pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
                continue;
            }
            consumer.accept(slot);
        }
    }

    private PatternModifierGuiHost getHost() {
        if (this.host == null) {
            throw new IllegalStateException("Pattern Modifier panel is not available");
        }
        return this.host;
    }

    @Nullable
    private PatternModifierGuiHost findHost() {
        var located = PatternModifierLocator.find(this.container.getPlayer());
        if (located != null) {
            Integer playerSlot = located.playerInventorySlot();
            if (playerSlot != null) {
                this.container.lockPatternModifierPlayerInventorySlot(playerSlot);
            }
            return new PatternModifierGuiHost(located.item(), this.container.getPlayer(), located.locator());
        }
        return null;
    }

    private InternalInventory getPatternInventory() {
        return this.host != null ? this.host.getPatternInventory() : this.emptyInventory;
    }

    @FunctionalInterface
    private interface SlotConsumer {
        void accept(int slot);
    }

    public interface Host {
        Slot addSlot(Slot slot, SlotSemantic semantic);

        List<Slot> getSlots(SlotSemantic semantic);

        boolean isClientSide();

        EntityPlayer getPlayer();

        void registerPatternModifierPanelAction(String action, Runnable runnable);

        void sendPatternModifierPanelAction(String action);

        void lockPatternModifierPlayerInventorySlot(int slot);
    }

    private static final class EmptyPatternInventory implements InternalInventory {
        @Override
        public int size() {
            return PatternModifierItem.PATTERN_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }
    }
}
