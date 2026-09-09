package ae2.me.service.helpers;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeService;
import ae2.api.networking.IGridVisitor;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.util.AEColor;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkCraftingProvidersTest {
    private static AEItemKey output;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        output = AEItemKey.of(new Item());
    }

    @Test
    void emptyProviderRegistrationDoesNotInvalidatePlanningOrKeySnapshots() {
        var providers = new NetworkCraftingProviders();
        var empty = new TestProvider(List.of());
        var snapshot = providers.getCraftableKeys();
        long revision = providers.getRevision();
        providers.addProvider(empty);
        providers.removeProvider(empty);
        assertEquals(revision, providers.getRevision());
        assertSame(snapshot, providers.getCraftableKeys());
    }

    @Test
    void unchangedRefreshPreservesSnapshotsAndRealRegistrationChangesAdvanceRevision() {
        var providers = new NetworkCraftingProviders();
        var pattern = new TestPattern();
        var provider = new TestProvider(List.of(pattern));
        var node = new TestNode(provider);
        providers.addProvider(node);
        var snapshot = providers.getMediumsSnapshot(pattern);
        long revision = providers.getRevision();
        long displayedRevision = providers.getCraftablesRevision();
        providers.refreshProvider(node);
        assertEquals(revision, providers.getRevision());
        assertSame(snapshot, providers.getMediumsSnapshot(pattern));
        providers.removeProvider(node);
        providers.addProvider(node);
        assertTrue(providers.getCraftablesRevision() > displayedRevision);
        providers.removeProvider(node);
        assertTrue(providers.getCraftablesRevision() > displayedRevision);
    }

    @Test
    void removesOnlyTheOccurrencesOwnedByOneNode() {
        var pattern = new TestPattern();
        var provider = new TestProvider(List.of(pattern, pattern));
        var firstNode = new TestNode(provider);
        var secondNode = new TestNode(provider);
        var providers = new NetworkCraftingProviders();

        providers.addProvider(firstNode);
        providers.addProvider(secondNode);
        assertEquals(List.of(provider, provider, provider, provider), providers.getMediumsSnapshot(pattern));

        providers.removeProvider(firstNode);
        assertEquals(List.of(provider, provider), providers.getMediumsSnapshot(pattern));

        providers.removeProvider(secondNode);
        assertTrue(providers.getMediumsSnapshot(pattern).isEmpty());
    }

    @Test
    void duplicatePatternOccurrencesRemainOneCraftingChoiceUntilProviderRemoval() {
        var pattern = new TestPattern();
        var provider = new TestProvider(List.of(pattern, pattern));
        var node = new TestNode(provider);
        var providers = new NetworkCraftingProviders();

        providers.addProvider(node);

        assertEquals(List.of(pattern), providers.getCraftingFor(output));

        providers.removeProvider(node);

        assertTrue(providers.getCraftingFor(output).isEmpty());
    }

    @Test
    void equivalentPriorityPatternsKeepTheirProviderSlotOrder() {
        var first = new TestPattern();
        var second = new TestPattern();
        var providers = new NetworkCraftingProviders();
        var provider = new TestProvider(List.of(first, second, first));
        providers.addProvider(provider);
        assertEquals(List.of(first, second), providers.getCraftingFor(output));
        providers.refreshProvider(provider);
        assertEquals(List.of(first, second), providers.getCraftingFor(output));
    }

    @Test
    void snapshotsAreLazyImmutableAndKeepRegistrationOrder() {
        var pattern = new TestPattern();
        var first = new TestProvider(List.of(pattern));
        var removed = new TestProvider(List.of(pattern));
        var last = new TestProvider(List.of(pattern));
        var providers = new NetworkCraftingProviders();

        providers.addProvider(first);
        providers.addProvider(removed);
        var oldSnapshot = providers.getMediumsSnapshot(pattern);
        providers.addProvider(last);
        providers.removeProvider(removed);

        assertEquals(List.of(first, removed), oldSnapshot);
        assertEquals(List.of(first, last), providers.getMediumsSnapshot(pattern));
        assertThrows(UnsupportedOperationException.class, () -> oldSnapshot.add(last));
    }

    @Test
    void refreshedOccurrencesRetainRegistrationOrderAndRoundRobin() {
        var pattern = new TestPattern();
        var occurrences = new ArrayList<IPatternDetails>();
        occurrences.add(pattern);
        var first = new TestProvider(occurrences);
        var last = new TestProvider(List.of(pattern));
        var providers = new NetworkCraftingProviders();
        providers.addProvider(first);
        providers.addProvider(last);
        assertEquals(List.of(first, last), providers.getMediumsSnapshot(pattern));
        occurrences.add(pattern);
        providers.refreshProvider(first);
        var iterator = providers.getMediums(pattern).iterator();
        assertSame(first, iterator.next());
        assertSame(first, iterator.next());
        assertSame(last, providers.getMediums(pattern).iterator().next());
        assertEquals(List.of(first, first, last), providers.getMediumsSnapshot(pattern));
    }

    @Test
    void keySnapshotsAreReusedUntilTheProviderSetChanges() {
        var pattern = new TestPattern();
        var providers = new NetworkCraftingProviders();

        Set<AEKey> emptyCraftableSnapshot = providers.getCraftableKeys();
        providers.addProvider(new TestProvider(List.of(pattern)));

        Set<AEKey> craftableSnapshot = providers.getCraftableKeys();
        assertNotSame(emptyCraftableSnapshot, craftableSnapshot);
        assertSame(craftableSnapshot, providers.getCraftableKeys());
        assertEquals(Set.of(output), craftableSnapshot);
        assertThrows(UnsupportedOperationException.class, craftableSnapshot::clear);

        Set<AEKey> emptyEmittableSnapshot = providers.getEmittableKeys();
        providers.addProvider(new TestProvider(List.of()) {
            @Override
            public Set<AEKey> getEmitableItems() {
                return Set.of(output);
            }
        });

        Set<AEKey> emittableSnapshot = providers.getEmittableKeys();
        assertNotSame(emptyEmittableSnapshot, emittableSnapshot);
        assertSame(emittableSnapshot, providers.getEmittableKeys());
        assertEquals(Set.of(output), emittableSnapshot);
    }

    @Test
    void roundRobinAdvancesAndStructuralChangesResetIt() {
        var pattern = new TestPattern();
        var first = new TestProvider(List.of(pattern));
        var second = new TestProvider(List.of(pattern));
        var third = new TestProvider(List.of(pattern));
        var providers = new NetworkCraftingProviders();
        providers.addProvider(first);
        providers.addProvider(second);

        Iterator<ICraftingProvider> firstPass = providers.getMediums(pattern).iterator();
        assertSame(first, firstPass.next());
        assertSame(second, providers.getMediums(pattern).iterator().next());

        providers.addProvider(third);
        assertSame(first, providers.getMediums(pattern).iterator().next());
    }

    @Test
    void sequentialPatternListsAreReadLinearly() {
        var pattern = new TestPattern();
        var patterns = new LinkedList<IPatternDetails>() {
            @Override
            public IPatternDetails get(int index) {
                throw new AssertionError("Sequential list must not be indexed");
            }
        };
        patterns.add(pattern);
        patterns.add(pattern);
        var provider = new TestProvider(patterns);
        var registry = new NetworkCraftingProviders();
        registry.addProvider(provider);
        assertEquals(List.of(provider, provider), registry.getMediumsSnapshot(pattern));
    }

    @Test
    void callbackRemovalContinuesWithRemainingProviders() {
        var pattern = new TestPattern();
        var registry = new NetworkCraftingProviders();
        var removed = new TestProvider(List.of(pattern)) {
            @Override
            public boolean isBusy() {
                registry.removeProvider(this);
                return true;
            }
        };
        var second = new TestProvider(List.of(pattern));
        var third = new TestProvider(List.of(pattern));
        registry.addProvider(removed);
        registry.addProvider(second);
        registry.addProvider(third);
        var iterator = registry.getMediums(pattern).iterator();
        assertTrue(iterator.next().isBusy());
        assertTrue(iterator.hasNext());
        assertSame(second, iterator.next());
        registry.removeProvider(third);
        assertSame(second, iterator.next());
        Assertions.assertFalse(iterator.hasNext());
    }

    @Test
    void providerCallbacksDoNotRunWhileTheRegistryLockIsHeld() throws Exception {
        var pattern = new TestPattern();
        var providers = new NetworkCraftingProviders();
        var existing = new TestProvider(List.of(pattern));
        providers.addProvider(existing);
        providers.ensureInitialized();

        var enteredCallback = new CountDownLatch(1);
        var releaseCallback = new CountDownLatch(1);
        var blocked = new TestProvider(List.of(pattern)) {
            @Override
            public List<? extends IPatternDetails> getAvailablePatterns() {
                enteredCallback.countDown();
                assertDoesNotThrow(() -> assertTrue(releaseCallback.await(10, TimeUnit.SECONDS)));
                return super.getAvailablePatterns();
            }
        };

        var failure = new AtomicReference<Throwable>();
        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                assertTrue(enteredCallback.await(2, TimeUnit.SECONDS));
                assertEquals(List.of(existing), providers.getMediumsSnapshot(pattern));
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                releaseCallback.countDown();
            }
        });
        providers.addProvider(blocked);
        reader.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }

        assertEquals(List.of(existing, blocked), providers.getMediumsSnapshot(pattern));
    }

    @Test
    void concurrentSnapshotReadersNeverObservePartialChanges() throws Exception {
        var pattern = new TestPattern();
        var stable = new TestProvider(List.of(pattern));
        var providers = new NetworkCraftingProviders();
        providers.addProvider(stable);
        providers.ensureInitialized();
        var failure = new AtomicReference<Throwable>();

        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                for (int i = 0; i < 1_000; i++) {
                    var snapshot = providers.getMediumsSnapshot(pattern);
                    assertSame(stable, snapshot.getFirst());
                    assertTrue(snapshot.size() == 1 || snapshot.size() == 3);
                    if (snapshot.size() == 3) {
                        assertSame(snapshot.get(1), snapshot.get(2));
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        for (int i = 0; i < 100; i++) {
            var transientProvider = new TestProvider(List.of(pattern, pattern));
            providers.addProvider(transientProvider);
            providers.removeProvider(transientProvider);
        }
        reader.join();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent snapshot reader failed", failure.get());
        }
        assertEquals(List.of(stable), providers.getMediumsSnapshot(pattern));
    }

    @Test
    void registrationAndRevisionQueriesDoNotCollectPatternsUntilFirstRead() throws Exception {
        var providers = new NetworkCraftingProviders();
        var reads = new AtomicInteger();
        var emissionReads = new AtomicInteger();
        var priorityReads = new AtomicInteger();
        var pattern = new TestPattern();
        var provider = new TestProvider(List.of(pattern)) {
            @Override
            public List<? extends IPatternDetails> getAvailablePatterns() {
                reads.incrementAndGet();
                return super.getAvailablePatterns();
            }

            @Override
            public Set<AEKey> getEmitableItems() {
                emissionReads.incrementAndGet();
                return Set.of();
            }

            @Override
            public int getPatternPriority() {
                priorityReads.incrementAndGet();
                return 0;
            }
        };
        providers.addProvider(provider);
        providers.refreshProvider(provider);
        assertEquals(0, providers.getRevision());
        assertEquals(0, providers.getCraftablesRevision());
        assertEquals(0, reads.get());
        assertEquals(0, emissionReads.get());
        assertEquals(0, priorityReads.get());
        var failure = new AtomicReference<Throwable>();
        var worker = Thread.ofPlatform().start(() -> {
            try {
                providers.getCraftableKeys();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        worker.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(0, reads.get());
        assertEquals(List.of(provider), providers.getMediumsSnapshot(pattern));
        providers.getCraftableKeys();
        assertEquals(1, reads.get());
        assertEquals(1, emissionReads.get());
        assertEquals(1, priorityReads.get());
        providers.addProvider(new TestProvider(List.of(pattern)));
        assertEquals(1, reads.get());
        providers.removeProvider(provider);
        assertEquals(1, reads.get());
    }

    @Test
    void failedOrSwallowedReentrantInitializationNeverPublishesPartialIndex() {
        var providers = new NetworkCraftingProviders();
        var pattern = new TestPattern();
        var good = new TestProvider(List.of(pattern));
        var bad = new TestProvider(List.of(pattern)) {
            @Override
            public List<? extends IPatternDetails> getAvailablePatterns() {
                assertThrows(IllegalStateException.class, providers::getCraftableKeys);
                return super.getAvailablePatterns();
            }
        };
        providers.addProvider(good);
        providers.addProvider(bad);
        assertThrows(IllegalStateException.class, providers::getCraftableKeys);
        assertEquals(0, providers.getRevision());
        providers.removeProvider(bad);
        assertEquals(List.of(good), providers.getMediumsSnapshot(pattern));
    }

    private static class TestProvider implements ICraftingProvider {
        private final List<? extends IPatternDetails> patterns;

        private TestProvider(List<? extends IPatternDetails> patterns) {
            this.patterns = patterns;
        }

        @Override
        public List<? extends IPatternDetails> getAvailablePatterns() {
            return patterns;
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class TestPattern implements IPatternDetails {
        private final AEItemKey definition = AEItemKey.of(new Item());

        @Override
        public AEItemKey getDefinition() {
            return definition;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(output, 1));
        }
    }

    private record TestNode(ICraftingProvider provider) implements IGridNode {

        @Override
            public <T extends IGridNodeService> T getService(Class<T> serviceClass) {
                return serviceClass == ICraftingProvider.class ? serviceClass.cast(provider) : null;
            }

            @Override
            public Object getOwner() {
                return this;
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
                return false;
            }

            @Override
            public boolean isPowered() {
                return false;
            }

            @Override
            public boolean meetsChannelRequirements() {
                return false;
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
}
