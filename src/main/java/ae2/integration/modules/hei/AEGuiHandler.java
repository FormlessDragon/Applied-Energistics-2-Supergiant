package ae2.integration.modules.hei;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.StackWithBounds;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.FakeSlotFilterSupport;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.bookmarks.BookmarkItem;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.util.List;

public final class AEGuiHandler implements IAdvancedGuiHandler<AEBaseGui<?>>, IGhostIngredientHandler<AEBaseGui<?>> {
    @Nullable
    private Object currentGhostIngredient;

    static ItemStack toFilterStack(FakeSlot slot, Object ingredient) {
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
    private static String getTextFieldInsertionText(Object ingredient, int mouseButton) {
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

    private static ItemStack toPacketFilterStack(Object ingredient) {
        if (ingredient instanceof BookmarkItem<?> bookmarkItem) {
            return toPacketFilterStack(bookmarkItem.ingredient);
        }

        if (ingredient instanceof ItemStack itemStack) {
            return itemStack.copy();
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(ingredient);
        return stack != null ? GenericStack.wrapInItemStack(stack) : ItemStack.EMPTY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @NotNull
    @Override
    public Class<AEBaseGui<?>> getGuiContainerClass() {
        return (Class) AEBaseGui.class;
    }

    @Override
    public List<Rectangle> getGuiExtraAreas(@NotNull AEBaseGui<?> guiContainer) {
        return guiContainer.getExclusionZones();
    }

    @Override
    public @Nullable Object getIngredientUnderMouse(@NotNull AEBaseGui<?> guiContainer, int mouseX, int mouseY) {
        StackWithBounds hoveredStack = guiContainer.getStackUnderMouse(mouseX, mouseY);
        if (hoveredStack != null) {
            return GenericIngredientHelper.stackToIngredient(hoveredStack.stack());
        }

        var slot = guiContainer.getSlotUnderMouse();
        if (slot != null && slot.getHasStack()) {
            return slot.getStack();
        }
        return null;
    }

    @NotNull
    @Override
    public <I> List<Target<I>> getTargets(@NotNull AEBaseGui<?> gui, @NotNull I ingredient, boolean doStart) {
        if (doStart) {
            this.currentGhostIngredient = ingredient;
        }
        return getTargetsForIngredient(gui, ingredient);
    }

    @Override
    public void onComplete() {
        this.currentGhostIngredient = null;
    }

    @Override
    public boolean shouldHighlightTargets() {
        return IGhostIngredientHandler.super.shouldHighlightTargets();
    }

    @Nullable
    public Object getCurrentGhostIngredient() {
        return this.currentGhostIngredient;
    }

    public <I> List<Target<I>> getTargetsForIngredient(AEBaseGui<?> gui, I ingredient) {
        List<Target<I>> targets = new ObjectArrayList<>();

        for (var slot : gui.getHEISlots(ingredient)) {
            if (!(slot instanceof FakeSlot fakeSlot) || !fakeSlot.isEnabled()) {
                continue;
            }

            ItemStack stack = toFilterStack(fakeSlot, ingredient);
            if (!stack.isEmpty()) {
                targets.add(new FakeSlotTarget<>(gui, fakeSlot));
            }
        }

        if (gui instanceof ITextFieldGui g) {
            for (var field : g.getTextFields()) {
                if (field.getVisible()) {
                    targets.add(new TextFieldTarget<>(gui, field));
                }
            }
        }

        return targets;
    }

    private record FakeSlotTarget <T> (AEBaseGui<?> gui, FakeSlot slot) implements Target<T> {

        @Override
        public Rectangle getArea() {
            return new Rectangle(this.gui.getGuiLeft() + this.slot.xPos, this.gui.getGuiTop() + this.slot.yPos, 16, 16);
        }

        @Override
        public void accept(@NotNull T ingredient) {
            if (!this.slot.isEnabled()) {
                return;
            }

            ItemStack stack = toPacketFilterStack(ingredient);
            if (stack.isEmpty()) {
                return;
            }

            InitNetwork.sendToServer(new InventoryActionPacket(
                this.gui.getContainer().windowId,
                InventoryAction.SET_FILTER,
                this.slot.slotNumber,
                Mouse.getEventButton(),
                stack));
        }

    }

    private record TextFieldTarget <T> (AEBaseGui<?> gui, GuiTextField field) implements Target<T> {

        @Override
        public Rectangle getArea() {
            if (this.field instanceof AETextField aeTextField) {
                return aeTextField.getTooltipArea();
            }

            return new Rectangle(this.field.x, this.field.y, this.field.width, this.field.height);
        }

        @Override
        public void accept(@NotNull Object ingredient) {
            String text = getTextFieldInsertionText(ingredient, Mouse.getEventButton());
            if (text == null) {
                return;
            }
            if (field instanceof AETextField aeTextField) {
                aeTextField.setTextFromClient(text);
            } else {
                field.setText(text);
            }
        }

    }
}
