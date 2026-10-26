package ae2.crafting;

import ae2.core.localization.PlayerMessages;

import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public class CraftingCalculationFailure extends RuntimeException {
    private final PlayerMessages messageKey;
    private final Object[] messageArguments;
    @Nullable
    private final NoNetOutputCycle cycle;

    public CraftingCalculationFailure(PlayerMessages messageKey) {
        this(messageKey, null);
    }

    public CraftingCalculationFailure(PlayerMessages messageKey, @Nullable NoNetOutputCycle cycle) {
        this(messageKey, cycle, new Object[0]);
    }

    private CraftingCalculationFailure(PlayerMessages messageKey, @Nullable NoNetOutputCycle cycle, Object[] arguments) {
        super(messageKey.getTranslationKey());
        this.messageKey = messageKey;
        this.cycle = cycle;
        this.messageArguments = arguments.clone();
    }

    public static CraftingCalculationFailure withDetails(PlayerMessages messageKey, Object... arguments) {
        return new CraftingCalculationFailure(messageKey, null, arguments);
    }

    /**
     * The cycle that made no net output, when that is what failed.
     */
    @Nullable
    public NoNetOutputCycle getCycle() {
        return this.cycle;
    }

    public PlayerMessages getMessageKey() {
        return this.messageKey;
    }

    public ITextComponent getLocalizedMessageComponent() {
        return this.messageKey.text(this.messageArguments);
    }
}
