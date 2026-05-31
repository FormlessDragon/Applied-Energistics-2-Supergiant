package ae2.helpers.patternmodifier;

public final class PatternModifierActions {
    public static final int[] STANDALONE_FACTORS = {2, 3, 5, 8};
    public static final int[] TOOLBOX_FACTORS = {2, 3, 5};

    private PatternModifierActions() {
    }

    public static boolean isSupportedStandaloneFactor(int factor) {
        return contains(STANDALONE_FACTORS, factor);
    }

    public static boolean isSupportedToolboxFactor(int factor) {
        return contains(TOOLBOX_FACTORS, factor);
    }

    private static boolean contains(int[] factors, int factor) {
        for (int supportedFactor : factors) {
            if (supportedFactor == factor) {
                return true;
            }
        }
        return false;
    }
}
