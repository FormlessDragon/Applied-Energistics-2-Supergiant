package ae2.integration.data;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CraftingTreeSourceIdTest {
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, CraftingTreeStackRegistry.MAX_TREE_PROCESSES - 1})
    void producerIdentitySurvivesFilteringSortingAndTheWire(int sourceId) {
        Bootstrap.register();
        var key = AEItemKey.of(Items.APPLE);
        var inputs = new ArrayList<LiteCraftTreeNode>();
        var proc = new LiteCraftTreeProc(inputs, List.of(), Map.of(), null, 0, sourceId);
        inputs.add(new LiteCraftTreeNode(proc, new GenericStack(key, Long.MAX_VALUE), new ArrayList<>(), 1));
        var root = new LiteCraftTreeNode(null, new GenericStack(key, Long.MAX_VALUE), new ArrayList<>(List.of(proc)), 0);
        var filtered = root.withMissingOnly();
        filtered.sort();
        var registry = new CraftingTreeStackRegistry();
        var buffer = Unpooled.buffer();
        try {
            filtered.writeToBuffer(buffer, registry);
            var decoded = LiteCraftTreeNode.fromBuffer(buffer, registry, null);
            var decodedProc = decoded.inputs().getFirst();
            assertEquals(sourceId, decodedProc.sourceId());
            assertEquals(Long.MAX_VALUE, decodedProc.inputs().getFirst().output().amount());
            assertEquals(1, decodedProc.inputs().getFirst().missing());
            assertSame(decodedProc, decodedProc.inputs().getFirst().parent());
            assertEquals(0, buffer.readableBytes());
            if (sourceId >= 0) assertEquals(sourceId, LiteCraftTreeNode.feedbackSourceId(LiteCraftTreeNode.sourceFeedback(sourceId)));
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, CraftingTreeStackRegistry.MAX_TREE_PROCESSES, Integer.MAX_VALUE})
    void decoderRejectsInvalidProducerIds(int sourceId) {
        var buffer = Unpooled.buffer();
        try {
            var registry = new CraftingTreeStackRegistry();
            new LiteCraftTreeProc(List.of(), List.of(), Map.of(), null, 0, sourceId).writeToBuffer(buffer, registry);
            var failure = assertThrows(IllegalArgumentException.class, () -> LiteCraftTreeProc.fromBuffer(buffer, registry));
            assertEquals("Invalid crafting tree source id: " + sourceId, failure.getMessage());
        } finally {
            buffer.release();
        }
    }
}
