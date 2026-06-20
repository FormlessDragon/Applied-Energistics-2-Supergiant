package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.GenericStack;
import ae2.container.AEBaseContainer;
import ae2.core.AELog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class AppEngSlot extends Slot {
    private final InternalInventory inventory;
    private final int invSlot;
    private boolean hideAmount;
    private boolean enabled = true;
    private boolean active = true;
    private boolean draggable = true;
    private boolean display;
    private boolean returnAsSingleStack;
    private Supplier<@Nullable List<ITextComponent>> emptyTooltip = () -> null;
    @Nullable
    private SlotBackgroundIcon backgroundIcon;
    @Nullable
    private Boolean validState;
    private AEBaseContainer container;

    public AppEngSlot(InternalInventory inventory, int slotIndex, int x, int y) {
        super(inventory.toContainer(), slotIndex, x, y);
        this.inventory = inventory;
        this.invSlot = slotIndex;
    }

    public AppEngSlot(InternalInventory inventory, int slotIndex) {
        this(inventory, slotIndex, 0, 0);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Slot setNotDraggable() {
        this.draggable = false;
        return this;
    }

    public void clearStack() {
        this.inventory.setItemDirect(this.invSlot, ItemStack.EMPTY);
    }

    public InternalInventory getInventory() {
        return this.inventory;
    }

    public InternalInventory getSlotInv() {
        return this.inventory.getSlotInv(this.invSlot);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        if (containsWrapperItem()) {
            return false;
        }
        if (this.isSlotEnabled()) {
            return this.inventory.isItemValid(this.invSlot, stack);
        }
        return false;
    }

    @Override
    public ItemStack getStack() {
        if (this.display) {
            this.display = false;
            ItemStack displayStack = getDisplayStack();
            if (this.returnAsSingleStack) {
                this.returnAsSingleStack = false;
                if (!displayStack.isEmpty()) {
                    displayStack = displayStack.copy();
                    displayStack.setCount(1);
                }
            }
            return displayStack;
        }
        return getRawStack();
    }

    @Override
    public void putStack(ItemStack stack) {
        if (!this.isSlotEnabled() && this.invSlot >= this.inventory.size() && stack.isEmpty()) {
            return;
        }
        this.inventory.setItemDirect(this.invSlot, stack);
        this.onSlotChanged();
    }

    @Override
    public void onSlotChanged() {
        this.validState = null;
        super.onSlotChanged();
        if (this.container != null) {
            this.container.onSlotChange(this);
        }
    }

    @Override
    public int getSlotStackLimit() {
        return this.inventory.getSlotLimit(this.invSlot);
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return Math.min(this.getSlotStackLimit(), stack.getMaxStackSize());
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        if (containsWrapperItem()) {
            return false;
        }
        if (this.isSlotEnabled()) {
            return !this.inventory.extractItem(this.invSlot, 1, true).isEmpty();
        }
        return false;
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        if (containsWrapperItem()) {
            return ItemStack.EMPTY;
        }
        return this.inventory.extractItem(this.invSlot, amount, false);
    }

    public boolean isSlotEnabled() {
        return this.enabled;
    }

    public void setSlotEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && this.isSlotEnabled() && this.active;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDraggable() {
        return this.draggable;
    }

    @SuppressWarnings("unused")
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
    }

    public AEBaseContainer getContainer() {
        return this.container;
    }

    public void setContainer(AEBaseContainer container) {
        this.container = container;
    }

    public ItemStack getDisplayStack() {
        ItemStack stack = getRawStack();
        if (this.hideAmount) {
            var what = GenericStack.unwrapWhat(stack);
            if (what != null) {
                return GenericStack.wrapInItemStack(what, 0);
            }

            if (!stack.isEmpty()) {
                stack = stack.copy();
                stack.setCount(1);
            }
        }
        return stack;
    }

    public int getSlotIndex() {
        return this.invSlot;
    }

    protected boolean isRemote() {
        return this.container == null || this.container.getPlayer().world.isRemote;
    }

    @Nullable
    public List<ITextComponent> getCustomTooltip(ItemStack carriedItem) {
        if (!getDisplayStack().isEmpty()) {
            return null;
        }
        return this.emptyTooltip.get();
    }

    public final boolean isValid() {
        if (this.validState == null) {
            try {
                this.validState = this.getCurrentValidationState();
            } catch (Exception e) {
                this.validState = false;
                AELog.warn("Failed to update validation state for slot %s: %s", this, e);
            }
        }
        return this.validState;
    }

    protected boolean getCurrentValidationState() {
        return true;
    }

    public void resetCachedValidation() {
        this.validState = null;
    }

    public void setHideAmount(boolean hideAmount) {
        this.hideAmount = hideAmount;
    }

    public void setDisplay(boolean display) {
        this.display = display;
    }

    public void setReturnAsSingleStack(boolean returnAsSingleStack) {
        this.returnAsSingleStack = returnAsSingleStack;
    }

    public float getOpacityOfIcon() {
        return 1.0f;
    }

    public boolean renderIconWithItem() {
        return false;
    }

    @Nullable
    public SlotBackgroundIcon getBackgroundIcon() {
        return this.backgroundIcon;
    }

    public void setBackgroundIcon(@Nullable SlotBackgroundIcon backgroundIcon) {
        this.backgroundIcon = backgroundIcon;
    }

    public void setEmptyTooltip(Supplier<@Nullable List<ITextComponent>> emptyTooltip) {
        this.emptyTooltip = emptyTooltip;
    }

    private boolean containsWrapperItem() {
        return GenericStack.isWrapped(getRawStack());
    }

    public ItemStack getRawStack() {
        if (!this.isSlotEnabled() || this.invSlot >= this.inventory.size()) {
            return ItemStack.EMPTY;
        }
        return this.inventory.getStackInSlot(this.invSlot);
    }
}
