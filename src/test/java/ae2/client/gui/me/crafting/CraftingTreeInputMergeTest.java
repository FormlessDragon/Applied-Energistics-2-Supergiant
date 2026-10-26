package ae2.client.gui.me.crafting;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTreeInputMergeTest {
    @ParameterizedTest
    @ValueSource(longs = {0, 1, 64})
    void stockAndMissingSeedsMergeWithCirculationWithoutChangingTheirAmounts(long missing) {
        Bootstrap.register();
        var key = AEItemKey.of(Items.APPLE);
        var inputs = new ArrayList<LiteCraftTreeNode>();
        var proc = new LiteCraftTreeProc(inputs, List.of(), Map.of(), null, 0, 0);
        inputs.add(new LiteCraftTreeNode(proc, new GenericStack(key, 64), new ArrayList<>(), missing));
        inputs.add(new LiteCraftTreeNode(proc, new GenericStack(key, 1_000_000_000L), new ArrayList<>(), 0, 0));
        var root = new LiteCraftTreeNode(null, new GenericStack(key, 1_000_000_000L), new ArrayList<>(List.of(proc)), 0);
        var tree = CraftingTreeCycleView.contract(root);
        var region = tree.cycles().values().iterator().next();
        var ring = CraftingTreeCycleView.ring(region, new ReferenceOpenHashSet<>(), tree.entries());
        var shownProc = ring.root().inputs().getFirst();
        assertEquals(1, shownProc.inputs().size());
        var input = shownProc.inputs().getFirst();
        assertEquals(1_000_000_064L, input.output().amount());
        assertEquals(missing, input.missing());
        assertEquals(new CraftingTreeCycleView.InputAmounts(64 - missing, 0, 1_000_000_000L), ring.amounts().get(input));
        assertSame(shownProc, ring.feedback().get(input));
        assertSame(shownProc, input.parent());
        assertEquals(missing > 0, LiteCraftTreeNode.isMissing(input));
        assertEquals(2, proc.inputs().size(), "display merging must not rewrite the original plan tree");
    }

    @Test
    void everyCycleUnderOneMaterialRemainsAccessible() {
        Bootstrap.register();
        var key = AEItemKey.of(Items.APPLE);
        var procs = new ArrayList<LiteCraftTreeProc>();
        for (int id = 0; id < 2; id++) {
            var inputs = new ArrayList<LiteCraftTreeNode>();
            var proc = new LiteCraftTreeProc(inputs, List.of(), Map.of(), null, id, id);
            inputs.add(new LiteCraftTreeNode(proc, new GenericStack(key, 40), new ArrayList<>(), 0, 0));
            procs.add(proc);
        }
        var root = new LiteCraftTreeNode(null, new GenericStack(key, 100), procs, 0);
        var tree = CraftingTreeCycleView.contract(root);
        var region = tree.cycles().values().iterator().next();
        assertEquals(2, region.crafts());
        assertEquals(2, region.cycles().size());
        var ring = CraftingTreeCycleView.ring(region, new ReferenceOpenHashSet<>(), tree.entries());
        assertEquals(2, ring.root().inputs().size());
        assertEquals(2, ring.feedback().size());
        assertTrue(ring.feedback().entrySet().stream().allMatch(e -> e.getKey().parent() == e.getValue()),
            "independent self returns must not be joined to the other cycle");
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1000000000,1", "9223372036854775743,64"})
    void mergingStockIntoANestedCycleKeepsItsEntryAndLongAmounts(long crafted, long missing) {
        Bootstrap.register();
        var a = AEItemKey.of(Items.APPLE);
        var b = AEItemKey.of(Items.CARROT);
        var nestedInputs = new ArrayList<LiteCraftTreeNode>();
        var nested = new LiteCraftTreeProc(nestedInputs, List.of(), Map.of(), null, 1, 1);
        nestedInputs.add(new LiteCraftTreeNode(nested, new GenericStack(b, crafted), new ArrayList<>(), 0, 0));
        var inputs = new ArrayList<LiteCraftTreeNode>();
        var proc = new LiteCraftTreeProc(inputs, List.of(), Map.of(), null, 0, 0);
        inputs.add(new LiteCraftTreeNode(proc, new GenericStack(a, 1), new ArrayList<>(), 0, 0));
        inputs.add(new LiteCraftTreeNode(proc, new GenericStack(b, 64), new ArrayList<>(), missing));
        var originalNested = new LiteCraftTreeNode(proc, new GenericStack(b, crafted), new ArrayList<>(List.of(nested)), 0);
        inputs.add(originalNested);
        var root = new LiteCraftTreeNode(null, new GenericStack(a, 10), new ArrayList<>(List.of(proc)), 0);
        var tree = CraftingTreeCycleView.contract(root);
        var ring = CraftingTreeCycleView.ring(tree.entries().get(root), new ReferenceOpenHashSet<>(), tree.entries());
        var shown = ring.root().inputs().getFirst().inputs();
        assertEquals(2, shown.size());
        var merged = shown.stream().filter(n -> n.output().what().equals(b)).findFirst().orElseThrow();
        assertEquals(Math.addExact(64, crafted), merged.output().amount());
        assertEquals(new CraftingTreeCycleView.InputAmounts(64 - missing, crafted, 0), ring.amounts().get(merged));
        assertEquals(missing, merged.missing());
        assertEquals(1, ring.nested().size());
        assertSame(tree.entries().get(originalNested), ring.nested().get(merged));
        assertNotNull(ring.nested().get(merged));
    }

    @Test
    void aVisibleSharedReturnStillConnectsWhenItsProducerIsFolded() throws Exception {
        Bootstrap.register();
        var key = AEItemKey.of(Items.APPLE);
        var processes = new ArrayList<LiteCraftTreeProc>();
        for (int id = 0; id < 8; id++) processes.add(new LiteCraftTreeProc(new ArrayList<>(), List.of(), Map.of(), null, 0, id));
        for (int id = 0; id < 7; id++) {
            processes.get(id).inputs().add(new LiteCraftTreeNode(processes.get(id), new GenericStack(key, 20),
                new ArrayList<>(List.of(processes.get(id + 1))), 0));
        }
        processes.get(7).inputs().add(new LiteCraftTreeNode(processes.get(7), new GenericStack(key, 20),
            new ArrayList<>(), 0, LiteCraftTreeNode.sourceFeedback(0)));
        processes.get(7).inputs().add(new LiteCraftTreeNode(processes.get(7), new GenericStack(AEItemKey.of(Items.DIAMOND), 1),
            new ArrayList<>(), 1));
        processes.getFirst().inputs().add(new LiteCraftTreeNode(processes.getFirst(), new GenericStack(key, 7),
            new ArrayList<>(), 0, LiteCraftTreeNode.sourceFeedback(7)));
        var root = new LiteCraftTreeNode(null, new GenericStack(key, 100), new ArrayList<>(List.of(processes.getFirst())), 0);
        var tree = CraftingTreeCycleView.contract(root);
        var region = tree.entries().get(root);
        var unfolded = new ReferenceOpenHashSet<LiteCraftTreeNode>();
        var folded = CraftingTreeCycleView.ring(region, unfolded, tree.entries());
        assertEquals(1, folded.folds().size());
        assertTrue(LiteCraftTreeNode.isMissing(folded.folds().keySet().iterator().next()), "a fold must retain shortages hidden inside it");
        assertEquals(1, folded.feedback().size(), "visible references to a hidden producer must connect to its fold");
        var widget = new CraftingTreeWidget();
        widget.setSize(1200, 1200);
        widget.setRoot(root);
        var open = CraftingTreeWidget.class.getDeclaredMethod("openRing", CraftingTreeCycleView.Region.class);
        open.setAccessible(true);
        open.invoke(widget, region);
        assertEquals(1, widget.layoutFeedbackLines().size(), "a resolved fold reference must actually have a rendered line");
        unfolded.add(folded.folds().values().iterator().next().node());
        var expanded = CraftingTreeCycleView.ring(region, unfolded, tree.entries());
        assertTrue(expanded.folds().isEmpty());
        assertEquals(2, expanded.feedback().size());
        assertEquals(1, CraftingTreeCycleView.ring(region, new ReferenceOpenHashSet<>(), tree.entries()).feedback().size(),
            "opening and closing the fold must not lose the original route");
    }
}
