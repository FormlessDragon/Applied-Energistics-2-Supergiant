package ae2.core.localization;

public enum GeneralText implements LocalizationEnum {
    ClientReadOnly("client_read_only");

    private final String translationKey;

    GeneralText(String translationKeySuffix) {
        this.translationKey = "ae2." + translationKeySuffix;
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
