package ae2.helpers.patternprovider;

import ae2.api.config.Actionable;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeMultiplierBoundsTest {
    @Test
    void exponentialProbeTerminatesAndNeverSendsOverflowedAmounts() {
        Bootstrap.register();
        var key = AEItemKey.of(Items.APPLE);
        var inputs = new KeyCounter();
        inputs.add(key, 1);
        var target = new AcceptingTarget();
        var mode = PatternProviderInsertionMode.values()[0];
        assertEquals(Integer.MAX_VALUE, PatternProviderMergeHelper.findMaxExternalMultiplier(
            target, new KeyCounter[]{inputs}, Integer.MAX_VALUE, mode));
        assertTrue(target.calls <= 33, "exponential search must have a finite bound");
        inputs.set(key, Long.MAX_VALUE / 2 + 1);
        target.calls = 0;
        assertEquals(1, PatternProviderMergeHelper.findMaxExternalMultiplier(
            target, new KeyCounter[]{inputs}, Integer.MAX_VALUE, mode));
        assertTrue(target.calls <= 2);
    }

    private static final class AcceptingTarget implements PatternProviderTarget {
        int calls;

        public long insert(AEKey what, long amount, Actionable mode) {
            assertTrue(amount > 0);
            assertEquals(Actionable.SIMULATE, mode);
            calls++;
            return amount;
        }

        public long insert(AEKey what, long amount, Actionable mode, PatternProviderInsertionMode insertionMode) {
            return insert(what, amount, mode);
        }

        public boolean containsPatternInput(Set<AEKey> input) {
            return false;
        }

        public boolean containsAnyStack() {
            return false;
        }

        public boolean hasEmptySlots() {
            return true;
        }
    }
}
