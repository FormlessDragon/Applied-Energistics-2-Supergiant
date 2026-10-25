package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuTestDriver;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static ae2.crafting.CraftingRemainderFixtures.Fixture;
import static ae2.crafting.CraftingRemainderFixtures.Rule;
import static ae2.crafting.CraftingRemainderFixtures.Slot;
import static ae2.crafting.CraftingRemainderFixtures.at;
import static ae2.crafting.CraftingRemainderFixtures.charge;
import static ae2.crafting.CraftingRemainderFixtures.damage;
import static ae2.crafting.CraftingRemainderFixtures.exact;
import static ae2.crafting.CraftingRemainderFixtures.item;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static ae2.crafting.CraftingRemainderFixtures.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRemainderExecutionTest extends CraftingRecipeTestSupport {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mergedPushesBatchAvailableStateGroupsWithoutMixingThem(boolean differentStates) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var rule = f.rule("merged_states", item(Items.CARROT), true,
            new Slot[]{exact(apple), tool()}, grid -> at(grid, 1, damage(grid.getStackInSlot(1).getItemDamage() + 1)));
        var cpu = new CraftingCpuTestDriver(rule.pattern, 8, CraftingTestNetwork.world());
        var machine = new Machine(rule, true);
        cpu.stock(apple, 8);
        cpu.stock(damage(0), differentStates ? 4 : 8);
        if (differentStates) cpu.stock(damage(7), 4);
        assertEquals(differentStates ? 4 : 8, cpu.run(machine, 64));
        if (differentStates) assertEquals(4, cpu.run(machine, 64));
        assertEquals(0, cpu.remaining());
        assertEquals(differentStates ? 4 : 8, machine.maxMultiplier);
        assertEquals(differentStates ? 2 : 1, machine.calls);
        assertEquals(differentStates ? 4 : 8, cpu.pending(damage(1)));
        assertEquals(differentStates ? 4 : 0, cpu.pending(damage(8)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {false, true})
    void delayedReturnsCannotBeBorrowedForTheNextDispatch(boolean merge) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var rule = f.rule("delayed_nbt", item(Items.CARROT), true,
            new Slot[]{exact(apple), variant(charge(3), k -> k.getItem() == Items.PAPER, Items.PAPER)}, grid -> {
                var stack = grid.getStackInSlot(1);
                int charge = stack.hasTagCompound() ? stack.getTagCompound().getInteger("charge") : 0;
                return at(grid, 1, charge > 1 ? charge(charge - 1) : null);
            });
        var cpu = new CraftingCpuTestDriver(rule.pattern, 3, CraftingTestNetwork.world());
        var machine = new Machine(rule, merge);
        cpu.stock(apple, 3);
        cpu.stock(charge(3), 1);
        for (int state = 3; state >= 1; state--) {
            assertEquals(1, cpu.run(machine, 64));
            assertEquals(state - 1, cpu.remaining());
            assertEquals(0, cpu.run(machine, 64), "returned tools have not arrived yet");
            if (state > 1) {
                assertEquals(1, machine.returned.get(charge(state - 1)));
                assertEquals(1, cpu.pending(charge(state - 1)));
                assertEquals(1, cpu.deliver(charge(state - 1), 1));
            }
            machine.returned.clear();
        }
        assertEquals(1, machine.maxMultiplier, "one tool only supports one in-flight craft");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {false, true})
    void providerRejectionRestoresTheExactNbtStateWithoutPhantomReturns(boolean merge) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var rule = f.rule("rejected_tool", item(Items.CARROT), false,
            new Slot[]{exact(apple), exact(charge(2))}, grid -> at(grid, 1, charge(1)));
        var cpu = new CraftingCpuTestDriver(rule.pattern, 2, CraftingTestNetwork.world());
        var machine = new Machine(rule, merge);
        cpu.stock(apple, 2);
        cpu.stock(charge(2), 1);
        machine.reject = true;
        assertEquals(0, cpu.run(machine, 64));
        assertEquals(2, cpu.stored(apple));
        assertEquals(1, cpu.stored(charge(2)));
        assertEquals(0, cpu.stored(charge(1)));
        assertEquals(0, cpu.pending(charge(1)));
        assertEquals(2, cpu.remaining());
        machine.reject = false;
        assertEquals(1, cpu.run(machine, 64));
        assertEquals(1, cpu.pending(charge(1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {false, true})
    void saveRestoreKeepsReturnedNbtInExactlyOneOwnershipState(boolean stillInMachine) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var rule = f.rule("saved_tool", item(Items.CARROT), true,
            new Slot[]{exact(apple), variant(charge(2), k -> k.getItem() == Items.PAPER, Items.PAPER)}, grid -> {
                var stack = grid.getStackInSlot(1);
                int value = stack.hasTagCompound() ? stack.getTagCompound().getInteger("charge") : 0;
                return at(grid, 1, value > 1 ? charge(value - 1) : null);
            });
        var cpu = new CraftingCpuTestDriver(rule.pattern, 2, CraftingTestNetwork.world());
        var machine = new Machine(rule, true);
        cpu.stock(apple, 2);
        cpu.stock(charge(2), 1);
        assertEquals(1, cpu.run(machine, 16));
        if (!stillInMachine) assertEquals(1, cpu.deliver(charge(1), 1));
        cpu.saveAndRestore();
        assertEquals(1, cpu.remaining());
        assertEquals(0, cpu.stored(charge(2)));
        assertEquals(stillInMachine ? 1 : 0, cpu.pending(charge(1)));
        assertEquals(stillInMachine ? 0 : 1, cpu.stored(charge(1)));
        if (stillInMachine) {
            assertEquals(0, cpu.run(machine, 16));
            assertEquals(1, cpu.deliver(charge(1), 1));
        }
        assertEquals(1, cpu.run(machine, 16));
        assertEquals(0, cpu.remaining());
        assertEquals(0, cpu.pending(charge(1)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {false, true})
    void missingOneSimultaneousToolPreventsAllDispatch(boolean merge) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var rule = f.rule("two_tools", item(Items.CARROT), false,
            new Slot[]{exact(apple), tool(), tool()}, grid -> {
                var out = at(grid, 1, damage(0));
                out.set(2, damage(0).toStack());
                return out;
            });
        var cpu = new CraftingCpuTestDriver(rule.pattern, 2, CraftingTestNetwork.world());
        var machine = new Machine(rule, merge);
        cpu.stock(apple, 2);
        cpu.stock(damage(0), 1);
        assertEquals(0, cpu.run(machine, 64));
        assertEquals(0, machine.calls);
        assertEquals(1, cpu.stored(damage(0)));
        assertEquals(0, cpu.pending(damage(0)));
        cpu.stock(damage(0), 1);
        assertEquals(1, cpu.run(machine, 64));
        assertEquals(2, cpu.pending(damage(0)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(ReturnRule.class)
    void cpuWaitsForExactlyWhatTheWholeRecipeActuallyReturns(ReturnRule kind) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var golden = item(Items.GOLDEN_APPLE);
        var returnedInput = kind == ReturnRule.TWO_IN_ONE_SLOT ? item(Items.BOWL) : damage(0);
        var slots = kind == ReturnRule.ONE_OF_TWO_SLOTS ? new Slot[]{exact(apple), tool(), tool()}
            : kind == ReturnRule.DEPENDS_ON_OTHER_SLOT
            ? new Slot[]{variant(apple, k -> k.equals(apple) || k.equals(golden), Items.APPLE, Items.GOLDEN_APPLE), tool()}
            : new Slot[]{exact(apple), exact(returnedInput)};
        var rule = f.rule("cpu_return_" + kind.name().toLowerCase(), item(Items.CARROT), true, slots, grid -> {
            if (kind == ReturnRule.FROM_EMPTY_SLOT) return at(grid, 8, item(Items.STICK));
            var out = at(grid, 1, kind == ReturnRule.DEPENDS_ON_OTHER_SLOT
                && grid.getStackInSlot(0).getItem() == Items.GOLDEN_APPLE ? damage(2) : returnedInput);
            if (kind == ReturnRule.TWO_IN_ONE_SLOT) out.get(1).setCount(2);
            return out;
        });
        var cpu = new CraftingCpuTestDriver(rule.pattern, 1, CraftingTestNetwork.world());
        var machine = new Machine(rule, false);
        cpu.stock(kind == ReturnRule.DEPENDS_ON_OTHER_SLOT ? golden : apple, 1);
        cpu.stock(returnedInput, kind == ReturnRule.ONE_OF_TWO_SLOTS ? 2 : 1);
        assertEquals(1, cpu.run(machine, 1));
        var expected = new KeyCounter();
        for (var key : List.of(damage(0), damage(2), item(Items.STICK), item(Items.BOWL)))
            expected.add(key, cpu.pending(key));
        assertEquals(amounts(machine.returned), amounts(expected), "CPU waits for a different remainder than the machine made");
    }

    enum ReturnRule {ONE_OF_TWO_SLOTS, TWO_IN_ONE_SLOT, DEPENDS_ON_OTHER_SLOT, FROM_EMPTY_SLOT}

    private static final class Machine implements ICraftingProvider {
        final Rule rule;
        final boolean merge;
        final KeyCounter returned = new KeyCounter();
        boolean reject;
        int calls;
        int maxMultiplier;

        Machine(Rule rule, boolean merge) {
            this.rule = rule;
            this.merge = merge;
        }

        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(rule.pattern);
        }

        public boolean isBusy() {
            return false;
        }

        public boolean canMergePatternPush(IPatternDetails pattern) {
            return merge;
        }

        public int getMaxPatternPushMultiplier(IPatternDetails pattern, int max) {
            return max;
        }

        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, int multiplier) {
            calls++;
            maxMultiplier = Math.max(maxMultiplier, multiplier);
            if (reject) return false;
            if (multiplier > 1) for (var input : inputs)
                assertEquals(1, input.size(),
                    "merged per-slot bags must preserve one validated combination");
            for (int craft = 0; craft < multiplier; craft++) {
                var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
                rule.pattern.fillCraftingGrid(inputs, grid::setInventorySlotContents);
                assertTrue(rule.recipe.matches(grid, CraftingTestNetwork.world()), "machine received an incomplete grid");
                assertEquals(rule.output, GenericStack.fromItemStack(rule.recipe.getCraftingResult(grid)));
                for (var stack : rule.recipe.getRemainingItems(grid)) {
                    if (!stack.isEmpty()) returned.add(AEItemKey.of(stack), stack.getCount());
                }
            }
            return true;
        }
    }
}
