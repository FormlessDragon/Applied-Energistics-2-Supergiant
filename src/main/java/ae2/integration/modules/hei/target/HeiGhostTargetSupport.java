package ae2.integration.modules.hei.target;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.FakeSlotFilterSupport;
import ae2.integration.modules.hei.GenericIngredientHelper;
import mezz.jei.bookmarks.BookmarkItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;

public final class HeiGhostTargetSupport {
    private HeiGhostTargetSupport() {
    }

    public static ItemStack toFilterStack(FakeSlot slot, Object ingredient) {
        ItemStack directStack = toPacketFilterStack(ingredient);
        if (!directStack.isEmpty()) {
            if (slot.canSetFilterTo(directStack)) {
                return directStack;
            }

            ItemStack preferred = FakeSlotFilterSupport.getPreferredFilterStack(slot, directStack);
            if (!preferred.isEmpty()) {
                return preferred;
            }
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        if (stack != null) {
            ItemStack wrapped = GenericStack.wrapInItemStack(stack);
            ItemStack preferred = FakeSlotFilterSupport.getPreferredFilterStack(slot, wrapped);
            if (slot.canSetFilterTo(wrapped)) {
                return wrapped;
            }
            if (!preferred.isEmpty()) {
                return preferred;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public static String getTextFieldInsertionText(Object ingredient, int mouseButton) {
        if (ingredient instanceof BookmarkItem<?> bookmarkItem) {
            return getTextFieldInsertionText(bookmarkItem.ingredient, mouseButton);
        }

        if (ingredient instanceof ItemStack itemStack) {
            return AEBaseGui.getTextFieldInsertionText(itemStack, mouseButton);
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        if (stack != null) {
            return stack.what().getDisplayName().getFormattedText();
        }

        ItemStack itemStack = toPacketFilterStack(ingredient);
        if (itemStack.isEmpty()) {
            return null;
        }

        return AEBaseGui.getTextFieldInsertionText(itemStack, mouseButton);
    }

    public static ItemStack toGhostDisplayStack(Object ingredient) {
        if (ingredient instanceof ItemStack itemStack) {
            return itemStack.copy();
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        if (stack == null) {
            return ItemStack.EMPTY;
        }

        if (stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.max(1, stack.amount()));
        }

        if (stack.what() instanceof AEFluidKey fluidKey) {
            ItemStack bucket = FluidUtil.getFilledBucket(fluidKey.toStack((int) Math.max(1, stack.amount())));
            if (!bucket.isEmpty()) {
                return bucket;
            }
        }

        return GenericStack.wrapInItemStack(stack.what(), Math.max(1, stack.amount()));
    }

    public static ItemStack toPacketFilterStack(Object ingredient) {
        if (ingredient instanceof BookmarkItem<?> bookmarkItem) {
            return toPacketFilterStack(bookmarkItem.ingredient);
        }

        if (ingredient instanceof ItemStack itemStack) {
            return itemStack.copy();
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        return stack != null ? GenericStack.wrapInItemStack(stack) : ItemStack.EMPTY;
    }

    @Nullable
    public static GenericStack toManualPinStack(Object ingredient, int mouseButton) {
        if (ingredient instanceof BookmarkItem<?> bookmarkItem) {
            GenericStack stack = toManualPinStack(bookmarkItem.ingredient, mouseButton);
            if (stack != null && bookmarkItem.amount > 0) {
                return new GenericStack(stack.what(), bookmarkItem.amount);
            }
            return stack;
        }

        ItemStack itemStack = toPacketFilterStack(ingredient);
        if (!itemStack.isEmpty()) {
            if (mouseButton == 1) {
                GenericStack contained = ContainerItemStrategies.getContainedStack(itemStack);
                if (contained != null) {
                    return contained;
                }
            }

            GenericStack wrapped = GenericStack.unwrapItemStack(itemStack);
            if (wrapped != null) {
                return wrapped;
            }
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        if (stack != null) {
            return stack;
        }

        if (!itemStack.isEmpty()) {
            GenericStack contained = ContainerItemStrategies.getContainedStack(itemStack);
            if (contained != null && mouseButton == 1) {
                return contained;
            }

            return GenericStack.fromItemStack(itemStack);
        }

        return null;
    }

    public static int getActiveMouseButton() {
        int mouseButton = Mouse.getEventButton();
        if (mouseButton >= 0) {
            return mouseButton;
        }
        if (Mouse.isButtonDown(1)) {
            return 1;
        }
        if (Mouse.isButtonDown(0)) {
            return 0;
        }
        return -1;
    }
}
