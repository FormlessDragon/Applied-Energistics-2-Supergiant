package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.pattern.AEProcessingPattern;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static ae2.crafting.CraftingPlanningTestSupport.amounts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test data and an independent, explicitly ordered feasibility witness. Never borrows missing ingredients.
 */
final class CraftingAdversarialFixtures {
    static final int RANDOM_CASES = 64;

    static long ceil(long amount, long batch) {
        return amount <= 0 ? 0 : 1 + (amount - 1) / batch;
    }

    static void balanced(ICraftingPlan plan) {
        var balance = new HashMap<AEKey, BigInteger>();
        for (var counter : List.of(plan.usedItems(), plan.emittedItems(), plan.missingItems())) {
            counter.forEach((key, count) -> balance.merge(key, BigInteger.valueOf(count), BigInteger::add));
        }
        plan.patternTimes().forEach((pattern, count) -> {
            assertTrue(count >= 0, "negative craft count");
            for (var input : pattern.getInputs()) {
                assertEquals(1, input.possibleInputs().length, "exact-input balance oracle");
                var stack = input.possibleInputs()[0];
                var taken = BigInteger.valueOf(stack.amount()).multiply(BigInteger.valueOf(input.getMultiplier()))
                                      .multiply(BigInteger.valueOf(count));
                balance.merge(stack.what(), taken.negate(), BigInteger::add);
                var remaining = input.getRemainingKey(stack.what());
                if (remaining != null) balance.merge(remaining, taken, BigInteger::add);
            }
            for (var output : pattern.getOutputs())
                balance.merge(output.what(),
                    BigInteger.valueOf(output.amount()).multiply(BigInteger.valueOf(count)), BigInteger::add);
        });
        balance.forEach((key, count) -> assertTrue(count.signum() >= 0, "negative material balance: " + count));
        assertTrue(balance.getOrDefault(plan.finalOutput().what(), BigInteger.ZERO)
                          .compareTo(BigInteger.valueOf(plan.finalOutput().amount())) >= 0, "final output is not covered");
    }

    /**
     * Chain, shared diamond, nested growth, independent loops, two-pattern ring, returned catalyst, shared byproduct,
     * and unavailable high-priority alternative. Coefficients, task hashes, rounding and registration order vary.
     * The witness deliberately keeps some surplus; feasibility is established without asking the planner to optimize.
     */
    static Scenario generated(int seed) {
        var random = new Random(0xAE212L + seed);
        var f = new Fixture(seed);
        int family = seed % 8;
        long request = new long[]{1, 3, 17, 65}[seed / 8 % 4];
        long rootOut = 1 + random.nextInt(4), leftNeed = 1 + random.nextInt(3), rightNeed = 1 + random.nextInt(3);
        if (family == 0) rightNeed = 0; // A chain, versus the shared diamond in family 1.
        long leftOut = 1 + random.nextInt(3), rightOut = 1 + random.nextInt(3);
        long leftInput = 1 + random.nextInt(3), rightInput = 1 + random.nextInt(3);
        long plateOut = 2 + random.nextInt(3), plateInput = 1 + random.nextInt(3);
        long rootTimes = ceil(request, rootOut), leftTimes = ceil(rootTimes * leftNeed, leftOut);
        long rightTimes = ceil(rootTimes * rightNeed, rightOut);
        long direct = seed % 3 == 0 ? 2 : 0;
        boolean independent = family == 3;
        long plateTimes = ceil(leftTimes * leftInput + (independent ? 0 : rightTimes * rightInput), plateOut);
        long needed = plateTimes * plateInput + rootTimes * direct;
        String main = family == 2 ? "b" : "a";
        long capacity = family == 4 ? 1 : 1 + random.nextInt(4), gain = 1 + random.nextInt(5);
        f.stock("a", capacity);
        f.stock("unrelated", 4096);
        if ((seed & 1) != 0) f.stock("result", 100_000);
        var witness = new ArrayList<Step>();
        if (family == 4) {
            var ab = f.recipe("ring-a-b", new Object[]{"a", 1}, new Object[]{"b", 2});
            var ba = f.recipe("ring-b-a", new Object[]{"b", 1}, new Object[]{"a", 2});
            long rounds = ceil(needed, 3);
            for (long i = 0; i < rounds; i++) {
                witness.add(new Step(ab, 1));
                witness.add(new Step(ba, 2));
            }
        } else {
            long bTimes = family == 2 ? needed : independent ? rightTimes * rightInput : 0;
            long aNeeded = family == 2 ? bTimes : needed;
            long aTimes = ceil(aNeeded, gain);
            if (family == 6) aTimes = Math.max(aTimes, rootTimes);
            var growInputs = new ArrayList<Object>(List.of("a", capacity));
            var growOutputs = new ArrayList<Object>(List.of("a", capacity + gain));
            if (family == 5) {
                growInputs.addAll(List.of("tool", 1));
                growOutputs.addAll(List.of("tool", 1));
                f.stock("tool", 1);
            }
            if (seed % 5 == 0) {
                growInputs.addAll(List.of("fuel", 2));
                f.stock("fuel", 2 * aTimes + 16);
            }
            if (family == 6) growOutputs.addAll(List.of("dust", 1));
            var grow = f.recipe("grow-a", growInputs.toArray(), growOutputs.toArray());
            witness.add(new Step(grow, aTimes));
            if (family == 2 || independent) {
                f.stock("b", 1);
                var inputs = family == 2 ? new Object[]{"b", 1, "a", 1} : new Object[]{"b", 1};
                var growB = f.recipe("grow-b", inputs, new Object[]{"b", 2});
                witness.add(new Step(growB, bTimes));
            }
        }
        var plate = f.recipe("plate", new Object[]{main, plateInput}, new Object[]{"plate", plateOut});
        var left = f.recipe("left", new Object[]{"plate", leftInput}, new Object[]{"left", leftOut});
        var right = f.recipe("right", new Object[]{independent ? "b" : "plate", rightInput},
            new Object[]{"right", rightOut});
        var rootInputs = new ArrayList<Object>(List.of("left", leftNeed));
        if (rightNeed > 0) rootInputs.addAll(List.of("right", rightNeed));
        if (direct > 0) rootInputs.addAll(List.of(main, direct));
        if (family == 6) rootInputs.addAll(List.of("dust", 1));
        var root = f.recipe("root", rootInputs.toArray(), new Object[]{"result", rootOut});
        if (family == 7) f.recipe("unavailable-plate", new Object[]{"absent-ore", 1}, new Object[]{"plate", 9});
        f.register((seed & 1) != 0);
        if (family == 7) f.net.priority(f.patterns.get("unavailable-plate"), 10);
        witness.addAll(List.of(new Step(plate, plateTimes), new Step(left, leftTimes),
            new Step(right, rightTimes), new Step(root, rootTimes)));
        return new Scenario(f, "result", request, List.copyOf(witness));
    }

    record Step(IPatternDetails pattern, long times) {
    }

    record Scenario(Fixture fixture, String output, long amount, List<Step> witness) {
        void verifyWitness() {
            var inventory = new HashMap<>(amounts(fixture.net.stock));
            // Existing target stock may seed a cycle, but cannot count as newly produced final output.
            long existingOutput = inventory.getOrDefault(fixture.key(output), 0L);
            for (var step : witness) {
                assertTrue(step.times >= 0);
                for (long craft = 0; craft < step.times; craft++) {
                    var returns = new HashMap<AEKey, Long>();
                    for (var input : step.pattern.getInputs()) {
                        assertEquals(1, input.possibleInputs().length, "witness requires an exact input");
                        var stack = input.possibleInputs()[0];
                        long take = Math.multiplyExact(stack.amount(), input.getMultiplier());
                        long held = inventory.getOrDefault(stack.what(), 0L);
                        assertTrue(held >= take, () -> "invalid witness: " + fixture.name(stack.what())
                            + " needs " + take + " but has " + held + "\n" + fixture.describe());
                        inventory.put(stack.what(), held - take);
                        var remaining = input.getRemainingKey(stack.what());
                        if (remaining != null) returns.merge(remaining, take, Math::addExact);
                    }
                    returns.forEach((key, count) -> inventory.merge(key, count, Math::addExact));
                    for (var output : step.pattern.getOutputs()) {
                        inventory.merge(output.what(), output.amount(), Math::addExact);
                    }
                }
            }
            assertTrue(inventory.getOrDefault(fixture.key(output), 0L) >= Math.addExact(amount, existingOutput),
                "witness must independently produce the entire request");
        }
    }

    static final class Fixture {
        final CraftingTestNetwork net = new CraftingTestNetwork();
        final Map<String, AEKey> keys = new LinkedHashMap<>();
        final Map<String, IPatternDetails> patterns = new LinkedHashMap<>();
        final int orderSalt;

        Fixture(int orderSalt) {
            this.orderSalt = orderSalt;
        }

        AEKey key(String name) {
            return keys.computeIfAbsent(name, label -> {
                var stack = new ItemStack(Items.PAPER);
                var tag = new NBTTagCompound();
                tag.setString("adversarial_material", label);
                stack.setTagCompound(tag);
                return AEItemKey.of(stack);
            });
        }

        String name(AEKey key) {
            return keys.entrySet().stream().filter(e -> e.getValue().equals(key)).map(Map.Entry::getKey)
                       .findFirst().orElse(key.toString());
        }

        Map<String, Long> named(KeyCounter counter) {
            var result = new LinkedHashMap<String, Long>();
            counter.forEach((key, count) -> {
                if (count != 0) result.put(name(key), count);
            });
            return result;
        }

        void stock(String name, long amount) {
            net.stock.set(key(name), amount);
        }

        IPatternDetails recipe(String name, Object[] inputs, Object[] outputs) {
            return sparseRecipe(name, stacks(inputs), stacks(outputs));
        }

        IPatternDetails sparseRecipe(String name, List<GenericStack> inputs, List<GenericStack> outputs) {
            var encoded = new ItemStack(Items.PAPER);
            AEProcessingPattern.encode(encoded, inputs, outputs);
            encoded.getTagCompound().setString("adversarial_pattern", name + ":" + orderSalt);
            // Keep task iteration reproducible across JVMs, while testing different legal pattern hash orders.
            var pattern = new OrderedProcessingPattern(AEItemKey.of(encoded), name,
                (name + ":" + orderSalt).hashCode());
            patterns.put(name, pattern);
            return pattern;
        }

        private List<GenericStack> stacks(Object[] pairs) {
            var stacks = new ArrayList<GenericStack>();
            for (int i = 0; i < pairs.length; i += 2) {
                stacks.add(new GenericStack(key((String) pairs[i]), ((Number) pairs[i + 1]).longValue()));
            }
            return stacks;
        }

        void register(boolean reverse) {
            var ordered = new ArrayList<>(patterns.values());
            if (reverse) Collections.reverse(ordered);
            for (var pattern : ordered) net.addPattern(pattern, 0);
        }

        String describe() {
            var text = new StringBuilder("orderSalt=" + orderSalt + "\n");
            patterns.forEach((name, pattern) -> {
                text.append(name).append(": ");
                for (var input : pattern.getInputs()) {
                    var stack = input.possibleInputs()[0];
                    text.append(name(stack.what())).append('x').append(stack.amount() * input.getMultiplier())
                        .append(' ');
                }
                text.append("-> ");
                for (var output : pattern.getOutputs())
                    text.append(name(output.what())).append('x')
                        .append(output.amount()).append(' ');
                text.append('\n');
            });
            net.stock.forEach((key, count) -> text.append(name(key)).append(" stored=").append(count).append(' '));
            return text.toString();
        }
    }

    private static final class OrderedProcessingPattern extends AEProcessingPattern {
        private final String name;
        private final int hash;

        OrderedProcessingPattern(AEItemKey definition, String name, int hash) {
            super(definition);
            this.name = name;
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
