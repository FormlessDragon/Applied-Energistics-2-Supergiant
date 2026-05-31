package ae2.core.localization;

public enum HeiText implements LocalizationEnum {
    ChargerRequiredPower,
    Consumed,
    FlowingFluidName,
    EntropyManipulatorHeat,
    EntropyManipulatorCool,
    RightClick,
    ShiftRightClick,
    TransformCategory,
    Explosion,
    SubmergeIn;

    private final String translationKey;

    HeiText() {
        this.translationKey = "ae2.hei." + name();
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
