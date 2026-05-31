package ae2.core.localization;

/**
 * Texts for item-list and recipe-view integrations.
 */
public enum ItemModText implements LocalizationEnum {

    NoItems,
    P2P_TUNNEL_ATTUNEMENT,
    P2P_API_ATTUNEMENT,
    P2P_TAG_ATTUNEMENT,
    CRANK_DESCRIPTION,
    RecipeTooLarge,
    RecipeTransferImportsAvailable,
    RecipeTransferRequestsCraftableMissing,
    RecipeTransferLeavesMissing,
    StoredEnergy,
    StoredItems;

    private final String translationKey;

    ItemModText() {
        this.translationKey = "ae2.itemlist_integration." + name();
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
