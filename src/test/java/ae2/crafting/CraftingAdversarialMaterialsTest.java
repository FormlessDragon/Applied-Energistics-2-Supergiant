package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.pattern.AEProcessingPattern;
import net.minecraftforge.fluids.FluidRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ae2.crafting.CraftingAdversarialFixtures.Fixture;
import static ae2.crafting.CraftingAdversarialFixtures.Scenario;
import static ae2.crafting.CraftingAdversarialFixtures.Step;
import static ae2.crafting.CraftingAdversarialFixtures.balanced;
import static ae2.crafting.CraftingAdversarialFixtures.ceil;
import static ae2.crafting.CraftingTestNetwork.crafts;
import static ae2.crafting.CraftingTreeCyclesTest.tree;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingAdversarialMaterialsTest extends CraftingPlanningTestSupport {
    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void sparseRepeatedSlotsFluidsAndSharedByproductsKeepTheirUnits(int cache) throws Exception {
        memo(cache);
        var f = new Fixture(9);
        f.keys.put("water", AEFluidKey.of(FluidRegistry.WATER));
        var inputs = new ArrayList<GenericStack>(Collections.nCopies(AEProcessingPattern.MAX_INPUT_SLOTS, null));
        inputs.set(0, new GenericStack(f.key("ore"), 2));
        inputs.set(40, new GenericStack(f.key("ore"), 3));
        inputs.set(80, new GenericStack(f.key("water"), 1000));
        var outputs = new ArrayList<GenericStack>(Collections.nCopies(AEProcessingPattern.MAX_OUTPUT_SLOTS, null));
        outputs.set(0, new GenericStack(f.key("part"), 2));
        outputs.set(25, new GenericStack(f.key("slag"), 1));
        outputs.set(26, new GenericStack(f.key("slag"), 1));
        var split = f.sparseRecipe("split", inputs, outputs);
        f.recipe("root", new Object[]{"part", 1, "slag", 1}, new Object[]{"result", 1});
        f.stock("ore", 10);
        f.stock("water", 2000);
        f.register(false);
        var plan = f.net.plan(f.key("result"), 4);
        assertAll(
            () -> complete(f.net, plan, f.key("result"), 4),
            () -> assertEquals(2, crafts(plan, split)),
            () -> materials(plan.usedItems(), f.key("ore"), 10, f.key("water"), 2000),
            () -> balanced(plan),
            () -> CraftingTreeAudit.audit(f, plan, tree(plan)));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "500, 1", "999, 1", "1000, 1", "0, 17", "500, 17", "999, 17", "1000, 17", "1001, 17"})
    void fluidAmplificationCountsMillibucketsAndTheActualStarterThreshold(long water, long request) {
        var f = new Fixture((int) water);
        f.keys.put("water", AEFluidKey.of(FluidRegistry.WATER));
        f.keys.put("lava", AEFluidKey.of(FluidRegistry.LAVA));
        f.recipe("grow", new Object[]{"water", 1000}, new Object[]{"water", 1500});
        f.recipe("root", new Object[]{"water", 750}, new Object[]{"result", 2});
        f.stock("water", water);
        f.stock("lava", 1_000_000);
        f.register(false);
        f.net.recursiveReserve(0);
        var plan = f.net.plan(f.key("result"), request);
        long requiredSeed = Math.min(1000, ceil(request, 2) * 750);
        assertAll(f.describe(),
            () -> materials(plan.missingItems(), f.key("water"), Math.max(0, requiredSeed - water)),
            () -> assertEquals(0, plan.usedItems().get(f.key("lava"))),
            () -> withinStock(f.net, plan),
            () -> balanced(plan),
            () -> CraftingTreeAudit.audit(f, plan, tree(plan)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void nbtVariantsNeverCoverEachOthersShortageOrChangeAnEarlierTree(int cache) throws Exception {
        memo(cache);
        var f = new Fixture(11);
        f.recipe("root", new Object[]{"pure", 3}, new Object[]{"result", 1});
        f.stock("pure", 2);
        f.stock("impure", 10_000);
        f.register(false);
        var shortPlan = f.net.plan(f.key("result"), 1);
        assertAll(
            () -> materials(shortPlan.usedItems(), f.key("pure"), 2),
            () -> materials(shortPlan.missingItems(), f.key("pure"), 1),
            () -> CraftingTreeAudit.audit(f, shortPlan, tree(shortPlan)));
        var saved = CraftingTreeAudit.wireData(tree(shortPlan));
        f.stock("pure", 3);
        var fullPlan = f.net.plan(f.key("result"), 1);
        complete(f.net, fullPlan, f.key("result"), 1);
        CraftingTreeAudit.audit(f, fullPlan, tree(fullPlan));
        assertEquals(saved, CraftingTreeAudit.wireData(tree(shortPlan)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5, 8, 9})
    void mixedInputChoicesDisplayTheActuallyAllocatedVariants(int red) throws Exception {
        var f = new Fixture(red);
        var encoded = f.recipe("template", new Object[]{"red", 3}, new Object[]{"result", 1});
        var flexible = new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                return encoded.getDefinition();
            }

            @Override
            public IInput[] getInputs() {
                return new IInput[]{f.net.input(3, new GenericStack(f.key("red"), 1),
                    new GenericStack(f.key("blue"), 1))};
            }

            @Override
            public List<GenericStack> getOutputs() {
                return encoded.getOutputs();
            }

            @Override
            public int hashCode() {
                return 47 + red;
            }
        };
        f.patterns.put("template", flexible);
        f.stock("red", red);
        f.stock("blue", 9 - red);
        f.register(false);
        var plan = f.net.plan(f.key("result"), 3);
        var displayed = tree(plan);
        var shown = new KeyCounter();
        CraftingTreeAudit.walk(displayed, node -> {
            if (node.inputs().isEmpty()) shown.add(node.output().what(), node.output().amount() - node.missing());
        }, p -> {
        });
        assertAll(
            () -> complete(f.net, plan, f.key("result"), 3),
            () -> materials(plan.usedItems(), f.key("red"), red, f.key("blue"), 9 - red),
            () -> assertEquals(f.named(plan.usedItems()), f.named(shown), "tree must display the allocated variants"),
            () -> assertEquals(amounts(plan.missingItems()), amounts(CraftingTreeAudit.missing(displayed))),
            () -> CraftingTreeAudit.roundTrip(displayed));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void returnedContainersCanBeRefilledAndReusedAcrossManyCrafts(int cache) throws Exception {
        memo(cache);
        var f = new Fixture(13);
        var drain = f.recipe("drain", new Object[]{"filled", 1}, new Object[]{"product", 1, "empty", 1});
        var refill = f.recipe("refill", new Object[]{"empty", 1, "water", 1000}, new Object[]{"filled", 1});
        var root = f.recipe("root", new Object[]{"product", 2}, new Object[]{"result", 1});
        f.stock("filled", 1);
        f.stock("water", 10_000);
        f.register(false);
        var steps = new ArrayList<Step>();
        for (int i = 0; i < 10; i++) {
            steps.add(new Step(drain, 1));
            if (i < 9) steps.add(new Step(refill, 1));
        }
        steps.add(new Step(root, 5));
        var scenario = new Scenario(f, "result", 5, steps);
        scenario.verifyWitness();
        var plan = f.net.plan(f.key("result"), 5);
        assertAll(
            () -> complete(f.net, plan, f.key("result"), 5),
            () -> balanced(plan),
            () -> CraftingTreeAudit.audit(f, plan, tree(plan)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void sortingAndMissingOnlyViewKeepExactMissingKeysAndQuantities(int cache) throws Exception {
        memo(cache);
        var f = new Fixture(3);
        f.recipe("left", new Object[]{"ore", 2}, new Object[]{"left", 1});
        f.recipe("right", new Object[]{"dust", 3}, new Object[]{"right", 1});
        f.recipe("root", new Object[]{"left", 1, "right", 1}, new Object[]{"result", 1});
        f.stock("ore", 3);
        f.stock("dust", 6);
        f.register(false);
        var plan = f.net.plan(f.key("result"), 2);
        var displayed = tree(plan);
        var before = amounts(CraftingTreeAudit.missing(displayed));
        displayed.sort();
        var filtered = displayed.withMissingOnly();
        assertAll(
            () -> materials(plan.missingItems(), f.key("ore"), 1),
            () -> assertEquals(before, amounts(CraftingTreeAudit.missing(displayed))),
            () -> assertEquals(before, amounts(CraftingTreeAudit.missing(filtered))),
            () -> assertEquals(plan.finalOutput(), filtered.output()),
            () -> CraftingTreeAudit.audit(f, plan, displayed),
            () -> CraftingTreeAudit.roundTrip(filtered));
    }
}
