package ae2.api.behaviors;

import ae2.api.stacks.AEKey;
import net.minecraft.util.text.ITextComponent;

/**
 * Describes the action of emptying an item into the storage network.
 */
public record EmptyingAction(ITextComponent description, AEKey what, long maxAmount) {
}
