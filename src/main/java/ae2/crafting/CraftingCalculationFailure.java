package ae2.crafting;

import ae2.core.localization.PlayerMessages;

import net.minecraft.util.text.ITextComponent;

public class CraftingCalculationFailure extends RuntimeException {
    private final PlayerMessages messageKey;

    public CraftingCalculationFailure(PlayerMessages messageKey) {
        super(messageKey.getTranslationKey());
        this.messageKey = messageKey;
    }

    public PlayerMessages getMessageKey() {
        return this.messageKey;
    }

    public ITextComponent getLocalizedMessageComponent() {
        return this.messageKey.text();
    }
}
