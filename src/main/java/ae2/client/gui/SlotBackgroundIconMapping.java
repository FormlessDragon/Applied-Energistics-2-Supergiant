package ae2.client.gui;

import ae2.container.slot.SlotBackgroundIcon;
import org.jetbrains.annotations.Nullable;

public final class SlotBackgroundIconMapping {

    private SlotBackgroundIconMapping() {
    }

    @Nullable
    public static Icon resolve(@Nullable SlotBackgroundIcon icon) {
        if (icon == null) {
            return null;
        }

        return switch (icon) {
            case PRIMARY_OUTPUT -> Icon.BACKGROUND_PRIMARY_OUTPUT;
            case STORAGE_CELL -> Icon.BACKGROUND_STORAGE_CELL;
            case ORE -> Icon.BACKGROUND_ORE;
            case STORAGE_COMPONENT -> Icon.BACKGROUND_STORAGE_COMPONENT;
            case WIRELESS_TERM -> Icon.BACKGROUND_WIRELESS_TERM;
            case TRASH -> Icon.BACKGROUND_TRASH;
            case ENCODED_PATTERN -> Icon.BACKGROUND_ENCODED_PATTERN;
            case BLANK_PATTERN -> Icon.BACKGROUND_BLANK_PATTERN;
            case CHARGABLE -> Icon.BACKGROUND_CHARGABLE;
            case WIRELESS_BOOSTER -> Icon.BACKGROUND_WIRELESS_BOOSTER;
            case SINGULARITY -> Icon.BACKGROUND_SINGULARITY;
            case SPATIAL_CELL -> Icon.BACKGROUND_SPATIAL_CELL;
            case SPATIAL_CELL_NO_SHADOW -> Icon.BACKGROUND_SPATIAL_CELL_NO_SHADOW;
            case FUEL -> Icon.BACKGROUND_FUEL;
            case UPGRADE -> Icon.BACKGROUND_UPGRADE;
            case VIEW_CELL -> Icon.BACKGROUND_VIEW_CELL;
            case PLATE -> Icon.BACKGROUND_PLATE;
            case INGOT -> Icon.BACKGROUND_INGOT;
        };
    }
}
