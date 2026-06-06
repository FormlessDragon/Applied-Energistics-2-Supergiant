package ae2.core.localization;

public enum HeiText implements LocalizationEnum {
    ChargerRequiredPower,
    Consumed,
    FlowingFluidName,
    EntropyManipulatorHeat,
    EntropyManipulatorCool,
    RightClick,
    ShiftRightClick,
    MoveItems("move_items"),
    TransformCategory,
    Explosion,
    SubmergeIn,
    CrystalFixerSuccessChance("crystal_fixer.success_chance");

    private final String translationKey;

    HeiText() {
        this.translationKey = "ae2.hei." + name();
    }

    HeiText(String translationKeySuffix) {
        this.translationKey = "ae2.hei." + translationKeySuffix;
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
