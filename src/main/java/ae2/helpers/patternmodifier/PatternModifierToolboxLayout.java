package ae2.helpers.patternmodifier;

import ae2.items.tools.PatternModifierItem;

public final class PatternModifierToolboxLayout {
    public static final int PANEL_LEFT_OFFSET = -126;
    public static final int PANEL_TOP_OFFSET = 59;
    public static final int PANEL_WIDTH = 86;
    public static final int PANEL_HEIGHT = 180;
    public static final int SLOT_LEFT = PANEL_LEFT_OFFSET + 8;
    public static final int SLOT_TOP = PANEL_TOP_OFFSET + 8;
    public static final int BUTTON_LEFT = PANEL_LEFT_OFFSET + 63;
    public static final int BUTTON_AREA_TOP = PANEL_TOP_OFFSET + 8;
    public static final int BUTTON_WIDTH = 18;
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_GAP_Y = 2;
    public static final int SLOT_ROWS = 9;

    private PatternModifierToolboxLayout() {
    }

    public static int getSlotX(int slot) {
        return SLOT_LEFT + getColumn(slot) * 18;
    }

    public static int getSlotY(int slot) {
        return SLOT_TOP + getRow(slot) * 18;
    }

    public static int getButtonX(int index) {
        return BUTTON_LEFT;
    }

    public static int getButtonY(int index) {
        return BUTTON_AREA_TOP + getButtonRow(index) * (BUTTON_HEIGHT + BUTTON_GAP_Y);
    }

    private static int getColumn(int slot) {
        return Math.floorMod(slot, PatternModifierItem.PATTERN_SLOTS) / SLOT_ROWS;
    }

    private static int getRow(int slot) {
        return Math.floorMod(slot, PatternModifierItem.PATTERN_SLOTS) % SLOT_ROWS;
    }

    private static int getButtonRow(int index) {
        return index;
    }
}
