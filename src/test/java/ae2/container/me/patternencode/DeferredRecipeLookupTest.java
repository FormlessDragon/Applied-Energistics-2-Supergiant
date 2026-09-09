package ae2.container.me.patternencode;

import ae2.container.crafting.DeferredRecipeLookup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredRecipeLookupTest {
    @Test
    void defersUntilTheFollowingTickAndCoalescesChanges() {
        DeferredRecipeLookup lookup = new DeferredRecipeLookup();

        assertTrue(lookup.markDirty(42));
        assertFalse(lookup.markDirty(42));
        assertTrue(lookup.isDirty());
        assertFalse(lookup.isDue(42));
        assertTrue(lookup.isDue(43));

        lookup.clear();
        assertFalse(lookup.isDirty());
        assertFalse(lookup.isDue(44));
    }
}
