package ae2.me.service.helpers;

import ae2.api.config.FuzzyMode;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.AEKeyFilter;
import ae2.hooks.ticking.TickHandler;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps track of the crafting patterns in the network, and related information.
 */
public class NetworkCraftingProviders {
    /**
     * Tracks the provider state for each grid node that provides auto-crafting to the network.
     */
    private final Reference2ObjectMap<IGridNode, ProviderState> craftingProviders = new Reference2ObjectOpenHashMap<>();
    /**
     * Tracks state for crafting providers that may be provided without a grid node, such as by other grid services.
     */
    private final ObjectList<ProviderState> globalProviders = new ObjectArrayList<>();
    private final Object2ObjectMap<IPatternDetails, CraftingProviderList> craftingMethods = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<AEKey, PatternsForKey> craftableItems = new Object2ObjectOpenHashMap<>();
    private final Object2IntOpenHashMap<AEItemKey> knownPatternDefinitions = new Object2IntOpenHashMap<>();
    /**
     * Used for looking up craftable alternatives using fuzzy search (i.e. ignore NBT).
     */
    private final KeyCounter craftableItemsList = new KeyCounter();
    private final Object2IntOpenHashMap<AEKey> emitableItems = new Object2IntOpenHashMap<>();

    private final Set<AEKey> craftableKeys = Collections.unmodifiableSet(craftableItems.keySet());
    private final Set<AEKey> emittableKeys = Collections.unmodifiableSet(emitableItems.keySet());
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    private long nextProviderOrder = 0;

    public NetworkCraftingProviders() {
        this.emitableItems.defaultReturnValue(0);
        this.knownPatternDefinitions.defaultReturnValue(0);
    }

    public void addProvider(IGridNode node) {
        var provider = node.getService(ICraftingProvider.class);
        if (provider != null) {
            if (craftingProviders.containsKey(node)) {
                throw new IllegalArgumentException("Duplicate crafting provider registration for node " + node);
            }
            var state = new ProviderState(provider, nextProviderOrder++);
            state.mount(this);
            craftingProviders.put(node, state);
            setLastModifiedOnTick();
        }
    }

    public void addProvider(ICraftingProvider provider) {
        for (var state : globalProviders) {
            if (state.provider == provider) {
                throw new IllegalArgumentException("Duplicate crafting provider registration for " + provider);
            }
        }

        var state = new ProviderState(provider, nextProviderOrder++);
        state.mount(this);
        globalProviders.add(state);
        setLastModifiedOnTick();
    }

    public void removeProvider(IGridNode node) {
        var provider = node.getService(ICraftingProvider.class);
        if (provider != null) {
            var state = craftingProviders.remove(node);
            if (state != null) {
                state.unmount(this);
                setLastModifiedOnTick();
            }
        }
    }

    public void removeProvider(ICraftingProvider provider) {
        var it = this.globalProviders.iterator();
        while (it.hasNext()) {
            var state = it.next();
            if (state.provider == provider) {
                it.remove();
                state.unmount(this);
                setLastModifiedOnTick();
            }
        }
    }

    public Set<AEKey> getCraftables(AEKeyFilter filter) {
        var result = new ObjectOpenHashSet<AEKey>();

        // add craftable items!
        for (var stack : this.craftableItems.keySet()) {
            if (filter.matches(stack)) {
                result.add(stack);
            }
        }

        for (var stack : this.emitableItems.keySet()) {
            if (filter.matches(stack)) {
                result.add(stack);
            }
        }

        return result;
    }

    public Set<AEKey> getCraftableKeys() {
        return craftableKeys;
    }

    public Set<AEKey> getEmittableKeys() {
        return emittableKeys;
    }

    public boolean isKnownPattern(AEItemKey patternDefinition) {
        return knownPatternDefinitions.containsKey(patternDefinition);
    }

    public Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
        var patterns = this.craftableItems.get(whatToCraft);
        if (patterns != null) {
            return patterns.getSortedPatterns();
        }
        return Collections.emptyList();
    }

    @Nullable
    public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
        for (var fuzzy : craftableItemsList.findFuzzy(whatToCraft, FuzzyMode.IGNORE_ALL)) {
            if (filter.matches(fuzzy.getKey())) {
                return fuzzy.getKey();
            }
        }
        return null;
    }

    public boolean canEmitFor(AEKey someItem) {
        return this.emitableItems.containsKey(someItem);
    }

    public Iterable<ICraftingProvider> getMediums(IPatternDetails key) {
        var mediumList = this.craftingMethods.get(key);
        return Objects.requireNonNullElse(mediumList, Collections.emptyList());
    }

    public List<ICraftingProvider> getMediumsSnapshot(IPatternDetails key) {
        var mediumList = this.craftingMethods.get(key);
        return mediumList == null ? List.of() : mediumList.snapshot();
    }

    private void setLastModifiedOnTick() {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    }

    /**
     * @see TickHandler#getCurrentTick()
     */
    public long getLastModifiedOnTick() {
        return lastModifiedOnTick;
    }

    private static class CraftingProviderList implements Iterable<ICraftingProvider> {
        private final ObjectList<ICraftingProvider> providers = new ObjectArrayList<>();
        /**
         * Cycling iterator for round-robin. Has to be refreshed after every addition or removal to providers to prevent
         * CMEs.
         */
        private Iterator<ICraftingProvider> cycleIterator = Iterators.cycle(providers);

        private void add(ICraftingProvider provider) {
            providers.add(provider);
            cycleIterator = Iterators.cycle(providers);
        }

        private void remove(ICraftingProvider provider) {
            providers.remove(provider);
            cycleIterator = Iterators.cycle(providers);
        }

        @Override
        @NotNull
        public Iterator<ICraftingProvider> iterator() {
            return Iterators.limit(cycleIterator, providers.size());
        }

        private List<ICraftingProvider> snapshot() {
            return List.copyOf(providers);
        }
    }

    private static class ProviderState {
        private final ICraftingProvider provider;
        private final ObjectSet<AEKey> emitableItems;
        private final ObjectList<IPatternDetails> patterns;
        private final int priority;
        private final long order;

        private ProviderState(ICraftingProvider provider, long order) {
            this.provider = provider;
            this.emitableItems = new ObjectOpenHashSet<>(provider.getEmitableItems());
            this.patterns = new ObjectArrayList<>(provider.getAvailablePatterns());
            this.priority = provider.getPatternPriority();
            this.order = order;
        }

        private void mount(NetworkCraftingProviders methods) {
            for (var emitable : emitableItems) {
                methods.emitableItems.merge(emitable, 1, Integer::sum);
            }
            for (var pattern : patterns) {
                methods.knownPatternDefinitions.merge(pattern.getDefinition(), 1, Integer::sum);

                // output -> pattern (for simulation)
                var primaryOutput = pattern.getPrimaryOutput();

                methods.craftableItemsList.add(primaryOutput.what(), 1);

                var patternsForKey = methods.craftableItems.computeIfAbsent(primaryOutput.what(),
                    ignored -> new PatternsForKey());
                patternsForKey.patterns.add(new PatternInfo(pattern, this));
                patternsForKey.needsSorting = true;

                // pattern -> method (for execution)
                methods.craftingMethods.computeIfAbsent(pattern, ignored -> new CraftingProviderList()).add(provider);
            }
        }

        private void unmount(NetworkCraftingProviders methods) {
            for (var emitable : emitableItems) {
                methods.emitableItems.compute(emitable, (ignored, cnt) -> cnt == null || cnt <= 1 ? null : cnt - 1);
            }
            for (var pattern : patterns) {
                methods.knownPatternDefinitions.compute(pattern.getDefinition(),
                    (ignored, cnt) -> cnt == null || cnt <= 1 ? null : cnt - 1);

                var primaryOutput = pattern.getPrimaryOutput();

                methods.craftableItemsList.remove(primaryOutput.what(), 1);

                methods.craftableItems.computeIfPresent(primaryOutput.what(), (ignored, patternsForKey) -> {
                    patternsForKey.patterns.remove(new PatternInfo(pattern, this));
                    patternsForKey.needsSorting = true;
                    return patternsForKey.patterns.isEmpty() ? null : patternsForKey;
                });

                methods.craftingMethods.computeIfPresent(pattern, (ignored, list) -> {
                    list.remove(provider);
                    return list.providers.isEmpty() ? null : list;
                });
            }
        }
    }

    private static class PatternsForKey {
        private final ObjectSet<PatternInfo> patterns = new ObjectOpenHashSet<>();
        private List<IPatternDetails> sortedPatterns = Collections.emptyList();
        private boolean needsSorting = false;

        private void sortPatterns() {
            var sortedPatternInfos = new ObjectArrayList<>(patterns);
            sortedPatternInfos.sort(Comparator
                .comparingInt((PatternInfo pi) -> pi.state().priority).reversed()
                .thenComparingLong(pi -> pi.state().order));

            var seenPatterns = new ObjectOpenHashSet<IPatternDetails>(sortedPatternInfos.size());
            var deduplicatedPatterns = new ObjectArrayList<IPatternDetails>(sortedPatternInfos.size());
            for (var patternInfo : sortedPatternInfos) {
                var pattern = patternInfo.pattern();
                if (seenPatterns.add(pattern)) {
                    deduplicatedPatterns.add(pattern);
                }
            }

            sortedPatterns = ObjectLists.unmodifiable(deduplicatedPatterns);
            needsSorting = false;
        }

        private List<IPatternDetails> getSortedPatterns() {
            if (needsSorting) {
                sortPatterns();
            }
            return sortedPatterns;
        }
    }

    private record PatternInfo(IPatternDetails pattern, ProviderState state) {
    }
}
