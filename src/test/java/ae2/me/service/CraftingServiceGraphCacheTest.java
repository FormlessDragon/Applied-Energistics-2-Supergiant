package ae2.me.service;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.events.GridEvent;
import ae2.api.stacks.AEItemKey;
import ae2.crafting.graph.CraftingGraph;
import com.google.gson.stream.JsonWriter;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression coverage for the crafting graph cache refusing structures built from patterns the network does not
 * actually have registered, such as the transient pseudo pattern a single HEI transfer injects. A graph cached under
 * a definition the network cannot supply would let a later calculation for the same output reuse a material
 * structure whose missing inputs never get requested, silently dropping ingredients from the plan.
 */
class CraftingServiceGraphCacheTest {
    private static AEItemKey output;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        output = AEItemKey.of(new Item());
    }

    @Test
    void graphBuiltFromAnUnregisteredPatternIsNotCached() {
        var crafting = new CraftingService(new EmptyGrid(), new StorageService(), null);
        // Never registered with the network: exactly like a transient pseudo pattern injected for one calculation.
        var transientDefinition = AEItemKey.of(new Item());

        var graph = new CraftingGraph();
        graph.recordPatternDefinition(transientDefinition);

        crafting.putCachedGraph(output, graph);

        assertNull(crafting.peekCachedGraph(output),
            "a graph built from a pattern the network does not know must not be cached for reuse");
        assertNull(crafting.peekSimulationClosure(output),
            "the material closure of an uncachable graph must not be remembered either");
    }

    @Test
    void graphWithoutAnyPatternDefinitionIsStillCached() {
        var crafting = new CraftingService(new EmptyGrid(), new StorageService(), null);

        // No patterns recorded at all, e.g. a plan that only reads emitted network items. The new check must not
        // reject a graph it has nothing to verify against.
        var graph = new CraftingGraph();

        crafting.putCachedGraph(output, graph);

        assertSame(graph, crafting.peekCachedGraph(output),
            "a graph that records no pattern definitions must still be cached normally");
        assertNotNull(crafting.peekSimulationClosure(output));
    }

    private static final class EmptyGrid implements IGrid {
        @Override
        public <C extends IGridService> C getService(Class<C> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            return List.of();
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> type) {
            return List.of();
        }

        @Override
        public <T> Set<T> getMachines(Class<T> type) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> type) {
            return Set.of();
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            return List.of();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public IGridNode getPivot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void export(JsonWriter writer) {
        }
    }
}
