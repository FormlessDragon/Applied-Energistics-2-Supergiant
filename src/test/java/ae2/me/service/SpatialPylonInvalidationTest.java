package ae2.me.service;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNode;
import ae2.api.networking.events.GridBootingStatusChange;
import ae2.me.GridNode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpatialPylonInvalidationTest {
    @Test
    void unusedRegionAndUnrelatedBootsDoNotScanPylons() {
        var grid = new CountingGrid();
        var spatial = new SpatialPylonService(grid);
        spatial.bootingRender(new GridBootingStatusChange(true));
        spatial.bootingRender(new GridBootingStatusChange(false));
        assertFalse(spatial.hasRegion());
        assertEquals(0, grid.scans);

        spatial.markDirty();
        spatial.bootingRender(new GridBootingStatusChange(true));
        assertEquals(0, grid.scans);
        assertFalse(spatial.hasRegion());
        assertEquals(1, grid.scans);
        spatial.bootingRender(new GridBootingStatusChange(false));
        assertEquals(1, grid.scans);

        var unrelated = new GridNode(null, new Object(), (owner, node) -> {
        }, EnumSet.noneOf(GridFlags.class));
        spatial.addNode(unrelated, null);
        spatial.onNodeStateChanged(unrelated);
        spatial.removeNode(unrelated);
        spatial.bootingRender(new GridBootingStatusChange(true));
        spatial.bootingRender(new GridBootingStatusChange(false));
        assertEquals(1, grid.scans);

        spatial.markDirty();
        spatial.markDirty();
        spatial.bootingRender(new GridBootingStatusChange(false));
        assertEquals(2, grid.scans);
    }

    private static final class CountingGrid extends ActivePatternProviderDirectoryTest.TestGrid {
        private int scans;

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            scans++;
            return List.of();
        }
    }
}
