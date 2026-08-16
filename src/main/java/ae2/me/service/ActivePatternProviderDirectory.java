package ae2.me.service;

import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
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

    private final IGrid grid;
    private final Reference2ObjectOpenHashMap<PatternContainer, ProviderKey> providerKeysByContainer =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<ProviderKey, PatternContainer> providersByKey =
        new Reference2ObjectOpenHashMap<>();
    private long observedActiveMachineSetRevision = Long.MIN_VALUE;
    private long nextProviderKey;
    private List<PatternContainer> activeProviders = List.of();

    public ActivePatternProviderDirectory(IGrid grid) {
        this.grid = Objects.requireNonNull(grid, "grid");
    }

    /** Returns the current server-only cache of active provider machines. */
    public List<PatternContainer> getActiveProviders() {
        refreshActiveProvidersIfNeeded();
        return this.activeProviders;
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
        for (PatternContainer container : this.activeProviders) {
            if (isSelectableProvider(container)) {
                descriptors.add(createDescriptor(container, this.providerKeysByContainer.get(container)));
            }
        }
        descriptors.sort(PROVIDER_DESCRIPTOR_ORDER);
        return List.copyOf(descriptors);
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

    private void refreshActiveProvidersIfNeeded() {
        long revision = this.grid.getActiveMachineSetRevision();
        if (revision < 0 || revision != this.observedActiveMachineSetRevision) {
            refreshActiveProviders();
            this.observedActiveMachineSetRevision = revision;
        }
    }

    private void refreshActiveProviders() {
        List<PatternContainer> providers = new ArrayList<>();
        Set<PatternContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> machineClass : this.grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }
            Class<? extends PatternContainer> containerClass = machineClass.asSubclass(PatternContainer.class);
            for (PatternContainer container : this.grid.getActiveMachines(containerClass)) {
                if (seen.add(container)) {
                    providers.add(container);
                }
            }
        }

        this.providerKeysByContainer.keySet().removeIf(container -> !seen.contains(container));
        this.providersByKey.clear();
        for (PatternContainer provider : providers) {
            ProviderKey key = this.providerKeysByContainer.get(provider);
            if (key == null) {
                key = new ProviderKey(this.nextProviderKey++);
                this.providerKeysByContainer.put(provider, key);
            }
            this.providersByKey.put(key, provider);
        }
        this.activeProviders = List.copyOf(providers);
    }

    private static boolean isSelectableProvider(PatternContainer container) {
        return container.isVisibleInTerminal() && !container.isAssemblerPatternContainer();
    }

    private static ProviderDescriptor createDescriptor(PatternContainer container, ProviderKey providerKey) {
        if (providerKey == null) {
            throw new IllegalStateException("Active pattern provider has no directory key");
        }
        ProviderLocation location = getProviderLocation(container);
        return new ProviderDescriptor(providerKey, container.getTerminalSortOrder(), container.getTerminalGroup(),
            countEmptySlots(container), !container.isAssemblerPatternContainer(),
            location == null ? null : new ProviderReference(location.dimensionId(), location.pos(), location.side()),
            location != null, location == null ? 0 : location.dimensionId(), location == null ? 0L : location.pos(),
            location == null ? -1 : location.side(), getProviderName(container), container.getClass().getName());
    }

    private static int countEmptySlots(PatternContainer container) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        int emptySlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
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

    @Override
    public void addNode(IGridNode gridNode, @Nullable NBTTagCompound savedData) {
        this.observedActiveMachineSetRevision = Long.MIN_VALUE;
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        this.observedActiveMachineSetRevision = Long.MIN_VALUE;
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
