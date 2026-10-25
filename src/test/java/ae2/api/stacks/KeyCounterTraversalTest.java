package ae2.api.stacks;

import ae2.api.config.FuzzyMode;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyCounterTraversalTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void singleVariantCopiesOverflowAndLiveViewsSurviveAdditionalVariants(boolean saturating) {
        var counter = saturating ? KeyCounter.saturating() : new KeyCounter();
        var plain = AEItemKey.of(Items.STICK);
        var taggedStack = new ItemStack(Items.STICK);
        var tag = new NBTTagCompound();
        tag.setString("variant", "second");
        taggedStack.setTagCompound(tag);
        var tagged = AEItemKey.of(taggedStack);
        counter.set(plain, Long.MAX_VALUE - 1);
        counter.add(plain, 10);
        assertEquals(saturating ? Long.MAX_VALUE : Long.MIN_VALUE + 8, counter.get(plain));
        var copy = saturating ? KeyCounter.saturating() : new KeyCounter();
        copy.addAll(counter);
        counter.remove(plain);
        assertTrue(counter.isEmpty());
        assertEquals(saturating ? Long.MAX_VALUE : Long.MIN_VALUE + 8, copy.get(plain));
        counter.set(plain, Long.MIN_VALUE + 1);
        counter.remove(plain, 10);
        assertEquals(saturating ? Long.MIN_VALUE : Long.MAX_VALUE - 8, counter.get(plain));
        var view = counter.findFuzzy(plain, FuzzyMode.IGNORE_ALL);
        counter.set(tagged, -3);
        assertEquals(2, view.size(), "a borrowed view must see newly added variants");
        for (var entry : view) entry.setValue(7);
        assertEquals(7, counter.get(plain));
        assertEquals(7, counter.get(tagged));
        counter.clear();
        assertTrue(view.isEmpty());
        counter.set(plain, 0);
        assertEquals(1, view.size(), "zero amounts remain present until explicitly removed");
        counter.removeZeros();
        assertTrue(counter.isEmpty());
        assertTrue(view.isEmpty());
    }

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static AEItemKey pickaxe(int damage) {
        return AEItemKey.of(new ItemStack(Items.IRON_PICKAXE, 1, damage));
    }

    private static KeyCounter mixedCounter() {
        var counter = new KeyCounter();
        for (int i = 0; i < 32; i++) {
            var stack = new ItemStack(Items.STICK);
            var tag = new NBTTagCompound();
            tag.setInteger("variant", i);
            stack.setTagCompound(tag);
            counter.add(AEItemKey.of(stack), i - 16L);
        }
        for (int damage : new int[]{0, 50, 100, 200}) {
            counter.add(pickaxe(damage), damage - 50L);
        }
        counter.add(AEItemKey.of(Items.APPLE), Long.MIN_VALUE);
        counter.add(AEItemKey.of(Items.POTATO), Long.MAX_VALUE);
        return counter;
    }

    @Test
    void forEachFormsPreserveIteratorOrderKeysAndPrimitiveAmounts() {
        var counter = mixedCounter();
        var expected = new ArrayList<GenericStack>();
        var expectedKeys = new HashSet<AEKey>();
        for (var entry : counter) {
            expected.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            expectedKeys.add(entry.getKey());
        }
        var entries = new ArrayList<GenericStack>();
        Iterable<Object2LongMap.Entry<AEKey>> iterable = counter;
        iterable.forEach(entry -> entries.add(new GenericStack(entry.getKey(), entry.getLongValue())));
        var amounts = new ArrayList<GenericStack>();
        counter.forEach((key, amount) -> amounts.add(new GenericStack(key, amount)));
        var alias = new ArrayList<GenericStack>();
        counter.forEach((ObjLongConsumer<? super AEKey>) (key, amount) -> alias.add(new GenericStack(key, amount)));
        assertEquals(expected, entries);
        assertEquals(expected, amounts);
        assertEquals(expected, alias);
        assertEquals(expectedKeys, counter.keySet());
    }

    @Test
    void forEachFormsCanUpdateExistingAmountsInHashAndFuzzyGroups() {
        var counter = mixedCounter();
        var expected = new ArrayList<GenericStack>();
        for (var entry : counter) expected.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        counter.forEach(entry -> entry.setValue(entry.getLongValue() / 2));
        for (var stack : expected) assertEquals(stack.amount() / 2, counter.get(stack.what()));
        counter.forEach((key, amount) -> counter.set(key, amount + 1));
        for (var stack : expected) assertEquals(stack.amount() / 2 + 1, counter.get(stack.what()));
        assertEquals(expected.size(), counter.size());
    }

    @Test
    void forEachFormsSkipEmptyGroupsAndRejectNullCallbacksEvenWhenEmpty() {
        var cleared = mixedCounter();
        cleared.clear();
        for (var counter : List.of(new KeyCounter(), cleared)) {
            int[] calls = {0};
            counter.forEach(entry -> calls[0]++);
            counter.forEach((key, amount) -> calls[0]++);
            counter.forEach((ObjLongConsumer<? super AEKey>) (key, amount) -> calls[0]++);
            assertEquals(0, calls[0]);
            assertTrue(counter.keySet().isEmpty());
            assertThrows(NullPointerException.class,
                () -> counter.forEach((Consumer<Object2LongMap.Entry<AEKey>>) null));
            assertThrows(NullPointerException.class, () -> counter.forEach((ObjLongConsumer<AEKey>) null));
            assertThrows(NullPointerException.class, () -> counter.forEach((ObjLongConsumer<? super AEKey>) null));
        }
        cleared.add(pickaxe(42), 3);
        var actual = new ArrayList<GenericStack>();
        cleared.forEach((key, amount) -> actual.add(new GenericStack(key, amount)));
        assertEquals(List.of(new GenericStack(pickaxe(42), 3)), actual);
    }

    @Test
    void forEachFormsStopAndPropagateTheCallbackException() {
        var counter = mixedCounter();
        var failure = new IllegalStateException("callback failure");
        int[] calls = {0};
        assertSame(failure, assertThrows(IllegalStateException.class, () -> counter.forEach(entry -> {
            calls[0]++;
            throw failure;
        })));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> counter.forEach((key, amount) -> {
            calls[0]++;
            throw failure;
        })));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> counter.forEach((ObjLongConsumer<? super AEKey>) (key, amount) -> {
            calls[0]++;
            throw failure;
        })));
        assertEquals(3, calls[0]);
    }

    @ParameterizedTest
    @EnumSource(FuzzyMode.class)
    void primitiveFuzzyTraversalPreservesVariantsAmountsAndOrder(FuzzyMode mode) {
        var counter = new KeyCounter();
        for (int damage : new int[]{0, 1, 25, 50, 99, 100, 250}) {
            var stack = new ItemStack(Items.IRON_PICKAXE, 1, damage);
            counter.add(AEItemKey.of(stack), damage - 1L);
        }
        for (String name : List.of("a", "b", "c")) {
            var stack = new ItemStack(Items.STICK);
            var tag = new NBTTagCompound();
            tag.setString("variant", name);
            stack.setTagCompound(tag);
            counter.add(AEItemKey.of(stack), name.charAt(0));
        }
        for (var probe : List.of(AEItemKey.of(new ItemStack(Items.IRON_PICKAXE, 1, 50)),
            AEItemKey.of(Items.STICK), AEItemKey.of(Items.APPLE))) {
            var expected = new ArrayList<GenericStack>();
            for (var entry : counter.findFuzzy(probe, mode)) {
                expected.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
            var actual = new ArrayList<GenericStack>();
            counter.forEachFuzzy(probe, mode, (key, amount) -> actual.add(new GenericStack(key, amount)));
            assertEquals(expected, actual);
        }
    }

    @Test
    void primitiveTraversalIncludesZeroAndNegativeAmountsAndCanUpdateExistingValues() {
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.STICK), 0);
        counter.add(AEItemKey.of(Items.APPLE), -3);
        counter.add(AEItemKey.of(Items.POTATO), 8);
        var expected = new ArrayList<GenericStack>();
        for (var entry : counter) expected.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        var actual = new ArrayList<GenericStack>();
        counter.forEach((ObjLongConsumer<? super AEKey>) (key, amount) -> {
            actual.add(new GenericStack(key, amount));
            counter.set(key, amount + 1);
        });
        assertEquals(expected, actual);
        assertEquals(1, counter.get(AEItemKey.of(Items.STICK)));
        assertEquals(-2, counter.get(AEItemKey.of(Items.APPLE)));
        assertEquals(9, counter.get(AEItemKey.of(Items.POTATO)));
    }

    @Test
    void iteratorRemovalStillTargetsTheReturnedGroupAfterHasNextAdvances() {
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.STICK), 1);
        counter.add(AEItemKey.of(Items.APPLE), 2);
        var iterator = counter.iterator();
        assertThrows(IllegalStateException.class, iterator::remove);
        var first = iterator.next().getKey();
        assertTrue(iterator.hasNext());
        iterator.remove();
        assertEquals(0, counter.get(first));
        assertEquals(1, counter.size());
        assertThrows(IllegalStateException.class, iterator::remove);
        var last = iterator.next().getKey();
        assertNotEquals(first, last);
        assertFalse(iterator.hasNext());
        iterator.remove();
        assertTrue(counter.isEmpty());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void fuzzyIteratorRemovalAfterPrefetchKeepsTheNextEntryAndItsLiveValue() {
        var counter = new KeyCounter();
        for (int damage : new int[]{0, 50, 100, 200}) {
            counter.add(pickaxe(damage), damage + 1L);
        }
        var iterator = counter.findFuzzy(pickaxe(0), FuzzyMode.IGNORE_ALL).iterator();
        assertThrows(IllegalStateException.class, iterator::remove);
        for (int damage : new int[]{200, 100, 50, 0}) {
            var entry = iterator.next();
            assertEquals(pickaxe(damage), entry.getKey());
            assertEquals(damage > 0, iterator.hasNext());
            assertEquals(damage > 0, iterator.hasNext());
            entry.setValue(42L);
            assertEquals(42, counter.get(entry.getKey()));
            iterator.remove();
            assertEquals(0, counter.get(entry.getKey()));
            assertThrows(IllegalStateException.class, iterator::remove);
        }
        assertTrue(counter.isEmpty());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void fuzzyIteratorRemovalAfterExhaustingNonmatchingEntriesKeepsThoseEntries() {
        var counter = new KeyCounter();
        counter.add(pickaxe(200), 1);
        counter.add(pickaxe(50), 2);
        counter.add(pickaxe(0), 3);
        var iterator = counter.findFuzzy(pickaxe(200), FuzzyMode.PERCENT_50).iterator();
        assertEquals(pickaxe(200), iterator.next().getKey());
        assertFalse(iterator.hasNext());
        iterator.remove();
        assertEquals(0, counter.get(pickaxe(200)));
        assertEquals(2, counter.get(pickaxe(50)));
        assertEquals(3, counter.get(pickaxe(0)));
        assertFalse(iterator.hasNext());
    }
}
