package ae2.api.stacks;

import ae2.api.config.FuzzyMode;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyCollisionTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static AEItemKey tool(int damage, String value) {
        var stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setItemDamage(damage);
        var tag = new NBTTagCompound();
        tag.setString("audit", value);
        stack.setTagCompound(tag);
        return AEItemKey.of(stack);
    }

    @Test
    void exactCollisionKeysAndFuzzyRangesMatchIndependentOracle() {
        var first = tool(1, "Aa");
        var second = tool(1, "BB");
        assertNotEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        var counter = new KeyCounter();
        counter.add(first, 1);
        counter.add(second, 2);
        assertEquals(2, counter.size());
        assertEquals(1, counter.get(tool(1, "Aa")));
        assertEquals(2, counter.get(tool(1, "BB")));
        var oracle = new HashMap<AEKey, Long>();
        oracle.put(first, 1L);
        oracle.put(second, 2L);
        var random = new Random(140029L);
        for (int i = 0; i < 256; i++) {
            var key = tool(random.nextInt(251), "variant-" + random.nextInt(12));
            long amount = random.nextInt(1, 32);
            counter.add(key, amount);
            oracle.merge(key, amount, Long::sum);
        }
        assertEquals(oracle.size(), counter.size());
        for (var entry : oracle.entrySet()) assertEquals(entry.getValue().longValue(), counter.get(entry.getKey()));
        for (var mode : FuzzyMode.values()) {
            for (int damage : new int[]{0, 1, 25, 50, 63, 125, 188, 250}) {
                var probe = tool(damage, "probe");
                var expected = new HashSet<AEKey>();
                for (var key : oracle.keySet()) if (probe.fuzzyEquals(key, mode)) expected.add(key);
                var actual = new HashSet<AEKey>();
                for (var entry : counter.findFuzzy(probe, mode)) actual.add(entry.getKey());
                assertEquals(expected, actual, mode + "/" + damage);
            }
        }
    }

    @Test
    void liveViewsCopyAndRemovalKeepBothIndexesConsistent() {
        var a = tool(1, "Aa");
        var b = tool(1, "BB");
        var c = tool(125, "third");
        var counter = new KeyCounter();
        counter.set(a, 1);
        counter.set(b, 2);
        counter.set(c, 3);
        var view = counter.findFuzzy(a, FuzzyMode.IGNORE_ALL);
        var it = view.iterator();
        var first = it.next();
        first.setValue(7L);
        assertEquals(7, counter.get(first.getKey()));
        it.remove();
        assertEquals(2, counter.size());
        assertEquals(2, view.size());
        var copy = new KeyCounter();
        copy.addAll(counter);
        counter.clear();
        assertTrue(view.isEmpty());
        assertEquals(2, copy.findFuzzy(a, FuzzyMode.IGNORE_ALL).size());
        copy.reset();
        copy.removeZeros();
        assertTrue(copy.isEmpty());
        assertTrue(copy.findFuzzy(a, FuzzyMode.IGNORE_ALL).isEmpty());
        copy.add(a, 5);
        copy.add(b, 8);
        var keys = copy.iterator();
        keys.next();
        keys.remove();
        assertEquals(1, copy.findFuzzy(a, FuzzyMode.IGNORE_ALL).size());
    }
}
