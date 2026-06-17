package ae2.core.localization;

public enum P2PText implements LocalizationEnum {
    NamePrefix("p2p_tunnel.name");

    private final String translationKey;

    P2PText(String translationKeySuffix) {
        this.translationKey = "item.ae2." + translationKeySuffix;
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
