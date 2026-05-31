package ae2.core.localization;

/**
 * Used to describe to the user on which sides of the block (when looking at it from the front), something can be
 * automated.
 */
public enum Side implements LocalizationEnum {

    North,
    South,
    East,
    West,
    Up,
    Down;

    private final String translationKey;

    Side() {
        this.translationKey = "ae2.side." + name();
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
