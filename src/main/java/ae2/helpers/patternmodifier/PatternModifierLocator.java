package ae2.helpers.patternmodifier;

import ae2.core.definitions.AEItems;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.integration.modules.baubles.BaublesIntegration;
import ae2.items.tools.PatternModifierItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class PatternModifierLocator {

    private PatternModifierLocator() {
    }

    @Nullable
    public static LocatedPatternModifier find(EntityPlayer player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (isPatternModifier(stack)) {
                return new LocatedPatternModifier(stack, GuiHostLocators.forInventorySlot(slot), slot);
            }
        }

        for (int slot = 0; slot < BaublesIntegration.getSlots(player); slot++) {
            ItemStack stack = BaublesIntegration.getStackInSlot(player, slot);
            if (isPatternModifier(stack)) {
                return new LocatedPatternModifier(stack, GuiHostLocators.forBaubleSlot(slot), null);
            }
        }
        return null;
    }

    public static boolean isPatternModifier(ItemStack stack) {
        return stack.getItem() instanceof PatternModifierItem && AEItems.PATTERN_MODIFIER.is(stack);
    }

    public record LocatedPatternModifier(ItemStack stack, ItemGuiHostLocator locator,
                                         @Nullable Integer playerInventorySlot) {
        public PatternModifierItem item() {
            return (PatternModifierItem) stack.getItem();
        }
    }
}
