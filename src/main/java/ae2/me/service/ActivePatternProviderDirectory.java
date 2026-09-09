package ae2.me.service;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.VersionedInternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.IGridServiceProvider;
import ae2.container.me.patternencode.ProviderDirectoryPage;
import ae2.container.me.patternencode.ProviderPageLimits;
import ae2.core.AELog;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.parts.AEBasePart;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal Grid directory for queryable pattern providers.
 *
 * <p>The directory is the sole owner of cached live {@link PatternContainer} instances. Consumers that need to retain
 * a provider across a request use {@link ProviderKey}; page and session data use {@link ProviderDescriptor}, neither
 * of which exposes a live machine object.</p>
 */
public final class ActivePatternProviderDirectory implements IGridService, IGridServiceProvider {
    private static final long WARNING_INTERVAL_NANOS = 10_000_000_000L;
    private static final AtomicLong LAST_DIRECTORY_TEXT_WARNING = new AtomicLong(Long.MIN_VALUE);

    private static final Comparator<ProviderReference> PROVIDER_REFERENCE_ORDER = Comparator
        .comparingInt(ProviderReference::dimension)
        .thenComparingLong(ProviderReference::pos)
        .thenComparingInt(ProviderReference::side);
    private static final Comparator<ProviderDescriptor> PROVIDER_DESCRIPTOR_ORDER = Comparator
        .comparingLong(ProviderDescriptor::sortBy)
        .thenComparing(ProviderDescriptor::reference, Comparator.nullsLast(PROVIDER_REFERENCE_ORDER))
        .thenComparing(ProviderDescriptor::providerName)
        .thenComparing(ProviderDescriptor::providerClassName)
        .thenComparingLong(descriptor -> descriptor.providerKey().serial);

    private final Reference2ObjectOpenHashMap<IGridNode, Candidate> candidates = new Reference2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<PatternContainer> candidateNodeCounts = new Reference2IntOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<PatternContainer, ProviderKey> providerKeysByContainer =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<ProviderKey, PatternContainer> providersByKey =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<PatternContainer, EmptySlotCount> emptySlotCounts =
        new Reference2ObjectOpenHashMap<>();
    private long providerSetRevision;
    private long observedProviderSetRevision = Long.MIN_VALUE;
    private long nextProviderKey;
    private List<PatternContainer> activeProviders = List.of();

    public ActivePatternProviderDirectory(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
    }

    /** Returns the current server-only cache of active provider machines. */
    public List<PatternContainer> getActiveProviders() {
        refreshActiveProvidersIfNeeded();
        return this.activeProviders;
    }

    private static int countEmptySlots(InternalInventory inventory, int size) {
        int emptySlots = 0;
        for (int slot = 0; slot < size; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    /** Resolves a server-only key while its provider remains active and selectable. */
    @Nullable
    public PatternContainer resolveSelectableProvider(ProviderKey providerKey) {
        Objects.requireNonNull(providerKey, "providerKey");
        refreshActiveProvidersIfNeeded();
        PatternContainer container = this.providersByKey.get(providerKey);
        return container != null && isSelectableProvider(container) ? container : null;
    }

    /** Returns whether a live provider remains active and eligible for Provider Selection. */
    public boolean isSelectableActiveProvider(PatternContainer container) {
        Objects.requireNonNull(container, "container");
        refreshActiveProvidersIfNeeded();
        return this.providerKeysByContainer.containsKey(container) && isSelectableProvider(container);
    }

    /**
     * Creates freshly evaluated Provider Selection descriptors from the active-provider cache.
     *
     * <p>Selection metadata such as available slots and display names may change without changing Grid membership, so
     * these immutable descriptors are rebuilt on each query while discovery itself remains revision-cached.</p>
     */
    public List<ProviderDescriptor> getSelectableProviderDescriptors() {
        refreshActiveProvidersIfNeeded();

        List<ProviderDescriptor> descriptors = new ArrayList<>();
        for (int i = 0, size = this.activeProviders.size(); i < size; i++) {
            var container = this.activeProviders.get(i);
            if (isSelectableProvider(container)) {
                descriptors.add(createDescriptor(container, this.providerKeysByContainer.get(container)));
            }
        }
        descriptors.sort(PROVIDER_DESCRIPTOR_ORDER);
        return List.copyOf(descriptors);
    }

    private void refreshActiveProvidersIfNeeded() {
        if (this.providerSetRevision != this.observedProviderSetRevision) {
            refreshActiveProviders();
            this.observedProviderSetRevision = this.providerSetRevision;
        }
    }

    private void refreshActiveProviders() {
        List<PatternContainer> providers = new ArrayList<>();
        Set<PatternContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var candidate : this.candidates.values()) {
            if (candidate.active && seen.add(candidate.container)) {
                providers.add(candidate.container);
            }
        }

        this.providerKeysByContainer.keySet().removeIf(container -> !seen.contains(container));
        this.providersByKey.clear();
        for (int i = 0; i < providers.size(); i++) {
            PatternContainer provider = providers.get(i);
            ProviderKey key = this.providerKeysByContainer.get(provider);
            if (key == null) {
                key = new ProviderKey(this.nextProviderKey++);
                this.providerKeysByContainer.put(provider, key);
            }
            this.providersByKey.put(key, provider);
        }
        this.activeProviders = List.copyOf(providers);
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        if (!(gridNode.getOwner() instanceof PatternContainer container)) {
            return;
        }
        if (this.candidates.putIfAbsent(gridNode, new Candidate(container, gridNode.isOnline())) != null) {
            throw new IllegalStateException("Pattern container node was added to the directory twice: " + gridNode);
        }
        this.candidateNodeCounts.addTo(container, 1);
        markProviderSetChanged();
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        Candidate removed = this.candidates.remove(gridNode);
        if (removed != null) {
            if (this.candidateNodeCounts.addTo(removed.container, -1) == 1) {
                this.candidateNodeCounts.removeInt(removed.container);
                this.emptySlotCounts.remove(removed.container);
                ProviderKey key = this.providerKeysByContainer.remove(removed.container);
                if (key != null) {
                    this.providersByKey.remove(key);
                }
            }
            markProviderSetChanged();
        }
    }

    /**
     * Refreshes one candidate after its owning node receives a state notification.
     *
     * <p>Unknown nodes are ignored, which keeps notifications for unrelated grid machines from querying their
     * active state.</p>
     */
    // GridNode is in ae2.me, so this entry point must remain public to cross the package boundary.
    public void onNodeStateChanged(IGridNode gridNode) {
        Objects.requireNonNull(gridNode, "gridNode");
        Candidate candidate = this.candidates.get(gridNode);
        if (candidate == null) {
            return;
        }

        boolean active = gridNode.isOnline();
        if (candidate.active != active) {
            candidate.active = active;
            markProviderSetChanged();
        }
    }

    long getProviderSetRevision() {
        return this.providerSetRevision;
    }

    private void markProviderSetChanged() {
        this.providerSetRevision = Math.incrementExact(this.providerSetRevision);
        this.activeProviders = List.of();
    }

    private static boolean isSelectableProvider(PatternContainer container) {
        return container.isVisibleInTerminal() && !container.isAssemblerPatternContainer();
    }

    private ProviderDescriptor createDescriptor(PatternContainer container, ProviderKey providerKey) {
        if (providerKey == null) {
            throw new IllegalStateException("Active pattern provider has no directory key");
        }
        ProviderLocation location = getProviderLocation(container);
        return new ProviderDescriptor(providerKey, container.getTerminalSortOrder(), container.getTerminalGroup(),
            getEmptySlots(container), !container.isAssemblerPatternContainer(),
            location == null ? null : new ProviderReference(location.dimensionId(), location.pos(), location.side()),
            location != null, location == null ? 0 : location.dimensionId(), location == null ? 0L : location.pos(),
            location == null ? -1 : location.side(), getProviderName(container), container.getClass().getName());
    }

    /**
     * Counts empty slots only on demand. Versioned inventories share the result across terminal sessions until
     * identity, size or contents version changes. Unversioned inventories are read on every request.
     * Must be called on the server thread for a provider still registered in this directory.
     */
    public int getEmptySlots(PatternContainer container) {
        if (!this.candidateNodeCounts.containsKey(container)) {
            throw new IllegalArgumentException("Cannot query slots of an unregistered pattern provider");
        }
        InternalInventory inventory = container.getTerminalPatternInventory();
        int size = inventory.size();
        if (inventory instanceof VersionedInternalInventory versioned) {
            long version = versioned.getContentsVersion();
            var cached = this.emptySlotCounts.get(container);
            if (cached != null && cached.inventory == inventory && cached.size == size && cached.version == version) {
                return cached.emptySlots;
            }
            int emptySlots = countEmptySlots(inventory, size);
            this.emptySlotCounts.put(container, new EmptySlotCount(inventory, size, version, emptySlots));
            return emptySlots;
        }
        this.emptySlotCounts.remove(container);
        return countEmptySlots(inventory, size);
    }

    private static final class Candidate {
        private final PatternContainer container;
        private boolean active;

        private Candidate(PatternContainer container, boolean active) {
            this.container = container;
            this.active = active;
        }
    }

    private record EmptySlotCount(InternalInventory inventory, int size, long version, int emptySlots) {
    }

    private static String getProviderName(PatternContainer container) {
        ITextComponent name = container.getTerminalGroup().name();
        return name == null ? container.getClass().getSimpleName() : name.getUnformattedText();
    }

    /** Builds one bounded client page entry without exposing the underlying provider. */
    public static ProviderDirectoryPage.Entry createDirectoryPageEntry(long providerEntryId,
                                                                        ProviderDescriptor descriptor,
                                                                        PatternProviderMappingData mappingData,
                                                                        String query) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(mappingData, "mappingData");
        ProviderReference reference = descriptor.reference();
        String providerName = limitPageText("provider-name", descriptor.providerName(), providerEntryId,
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF16_LENGTH,
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF8_BYTES);
        int recipeTypeCount = reference == null ? 0 : mappingData.getRecipeTypeCount(reference);
        List<String> recipeTypeUids = reference == null ? List.of()
            : mappingData.getRecipeTypePreview(reference, query.trim().toLowerCase(Locale.ROOT));
        return new ProviderDirectoryPage.Entry(providerEntryId, descriptor.group().icon(), providerName,
            descriptor.emptySlots(), recipeTypeCount, recipeTypeUids, descriptor.acceptsProcessingPatterns(),
            descriptor.hasLocation(), descriptor.locationDimension(), descriptor.locationPos(),
            descriptor.locationSide());
    }

    /** Returns whether a descriptor matches a Provider Selection directory query. */
    public static boolean matchesDirectoryQuery(ProviderDescriptor descriptor, PatternProviderMappingData mappingData,
                                                String query) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(mappingData, "mappingData");
        String normalizedQuery = Objects.requireNonNull(query, "query").trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty() || descriptor.providerName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
            || formatLocationSearchText(descriptor).contains(normalizedQuery)) {
            return true;
        }
        ProviderReference reference = descriptor.reference();
        if (reference != null) {
            for (String recipeType : mappingData.getRecipeTypes(reference)) {
                if (recipeType.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatLocationSearchText(ProviderDescriptor descriptor) {
        if (!descriptor.hasLocation()) {
            return "";
        }
        BlockPos pos = BlockPos.fromLong(descriptor.locationPos());
        String side = descriptor.locationSide() < 0
            ? ""
            : EnumFacing.VALUES[descriptor.locationSide()].getName().toLowerCase(Locale.ROOT);
        return (descriptor.locationDimension() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
            + " " + descriptor.locationDimension() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
            + " " + side).toLowerCase(Locale.ROOT);
    }

    private static String limitPageText(String field, String value, Object providerIdentity,
                                        int maxUtf16Length, int maxUtf8Bytes) {
        if (value.length() <= maxUtf16Length && value.getBytes(StandardCharsets.UTF_8).length <= maxUtf8Bytes) {
            return value;
        }

        StringBuilder result = new StringBuilder(Math.min(value.length(), maxUtf16Length));
        int utf8Bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (result.length() + character.length() > maxUtf16Length || utf8Bytes + characterBytes > maxUtf8Bytes) {
                break;
            }
            result.append(character);
            utf8Bytes += characterBytes;
            offset += character.length();
        }
        if (shouldLogDirectoryWarning()) {
            AELog.warn("Truncated provider directory %s for provider entry %s to fit packet bounds", field,
                providerIdentity);
        }
        return result.toString();
    }

    private static boolean shouldLogDirectoryWarning() {
        long now = System.nanoTime();
        while (true) {
            long previous = LAST_DIRECTORY_TEXT_WARNING.get();
            if (previous != Long.MIN_VALUE && now - previous < WARNING_INTERVAL_NANOS) {
                return false;
            }
            if (LAST_DIRECTORY_TEXT_WARNING.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    @Nullable
    private static ProviderLocation getProviderLocation(PatternContainer container) {
        if (container instanceof TileEntity tile) {
            return getProviderLocation(tile, null);
        }
        if (container instanceof AEBasePart part) {
            return getProviderLocation(part.getTileEntity(), part.getSide());
        }
        if (container instanceof PatternProviderLogicHost host) {
            return getProviderLocation(host.getTileEntity(), null);
        }
        return null;
    }

    @Nullable
    private static ProviderLocation getProviderLocation(@Nullable TileEntity tile, @Nullable EnumFacing side) {
        if (tile == null || tile.getWorld() == null) {
            return null;
        }
        return new ProviderLocation(tile.getWorld().provider.getDimension(), tile.getPos().toLong(),
            side == null ? -1 : side.ordinal());
    }

    /** Opaque server-only identity for a currently active provider. */
    public static final class ProviderKey {
        private final long serial;

        private ProviderKey(long serial) {
            this.serial = serial;
        }
    }

    /** Immutable provider metadata safe to retain in server-side sessions and page construction. */
    public record ProviderDescriptor(ProviderKey providerKey, long sortBy, PatternContainerGroup group, int emptySlots,
                                     boolean acceptsProcessingPatterns, @Nullable ProviderReference reference,
                                     boolean hasLocation, int locationDimension, long locationPos, int locationSide,
                                     String providerName, String providerClassName) {
        public ProviderDescriptor {
            Objects.requireNonNull(providerKey, "providerKey");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(providerName, "providerName");
            Objects.requireNonNull(providerClassName, "providerClassName");
            if (emptySlots < 0) {
                throw new IllegalArgumentException("emptySlots must not be negative");
            }
            if (hasLocation && (locationSide < -1 || locationSide >= EnumFacing.VALUES.length)) {
                throw new IllegalArgumentException("Invalid provider location side: " + locationSide);
            }
        }
    }

    private record ProviderLocation(int dimensionId, long pos, int side) {
    }
}
