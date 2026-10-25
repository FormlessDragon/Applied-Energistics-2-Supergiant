package ae2.crafting;

import net.minecraft.init.Items;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ae2.crafting.CraftingRemainderFixtures.Fixture;
import static ae2.crafting.CraftingRemainderFixtures.Slot;
import static ae2.crafting.CraftingRemainderFixtures.at;
import static ae2.crafting.CraftingRemainderFixtures.damage;
import static ae2.crafting.CraftingRemainderFixtures.exact;
import static ae2.crafting.CraftingRemainderFixtures.item;
import static ae2.crafting.CraftingRemainderFixtures.tool;
import static ae2.crafting.CraftingTestNetwork.crafts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic recipes whose actual returns differ from the encoded template or declared primary output.
 */
class CraftingRemainderRoutingTest extends CraftingRecipeTestSupport {
    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aSubstituteCanReturnAToolEvenWhenTheEncodedTemplateDoesNot(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("substitute_only_return", carrot, true, new Slot[]{exact(apple), tool()}, grid -> {
            int state = grid.getStackInSlot(1).getItemDamage();
            return at(grid, 1, state == 0 ? null : damage(state));
        });
        f.stock(apple, 3);
        f.stock(damage(7), 1);
        assertTrue(f.reachable(f.net.stock, carrot, 3));
        var plan = f.net.plan(carrot, 3);
        assertAll(
            () -> complete(f.net, plan, carrot, 3),
            () -> materials(plan.usedItems(), apple, 3, damage(7), 1),
            () -> assertEquals(3, crafts(plan, rule.pattern)),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aConsumedSubstituteCannotBorrowTheEncodedTemplatesReturn(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("consuming_substitute", carrot, true, new Slot[]{exact(apple), tool()}, grid ->
            at(grid, 1, grid.getStackInSlot(1).getItemDamage() == 0 ? damage(0) : null));
        f.stock(apple, 3);
        f.stock(damage(7), 3);
        assertTrue(f.reachable(f.net.stock, carrot, 3));
        var plan = f.net.plan(carrot, 3);
        assertAll(
            () -> complete(f.net, plan, carrot, 3),
            () -> materials(plan.usedItems(), apple, 3, damage(7), 3),
            () -> assertEquals(3, crafts(plan, rule.pattern)),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aRemainderWithThePrimaryOutputKeyAlsoFulfilsTheRequestedAmount(int cache) throws Exception {
        memo(cache);
        var f = new Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var rule = f.rule("same_key_output_planning", carrot, false, new Slot[]{exact(apple)},
            grid -> at(grid, 8, carrot));
        f.stock(apple, 2);
        assertTrue(f.reachable(f.net.stock, carrot, 4), "two complete grids each produce two carrots");
        var plan = f.net.plan(carrot, 4);
        assertAll(
            () -> complete(f.net, plan, carrot, 4),
            () -> materials(plan.usedItems(), apple, 2),
            () -> assertEquals(2, crafts(plan, rule.pattern)),
            () -> f.assertExecutable(plan),
            () -> f.assertTree(plan));
    }
}
