package ae2.crafting;

import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import net.minecraft.init.Items;
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
import static ae2.crafting.CraftingRemainderFixtures.remaining;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static ae2.crafting.CraftingRemainderFixtures.variant;
import static ae2.crafting.CraftingTestNetwork.crafts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral categories use deliberately small requests, checked against complete recipe grids.
 */
class CraftingRemainderBoundaryTest extends CraftingRecipeTestSupport {
    private static AEItemKey apple() {
        return item(Items.APPLE);
    }

    private static AEItemKey carrot() {
        return item(Items.CARROT);
    }

    private static Rule wearing(Fixture f, int slots, int outputCount) {
        var inputs = new Slot[slots + 1];
        inputs[0] = exact(apple());
        for (int i = 1; i <= slots; i++) inputs[i] = tool();
        return f.rule("three_use_tools", carrot(), outputCount, true, inputs, grid -> {
            var out = remaining(grid);
            for (int i = 1; i <= slots; i++) {
                int next = grid.getStackInSlot(i).getItemDamage() + 1;
                if (next < 3) out.set(i, damage(next).toStack());
            }
            return out;
        });
    }

    private void supplied(Fixture f, AEItemKey output, int requested, Object... used) {
        assertTrue(f.reachable(f.net.stock, output, requested), "invalid fixture: no independent execution witness");
        for (int attempt = 0; attempt < 2; attempt++) {
            var before = amounts(f.net.stock);
            var plan = f.net.plan(output, requested);
            assertAll(
                () -> complete(f.net, plan, output, requested),
                () -> materials(plan.usedItems(), used),
                () -> assertEquals(before, amounts(f.net.stock), "planning changed network stock"),
                () -> f.assertExecutable(plan),
                () -> f.assertTree(plan));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void identicalSlotsWithOnlyOneReturnHaveARealNetCost(int cache) {
        memo(cache);
        var f = new Fixture();
        f.rule("one_slot_consumed", carrot(), false, new Slot[]{exact(apple()), tool(), tool()},
            grid -> at(grid, 1, damage(0)));
        f.stock(apple(), 3);
        f.stock(damage(0), 4); // Two before the first craft, then one replacement per further craft.
        supplied(f, carrot(), 3, apple(), 3, damage(0), 4);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void identicalSlotsMayReturnDifferentDamageStates(int cache) {
        memo(cache);
        var f = new Fixture();
        f.rule("different_slot_wear", carrot(), true, new Slot[]{exact(apple()), tool(), tool()}, grid -> {
            var out = at(grid, 1, AEItemKey.of(grid.getStackInSlot(1)));
            out.set(2, damage(grid.getStackInSlot(2).getItemDamage() + 1).toStack());
            return out;
        });
        f.stock(apple(), 3);
        f.stock(damage(0), 2);
        supplied(f, carrot(), 3, apple(), 3, damage(0), 2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void crossSlotReturnsCannotSupplyAnotherSlotBeforeTheCraft(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var bucket = item(Items.BOWL);
        var bottle = item(Items.GLASS_BOTTLE);
        f.rule("swapped_containers", carrot(), false,
            new Slot[]{exact(apple()), exact(bucket), exact(bottle)}, grid -> {
                var out = at(grid, 1, bottle);
                out.set(2, bucket.toStack());
                return out;
            });
        f.stock(apple(), 3);
        f.stock(bucket, 1);
        assertFalse(f.reachable(f.net.stock, carrot(), 1));
        var missing = f.net.plan(carrot(), 3);
        assertAll(
            () -> materials(missing.usedItems(), apple(), 3, bucket, 1),
            () -> materials(missing.missingItems(), bottle, 1),
            () -> f.assertExecutable(missing),
            () -> f.assertTree(missing));
        f.stock(bottle, 1);
        supplied(f, carrot(), 3, apple(), 3, bucket, 1, bottle, 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Byproduct.class)
    void remainderByproductsAreAvailableToDownstreamRecipes(Byproduct kind) {
        var f = new Fixture();
        var bucket = item(Items.BOWL);
        var stick = item(Items.STICK);
        var result = item(Items.GOLD_INGOT);
        if (kind == Byproduct.MULTIPLE_ITEMS) {
            f.rule("two_buckets", carrot(), false, new Slot[]{exact(apple()), exact(bucket)}, grid -> {
                var out = remaining(grid);
                out.set(1, bucket.toStack(2));
                return out;
            });
            f.rule("use_buckets", result, false,
                new Slot[]{exact(carrot()), exact(carrot()), exact(bucket), exact(bucket), exact(bucket)},
                CraftingRemainderFixtures::remaining);
            f.stock(apple(), 2);
            f.stock(bucket, 1);
            supplied(f, result, 1, apple(), 2, bucket, 1);
        } else {
            var slots = kind == Byproduct.EMPTY_SLOT ? new Slot[]{exact(apple())}
                : new Slot[]{exact(apple()), exact(damage(58))};
            f.rule("extra_remainder", carrot(), false, slots,
                grid -> at(grid, kind == Byproduct.EMPTY_SLOT ? 8 : 1, stick));
            f.rule("consume_remainder", result, false, new Slot[]{exact(carrot()), exact(stick)},
                CraftingRemainderFixtures::remaining);
            f.stock(apple(), 1);
            if (kind == Byproduct.BROKEN_TOOL) f.stock(damage(58), 1);
            supplied(f, result, 1, kind == Byproduct.EMPTY_SLOT
                ? new Object[]{apple(), 1} : new Object[]{apple(), 1, damage(58), 1});
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void toolRemainderDependsOnTheActuallyChosenOtherIngredient(int cache) {
        memo(cache);
        var f = new Fixture();
        var golden = item(Items.GOLDEN_APPLE);
        f.rule("ingredient_dependent_wear", carrot(), true,
            new Slot[]{variant(apple(), k -> k.equals(apple()) || k.equals(golden), Items.APPLE, Items.GOLDEN_APPLE), tool()},
            grid -> {
                int wear = grid.getStackInSlot(0).getItem() == Items.GOLDEN_APPLE ? 2 : 0;
                int next = grid.getStackInSlot(1).getItemDamage() + wear;
                return at(grid, 1, next < 4 ? damage(next) : null);
            });
        f.stock(golden, 2);
        f.stock(damage(0), 1);
        supplied(f, carrot(), 2, golden, 2, damage(0), 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void overlappingSlotAlternativesPreserveTheRestrictedSlot(int cache) {
        memo(cache);
        var f = new Fixture();
        f.rule("restricted_and_flexible", carrot(), true,
            new Slot[]{exact(apple()), exact(damage(0)), tool()}, grid -> {
                var out = at(grid, 1, AEItemKey.of(grid.getStackInSlot(1)));
                out.set(2, grid.getStackInSlot(2).copy());
                return out;
            });
        f.stock(apple(), 2);
        f.stock(damage(0), 1);
        f.stock(damage(1), 1);
        supplied(f, carrot(), 2, apple(), 2, damage(0), 1, damage(1), 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Transition.class)
    void returnedStatesNeedNotBeLinearDamageIncrements(Transition transition) {
        var f = new Fixture();
        AEItemKey initial = transition == Transition.REPAIR ? damage(2)
            : transition == Transition.NBT_DRAIN ? charge(3) : damage(0);
        var input = transition == Transition.NBT_DRAIN
            ? variant(initial, key -> key.getItem() == Items.PAPER && key.toStack().hasTagCompound()
            && key.toStack().getTagCompound().getInteger("charge") > 0, Items.PAPER)
            : transition == Transition.ITEM_TYPE_CHANGE
            ? variant(initial, key -> List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE)
                                          .contains(key.getItem()), Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE)
            : variant(initial, key -> key.getItem() == Items.WOODEN_PICKAXE, Items.WOODEN_PICKAXE);
        f.rule("state_" + transition.name().toLowerCase(), carrot(), true,
            new Slot[]{exact(apple()), input}, grid -> {
                var stack = grid.getStackInSlot(1);
                int d = stack.getItemDamage();
                AEItemKey next = switch (transition) {
                    case TWO_POINT_WEAR -> d + 2 < 6 ? damage(d + 2) : null;
                    case NONLINEAR_WEAR -> d < 3 ? damage(d * 2 + 1) : null;
                    case REPAIR -> damage(Math.max(0, d - 1));
                    case RESET -> damage((d + 1) % 3);
                    case TWO_STATE_CYCLE -> damage(1 - d);
                    case NBT_DRAIN -> stack.hasTagCompound() && stack.getTagCompound().getInteger("charge") > 1
                        ? charge(stack.getTagCompound().getInteger("charge") - 1) : null;
                    case ITEM_TYPE_CHANGE -> stack.getItem() == Items.WOODEN_PICKAXE ? item(Items.STONE_PICKAXE)
                        : stack.getItem() == Items.STONE_PICKAXE ? item(Items.IRON_PICKAXE) : null;
                };
                return at(grid, 1, next);
            });
        f.stock(apple(), 3);
        f.stock(initial, 1);
        if (transition == Transition.NBT_DRAIN) {
            f.stock(charge(0), 4);
            f.stock(item(Items.PAPER), 4);
        }
        supplied(f, carrot(), 3, apple(), 3, initial, 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void aRemainderCanBeRechargedByAnotherAssemblerPattern(int cache) {
        memo(cache);
        var f = new Fixture();
        var redstone = item(Items.REDSTONE);
        f.rule("drain_charge", carrot(), false, new Slot[]{exact(apple()), exact(charge(1))},
            grid -> at(grid, 1, charge(0)));
        f.rule("charge_again", charge(1), false, new Slot[]{exact(charge(0)), exact(redstone)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple(), 3);
        f.stock(charge(1), 1);
        f.stock(redstone, 2);
        supplied(f, carrot(), 3, apple(), 3, charge(1), 1, redstone, 2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {false, true})
    void separateBranchesShareAToolBeforeItsFinalConsumption(boolean consumedAtEnd) {
        var f = new Fixture();
        var potato = item(Items.POTATO);
        var bread = item(Items.BREAD);
        var result = item(Items.GOLD_INGOT);
        f.rule("left", carrot(), false, new Slot[]{exact(apple()), tool()}, grid -> at(grid, 1, damage(0)));
        f.rule("right", bread, false, new Slot[]{exact(potato), tool()}, grid -> at(grid, 1, damage(0)));
        f.rule("join", result, false, consumedAtEnd
                ? new Slot[]{exact(carrot()), exact(bread), tool()} : new Slot[]{exact(carrot()), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple(), 1);
        f.stock(potato, 1);
        f.stock(damage(0), 1);
        supplied(f, result, 1, apple(), 1, potato, 1, damage(0), 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void aConsumingSiblingMustWaitUntilTheOtherBranchReturnsItsTool(int cache) {
        memo(cache);
        var f = new Fixture();
        var potato = item(Items.POTATO);
        var bread = item(Items.BREAD);
        var result = item(Items.GOLD_INGOT);
        f.rule("consume_tool", carrot(), false, new Slot[]{exact(apple()), tool()},
            CraftingRemainderFixtures::remaining);
        f.rule("reuse_tool", bread, false, new Slot[]{exact(potato), tool()}, grid -> at(grid, 1, damage(0)));
        f.rule("join_consumers", result, false, new Slot[]{exact(carrot()), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple(), 1);
        f.stock(potato, 1);
        f.stock(damage(0), 1);
        supplied(f, result, 1, apple(), 1, potato, 1, damage(0), 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void identicalInputKeysInDifferentRecipesKeepTheirOwnRemainderRules(int cache) {
        memo(cache);
        var f = new Fixture();
        var potato = item(Items.POTATO);
        var bread = item(Items.BREAD);
        var result = item(Items.GOLD_INGOT);
        f.rule("left_state", carrot(), true, new Slot[]{exact(apple()), tool()}, grid -> at(grid, 1, damage(1)));
        f.rule("right_state", bread, true, new Slot[]{exact(potato), tool()}, grid -> at(grid, 1, damage(2)));
        f.rule("join_states", result, false, new Slot[]{exact(carrot()), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple(), 1);
        f.stock(potato, 1);
        f.stock(damage(0), 1);
        supplied(f, result, 1, apple(), 1, potato, 1, damage(0), 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aRealMissingConsumableDoesNotPreserveAFalseSharedToolShortage(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var potato = item(Items.POTATO);
        var bread = item(Items.BREAD);
        var result = item(Items.GOLD_INGOT);
        f.rule("consume_with_missing_food", carrot(), false, new Slot[]{exact(apple()), tool()},
            CraftingRemainderFixtures::remaining);
        f.rule("reuse_before_consumer", bread, false, new Slot[]{exact(potato), tool()},
            grid -> at(grid, 1, damage(0)));
        f.rule("join_with_missing_food", result, false, new Slot[]{exact(carrot()), exact(bread)},
            CraftingRemainderFixtures::remaining);
        f.stock(potato, 1);
        f.stock(damage(0), 1);
        var plan = f.net.plan(result, 1);
        materials(plan.missingItems(), apple(), 1);
        materials(plan.usedItems(), potato, 1, damage(0), 1);
        f.assertExecutable(plan);
        f.assertTree(plan);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void totalDurabilityCannotReplaceTheNumberOfSimultaneousTools(int cache) {
        memo(cache);
        var f = new Fixture();
        wearing(f, 2, 1);
        f.stock(apple(), 2);
        f.stock(damage(0), 1);
        f.stock(damage(2), 1); // Four total uses, but only one surviving tool after the first two-slot craft.
        assertFalse(f.reachable(f.net.stock, carrot(), 2));
        var enough = new KeyCounter();
        enough.addAll(f.net.stock);
        enough.add(damage(0), 1);
        assertTrue(f.reachable(enough, carrot(), 2));
        var plan = f.net.plan(carrot(), 2);
        assertAll(
            () -> materials(plan.missingItems(), damage(0), 1),
            () -> withinStock(f.net, plan),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void mixedDurabilityToolsCanBeDistributedAcrossSuccessiveFullGrids(int cache) {
        memo(cache);
        var f = new Fixture();
        wearing(f, 2, 1);
        f.stock(apple(), 3);
        f.stock(damage(1), 3); // Three tools with two uses each can cover three crafts with two occupied tool slots.
        supplied(f, carrot(), 3, apple(), 3, damage(1), 3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aConsumableBoundaryDoesNotTreatAPartiallyWornToolAsBroken(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        wearing(f, 1, 1);
        f.stock(apple(), 2);
        f.stock(damage(0), 1);
        var plan = f.net.plan(carrot(), 6);
        materials(plan.usedItems(), apple(), 2, damage(0), 1);
        materials(plan.missingItems(), apple(), 4, damage(0), 1);
        f.assertExecutable(plan);
        f.assertTree(plan);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Bottleneck.class)
    void craftLessUsesTheActualBottleneck(Bottleneck bottleneck) {
        var f = new Fixture();
        wearing(f, bottleneck == Bottleneck.SIMULTANEOUS_TOOLS ? 2 : 1, 1);
        f.stock(apple(), bottleneck == Bottleneck.CONSUMABLE ? 2 : 6);
        f.stock(damage(0), bottleneck == Bottleneck.NO_SEED ? 0 : 1);
        if (bottleneck == Bottleneck.SIMULTANEOUS_TOOLS) f.stock(damage(2), 1);
        int maximum = 0;
        for (int count = 1; count <= 6; count++) if (f.reachable(f.net.stock, carrot(), count)) maximum = count;
        var plan = f.net.plan(carrot(), 6, CalculationStrategy.CRAFT_LESS);
        if (maximum == 0) {
            assertEquals(6, plan.finalOutput().amount());
            assertFalse(amounts(plan.missingItems()).isEmpty());
        } else {
            complete(f.net, plan, carrot(), maximum);
        }
        f.assertExecutable(plan);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void outputRoundingUsesCraftsRatherThanOutputItemsAsDurabilityCost(int cache) {
        memo(cache);
        var f = new Fixture();
        var rule = wearing(f, 1, 2);
        f.stock(apple(), 3);
        f.stock(damage(0), 1);
        assertTrue(f.reachable(f.net.stock, carrot(), 5));
        var plan = f.net.plan(carrot(), 5);
        assertAll(
            () -> complete(f.net, plan, carrot(), 5),
            () -> assertEquals(3, crafts(plan, rule.pattern)),
            () -> materials(plan.usedItems(), apple(), 3, damage(0), 1),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void craftableToolsAreMadeOnlyWhenTheirActualLifetimeRequiresIt(int cache) {
        memo(cache);
        var f = new Fixture();
        wearing(f, 1, 1);
        var stick = item(Items.STICK);
        var maker = f.rule("make_tool", damage(0), false, new Slot[]{exact(stick), exact(stick)},
            CraftingRemainderFixtures::remaining);
        f.stock(apple(), 3);
        f.stock(stick, 2);
        assertTrue(f.reachable(f.net.stock, carrot(), 3));
        var plan = f.net.plan(carrot(), 3);
        assertAll(
            () -> complete(f.net, plan, carrot(), 3),
            () -> assertEquals(1, crafts(plan, maker.pattern)),
            () -> materials(plan.usedItems(), apple(), 3, stick, 2),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = {0, 10_000})
    void missingConsumablesDoNotMultiplyTheUnchangedToolSeed(int cache) {
        memo(cache);
        var f = new Fixture();
        f.rule("missing_consumable", carrot(), false, new Slot[]{exact(apple()), tool()},
            grid -> at(grid, 1, damage(0)));
        f.stock(damage(0), 1);
        var plan = f.net.plan(carrot(), 3);
        assertAll(
            () -> materials(plan.usedItems(), damage(0), 1),
            () -> materials(plan.missingItems(), apple(), 3),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    enum Byproduct {MULTIPLE_ITEMS, EMPTY_SLOT, BROKEN_TOOL}

    enum Transition {TWO_POINT_WEAR, NONLINEAR_WEAR, REPAIR, RESET, TWO_STATE_CYCLE, NBT_DRAIN, ITEM_TYPE_CHANGE}

    enum Bottleneck {DURABILITY, SIMULTANEOUS_TOOLS, CONSUMABLE, NO_SEED}
}
