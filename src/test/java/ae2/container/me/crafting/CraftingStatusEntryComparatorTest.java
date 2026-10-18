package ae2.container.me.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingStatusEntryComparatorTest {
    @Test
    void comparesNonNegativeActiveAndPendingAmountsWithoutSignedOverflow() {
        var huge = new CraftingStatusEntry(1, null, 0, Long.MAX_VALUE, 1);
        var small = new CraftingStatusEntry(2, null, 0, 1, 0);
        var equalTotalHigherStored = new CraftingStatusEntry(3, null, 9, Long.MAX_VALUE, 1);

        assertEquals(List.of(equalTotalHigherStored, huge, small),
            List.of(huge, small, equalTotalHigherStored).stream().sorted().toList());
    }
}
