package ae2.integration.modules.theoneprobe;

import ae2.core.localization.LocalizationEnum;

public enum TopNetDebugText implements LocalizationEnum {
    grid_pivot_pos(TopText.netdebug_grid_pivot_pos),
    grid_id(TopText.netdebug_grid_id),
    grid_nodes(TopText.netdebug_grid_nodes),
    grid_cpu_avg_max(TopText.netdebug_grid_cpu_avg_max),
    storage(TopText.netdebug_storage),
    crafting(TopText.netdebug_crafting),
    tick(TopText.netdebug_tick),
    misc(TopText.netdebug_misc);

    private final String translationKey;

    TopNetDebugText(TopText text) {
        this.translationKey = text.getTranslationKey();
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
