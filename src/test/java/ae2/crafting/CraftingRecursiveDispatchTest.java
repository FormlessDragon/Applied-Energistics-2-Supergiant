package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.execution.CraftingCpuTestDriver;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRecursiveDispatchTest extends CraftingPlanningTestSupport {
    @ParameterizedTest
    @CsvSource({"false,1,false", "false,1,true", "true,1,false", "true,1,true"})
    void plannedRecursiveGroupsExecuteWithDelayedReturnsRejectionAndReload(boolean mutual, int budget, boolean merge) throws Exception {
        var f = new Fixture(budget);
        if (mutual) {
            for (int output = 0; output < 4; output++) {
                var inputs = new ArrayList<Object>();
                for (int input = 0; input < 4; input++) if (input != output) inputs.addAll(List.of("m" + input, 1));
                f.recipe("grow-" + output, inputs.toArray(), new Object[]{"m" + output, 6});
                f.stock("m" + output, output == 0 ? 0 : 3);
            }
            f.recipe("root", new Object[]{"m0", 1}, new Object[]{"result", 1});
        } else {
            f.recipe("grow-a", new Object[]{"a", 1, "tool", 1}, new Object[]{"a", 2, "tool", 1});
            f.recipe("grow-b", new Object[]{"b", 1, "tool", 1}, new Object[]{"b", 2, "tool", 1});
            f.recipe("root", new Object[]{"a", 3, "b", 2}, new Object[]{"result", 1});
            f.stock("a", 1);
            f.stock("b", 1);
            f.stock("tool", 1);
        }
        f.register(merge);
        var plan = f.net.plan(f.key("result"), 17);
        complete(f.net, plan, f.key("result"), 17);
        var cpu = new CraftingCpuTestDriver(plan, CraftingTestNetwork.world());
        plan.usedItems().forEach(cpu::stock);
        var pending = new KeyCounter();
        var dispatched = new Object2LongOpenHashMap<IPatternDetails>();
        boolean[] rejected = {true};
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.copyOf(f.patterns.values()); }
            public boolean isBusy() { return false; }
            public boolean canMergePatternPush(IPatternDetails pattern) { return merge; }
            public int getMaxPatternPushMultiplier(IPatternDetails pattern, int max) { return max; }
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, int multiplier) {
                if (rejected[0]) return false;
                for (int slot = 0; slot < inputs.length; slot++) {
                    var input = pattern.getInputs()[slot];
                    var expected = input.possibleInputs()[0];
                    assertEquals(expected.amount() * input.getMultiplier() * multiplier, inputs[slot].get(expected.what()));
                }
                dispatched.addTo(pattern, multiplier);
                pattern.getOutputs().forEach(stack -> pending.add(stack.what(), stack.amount() * multiplier));
                return true;
            }
        };
        assertEquals(0, cpu.run(provider, budget));
        plan.usedItems().forEach((key, amount) -> assertEquals(amount, cpu.stored(key), "rejected dispatch must restore its seeds"));
        rejected[0] = false;
        for (int tick = 0; cpu.remaining() > 0 && tick < 10_000; tick++) {
            cpu.run(provider, budget);
            if (tick == 0) {
                assertTrue(pending.size() > 0, "reload must include in-flight crafting outputs");
                cpu.saveAndRestore();
            }
            if (tick % 3 != 2) continue;
            for (var key : f.keys.values()) {
                if (key.equals(f.key("result"))) continue;
                long returned = pending.get(key);
                if (returned > 0) assertEquals(returned, cpu.deliver(key, returned));
                pending.remove(key);
            }
        }
        assertEquals(0, cpu.remaining(), "the real CPU must finish without spending a seed that is still needed");
        assertEquals(17, pending.get(f.key("result")));
        assertEquals(17, cpu.pending(f.key("result")));
        // Reload decodes new pattern instances. Compare by their persistent encoded definitions.
        for (var entry : plan.patternTimes().object2LongEntrySet()) {
            long actual = dispatched.object2LongEntrySet().stream()
                .filter(e -> e.getKey().getDefinition().equals(entry.getKey().getDefinition())).mapToLong(e -> e.getLongValue()).sum();
            assertEquals(entry.getLongValue(), actual, "CPU dispatch counts must match the calculated plan");
        }
    }
}
