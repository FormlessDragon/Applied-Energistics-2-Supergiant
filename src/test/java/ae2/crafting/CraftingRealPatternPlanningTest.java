package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuHelper;
import ae2.crafting.inv.ListCraftingInventory;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.crafting.pattern.AEProcessingPattern;
import com.sun.management.ThreadMXBean;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ae2.crafting.CraftingTestNetwork.crafts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRealPatternPlanningTest extends CraftingRecipeTestSupport {
    private static final AtomicInteger RECIPE_IDS = new AtomicInteger();

    private static AEProcessingPattern processing(List<GenericStack> inputs, List<GenericStack> outputs) {
        var encoded = new ItemStack(Items.PAPER);
        AEProcessingPattern.encode(encoded, inputs, outputs);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    private static AECraftingPattern crafting(boolean substitutes) {
        var ingredient = substitutes ? Ingredient.fromItems(Items.APPLE, Items.GOLDEN_APPLE)
            : Ingredient.fromItems(Items.APPLE);
        var recipe = new ShapelessRecipes("", new ItemStack(Items.CARROT),
            NonNullList.from(Ingredient.EMPTY, ingredient, ingredient));
        recipe.setRegistryName("ae2_planning_test", "pair_" + RECIPE_IDS.getAndIncrement());
        ForgeRegistries.RECIPES.register(recipe);
        var slots = new ItemStack[9];
        Arrays.fill(slots, ItemStack.EMPTY);
        slots[0] = new ItemStack(Items.APPLE);
        slots[1] = new ItemStack(Items.APPLE);
        var encoded = new ItemStack(Items.PAPER);
        AECraftingPattern.encode(encoded, recipe, slots, new ItemStack(Items.CARROT), substitutes, false);
        return new AECraftingPattern(AEItemKey.of(encoded), CraftingTestNetwork.world());
    }

    private static AEItemKey tool(int damage) {
        return AEItemKey.of(new ItemStack(Items.WOODEN_PICKAXE, 1, damage));
    }

    /**
     * Real encoded assembler recipe: every occupied tool slot needs its own item before any slot returns it.
     */
    private static AECraftingPattern returningTools(int slots, boolean wears, boolean substitutes) {
        return returningTools(slots, wears, substitutes, new AtomicInteger());
    }

    private static AECraftingPattern returningTools(int slots, boolean wears, boolean substitutes,
                                                    AtomicInteger callbacks) {
        var ingredients = NonNullList.<Ingredient>create();
        ingredients.add(Ingredient.fromItems(Items.APPLE));
        for (int i = 0; i < slots; i++)
            ingredients.add(Ingredient.fromStacks(
                new ItemStack(Items.WOODEN_PICKAXE, 1, OreDictionary.WILDCARD_VALUE)));
        var recipe = new ShapelessRecipes("", new ItemStack(Items.CARROT), ingredients) {
            @Override
            public boolean matches(InventoryCrafting input, World world) {
                callbacks.incrementAndGet();
                return super.matches(input, world);
            }

            @Override
            public NonNullList<ItemStack> getRemainingItems(InventoryCrafting input) {
                callbacks.incrementAndGet();
                var result = NonNullList.withSize(input.getSizeInventory(), ItemStack.EMPTY);
                for (int slot = 0; slot < input.getSizeInventory(); slot++) {
                    var stack = input.getStackInSlot(slot);
                    if (stack.getItem() != Items.WOODEN_PICKAXE) continue;
                    // This recipe uses exactly maxDamage times, then destroys the tool.
                    int damage = stack.getItemDamage() + (wears ? 1 : 0);
                    if (damage < stack.getMaxDamage()) {
                        var remaining = stack.copy();
                        remaining.setCount(1);
                        remaining.setItemDamage(damage);
                        result.set(slot, remaining);
                    }
                }
                return result;
            }
        };
        recipe.setRegistryName("ae2_planning_test", "returning_tools_" + RECIPE_IDS.getAndIncrement());
        ForgeRegistries.RECIPES.register(recipe);
        var grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = new ItemStack(Items.APPLE);
        for (int i = 1; i <= slots; i++) grid[i] = tool(0).toStack();
        var encoded = new ItemStack(Items.PAPER);
        AECraftingPattern.encode(encoded, recipe, grid, new ItemStack(Items.CARROT), substitutes, false);
        var pattern = new AECraftingPattern(AEItemKey.of(encoded), CraftingTestNetwork.world());
        assertEquals(slots, Arrays.stream(pattern.getInputs())
                                  .filter(input -> input.possibleInputs()[0].what().equals(tool(0)))
                                  .mapToLong(input -> input.getMultiplier() * input.possibleInputs()[0].amount()).sum(),
            "fixture lost simultaneous tool slots");
        return pattern;
    }

    static Stream<Arguments> reusableToolCases() {
        var cases = new ArrayList<Arguments>();
        for (int cache : new int[]{0, 10_000}) {
            for (boolean substitutes : new boolean[]{false, true}) {
                for (int slots : new int[]{1, 2, 8}) {
                    for (int stored : IntStream.of(0, 1, slots).distinct().toArray()) {
                        cases.add(Arguments.of(cache, substitutes, slots, stored));
                    }
                }
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> wearingToolCases() {
        var cases = new ArrayList<Arguments>();
        // slots, stored, initial damage, crafts, additional NEW tools. Wooden pickaxes have 59 uses here.
        int[][] rows = {
            {1, 1, 0, 1, 0}, {1, 1, 0, 2, 0}, {1, 1, 0, 3, 0}, {1, 1, 0, 9, 0},
            {1, 1, 0, 59, 0}, {1, 1, 0, 60, 1}, {1, 1, 0, 121, 2},
            {1, 1, 57, 2, 0}, {1, 1, 57, 3, 1}, {1, 1, 57, 61, 1},
            {1, 2, 58, 2, 0}, {1, 2, 58, 3, 1},
            {2, 2, 57, 2, 0}, {2, 2, 57, 3, 2}, {2, 2, 57, 5, 2},
            {2, 1, 0, 1, 1}, {2, 1, 0, 3, 1}, {8, 8, 0, 3, 0}
        };
        for (int cache : new int[]{0, 10_000}) {
            for (var row : rows) cases.add(Arguments.of(cache, row[0], row[1], row[2], row[3], row[4]));
        }
        return cases.stream();
    }

    static long craftingAllocatedBytes() {
        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean threads)
            || !threads.isThreadAllocatedMemorySupported()) return 0;
        if (!threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
        long bytes = 0, driver = Thread.currentThread().threadId();
        for (var thread : threads.getThreadInfo(threads.getAllThreadIds())) {
            if (thread != null && (thread.getThreadId() == driver || thread.getThreadName().equals("AE Crafting Calculator"))) {
                bytes += Math.max(0, threads.getThreadAllocatedBytes(thread.getThreadId()));
            }
        }
        return bytes;
    }

    /**
     * Validate real CPU extraction and assembler callbacks; the old test replay only considers encoded variants.
     */
    private static void verifyReturnedToolExecutionAndTree(ICraftingPlan plan, AECraftingPattern pattern)
        throws Exception {
        var inventory = new ListCraftingInventory(key -> {
        });
        inventory.list.addAll(plan.usedItems());
        inventory.list.addAll(plan.missingItems());
        var consumed = new KeyCounter();
        for (long craft = 0; craft < crafts(plan, pattern); craft++) {
            var expectedOutputs = new KeyCounter();
            var expectedContainers = new KeyCounter();
            var inputs = CraftingCpuHelper.extractPatternInputs(pattern, inventory, CraftingTestNetwork.world(),
                expectedOutputs, expectedContainers, 1);
            assertNotNull(inputs, "actual CPU cannot extract the complete grid for craft " + craft);
            for (var input : inputs) consumed.addAll(input);
            var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
            pattern.fillCraftingGrid(inputs, grid::setInventorySlotContents);
            assertEquals(pattern.getPrimaryOutput(), GenericStack.fromItemStack(pattern.assemble(grid,
                CraftingTestNetwork.world())));
            var actualContainers = new KeyCounter();
            for (var stack : pattern.getRemainingItems(grid)) {
                var remaining = GenericStack.fromItemStack(stack);
                if (remaining != null) actualContainers.add(remaining.what(), remaining.amount());
            }
            assertEquals(amounts(expectedContainers), amounts(actualContainers), "CPU expected wrong durability state");
            actualContainers.forEach((key, count) -> inventory.insert(key, count, Actionable.MODULATE));
            expectedOutputs.forEach((key, count) -> inventory.insert(key, count, Actionable.MODULATE));
        }
        assertEquals(plan.finalOutput().amount(), inventory.list.get(plan.finalOutput().what()));
        var tree = CraftingTreeCyclesTest.tree(plan);
        var displayed = new KeyCounter();
        CraftingTreeAudit.walk(tree, n -> {
        }, proc -> {
            if (proc.pattern() == pattern) for (var input : proc.inputs()) {
                displayed.add(input.output().what(), input.output().amount());
            }
        });
        // Damaged stock and newly supplied tools may be selected in either legal order. Check the entire displayed
        // durability flow, rather than requiring this one replay's arbitrary ordering of fuzzy candidates.
        assertEquals(consumed.get(AEItemKey.of(Items.APPLE)), displayed.get(AEItemKey.of(Items.APPLE)));
        long toolUses = 0;
        boolean wears = !tool(0).equals(Arrays.stream(pattern.getInputs())
                                              .filter(i -> i.possibleInputs()[0].what().equals(tool(0))).findFirst().orElseThrow().getRemainingKey(tool(0)));
        for (int damage = 0; damage < Items.WOODEN_PICKAXE.getMaxDamage(); damage++) {
            long uses = displayed.get(tool(damage));
            long initial = plan.usedItems().get(tool(damage)) + plan.missingItems().get(tool(damage));
            long returned = wears && damage > 0 ? displayed.get(tool(damage - 1)) : 0;
            if (wears) assertTrue(uses <= initial + returned, "tree consumes unavailable tool damage " + damage);
            else if (uses > 0) assertTrue(initial > 0, "tree invented an unchanged tool seed");
            toolUses += uses;
        }
        long expectedUses = 0;
        for (var entry : consumed)
            if (entry.getKey() instanceof AEItemKey item && item.getItem() == Items.WOODEN_PICKAXE) {
                expectedUses += entry.getLongValue();
            }
        assertEquals(expectedUses, toolUses, "tree must retain every tool slot of every craft");
        assertEquals(amounts(plan.missingItems()), amounts(CraftingTreeAudit.missing(tree)));
        CraftingTreeAudit.roundTrip(tree);
    }

    @ParameterizedTest(name = "memo={0}, substitutes={1}, slots={2}, stock={3}")
    @MethodSource("reusableToolCases")
    void returnedToolsCannotFillOtherSlotsOfTheSameCraft(int cache, boolean substitutes, int slots, int stored)
        throws Exception {
        memo(cache);
        var net = new CraftingTestNetwork();
        var pattern = net.addPattern(returningTools(slots, false, substitutes), 0);
        var apple = AEItemKey.of(Items.APPLE);
        var carrot = AEItemKey.of(Items.CARROT);
        net.store(apple, 19);
        net.store(tool(0), stored);
        // Same cached pattern, changing requested quantities, and cold/warm calculations.
        for (int requested : new int[]{1, 7, 19, 7}) {
            var plan = net.plan(carrot, requested);
            materials(plan.usedItems(), apple, requested, tool(0), Math.min(slots, stored));
            materials(plan.missingItems(), tool(0), Math.max(0, slots - stored));
            if (stored >= slots) assertFalse(plan.simulation());
            assertEquals(requested, crafts(plan, pattern));
            verifyReturnedToolExecutionAndTree(plan, pattern);
        }
    }

    @ParameterizedTest(name = "memo={0}, slots={1}, stock={2}, damage={3}, crafts={4}, missing={5}")
    @MethodSource("wearingToolCases")
    void toolsAreReusedAcrossDamageStatesUntilTheyActuallyBreak(int cache, int slots, int stored, int damage,
                                                                int requested, int missing) throws Exception {
        memo(cache);
        assertEquals(59, Items.WOODEN_PICKAXE.getMaxDamage(), "fixture lifetime changed");
        var net = new CraftingTestNetwork();
        var pattern = net.addPattern(returningTools(slots, true, true), 0);
        var apple = AEItemKey.of(Items.APPLE);
        var carrot = AEItemKey.of(Items.CARROT);
        net.store(apple, requested);
        net.store(tool(damage), stored);
        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = net.plan(carrot, requested);
            materials(plan.usedItems(), apple, requested, tool(damage), stored);
            materials(plan.missingItems(), tool(0), missing);
            if (missing == 0) assertFalse(plan.simulation());
            assertEquals(requested, crafts(plan, pattern));
            verifyReturnedToolExecutionAndTree(plan, pattern);
        }
    }

    @ParameterizedTest(name = "memo={0}, crafts={1}")
    @CsvSource({"0, 2", "0, 3", "10000, 2", "10000, 3"})
    void disabledSubstitutionCannotReuseADifferentDamageState(int cache, int requested) throws Exception {
        memo(cache);
        var net = new CraftingTestNetwork();
        var pattern = net.addPattern(returningTools(2, true, false), 0);
        var apple = AEItemKey.of(Items.APPLE);
        var carrot = AEItemKey.of(Items.CARROT);
        net.store(apple, requested);
        net.store(tool(0), 2);
        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = net.plan(carrot, requested);
            materials(plan.usedItems(), apple, requested, tool(0), 2);
            materials(plan.missingItems(), tool(0), 2L * (requested - 1));
            assertEquals(requested, crafts(plan, pattern));
            verifyReturnedToolExecutionAndTree(plan, pattern);
        }
    }

    @ParameterizedTest(name = "memo={0}, substitutes={1}")
    @CsvSource({"0, false", "0, true", "10000, false", "10000, true"})
    void unchangedRemaindersKeepPlanningBatched(int cache, boolean substitutes) {
        memo(cache);
        var callbacks = new AtomicInteger();
        var net = new CraftingTestNetwork();
        var pattern = net.addPattern(returningTools(1, false, substitutes, callbacks), 0);
        var carrot = AEItemKey.of(Items.CARROT);
        net.store(AEItemKey.of(Items.APPLE), 4096);
        net.store(tool(0), 1);
        net.plan(carrot, 32); // Exclude recipe initialization and calculator thread startup.
        long allocated = 0;
        for (int requested : new int[]{128, 4096}) {
            callbacks.set(0);
            long before = craftingAllocatedBytes();
            long start = System.nanoTime();
            var plan = net.plan(carrot, requested);
            allocated = craftingAllocatedBytes() - before;
            System.out.printf("unchanged-tool memo=%d substitutes=%s requested=%d callbacks=%d allocated=%.3fMiB elapsed=%.3fms%n",
                cache, substitutes, requested, callbacks.get(), allocated / 1048576.0,
                (System.nanoTime() - start) / 1_000_000.0);
            complete(net, plan, carrot, requested);
            assertEquals(requested, crafts(plan, pattern));
            materials(plan.usedItems(), AEItemKey.of(Items.APPLE), requested, tool(0), 1);
        }
        assertTrue(allocated < 16L * 1024 * 1024,
            "one unchanged tool and one recipe must not allocate per craft: " + allocated + " bytes");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void durabilityValidationDoesNotRepeatedlyReplayTheWholePrefix(int cache) {
        memo(cache);
        var callbacks = new AtomicInteger();
        var net = new CraftingTestNetwork();
        net.addPattern(returningTools(1, true, true, callbacks), 0);
        var carrot = AEItemKey.of(Items.CARROT);
        net.store(AEItemKey.of(Items.APPLE), 256);
        net.store(tool(0), 5); // 295 uses: enough for both requests, with only 59 distinct durability states.
        ICraftingPlan last = null;
        for (int requested : new int[]{32, 256}) {
            callbacks.set(0);
            long start = System.nanoTime();
            last = net.plan(carrot, requested);
            System.out.printf("wearing-tool memo=%d requested=%d callbacks=%d elapsed=%.3fms missing=%s%n",
                cache, requested, callbacks.get(), (System.nanoTime() - start) / 1_000_000.0,
                amounts(last.missingItems()));
        }
        var plan = last;
        assertAll(
            () -> complete(net, plan, carrot, 256),
            () -> assertTrue(callbacks.get() < 5000, "repeated prefix validation: " + callbacks.get() + " callbacks"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void processingUsesCondensedSparseInputsIncludingFluidsAndMaximumSlots(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(FluidRegistry.WATER);
        var plate = AEItemKey.of(Items.GOLD_INGOT);
        var dust = AEItemKey.of(Items.REDSTONE);
        var inputs = new ArrayList<GenericStack>(Collections.nCopies(AEProcessingPattern.MAX_INPUT_SLOTS, null));
        inputs.set(0, new GenericStack(iron, 2));
        inputs.set(40, new GenericStack(iron, 3));
        inputs.set(80, new GenericStack(water, 1_000));
        var outputs = new ArrayList<GenericStack>(Collections.nCopies(AEProcessingPattern.MAX_OUTPUT_SLOTS, null));
        outputs.set(0, new GenericStack(plate, 2));
        outputs.set(25, new GenericStack(dust, 1));
        outputs.set(26, new GenericStack(dust, 1));
        var pattern = net.addPattern(processing(inputs, outputs), 0);
        assertEquals(2, pattern.getInputs().length);
        assertEquals(2, pattern.getOutputs().size());
        net.store(iron, 15);
        net.store(water, 3_000);
        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = net.plan(plate, 5);
            complete(net, plan, plate, 5);
            assertEquals(3, crafts(plan, pattern));
            materials(plan.usedItems(), iron, 15, water, 3_000);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void secondaryProcessingOutputCanIncreaseTheSharedProducerCount(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var ore = AEItemKey.of(Items.IRON_INGOT);
        var a = AEItemKey.of(Items.GOLD_INGOT);
        var b = AEItemKey.of(Items.REDSTONE);
        var c = AEItemKey.of(Items.REPEATER);
        var result = AEItemKey.of(Items.COMPARATOR);
        var split = net.addPattern(processing(List.of(new GenericStack(ore, 6)),
            List.of(new GenericStack(a, 2), new GenericStack(b, 3))), 0);
        var component = net.addPattern(processing(List.of(new GenericStack(b, 2)), List.of(new GenericStack(c, 1))), 0);
        var root = net.addPattern(processing(List.of(new GenericStack(a, 1), new GenericStack(c, 1)),
            List.of(new GenericStack(result, 1))), 0);
        net.store(ore, 24);
        for (int attempt = 0; attempt < 2; attempt++) {
            var plan = net.plan(result, 6);
            complete(net, plan, result, 6);
            assertEquals(4, crafts(plan, split));
            assertEquals(6, crafts(plan, component));
            assertEquals(6, crafts(plan, root));
            materials(plan.usedItems(), ore, 24);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void processingKeepsNbtDistinctWhenOnlyTheOtherVariantIsAvailable(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var requiredStack = new ItemStack(Items.IRON_INGOT);
        var tag = new NBTTagCompound();
        tag.setString("grade", "pure");
        requiredStack.setTagCompound(tag);
        var required = AEItemKey.of(requiredStack);
        var other = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.GOLD_INGOT);
        var pattern = net.addPattern(processing(List.of(new GenericStack(required, 1)),
            List.of(new GenericStack(output, 1))), 0);
        net.store(required, 2);
        net.store(other, 1_000);
        var shortPlan = net.plan(output, 3);
        assertEquals(3, crafts(shortPlan, pattern));
        materials(shortPlan.usedItems(), required, 2);
        materials(shortPlan.missingItems(), required, 1);
        net.store(required, 1);
        var replenished = net.plan(output, 3);
        complete(net, replenished, output, 3);
        materials(replenished.usedItems(), required, 3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void craftingPatternPlansRepeatedSlotsWithAndWithoutSubstitution(int cache) {
        memo(cache);
        for (boolean substitutes : new boolean[]{false, true}) {
            var net = new CraftingTestNetwork();
            var pattern = net.addPattern(crafting(substitutes), 0);
            var apple = AEItemKey.of(Items.APPLE);
            var golden = AEItemKey.of(Items.GOLDEN_APPLE);
            var carrot = AEItemKey.of(Items.CARROT);
            assertEquals(2, Arrays.stream(pattern.getInputs()).mapToLong(input -> input.getMultiplier()).sum());
            if (substitutes) assertTrue(pattern.getInputs()[0].isValid(golden, CraftingTestNetwork.world()));
            net.store(apple, substitutes ? 3 : 8);
            net.store(golden, 5);
            for (int attempt = 0; attempt < 2; attempt++) {
                var plan = net.plan(carrot, 4);
                complete(net, plan, carrot, 4);
                assertEquals(4, crafts(plan, pattern));
                materials(plan.usedItems(), apple, substitutes ? 3 : 8, golden, substitutes ? 5 : 0);
            }
        }
    }

    @ParameterizedTest(name = "memo={0}, warm={1}")
    @CsvSource({"0, false", "0, true", "10000, false", "10000, true"})
    void fixedInputRecipesStillRequireValidationAfterEncodingAndOnWarmPlans(int cache, boolean warm) {
        memo(cache);
        var enabled = new AtomicBoolean(true);
        var tickThread = Thread.currentThread();
        var recipe = new ShapelessRecipes("", new ItemStack(Items.CARROT),
            NonNullList.from(Ingredient.EMPTY, Ingredient.fromItems(Items.APPLE))) {
            @Override
            public boolean matches(InventoryCrafting input, World world) {
                assertEquals(tickThread, Thread.currentThread(), "fixed recipe callbacks must use the tick thread");
                return enabled.get() && super.matches(input, world);
            }
        };
        recipe.setRegistryName("ae2_planning_test", "fixed_" + RECIPE_IDS.getAndIncrement());
        ForgeRegistries.RECIPES.register(recipe);
        var slots = new ItemStack[9];
        Arrays.fill(slots, ItemStack.EMPTY);
        slots[0] = new ItemStack(Items.APPLE);
        var encoded = new ItemStack(Items.PAPER);
        AECraftingPattern.encode(encoded, recipe, slots, new ItemStack(Items.CARROT), false, false);
        var net = new CraftingTestNetwork();
        var pattern = net.addPattern(new AECraftingPattern(AEItemKey.of(encoded), CraftingTestNetwork.world()), 0);
        var carrot = AEItemKey.of(Items.CARROT);
        var apple = AEItemKey.of(Items.APPLE);
        net.store(apple, 2);
        if (warm) complete(net, net.plan(carrot, 2), carrot, 2);
        enabled.set(false);
        var before = amounts(net.stock);
        assertThrows(CraftingCalculationFailure.class, () -> net.plan(carrot, 2),
            "balanced fixed inputs must not hide a recipe that no longer accepts them");
        assertEquals(before, amounts(net.stock), "rejected planning mutated storage");
        enabled.set(true);
        var recovered = net.plan(carrot, 2);
        complete(net, recovered, carrot, 2);
        assertEquals(2, crafts(recovered, pattern));
        materials(recovered.usedItems(), apple, 2);
    }

    @ParameterizedTest(name = "memo={0}, warm={1}")
    @CsvSource({"0, false", "0, true", "10000, false", "10000, true"})
    void individuallyValidSubstitutionsCannotMakeAnInvalidCombinedRecipe(int cache, boolean warm) {
        memo(cache);
        var ingredients = NonNullList.from(Ingredient.EMPTY,
            Ingredient.fromItems(Items.APPLE, Items.GOLDEN_APPLE),
            Ingredient.fromItems(Items.POTATO, Items.BAKED_POTATO));
        var recipe = new ShapedRecipes("", 2, 1, ingredients, new ItemStack(Items.CARROT)) {
            @Override
            public boolean matches(InventoryCrafting input, World world) {
                if (!super.matches(input, world)) return false;
                boolean golden = false;
                boolean baked = false;
                for (int slot = 0; slot < input.getSizeInventory(); slot++) {
                    golden |= input.getStackInSlot(slot).getItem() == Items.GOLDEN_APPLE;
                    baked |= input.getStackInSlot(slot).getItem() == Items.BAKED_POTATO;
                }
                return !golden || !baked;
            }
        };
        recipe.setRegistryName("ae2_planning_test", "coupled_" + RECIPE_IDS.getAndIncrement());
        ForgeRegistries.RECIPES.register(recipe);
        var slots = new ItemStack[9];
        Arrays.fill(slots, ItemStack.EMPTY);
        slots[0] = new ItemStack(Items.APPLE);
        slots[1] = new ItemStack(Items.POTATO);
        var encoded = new ItemStack(Items.PAPER);
        AECraftingPattern.encode(encoded, recipe, slots, new ItemStack(Items.CARROT), true, false);
        var net = new CraftingTestNetwork();
        var pattern = net.addPattern(new AECraftingPattern(AEItemKey.of(encoded), CraftingTestNetwork.world()), 0);
        var golden = AEItemKey.of(Items.GOLDEN_APPLE);
        var potato = AEItemKey.of(Items.POTATO);
        var baked = AEItemKey.of(Items.BAKED_POTATO);
        var carrot = AEItemKey.of(Items.CARROT);
        // Establish the fixture's distinction: each substitution works against the encoded template, but their pair
        // is impossible. These calls validate the fixture; assertions below inspect planning only.
        assertTrue(Arrays.stream(pattern.getInputs()).anyMatch(input -> input.isValid(golden, CraftingTestNetwork.world())));
        assertTrue(Arrays.stream(pattern.getInputs()).anyMatch(input -> input.isValid(baked, CraftingTestNetwork.world())));
        var invalid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
        invalid.setInventorySlotContents(0, golden.toStack());
        invalid.setInventorySlotContents(1, baked.toStack());
        assertFalse(recipe.matches(invalid, CraftingTestNetwork.world()));

        net.store(golden, 1);
        if (warm) {
            net.store(potato, 1);
            var validPlan = net.plan(carrot, 1);
            complete(net, validPlan, carrot, 1);
            materials(validPlan.usedItems(), golden, 1, potato, 1);
            net.stock.set(potato, 0);
        }
        net.store(baked, 1);
        var before = amounts(net.stock);
        try {
            var plan = net.plan(carrot, 1);
            assertEquals(new GenericStack(carrot, 1), plan.finalOutput());
            assertTrue(plan.simulation() || !amounts(plan.missingItems()).isEmpty(),
                "individually valid but incompatible inputs must not produce a submit-capable, zero-missing plan");
            withinStock(net, plan);
        } catch (CraftingCalculationFailure expected) {
            // An explicit planning rejection is also a valid outcome for an impossible recipe combination.
        }
        assertEquals(before, amounts(net.stock), "planning mutated storage");
    }
}
