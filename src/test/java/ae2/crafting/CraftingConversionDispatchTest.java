package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.execution.CraftingCpuTestDriver;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.Scenario;
import static ae2.crafting.CraftingAdversarialFixtures.Step;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;

/** Zero-slack conversion plans must also survive machine timing and restored pattern instances. */
class CraftingConversionDispatchTest extends CraftingPlanningTestSupport {
    static Stream<Arguments> cases() {
        var cases = new ArrayList<Arguments>();
        for (int budget : new int[]{1, 13})
            for (boolean merge : new boolean[]{false, true}) for (boolean reload : new boolean[]{false, true})
                for (int order : new int[]{0, 2}) cases.add(Arguments.of(18, budget, merge, reload, order));
        return cases.stream();
    }

    @ParameterizedTest(name = "seed={0}, budget={1}, merge={2}, reload={3}, returns={4}")
    @MethodSource("cases")
    void conversionTasksSurviveRejectedPushesStaggeredReturnsAndReload(
        int seed, int budget, boolean merge, boolean reload, int order) throws Exception {
        var f = CraftingFiniteStatePlanningTest.conversionFixture(seed);
        var plan = f.net.plan(f.key("result"), 3);
        complete(f.net, plan, f.key("result"), 3);
        drive(f, plan, budget, merge, reload, order, null);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "18,true"})
    void competingConversionsMustKeepTheOnlyViableFirstStep(int salt, boolean reverseInputs) throws Exception {
        var f = new Fixture(salt);
        f.net.recursiveReserve(0);
        f.recipe("ab-c", reverseInputs ? new Object[]{"b", 1, "a", 1} : new Object[]{"a", 1, "b", 1}, new Object[]{"c", 2});
        f.recipe("ac-b", reverseInputs ? new Object[]{"c", 1, "a", 1} : new Object[]{"a", 1, "c", 1}, new Object[]{"b", 2});
        f.recipe("b-a", new Object[]{"b", 2}, new Object[]{"a", 2});
        f.recipe("root", new Object[]{"a", 1, "c", 2}, new Object[]{"result", 1});
        for (var name : List.of("a", "b", "c")) f.stock(name, 1);
        f.register(reverseInputs);
        // ab-c is initially runnable too, but choosing it first leaves only 3 C and no possible next craft.
        new Scenario(f, "result", 1, List.of(new Step(f.patterns.get("ac-b"), 1),
            new Step(f.patterns.get("b-a"), 1), new Step(f.patterns.get("ab-c"), 1),
            new Step(f.patterns.get("root"), 1))).verifyWitness();
        var plan = f.net.plan(f.key("result"), 1);
        complete(f.net, plan, f.key("result"), 1);
        balanced(plan);
        assertNull(CraftingTestNetwork.replay(plan));
        var impossible = f.net.plan(f.key("result"), 2);
        assertTrue(impossible.simulation());
        var less = f.net.plan(f.key("result"), 2, ae2.api.networking.crafting.CalculationStrategy.CRAFT_LESS);
        complete(f.net, less, f.key("result"), 1);
        drive(f, plan, 1, true, true, 2, null);
    }

    @ParameterizedTest
    @CsvSource({"0,true", "3,true"})
    void orderedConversionsSurviveMergingAndBatchComposition(int mode, boolean reload) throws Exception {
        var f = CraftingFiniteStatePlanningTest.conversionFixture(18);
        var conversion = f.net.plan(f.key("result"), 3);
        complete(f.net, conversion, f.key("result"), 3);
        var root = f.patterns.get("root");
        var stock = new KeyCounter();
        for (var input : root.getInputs()) stock.add(input.possibleInputs()[0].what(), input.possibleInputs()[0].amount() * input.getMultiplier());
        var times = new Object2LongOpenHashMap<IPatternDetails>();
        times.put(root, 1);
        var ordinary = new CraftingPlan(new GenericStack(f.key("result"), 1), 128, false, false,
            stock, new KeyCounter(), new KeyCounter(), 0, times, null, List.of(), null, new CraftingAttemptMetrics(), null);
        if (mode == 3) {
            var batch = BatchCraftingPlan.combine(new GenericStack(f.key("result"), 4), List.of(conversion, ordinary), List.of());
            drive(f, batch, 13, true, reload, 1, null);
        } else {
            var first = mode == 1 ? ordinary : conversion;
            var second = mode == 0 ? ordinary : conversion;
            drive(f, first, 13, true, reload, 2, second);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {65, 1_000_000_000_000L})
    void repeatedConversionCertificatesStayCompressed(long count) throws Exception {
        var f = new Fixture(0);
        f.net.recursiveReserve(0);
        f.recipe("a-b", new Object[]{"a", 1, "fuel", 1}, new Object[]{"dust", 1, "b", 1});
        f.recipe("b-a", new Object[]{"b", 1}, new Object[]{"a", 1});
        f.recipe("root", new Object[]{"dust", 1}, new Object[]{"result", 1});
        f.stock("a", 1);
        f.stock("fuel", count);
        f.register(false);
        var plan = f.net.plan(f.key("result"), count);
        complete(f.net, plan, f.key("result"), count);
        var order = CraftingDispatchOrder.forPlan(plan);
        assertFalse(order.isEmpty());
        assertTrue(order.size() < 20, "repeated passes must not allocate one step per craft");
        assertTrue(order.stream().anyMatch(batch -> batch.repetitions() > 1));
        var cpu = new CraftingCpuTestDriver(plan, CraftingTestNetwork.world());
        cpu.saveAndRestore();
        assertEquals(plan.patternTimes().values().longStream().sum(), cpu.remaining());
        if (count == 65) drive(f, plan, 13, true, true, 1, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4})
    void aDamagedCertificateCannotSilentlyDiscardOrReorderTasks(int damage) throws Exception {
        var f = CraftingFiniteStatePlanningTest.conversionFixture(18);
        var plan = f.net.plan(f.key("result"), 3);
        var cpu = new CraftingCpuTestDriver(plan, CraftingTestNetwork.world());
        plan.usedItems().forEach(cpu::stock);
        long remaining = cpu.remaining();
        assertThrows(IllegalArgumentException.class, () -> cpu.saveAndRestore(saved -> {
            var order = saved.getTagList("dispatchOrder", 10);
            var step = order.getCompoundTagAt(0).getTagList("steps", 10).getCompoundTagAt(0);
            switch (damage) {
                case 0 -> saved.setTag("dispatchOrder", new net.minecraft.nbt.NBTTagList());
                case 1 -> step.setLong("crafts", step.getLong("crafts") + 1);
                case 2 -> step.setInteger("task", Integer.MAX_VALUE);
                case 3 -> saved.setString("dispatchOrder", "broken");
                case 4 -> order.getCompoundTagAt(0).removeTag("ordered");
                case 5 -> step.removeTag("task");
            }
        }));
        assertEquals(remaining, cpu.remaining());
        plan.usedItems().forEach((key, amount) -> assertEquals(amount, cpu.stored(key)));
        cpu.saveAndRestore();
        assertEquals(remaining, cpu.remaining());
    }

    private static void drive(Fixture f, ICraftingPlan plan, int budget, boolean merge, boolean reload,
                              int order, ICraftingPlan appended) throws Exception {
        var cpu = new CraftingCpuTestDriver(plan, CraftingTestNetwork.world());
        plan.usedItems().forEach(cpu::stock);
        var pending = new ArrayList<Delivery>();
        var dispatched = new Object2LongOpenHashMap<AEKey>();
        var expectedCounts = new Object2LongOpenHashMap<AEKey>();
        plan.patternTimes().forEach((pattern, count) -> expectedCounts.addTo(pattern.getDefinition(), count));
        if (appended != null) appended.patternTimes().forEach((pattern, count) -> expectedCounts.addTo(pattern.getDefinition(), count));
        int[] tick = {0};
        int[] rejects = {0};
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.copyOf(f.patterns.values()); }
            public boolean isBusy() { return false; }
            public boolean canMergePatternPush(IPatternDetails pattern) { return merge; }
            public int getMaxPatternPushMultiplier(IPatternDetails pattern, int max) { return max; }
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, int multiplier) {
                if (tick[0] % 7 == 0) { rejects[0]++; return false; }
                for (int slot = 0; slot < inputs.length; slot++) {
                    var input = pattern.getInputs()[slot];
                    var expected = input.possibleInputs()[0];
                    assertEquals(expected.amount() * input.getMultiplier() * multiplier, inputs[slot].get(expected.what()));
                }
                dispatched.addTo(pattern.getDefinition(), multiplier);
                for (var output : pattern.getOutputs()) {
                    int delay = 1 + Math.floorMod(f.name(output.what()).hashCode(), 5);
                    pending.add(new Delivery(output.what(), output.amount() * multiplier, tick[0] + delay));
                }
                return true;
            }
        };
        // A small dispatch budget visits only a rotating window, which may contain no runnable task.
        for (int attempt = 0; attempt < plan.patternTimes().size() && rejects[0] == 0; attempt++) {
            assertEquals(0, cpu.run(provider, budget));
        }
        assertTrue(rejects[0] > 0);
        plan.usedItems().forEach((key, amount) -> assertEquals(amount, cpu.stored(key)));
        boolean restored = false;
        boolean added = false;
        long produced = 0;
        for (tick[0] = 1; tick[0] < 2000 && (cpu.remaining() > 0 || !pending.isEmpty()); tick[0]++) {
            cpu.run(provider, budget);
            if (appended != null && !added && !pending.isEmpty()) {
                cpu.merge(appended);
                added = true;
            }
            if (reload && !pending.isEmpty() && tick[0] % 3 == 1) {
                cpu.saveAndRestore();
                restored = true;
            }
            var ready = new ArrayList<Delivery>();
            for (var delivery : pending) if (delivery.due <= tick[0]) ready.add(delivery);
            if (order == 2) Collections.reverse(ready);
            for (var delivery : ready) {
                if (delivery.key.equals(f.key("result"))) produced += delivery.amount;
                else assertEquals(delivery.amount, cpu.deliver(delivery.key, delivery.amount));
                pending.remove(delivery);
                if (order != 0) break;
            }
        }
        assertEquals(reload, restored);
        assertEquals(0, cpu.remaining(), () -> "stalled conversion\n" + f.describe() + "\nplan=" + plan.patternTimes());
        assertEquals(appended != null, added);
        assertEquals(plan.finalOutput().amount() + (appended == null ? 0 : appended.finalOutput().amount()), produced);
        assertEquals(expectedCounts, dispatched);
    }

    private record Delivery(AEKey key, long amount, int due) {
    }
}
