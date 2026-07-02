package ae2.integration.modules.hei;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.StackWithBounds;
import ae2.integration.modules.hei.target.HeiGhostTargetSupport;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;

public final class AEGuiHandler implements IAdvancedGuiHandler<AEBaseGui<?>>, IGhostIngredientHandler<AEBaseGui<?>> {
    @Nullable
    private Object currentGhostIngredient;
    private int currentGhostMouseButton = -1;

    public static ItemStack toGhostDisplayStack(Object ingredient) {
        return HeiGhostTargetSupport.toGhostDisplayStack(ingredient);
    }

    private void updateCurrentGhostMouseButton() {
        int mouseButton = HeiGhostTargetSupport.getActiveMouseButton();
        if (mouseButton >= 0) {
            this.currentGhostMouseButton = mouseButton;
        }
    }

    private void clearCurrentGhostIngredient() {
        this.currentGhostIngredient = null;
        this.currentGhostMouseButton = -1;
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
            this.currentGhostMouseButton = -1;
            updateCurrentGhostMouseButton();
        }
        return getTargetsForIngredient(gui, ingredient);
    }

    @Override
    public void onComplete() {
        clearCurrentGhostIngredient();
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
        return gui.getHEITargets(ingredient, this.currentGhostMouseButton);
    }
}
