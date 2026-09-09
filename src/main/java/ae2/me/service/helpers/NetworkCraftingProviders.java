package ae2.me.service.helpers;

import ae2.api.config.FuzzyMode;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.AEKeyFilter;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;

/**
 * Keeps track of the crafting patterns in the network, and related information.
 */
public class NetworkCraftingProviders {
    private final Object lock = new Object();
    private final Thread ownerThread = Thread.currentThread();
    private final Object network;
    /**
     * Tracks state for crafting providers that may be provided without a grid node, such as by other grid services.
     */
    private final Reference2ObjectMap<ICraftingProvider, ProviderState> globalProviders = new Reference2ObjectOpenHashMap<>();
    private volatile boolean initialized;
    private boolean initializing;
    private boolean collecting;
    /**
     * Tracks the provider state for each grid node that provides auto-crafting to the network.
     */
    private final Reference2ObjectMap<IGridNode, ProviderState> craftingProviders = new Reference2ObjectOpenHashMap<>();
    private long registrationsRevision;
    private final Object2ObjectMap<IPatternDetails, CraftingProviderList> craftingMethods = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<AEKey, PatternsForKey> craftableItems = new Object2ObjectOpenHashMap<>();
    private final Object2IntOpenHashMap<AEItemKey> knownPatternDefinitions = new Object2IntOpenHashMap<>();
    /**
     * Used for looking up craftable alternatives using fuzzy search (i.e. ignore NBT).
     */
    private final KeyCounter craftableItemsList = new KeyCounter();
    private final Object2IntOpenHashMap<AEKey> emitableItems = new Object2IntOpenHashMap<>();
    private volatile Set<AEKey> craftableKeysSnapshot = Set.of();
    private volatile Set<AEKey> emittableKeysSnapshot = Set.of();
    private boolean keySnapshotsDirty;
    private volatile long revision;
    private long nextProviderOrder = 0;

    public NetworkCraftingProviders() {
        this("standalone pattern registry");
    }

    public NetworkCraftingProviders(Object network) {
        this.network = Objects.requireNonNull(network);
        this.emitableItems.defaultReturnValue(0);
        this.knownPatternDefinitions.defaultReturnValue(0);
    }

    /**
     * Initializes the index on its owning server thread, before any asynchronous calculation can read it.
     */
    public void ensureInitialized() {
        if (this.initialized) {
            return;
        }
        if (Thread.currentThread() != this.ownerThread) {
            throw initializationFailure("Uninitialized pattern index read from a worker thread");
        }
        ObjectArrayList<ProviderState> registrations;
        long expectedRevision;
        synchronized (lock) {
            if (this.initializing) {
                this.registrationsRevision++;
                throw initializationFailure("Reentrant pattern index initialization");
            }
            this.initializing = true;
            expectedRevision = this.registrationsRevision;
            registrations = new ObjectArrayList<>(this.craftingProviders.values());
            registrations.addAll(this.globalProviders.values());
        }
        try {
            registrations.sort(Comparator.comparingLong(state -> state.order));
            var captured = new ObjectArrayList<ProviderState>(registrations.size());
            for (int i = 0; i < registrations.size(); i++) {
                var registration = registrations.get(i);
                captured.add(ProviderState.capture(registration.provider).withOrder(registration.order));
            }
            synchronized (lock) {
                if (this.registrationsRevision != expectedRevision) {
                    throw initializationFailure("Provider registrations changed during initialization");
                }
                try {
                    for (int i = 0; i < captured.size(); i++) {
                        captured.get(i).mount(this);
                    }
                    if (this.registrationsRevision != expectedRevision) {
                        throw initializationFailure("Provider registrations changed while publishing patterns");
                    }
                } catch (RuntimeException | Error exception) {
                    this.craftingMethods.clear();
                    this.craftableItems.clear();
                    this.knownPatternDefinitions.clear();
                    this.craftableItemsList.clear();
                    this.craftableItemsList.removeEmptySubmaps();
                    this.emitableItems.clear();
                    throw exception;
                }
                for (int i = 0; i < registrations.size(); i++) {
                    registrations.get(i).captured = captured.get(i);
                }
                if (!this.craftingMethods.isEmpty() || !this.emitableItems.isEmpty()) {
                    markModified();
                }
                this.initialized = true;
            }
        } catch (RuntimeException | Error exception) {
            AELog.error(exception, "Failed to initialize pattern index for network " + this.network);
            throw exception;
        } finally {
            synchronized (lock) {
                this.initializing = false;
            }
        }
    }

    private IllegalStateException initializationFailure(String message) {
        AELog.error("%s: %s", message, this.network);
        return new IllegalStateException(message + ": " + this.network);
    }

    private ProviderState register(ICraftingProvider provider) {
        this.registrationsRevision++;
        var registration = new ProviderState(provider, null, null, 0, this.nextProviderOrder++);
        if (this.initialized) {
            registration.captured = captureProvider(provider).withOrder(registration.order);
        }
        return registration;
    }

    public void addProvider(IGridNode node) {
        beginMutation();
        var provider = node.getService(ICraftingProvider.class);
        if (provider != null) {
            synchronized (lock) {
                if (craftingProviders.containsKey(node)) {
                    throw new IllegalArgumentException("Duplicate crafting provider registration for node " + node);
                }
            }

            var state = register(provider);
            synchronized (lock) {
                if (craftingProviders.containsKey(node)) {
                    throw new IllegalArgumentException("Duplicate crafting provider registration for node " + node);
                }
                if (state.captured != null) {
                    state.captured.mount(this);
                }
                craftingProviders.put(node, state);
                if (state.captured != null && state.captured.hasContents()) {
                    markModified();
                }
            }
        }
    }

    public void addProvider(ICraftingProvider provider) {
        beginMutation();
        synchronized (lock) {
            ensureGlobalProviderIsNotRegistered(provider);
        }

        var state = register(provider);
        synchronized (lock) {
            ensureGlobalProviderIsNotRegistered(provider);
            if (state.captured != null) {
                state.captured.mount(this);
            }
            globalProviders.put(provider, state);
            if (state.captured != null && state.captured.hasContents()) {
                markModified();
            }
        }
    }

    private void ensureGlobalProviderIsNotRegistered(ICraftingProvider provider) {
        if (globalProviders.containsKey(provider)) {
            throw new IllegalArgumentException("Duplicate crafting provider registration for " + provider);
        }
    }

    public void removeProvider(IGridNode node) {
        beginMutation();
        synchronized (lock) {
            var state = craftingProviders.remove(node);
            if (state != null) {
                this.registrationsRevision++;
                if (state.captured != null) {
                    state.captured.unmount(this);
                }
                if (state.captured != null && state.captured.hasContents()) {
                    markModified();
                }
            }
        }
    }

    public void removeProvider(ICraftingProvider provider) {
        beginMutation();
        synchronized (lock) {
            var state = this.globalProviders.remove(provider);
            if (state != null) {
                this.registrationsRevision++;
                if (state.captured != null) {
                    state.captured.unmount(this);
                }
                if (state.captured != null && state.captured.hasContents()) {
                    markModified();
                }
            }
        }
    }

    /**
     * Refreshes one registration only when its captured contents changed.
     */
    public void refreshProvider(IGridNode node) {
        beginMutation();
        var provider = node.getService(ICraftingProvider.class);
        if (provider == null) {
            removeProvider(node);
            return;
        }
        if (!this.initialized) {
            synchronized (lock) {
                var previous = this.craftingProviders.get(node);
                this.craftingProviders.put(node, new ProviderState(provider, null, null, 0,
                    previous == null ? nextProviderOrder++ : previous.order));
                this.registrationsRevision++;
            }
            return;
        }
        var captured = captureProvider(provider);
        synchronized (lock) {
            var previous = this.craftingProviders.get(node);
            if (previous != null && previous.captured.sameContents(captured)) {
                return;
            }
            if (previous != null) {
                previous.captured.unmount(this);
            }
            var replacement = new ProviderState(provider, null, null, 0,
                previous == null ? nextProviderOrder++ : previous.order);
            replacement.captured = captured.withOrder(replacement.order);
            replacement.captured.mount(this);
            this.craftingProviders.put(node, replacement);
            if (captured.hasContents() || previous != null && previous.captured.hasContents()) {
                markModified();
            }
        }
    }

    /**
     * Pure revision query; never initializes or reads providers.
     */
    public long getCraftablesRevision() {
        return this.revision;
    }

    /**
     * Refreshes an existing global provider while preserving an unchanged registration.
     */
    public void refreshProvider(ICraftingProvider provider) {
        beginMutation();
        if (!this.initialized) {
            synchronized (lock) {
                this.globalProviders.computeIfAbsent(provider,
                    ignored -> new ProviderState(provider, null, null, 0, nextProviderOrder++));
                this.registrationsRevision++;
            }
            return;
        }
        var captured = captureProvider(provider);
        synchronized (lock) {
            var previous = this.globalProviders.get(provider);
            if (previous != null && previous.captured.sameContents(captured)) {
                return;
            }
            if (previous != null) {
                previous.captured.unmount(this);
            }
            var replacement = new ProviderState(provider, null, null, 0,
                previous == null ? nextProviderOrder++ : previous.order);
            replacement.captured = captured.withOrder(replacement.order);
            replacement.captured.mount(this);
            this.globalProviders.put(provider, replacement);
            if (captured.hasContents() || previous != null && previous.captured.hasContents()) {
                markModified();
            }
        }
    }

    public Set<AEKey> getCraftables(AEKeyFilter filter) {
        ensureInitialized();
        Set<AEKey> craftableKeys;
        Set<AEKey> emittableKeys;
        synchronized (lock) {
            refreshKeySnapshots();
            craftableKeys = this.craftableKeysSnapshot;
            emittableKeys = this.emittableKeysSnapshot;
        }

        var result = new ObjectOpenHashSet<AEKey>();
        for (var stack : craftableKeys) {
            if (filter.matches(stack)) {
                result.add(stack);
            }
        }
        for (var stack : emittableKeys) {
            if (filter.matches(stack)) {
                result.add(stack);
            }
        }
        return result;
    }

    public Set<AEKey> getCraftableKeys() {
        ensureInitialized();
        synchronized (lock) {
            refreshKeySnapshots();
            return this.craftableKeysSnapshot;
        }
    }

    public Set<AEKey> getEmittableKeys() {
        ensureInitialized();
        synchronized (lock) {
            refreshKeySnapshots();
            return this.emittableKeysSnapshot;
        }
    }

    public boolean isKnownPattern(AEItemKey patternDefinition) {
        ensureInitialized();
        synchronized (lock) {
            return knownPatternDefinitions.containsKey(patternDefinition);
        }
    }

    public Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
        ensureInitialized();
        synchronized (lock) {
            var patterns = this.craftableItems.get(whatToCraft);
            if (patterns != null) {
                return patterns.getSortedPatterns();
            }
            return Collections.emptyList();
        }
    }

    @Nullable
    public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
        ensureInitialized();
        ObjectList<AEKey> fuzzyCandidates = new ObjectArrayList<>();
        synchronized (lock) {
            for (var fuzzy : craftableItemsList.findFuzzy(whatToCraft, FuzzyMode.IGNORE_ALL)) {
                fuzzyCandidates.add(fuzzy.getKey());
            }
        }
        for (int i = 0, size = fuzzyCandidates.size(); i < size; i++) {
            var fuzzy = fuzzyCandidates.get(i);
            if (filter.matches(fuzzy)) {
                return fuzzy;
            }
        }
        return null;
    }

    public boolean canEmitFor(AEKey someItem) {
        ensureInitialized();
        synchronized (lock) {
            return this.emitableItems.containsKey(someItem);
        }
    }

    public Iterable<ICraftingProvider> getMediums(IPatternDetails key) {
        ensureInitialized();
        synchronized (lock) {
            var mediumList = this.craftingMethods.get(key);
            return Objects.requireNonNullElse(mediumList, Collections.emptyList());
        }
    }

    public List<ICraftingProvider> getMediumsSnapshot(IPatternDetails key) {
        ensureInitialized();
        synchronized (lock) {
            var mediumList = this.craftingMethods.get(key);
            return mediumList == null ? List.of() : mediumList.snapshot();
        }
    }

    private void markModified() {
        keySnapshotsDirty = true;
        revision = Math.incrementExact(revision);
    }

    private void refreshKeySnapshots() {
        if (!keySnapshotsDirty) {
            return;
        }
        if (!craftableKeysSnapshot.equals(craftableItems.keySet())
            || !emittableKeysSnapshot.equals(emitableItems.keySet())) {
            craftableKeysSnapshot = Set.copyOf(craftableItems.keySet());
            emittableKeysSnapshot = Set.copyOf(emitableItems.keySet());
        }
        keySnapshotsDirty = false;
    }

    public long getRevision() {
        return revision;
    }

    private void beginMutation() {
        if (Thread.currentThread() != this.ownerThread) {
            throw initializationFailure("Pattern registration changed off the owning server thread");
        }
        if (this.collecting) {
            this.registrationsRevision++;
            throw initializationFailure("Reentrant provider mutation while collecting patterns");
        }
    }

    private ProviderState captureProvider(ICraftingProvider provider) {
        long expected = this.registrationsRevision;
        this.collecting = true;
        try {
            var result = ProviderState.capture(provider);
            if (expected != this.registrationsRevision) {
                throw initializationFailure("Provider registrations changed while collecting patterns");
            }
            return result;
        } catch (RuntimeException | Error exception) {
            AELog.error(exception, "Failed to collect crafting provider for network " + this.network);
            throw exception;
        } finally {
            this.collecting = false;
        }
    }

    private static class ProviderState {
        private final ObjectList<PatternOccurrence> patterns;
        private final ICraftingProvider provider;
        private final ObjectSet<AEKey> emitableItems;
        @Nullable
        private ProviderState captured;
        private final int priority;
        private final long order;
        private ObjectList<ProviderHandle> handles;

        private ProviderState(ICraftingProvider provider, ObjectSet<AEKey> emitableItems,
                              ObjectList<PatternOccurrence> patterns, int priority, long order) {
            this.provider = provider;
            this.emitableItems = emitableItems;
            this.patterns = patterns;
            this.priority = priority;
            this.order = order;
        }

        private static ProviderState capture(ICraftingProvider provider) {
            var emitableItems = new ObjectOpenHashSet<>(provider.getEmitableItems());
            var availablePatterns = provider.getAvailablePatterns();
            var patterns = new ObjectArrayList<PatternOccurrence>(availablePatterns.size());
            if (availablePatterns instanceof RandomAccess) {
                for (int i = 0, size = availablePatterns.size(); i < size; i++) {
                    patterns.add(capturePattern(availablePatterns.get(i)));
                }
            } else {
                for (var pattern : availablePatterns) {
                    patterns.add(capturePattern(pattern));
                }
            }
            return new ProviderState(provider, emitableItems, patterns, provider.getPatternPriority(), -1);
        }

        private static PatternOccurrence capturePattern(IPatternDetails pattern) {
            return new PatternOccurrence(Objects.requireNonNull(pattern), Objects.requireNonNull(pattern.getDefinition()),
                Objects.requireNonNull(pattern.getPrimaryOutput().what()));
        }

        private boolean hasContents() {
            return !this.patterns.isEmpty() || !this.emitableItems.isEmpty();
        }

        private ProviderState withOrder(long order) {
            return new ProviderState(provider, emitableItems, patterns, priority, order);
        }

        private boolean sameContents(ProviderState other) {
            if (this.provider != other.provider || this.priority != other.priority
                || !this.emitableItems.equals(other.emitableItems) || this.patterns.size() != other.patterns.size()) {
                return false;
            }
            for (int i = 0; i < this.patterns.size(); i++) {
                // Replacing a pattern object may carry new implementation state despite an equal definition.
                if (this.patterns.get(i).pattern != other.patterns.get(i).pattern
                    || !this.patterns.get(i).equals(other.patterns.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private void mount(NetworkCraftingProviders methods) {
            this.handles = new ObjectArrayList<>(patterns.size());
            for (var emitable : emitableItems) {
                methods.emitableItems.merge(emitable, 1, Integer::sum);
            }
            for (int i = 0; i < patterns.size(); i++) {
                var occurrence = patterns.get(i);
                var pattern = occurrence.pattern;
                methods.knownPatternDefinitions.merge(occurrence.definition, 1, Integer::sum);

                // output -> pattern (for simulation)
                methods.craftableItemsList.add(occurrence.primaryOutput, 1);

                var patternsForKey = methods.craftableItems.computeIfAbsent(occurrence.primaryOutput,
                    ignored -> new PatternsForKey());
                patternsForKey.patterns.add(new PatternInfo(pattern, this, i));
                patternsForKey.needsSorting = true;
                patternsForKey.sortedPatterns = List.of();

                // pattern -> method (for execution)
                handles.add(methods.craftingMethods.computeIfAbsent(pattern, ignored -> methods.new CraftingProviderList())
                                                   .add(provider, this.order, i));
            }
        }

        private void unmount(NetworkCraftingProviders methods) {
            for (var emitable : emitableItems) {
                methods.emitableItems.compute(emitable, (ignored, cnt) -> cnt == null || cnt <= 1 ? null : cnt - 1);
            }
            for (int i = 0; i < patterns.size(); i++) {
                final int patternIndex = i;
                var occurrence = patterns.get(i);
                var pattern = occurrence.pattern;
                var handle = handles.get(i);
                methods.knownPatternDefinitions.compute(occurrence.definition,
                    (ignored, cnt) -> cnt == null || cnt <= 1 ? null : cnt - 1);

                methods.craftableItemsList.remove(occurrence.primaryOutput, 1);

                methods.craftableItems.computeIfPresent(occurrence.primaryOutput, (ignored, patternsForKey) -> {
                    patternsForKey.patterns.remove(new PatternInfo(pattern, this, patternIndex));
                    patternsForKey.needsSorting = true;
                    patternsForKey.sortedPatterns = List.of();
                    return patternsForKey.patterns.isEmpty() ? null : patternsForKey;
                });

                methods.craftingMethods.computeIfPresent(pattern, (ignored, list) -> {
                    list.remove(handle);
                    return list.isEmpty() ? null : list;
                });
            }
            handles.clear();
        }
    }

    private static final class ProviderHandle {
        private static final Comparator<ProviderHandle> ORDER = Comparator
            .comparingLong((ProviderHandle handle) -> handle.order)
            .thenComparingInt(handle -> handle.occurrence);
        private final NetworkCraftingProviders.CraftingProviderList owner;
        private final ICraftingProvider provider;
        private final long order;
        private final int occurrence;
        @Nullable
        private ProviderHandle previous;
        @Nullable
        private ProviderHandle next;
        private boolean linked = true;

        private ProviderHandle(NetworkCraftingProviders.CraftingProviderList owner, ICraftingProvider provider,
                               @Nullable ProviderHandle previous, long order, int occurrence) {
            this.owner = owner;
            this.provider = provider;
            this.previous = previous;
            this.order = order;
            this.occurrence = occurrence;
        }
    }

    private record PatternOccurrence(IPatternDetails pattern, AEItemKey definition, AEKey primaryOutput) {
    }

    private static class PatternsForKey {
        private final ObjectSet<PatternInfo> patterns = new ObjectOpenHashSet<>();
        private List<IPatternDetails> sortedPatterns = Collections.emptyList();
        private boolean needsSorting = false;

        private void sortPatterns() {
            var order = Comparator
                .comparingInt((PatternInfo pi) -> pi.state().priority).reversed()
                .thenComparingLong(pi -> pi.state().order)
                .thenComparingInt(PatternInfo::occurrence);
            var bestOccurrences = new Object2ObjectOpenHashMap<IPatternDetails, PatternInfo>();
            for (var occurrence : this.patterns) {
                var previous = bestOccurrences.get(occurrence.pattern());
                if (previous == null || order.compare(occurrence, previous) < 0) {
                    bestOccurrences.put(occurrence.pattern(), occurrence);
                }
            }
            var sortedPatternInfos = new ObjectArrayList<>(bestOccurrences.values());
            sortedPatternInfos.sort(order);

            var deduplicatedPatterns = new ObjectArrayList<IPatternDetails>(sortedPatternInfos.size());
            for (int i = 0; i < sortedPatternInfos.size(); i++) {
                var patternInfo = sortedPatternInfos.get(i);
                var pattern = patternInfo.pattern();
                deduplicatedPatterns.add(pattern);
            }

            sortedPatterns = List.copyOf(deduplicatedPatterns);
            needsSorting = false;
        }

        private List<IPatternDetails> getSortedPatterns() {
            if (needsSorting) {
                sortPatterns();
            }
            return sortedPatterns;
        }
    }

    private record PatternInfo(IPatternDetails pattern, ProviderState state, int occurrence) {
    }

    private final class CraftingProviderList implements Iterable<ICraftingProvider> {
        @Nullable
        private ProviderHandle first;
        @Nullable
        private ProviderHandle last;
        @Nullable
        private ProviderHandle nextProvider;
        private volatile List<ICraftingProvider> snapshot = List.of();
        private int size;
        private boolean snapshotDirty;
        private boolean orderDirty;

        private ProviderHandle add(ICraftingProvider provider, long order, int occurrence) {
            var handle = new ProviderHandle(this, provider, last, order, occurrence);
            if (last == null) {
                first = handle;
            } else {
                orderDirty |= ProviderHandle.ORDER.compare(last, handle) > 0;
                last.next = handle;
            }
            last = handle;
            size++;
            changed();
            return handle;
        }

        private void remove(ProviderHandle handle) {
            if (handle.owner != this || !handle.linked) {
                throw new IllegalStateException("Crafting provider handle is not mounted in this list");
            }

            if (handle.previous == null) {
                first = handle.next;
            } else {
                handle.previous.next = handle.next;
            }
            if (handle.next == null) {
                last = handle.previous;
            } else {
                handle.next.previous = handle.previous;
            }
            handle.linked = false;
            handle.previous = null;
            handle.next = null;
            size--;
            changed();
        }

        private void changed() {
            nextProvider = first;
            snapshotDirty = true;
            snapshot = List.of();
        }

        @Override
        public Iterator<ICraftingProvider> iterator() {
            synchronized (lock) {
                ensureOrder();
                return new Iterator<>() {
                    private int remaining = size;

                    @Override
                    public boolean hasNext() {
                        synchronized (lock) {
                            return remaining > 0 && first != null;
                        }
                    }

                    @Override
                    public ICraftingProvider next() {
                        synchronized (lock) {
                            if (remaining <= 0 || first == null) {
                                throw new NoSuchElementException();
                            }

                            var result = nextProvider == null ? first : nextProvider;
                            nextProvider = result.next == null ? first : result.next;
                            remaining--;
                            return result.provider;
                        }
                    }
                };
            }
        }

        private List<ICraftingProvider> snapshot() {
            if (snapshotDirty) {
                ensureOrder();
                var newSnapshot = new ObjectArrayList<ICraftingProvider>(size);
                var handle = first;
                while (handle != null) {
                    newSnapshot.add(handle.provider);
                    handle = handle.next;
                }
                snapshot = List.copyOf(newSnapshot);
                snapshotDirty = false;
            }
            return snapshot;
        }

        private void ensureOrder() {
            if (!orderDirty) {
                return;
            }
            // Refreshes may reinsert an older registration. Sort once when consumed, never on every mutation.
            var handles = new ObjectArrayList<ProviderHandle>(size);
            for (var handle = first; handle != null; handle = handle.next) {
                handles.add(handle);
            }
            handles.sort(ProviderHandle.ORDER);
            first = handles.isEmpty() ? null : handles.getFirst();
            last = handles.isEmpty() ? null : handles.getLast();
            for (int i = 0; i < handles.size(); i++) {
                var handle = handles.get(i);
                handle.previous = i == 0 ? null : handles.get(i - 1);
                handle.next = i + 1 == handles.size() ? null : handles.get(i + 1);
            }
            nextProvider = first;
            orderDirty = false;
        }

        private boolean isEmpty() {
            return size == 0;
        }
    }
}
