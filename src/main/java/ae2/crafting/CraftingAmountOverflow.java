package ae2.crafting;

import ae2.api.stacks.AmountOverflowException;
import ae2.core.localization.PlayerMessages;
import net.minecraft.util.text.ITextComponent;

/** Terminal planning failure; retrying or clamping would hide an unrepresentable material requirement. */
public final class CraftingAmountOverflow extends CraftingCalculationFailure {
    private final AmountOverflowException overflow;

    public CraftingAmountOverflow(AmountOverflowException overflow) {
        super(PlayerMessages.CraftingAmountOverflow);
        this.overflow = overflow;
        initCause(overflow);
    }

    public AmountOverflowException getOverflow() {
        return overflow;
    }

    @Override
    public ITextComponent getLocalizedMessageComponent() {
        return getMessageKey().text(overflow.getKey().getDisplayName().createCopy(),
            overflow.getAmount().abs().toString(), Long.toString(Long.MAX_VALUE));
    }
}
