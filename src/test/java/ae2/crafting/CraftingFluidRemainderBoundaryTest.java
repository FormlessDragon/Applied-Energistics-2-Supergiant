package ae2.crafting;

import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuHelper;
import ae2.crafting.inv.ListCraftingInventory;
import ae2.crafting.pattern.AECraftingPattern;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CraftingFluidRemainderBoundaryTest extends CraftingRecipeTestSupport {
    private static final AtomicInteger IDS = new AtomicInteger();

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Supply.class)
    void onlyPhysicalBucketsProduceEmptyBucketRemainders(Supply supply) throws Exception {
        var net = new CraftingTestNetwork();
        var recipe = new ShapedRecipes("", 2, 1, NonNullList.from(Ingredient.EMPTY,
            Ingredient.fromItems(Items.WATER_BUCKET), Ingredient.fromItems(Items.WATER_BUCKET)), new ItemStack(Items.CARROT));
        recipe.setRegistryName("ae2_remainder_test", "mixed_fluid_" + IDS.getAndIncrement());
        ForgeRegistries.RECIPES.register(recipe);
        var slots = new ItemStack[9];
        Arrays.fill(slots, ItemStack.EMPTY);
        slots[0] = new ItemStack(Items.WATER_BUCKET);
        slots[1] = new ItemStack(Items.WATER_BUCKET);
        var encoded = new ItemStack(Items.PAPER);
        AECraftingPattern.encode(encoded, recipe, slots, new ItemStack(Items.CARROT), true, true);
        var pattern = net.addPattern(new AECraftingPattern(AEItemKey.of(encoded), CraftingTestNetwork.world()), 0);
        var water = AEFluidKey.of(FluidRegistry.WATER);
        var filled = AEItemKey.of(Items.WATER_BUCKET);
        var empty = AEItemKey.of(Items.BUCKET);
        var carrot = AEItemKey.of(Items.CARROT);
        int buckets = supply == Supply.FLUID_ONLY ? 0 : supply == Supply.MIXED ? 1 : 2;
        int fluid = supply == Supply.FLUID_ONLY ? 2000 : supply == Supply.MIXED ? 1000
            : supply == Supply.PARTIAL_FLUID_WITH_BUCKETS ? 999 : 0;
        net.store(water, fluid);
        net.store(filled, buckets);
        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = net.plan(carrot, 1);
            complete(net, plan, carrot, 1);
            materials(plan.usedItems(), water, supply == Supply.PARTIAL_FLUID_WITH_BUCKETS ? 0 : fluid,
                filled, buckets);
            var inventory = new ListCraftingInventory(key -> {
            });
            inventory.list.addAll(plan.usedItems());
            var expected = new KeyCounter();
            var inputs = CraftingCpuHelper.extractPatternInputs(pattern, inventory, CraftingTestNetwork.world(),
                new KeyCounter(), expected, 1);
            assertNotNull(inputs);
            var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
            pattern.fillCraftingGrid(inputs, grid::setInventorySlotContents);
            assertEquals(new GenericStack(carrot, 1), GenericStack.fromItemStack(pattern.assemble(grid, CraftingTestNetwork.world())));
            var actual = new KeyCounter();
            for (var stack : pattern.getRemainingItems(grid)) {
                if (!stack.isEmpty()) actual.add(AEItemKey.of(stack), stack.getCount());
            }
            assertAll(
                () -> materials(actual, empty, buckets),
                () -> assertEquals(amounts(actual), amounts(expected), "CPU invents a bucket for a fluid-only slot"),
                () -> CraftingTreeAudit.roundTrip(CraftingTreeCyclesTest.tree(plan)));
        }
    }

    enum Supply {FLUID_ONLY, BUCKET_ONLY, MIXED, PARTIAL_FLUID_WITH_BUCKETS}
}
