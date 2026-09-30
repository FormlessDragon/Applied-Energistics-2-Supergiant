package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.integration.data.LiteCraftTreeNode;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Coverage for the batch order of the crafting terminal: one plan that carries a sub-plan per missing material, plus a
 * placeholder task that fakes the output of the transferred recipe.
 */
class BatchCraftingPlanTest {
    private static AEItemKey mainOutput;
    private static AEItemKey coal;
    private static AEItemKey rawMaterial;
    private static AEItemKey stick;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        // Registered items: unregistered ones share their primary key, which makes KeyCounter entries collide.
        mainOutput = AEItemKey.of(new ItemStack(Items.DIAMOND_PICKAXE));
        coal = AEItemKey.of(new ItemStack(Items.SLIME_BALL));
        rawMaterial = AEItemKey.of(new ItemStack(Items.ENDER_PEARL));
        stick = AEItemKey.of(new ItemStack(Items.SNOWBALL));
    }

    @Test
    void combineUnionsSubPlansAndAddsThePlaceholderTask() {
        var coalPattern = new TestPattern();
        var subPlan = new TestPlan(new GenericStack(coal, 1), 100, counter(coal, 1), counter(rawMaterial, 1));
        subPlan.patternTimes.mergeLong(coalPattern, 1, Long::sum);
        var placeholderPattern = new TestPattern();
        var placeholder = new TestProvider(placeholderPattern);

        var plan = BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(subPlan),
            List.of(placeholder));

        assertEquals(new GenericStack(mainOutput, 4), plan.finalOutput());
        assertFalse(plan.simulation());
        assertEquals(108, plan.bytes(), "the sub-plan bytes plus one more task for the faked output");
        assertEquals(1, plan.patternTimes().getLong(coalPattern), "the material is crafted by the batch");
        assertEquals(1, plan.patternTimes().getLong(placeholderPattern),
            "the faked output must be pushed once, otherwise the job never completes");
        assertEquals(1, plan.usedItems().get(coal), "the material the sub-plan crafts is the job's own demand");
        assertEquals(1, plan.emittedItems().get(rawMaterial));
        assertEquals(List.of(placeholder), plan.temporaryProviders());
    }

    @Test
    void combineMergesRepeatedPatternsAndMaterialsOfSeveralSubPlans() {
        var sharedPattern = new TestPattern();
        var firstPlan = new TestPlan(new GenericStack(coal, 1), 10, counter(coal, 1), counter(rawMaterial, 1));
        var secondPlan = new TestPlan(new GenericStack(stick, 4), 20, counter(stick, 4), counter(rawMaterial, 2));
        firstPlan.patternTimes.mergeLong(sharedPattern, 1, Long::sum);
        secondPlan.patternTimes.mergeLong(sharedPattern, 1, Long::sum);

        var plan = BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(firstPlan, secondPlan),
            List.of());

        assertEquals(2, plan.patternTimes().getLong(sharedPattern),
            "descendant patterns shared by sub-plans must describe one merged craft in the batch");
        assertEquals(3, plan.emittedItems().get(rawMaterial), "the raw material demand of all sub-plans is summed");
        assertEquals(30 + 8, plan.bytes());
    }

    @Test
    void combineKeepsMissingMaterialsSoTheOrderCanReportThem() {
        var plan = new TestPlan(new GenericStack(coal, 1), 5, counter(coal, 1), new KeyCounter());
        plan.missing.add(rawMaterial, 2);

        var batch = BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(plan), List.of());

        assertEquals(2, batch.missingItems().get(rawMaterial),
            "a material no sub-plan can supply has to stay visible in the confirmation");
    }

    @Test
    void combineRejectsSimulationPlansAndEmptyBatches() {
        var simulation = new TestPlan(new GenericStack(coal, 1), 5, new KeyCounter(), new KeyCounter());
        simulation.simulation = true;

        assertThrows(IllegalArgumentException.class,
            () -> BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(simulation), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(), List.of()));
    }


    @Test
    void batchTargetsAreTheMaterialsTheOrderCrafts() {
        var first = new TestPlan(new GenericStack(coal, 1), 1, new KeyCounter(), new KeyCounter());
        var second = new TestPlan(new GenericStack(stick, 4), 1, new KeyCounter(), new KeyCounter());

        var plan = BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(first, second), List.of());

        assertEquals(List.of(new GenericStack(coal, 1), new GenericStack(stick, 4)), plan.batchTargets(),
            "the terminal pins these instead of the placeholder output the job reports");
    }

    @Test
    void displayTreeShowsTheOrderedMaterialsBelowTheFakedOutput() {
        var materialTree = new LiteCraftTreeNode(null, new GenericStack(coal, 1), List.of(), 0);
        var subPlan = new TestPlan(new GenericStack(coal, 1), 1, new KeyCounter(), new KeyCounter());
        subPlan.displayTree = CompletableFuture.completedFuture(materialTree);

        var plan = BatchCraftingPlan.combine(new GenericStack(mainOutput, 4), List.of(subPlan), List.of());

        var root = plan.requestDisplayTree().join();
        assertEquals(new GenericStack(mainOutput, 4), root.output());
        assertEquals(1, root.inputs().size(), "the ordered material is a sub-tree of the batch");
        assertEquals(materialTree, root.inputs().getFirst().inputs().getFirst());
    }

    private static KeyCounter counter(AEKey what, long amount) {
        var counter = new KeyCounter();
        counter.add(what, amount);
        return counter;
    }

    private static final class TestPlan implements ICraftingPlan, CraftingPlanDisplaySource {
        private final GenericStack finalOutput;
        private final long bytes;
        private final KeyCounter usedItems;
        private final KeyCounter emittedItems;
        private final KeyCounter missing = new KeyCounter();
        private final Object2LongMap<IPatternDetails> patternTimes = new Object2LongLinkedOpenHashMap<>();
        private boolean simulation;
        private CompletableFuture<LiteCraftTreeNode> displayTree;

        private TestPlan(GenericStack finalOutput, long bytes, KeyCounter usedItems, KeyCounter emittedItems) {
            this.finalOutput = finalOutput;
            this.bytes = bytes;
            this.usedItems = usedItems;
            this.emittedItems = emittedItems;
        }

        @Override
        public GenericStack finalOutput() {
            return finalOutput;
        }

        @Override
        public long bytes() {
            return bytes;
        }

        @Override
        public boolean simulation() {
            return simulation;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return usedItems;
        }

        @Override
        public KeyCounter emittedItems() {
            return emittedItems;
        }

        @Override
        public KeyCounter missingItems() {
            return missing;
        }

        @Override
        public long intermediateFinalOutputAmount() {
            return 0;
        }

        @Override
        public Object2LongMap<IPatternDetails> patternTimes() {
            return patternTimes;
        }

        @Override
        public CompletableFuture<LiteCraftTreeNode> requestDisplayTree() {
            if (displayTree == null) {
                throw new UnsupportedOperationException("This test plan has no display tree");
            }
            return displayTree;
        }
    }

    /**
     * A pattern that shares one instance with the map lookups of the test, so repeated sub-plans can be identified.
     */
    private static final class TestPattern implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private static final class TestProvider implements ICraftingProvider {
        private final IPatternDetails pattern;

        private TestProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        @Override
        public List<? extends IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
            return patternDetails == pattern;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
