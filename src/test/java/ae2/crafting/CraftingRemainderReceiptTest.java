package ae2.crafting;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuTestDriver;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mixed ownership during partial receipts, and overlapping primary/remainder keys.
 */
class CraftingRemainderReceiptTest extends CraftingRecipeTestSupport {
    private static ICraftingProvider machine(Rule rule) {
        return new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() {
                return List.of(rule.pattern);
            }

            public boolean isBusy() {
                return false;
            }

            public boolean canMergePatternPush(IPatternDetails pattern) {
                return true;
            }

            public int getMaxPatternPushMultiplier(IPatternDetails pattern, int limit) {
                return limit;
            }

            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, int multiplier) {
                for (int i = 0; i < multiplier; i++) {
                    var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
                    assertInstanceOf(IAssemblerPattern.class, pattern).fillCraftingGrid(inputs, grid::setInventorySlotContents);
                    assertTrue(rule.recipe.matches(grid, CraftingTestNetwork.world()));
                    assertEquals(rule.output, GenericStack.fromItemStack(rule.recipe.getCraftingResult(grid)));
                }
                for (var input : inputs)
                    for (var entry : input)
                        assertEquals(0, entry.getLongValue(),
                            "provider must receive exact input counts");
                return true;
            }
        };
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void partialNbtReturnsSurviveSaveRestoreWithoutDuplicatingTheInFlightCopies(int received) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var rule = f.rule("partially_received_cells", item(Items.CARROT), true,
            new Slot[]{exact(apple), variant(charge(2), k -> k.getItem() == Items.PAPER && k.toStack().hasTagCompound(), Items.PAPER)},
            grid -> {
                var cell = grid.getStackInSlot(1);
                int value = cell.hasTagCompound() ? cell.getTagCompound().getInteger("charge") : 0;
                return at(grid, 1, value > 1 ? charge(value - 1) : null);
            });
        var cpu = new CraftingCpuTestDriver(rule.pattern, 4, CraftingTestNetwork.world());
        var machine = machine(rule);
        cpu.stock(apple, 4);
        cpu.stock(charge(2), 2);
        assertEquals(2, cpu.run(machine, 64));
        assertEquals(2, cpu.pending(charge(1)));
        if (received > 0) assertEquals(received, cpu.deliver(charge(1), received));
        cpu.saveAndRestore();
        assertEquals(received, cpu.stored(charge(1)));
        assertEquals(2 - received, cpu.pending(charge(1)));
        assertEquals(0, cpu.stored(charge(2)));
        assertEquals(received, cpu.run(machine, 64));
        assertEquals(2 - received, cpu.remaining());
        assertEquals(0, cpu.stored(charge(1)));
        if (received < 2) {
            assertEquals(2 - received, cpu.deliver(charge(1), 3), "accept only the remaining in-flight copies");
            assertEquals(2 - received, cpu.run(machine, 64));
        }
        assertEquals(0, cpu.remaining());
        assertEquals(0, cpu.pending(charge(1)));
        assertEquals(0, cpu.deliver(charge(1), 1), "duplicate completion does not create a tool");
        assertEquals(0, cpu.stored(charge(1)));
        assertEquals(0, cpu.stored(apple));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aPrimaryOutputAndItsSameKeyRemainderAreConservedAcrossReceiptBoundaries(boolean combined) {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("primary_and_return_same_key", carrot, false, new Slot[]{exact(apple)}, grid -> at(grid, 8, carrot));
        var cpu = new CraftingCpuTestDriver(rule.pattern, 1, CraftingTestNetwork.world());
        cpu.stock(apple, 1);
        assertEquals(1, cpu.run(machine(rule), 64));
        assertEquals(2, cpu.pending(carrot), "declared output and actual remainder must both be counted");
        long leftAtMachine = 2;
        if (combined) leftAtMachine -= cpu.deliver(carrot, 2);
        else {
            leftAtMachine -= cpu.deliver(carrot, 1);
            leftAtMachine -= cpu.deliver(carrot, 1);
        }
        // Finishing the job may reject surplus. An unaccepted receipt remains owned by the sender.
        assertEquals(2, cpu.stored(carrot) + leftAtMachine);
        assertTrue(cpu.stored(carrot) >= 1);
        assertEquals(0, cpu.stored(apple));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectingALaterStateGroupPreservesTheEarlierGroupsInFlightReturns(boolean restore) throws Exception {
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("accepted_then_rejected_group", carrot, true,
            new Slot[]{exact(apple), tool()}, grid -> at(grid, 1, damage(grid.getStackInSlot(1).getItemDamage() + 1)));
        var cpu = new CraftingCpuTestDriver(rule.pattern, 4, CraftingTestNetwork.world());
        var delegate = machine(rule);
        var provider = new ICraftingProvider() {
            boolean reject;

            public List<IPatternDetails> getAvailablePatterns() {
                return List.of(rule.pattern);
            }

            public boolean isBusy() {
                return false;
            }

            public boolean canMergePatternPush(IPatternDetails pattern) {
                return true;
            }

            public int getMaxPatternPushMultiplier(IPatternDetails pattern, int limit) {
                return limit;
            }

            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, int multiplier) {
                return !reject && delegate.pushPattern(pattern, inputs, multiplier);
            }
        };
        cpu.stock(apple, 4);
        cpu.stock(damage(0), 2);
        cpu.stock(damage(7), 2);
        assertEquals(2, cpu.run(provider, 64));
        provider.reject = true;
        assertEquals(0, cpu.run(provider, 64));
        if (restore) cpu.saveAndRestore();
        assertAll(
            () -> assertEquals(2, cpu.remaining()),
            () -> assertEquals(2, cpu.stored(apple)),
            () -> assertEquals(0, cpu.stored(damage(0))),
            () -> assertEquals(2, cpu.stored(damage(7))),
            () -> assertEquals(2, cpu.pending(damage(1))),
            () -> assertEquals(0, cpu.pending(damage(8))),
            () -> assertEquals(2, cpu.pending(carrot)));
        provider.reject = false;
        assertEquals(2, cpu.run(provider, 64));
        assertAll(
            () -> assertEquals(0, cpu.remaining()),
            () -> assertEquals(0, cpu.stored(apple)),
            () -> assertEquals(0, cpu.stored(damage(7))),
            () -> assertEquals(2, cpu.pending(damage(1))),
            () -> assertEquals(2, cpu.pending(damage(8))),
            () -> assertEquals(4, cpu.pending(carrot)));
    }
}
