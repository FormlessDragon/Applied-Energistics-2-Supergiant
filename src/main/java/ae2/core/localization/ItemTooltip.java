package ae2.core.localization;

public enum ItemTooltip implements LocalizationEnum {
    ParallelCard("parallel_card.tooltip"),
    EntroSeed("entro_seed.tooltip");

    private final String translationKey;

    ItemTooltip(String translationKeySuffix) {
        this.translationKey = "item.ae2." + translationKeySuffix;
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
