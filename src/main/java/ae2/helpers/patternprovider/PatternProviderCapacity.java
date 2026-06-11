package ae2.helpers.patternprovider;

import java.util.function.IntPredicate;

public final class PatternProviderCapacity {
    public static final int BASE_PATTERN_SLOTS = 9;
    public static final int SLOTS_PER_CAPACITY_CARD = 9;
    public static final int GUI_PATTERN_SLOTS_PER_PAGE = 36;
    public static final int PATTERN_MODIFIER_SLOTS_PER_PAGE = 27;

    private PatternProviderCapacity() {
    }

    public static int getMaxPatternSlots(int maxCapacityCards) {
        return getActivePatternSlots(maxCapacityCards, maxCapacityCards);
    }

    public static int getActivePatternSlots(int capacityCards, int maxCapacityCards) {
        int cards = Math.clamp(capacityCards, 0, Math.max(0, maxCapacityCards));
        return saturatingToInt((long) BASE_PATTERN_SLOTS + (long) cards * SLOTS_PER_CAPACITY_CARD);
    }

    public static int getPageCount(int activePatternSlots) {
        return getPageCount(activePatternSlots, GUI_PATTERN_SLOTS_PER_PAGE);
    }

    public static int getPatternModifierPageCount(int activePatternSlots) {
        return getPageCount(activePatternSlots, PATTERN_MODIFIER_SLOTS_PER_PAGE);
    }

    public static int getFirstSlotOnPage(int page) {
        return saturatingToInt((long) Math.max(0, page) * GUI_PATTERN_SLOTS_PER_PAGE);
    }

    public static int getFirstPatternModifierSlotOnPage(int page) {
        return saturatingToInt((long) Math.max(0, page) * PATTERN_MODIFIER_SLOTS_PER_PAGE);
    }

    public static boolean isPatternSlotOnPage(int slot, int page) {
        long firstSlot = (long) Math.max(0, page) * GUI_PATTERN_SLOTS_PER_PAGE;
        return slot >= firstSlot && slot < firstSlot + GUI_PATTERN_SLOTS_PER_PAGE;
    }

    public static boolean canRemoveCapacityCards(int currentCards, int cardsToRemove, int maxCapacityCards,
                                                 IntPredicate occupiedSlot) {
        int newCards = Math.max(0, currentCards - Math.max(0, cardsToRemove));
        int firstClosedSlot = getActivePatternSlots(newCards, maxCapacityCards);
        int currentVisibleSlots = getActivePatternSlots(currentCards, maxCapacityCards);
        for (int slot = firstClosedSlot; slot < currentVisibleSlots; slot++) {
            if (occupiedSlot.test(slot)) {
                return false;
            }
        }
        return true;
    }

    private static int getPageCount(int activePatternSlots, int slotsPerPage) {
        long slots = Math.max(BASE_PATTERN_SLOTS, activePatternSlots);
        return Math.max(1, saturatingToInt((slots + slotsPerPage - 1) / slotsPerPage));
    }

    private static int saturatingToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
