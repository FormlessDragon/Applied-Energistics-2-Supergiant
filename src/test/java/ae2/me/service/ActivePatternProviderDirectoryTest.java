package ae2.me.service;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.VersionedInternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.IGridService;
import ae2.api.networking.IGridVisitor;
import ae2.api.networking.events.GridEvent;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AEColor;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.util.inv.AppEngInternalInventory;
import com.google.gson.stream.JsonWriter;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivePatternProviderDirectoryTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void emptySlotCountsAreLazyAndSharedAcrossQueriesUntilInventoryChanges() {
        var directory = new ActivePatternProviderDirectory(new TestGrid());
        var provider = new TestPatternContainer();
        var inventory = new CountingVersionedInventory(3);
        provider.inventory = inventory;
        var node = new TestNode(provider, true);
        directory.addNode(node, null);
        directory.getActiveProviders();
        assertEquals(0, inventory.reads);
        assertEquals(3, directory.getEmptySlots(provider));
        for (int i = 0; i < 20; i++) {
            assertEquals(3, directory.getEmptySlots(provider));
            assertEquals(3, directory.getSelectableProviderDescriptors().getFirst().emptySlots());
        }
        assertEquals(3, inventory.reads);
        inventory.setItemDirect(1, new ItemStack(Items.APPLE));
        assertEquals(2, directory.getEmptySlots(provider));
        assertEquals(6, inventory.reads);
        var replacement = new CountingVersionedInventory(3);
        replacement.version = inventory.version;
        provider.inventory = replacement;
        assertEquals(3, directory.getEmptySlots(provider));
        assertEquals(3, replacement.reads);
        replacement.visibleSize = 2;
        assertEquals(2, directory.getEmptySlots(provider));
        assertEquals(5, replacement.reads);
        directory.removeNode(node);
        directory.addNode(node, null);
        assertEquals(2, directory.getEmptySlots(provider));
        assertEquals(7, replacement.reads);
    }

    @Test
    void unversionedInventoriesStillReflectEveryQuery() {
        var directory = new ActivePatternProviderDirectory(new TestGrid());
        var provider = new TestPatternContainer();
        var inventory = new AppEngInternalInventory(2);
        provider.inventory = inventory;
        directory.addNode(new TestNode(provider, true), null);
        assertEquals(2, directory.getEmptySlots(provider));
        inventory.setItemDirect(0, new ItemStack(Items.STICK));
        assertEquals(1, directory.getEmptySlots(provider));
        inventory.setItemDirect(1, new ItemStack(Items.APPLE));
        assertEquals(0, directory.getEmptySlots(provider));
    }

    @Test
    void tracksOnlyPatternContainersAndRefreshesNodeStateByIdentity() {
        var directory = new ActivePatternProviderDirectory(new TestGrid());
        var container = new TestPatternContainer();
        var providerNode = new TestNode(container, true);

        directory.addNode(providerNode, null);
        List<PatternContainer> initial = directory.getActiveProviders();
        assertEquals(List.of(container), initial);

        directory.addNode(new TestNode(new Object(), true), null);
        assertSame(initial, directory.getActiveProviders());
        long revision = directory.getProviderSetRevision();

        providerNode.active = false;
        directory.onNodeStateChanged(providerNode);
        assertTrue(directory.getActiveProviders().isEmpty());
        assertEquals(revision + 1, directory.getProviderSetRevision());

        providerNode.active = true;
        directory.onNodeStateChanged(providerNode);
        assertEquals(List.of(container), directory.getActiveProviders());
    }

    @Test
    void ignoresStateChangesFromNodesThatAreNotCandidates() {
        var directory = new ActivePatternProviderDirectory(new TestGrid());
        var container = new TestPatternContainer();
        var providerNode = new TestNode(container, true);
        var unrelatedNode = new TestNode(new Object(), true);

        directory.addNode(providerNode, null);
        long revision = directory.getProviderSetRevision();
        unrelatedNode.active = false;
        directory.onNodeStateChanged(unrelatedNode);

        assertEquals(revision, directory.getProviderSetRevision());
        assertEquals(List.of(container), directory.getActiveProviders());
    }

    @Test
    void keepsSharedContainerUntilItsLastActiveNodeIsRemoved() {
        var directory = new ActivePatternProviderDirectory(new TestGrid());
        var container = new TestPatternContainer();
        var first = new TestNode(container, true);
        var second = new TestNode(container, true);

        directory.addNode(first, null);
        directory.addNode(second, null);
        assertEquals(List.of(container), directory.getActiveProviders());

        directory.removeNode(first);
        assertEquals(List.of(container), directory.getActiveProviders());

        directory.removeNode(second);
        assertTrue(directory.getActiveProviders().isEmpty());
    }

    @Test
    void removingLastNodeRevokesItsKeyBeforeAReplacementIsRead() {
        var directory = new ActivePatternProviderDirectory(new TestGrid());
        var container = new TestPatternContainer();
        var first = new TestNode(container, true);
        var second = new TestNode(container, true);
        directory.addNode(first, null);
        directory.addNode(second, null);
        var key = directory.getSelectableProviderDescriptors().getFirst().providerKey();
        directory.removeNode(first);
        assertSame(container, directory.resolveSelectableProvider(key));
        directory.removeNode(second);
        directory.addNode(new TestNode(container, true), null);
        assertNull(directory.resolveSelectableProvider(key));
        assertEquals(List.of(container), directory.getActiveProviders());
    }

    private static final class CountingVersionedInventory extends AppEngInternalInventory
        implements VersionedInternalInventory {
        private int reads;
        private long version;
        private int visibleSize;

        private CountingVersionedInventory(int size) {
            super(size);
            this.visibleSize = size;
        }

        @Override
        public int size() {
            return this.visibleSize;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            this.reads++;
            return super.getStackInSlot(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            this.version++;
            super.onContentsChanged(slot);
        }

        @Override
        public long getContentsVersion() {
            return this.version;
        }
    }

    private static final class TestPatternContainer implements PatternContainer {
        private InternalInventory inventory = InternalInventory.empty();

        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return this.inventory;
        }

        @Override
        public boolean containsPattern(AEItemKey pattern) {
            return false;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return new PatternContainerGroup(null, new TextComponentString("test"), List.of());
        }
    }

    private static final class TestNode implements IGridNode {
        private final Object owner;
        private boolean active;

        private TestNode(Object owner, boolean active) {
            this.owner = owner;
            this.active = active;
        }

        @Override
        public <T extends IGridNodeService> T getService(Class<T> serviceClass) {
            return null;
        }

        @Override
        public Object getOwner() {
            return owner;
        }

        @Override
        public void beginVisit(IGridVisitor visitor) {
        }

        @Override
        public IGrid grid() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldServer getLevel() {
            return null;
        }

        @Override
        public Set<EnumFacing> getConnectedSides() {
            return Set.of();
        }

        @Override
        public Map<EnumFacing, IGridConnection> getInWorldConnections() {
            return Map.of();
        }

        @Override
        public List<IGridConnection> getConnections() {
            return List.of();
        }

        @Override
        public boolean hasGridBooted() {
            return active;
        }

        @Override
        public boolean isPowered() {
            return active;
        }

        @Override
        public boolean meetsChannelRequirements() {
            return active;
        }

        @Override
        public boolean hasFlag(GridFlags flag) {
            return false;
        }

        @Override
        public int getOwningPlayerId() {
            return -1;
        }

        @Override
        public UUID getOwningPlayerProfileId() {
            return null;
        }

        @Override
        public double getIdlePowerUsage() {
            return 0;
        }

        @Override
        public AEItemKey getVisualRepresentation() {
            return null;
        }

        @Override
        public AEColor getGridColor() {
            return AEColor.TRANSPARENT;
        }

        @Override
        public void fillCrashReportCategory(CrashReportCategory category) {
        }

        @Override
        public int getMaxChannels() {
            return 0;
        }

        @Override
        public int getUsedChannels() {
            return 0;
        }
    }

    static class TestGrid implements IGrid {
        @Override
        public <C extends IGridService> C getService(Class<C> iface) {
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
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            return List.of();
        }

        @Override
        public <T> Set<T> getMachines(Class<T> machineClass) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> machineClass) {
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
        public void export(JsonWriter jsonWriter) {
        }
    }
}
