package ae2.container.me.patternaccess;

import ae2.api.config.ShowPatternProviders;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.stacks.AEItemKey;
import ae2.container.AEBaseContainer;
import ae2.core.AELog;
import ae2.core.gui.PatternContainerGuiReturnContext;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.clientbound.ClearPatternAccessTerminalPacket;
import ae2.core.network.clientbound.PatternAccessTerminalInfoPacket;
import ae2.core.network.clientbound.PatternAccessTerminalPacket;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.helpers.InventoryAction;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.parts.AEBasePart;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared server-side behavior for containers that expose pattern provider inventories.
 * <p>
 * PAT and PEAT own their GUI-specific state. This support owns only provider discovery, provider state indexes,
 * provider inventory interaction, server validation, and packet emission.
 */
public final class PatternAccessSupport<C extends AEBaseContainer & IPatternAccess> {
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private static final int MAX_PROVIDER_DISPLAY_STRING_BYTES = 4096;
    private static final int MAX_DISPLAY_WARNING_KEYS = 512;
    private static final int PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS = 10;
    private static final long DISPLAY_WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final DisplayWarningLimiter DISPLAY_WARNING_LIMITER =
        new DisplayWarningLimiter(MAX_DISPLAY_WARNING_KEYS, DISPLAY_WARNING_INTERVAL_NANOS);
    private static long inventorySerial = Long.MIN_VALUE;

    private final Supplier<@Nullable IGrid> gridSupplier;
    private final Supplier<ShowPatternProviders> shownProvidersSupplier;
    private final Supplier<@Nullable World> worldSupplier;
    private final Predicate<Slot> sourceSlotAllowed;
    private final Consumer<ClientboundPacket> packetSender;
    private final PlayerHandAccess playerHandAccess;
    private final PatternDecoder patternDecoder;
    private final Supplier<PatternProviderMappingData> mappingDataSupplier;
    private final C ownerContainer;
    private final Reference2ObjectMap<PatternContainer, ContainerTracker> diList = new Reference2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ContainerTracker> byId = new Long2ObjectOpenHashMap<>();
    private final ReferenceSet<PatternContainer> pinnedHosts = new ReferenceOpenHashSet<>();
    private final Reference2LongOpenHashMap<PatternContainer> providerIdentityOrdinals =
        new Reference2LongOpenHashMap<>();
    private List<ProviderDirectoryEntry> providerDirectorySnapshot = List.of();
    private List<ProviderStamp> providerDirectorySignature = List.of();
    @Nullable
    private IGrid observedGrid;
    @Nullable
    private ShowPatternProviders observedShownProviders;
    private long observedMappingRevision = Long.MIN_VALUE;
    private long nextProviderIdentityOrdinal;
    private long directoryRevision;
    private int ticksUntilProviderDirectoryScan;
    private boolean providerDirectoryInitialized;

    public PatternAccessSupport(Supplier<@Nullable IGrid> gridSupplier,
                                Supplier<ShowPatternProviders> shownProvidersSupplier,
                                Supplier<@Nullable World> worldSupplier,
                                Predicate<Slot> sourceSlotAllowed,
                                Consumer<ClientboundPacket> packetSender,
                                PlayerHandAccess playerHandAccess,
                                C ownerContainer) {
        this(gridSupplier, shownProvidersSupplier, worldSupplier, sourceSlotAllowed, packetSender, playerHandAccess,
            PatternDetailsHelper::decodePattern, createMappingDataSupplier(worldSupplier), ownerContainer);
    }

    private PatternAccessSupport(Supplier<@Nullable IGrid> gridSupplier,
                                 Supplier<ShowPatternProviders> shownProvidersSupplier,
                                 Supplier<@Nullable World> worldSupplier,
                                 Predicate<Slot> sourceSlotAllowed,
                                 Consumer<ClientboundPacket> packetSender,
                                 PlayerHandAccess playerHandAccess,
                                 PatternDecoder patternDecoder,
                                 Supplier<PatternProviderMappingData> mappingDataSupplier,
                                 @Nullable C ownerContainer) {
        this.gridSupplier = Objects.requireNonNull(gridSupplier);
        this.shownProvidersSupplier = Objects.requireNonNull(shownProvidersSupplier);
        this.worldSupplier = Objects.requireNonNull(worldSupplier);
        this.sourceSlotAllowed = Objects.requireNonNull(sourceSlotAllowed);
        this.packetSender = Objects.requireNonNull(packetSender);
        this.playerHandAccess = Objects.requireNonNull(playerHandAccess);
        this.patternDecoder = Objects.requireNonNull(patternDecoder);
        this.mappingDataSupplier = Objects.requireNonNull(mappingDataSupplier);
        this.ownerContainer = ownerContainer;
    }

    private static Supplier<PatternProviderMappingData> createMappingDataSupplier(
        Supplier<@Nullable World> worldSupplier) {
        Objects.requireNonNull(worldSupplier, "worldSupplier");
        return () -> {
            World world = worldSupplier.get();
            if (world == null) {
                AELog.warn("Cannot read pattern provider mappings without a server world");
                throw new IllegalStateException("Pattern provider mappings require a server world");
            }
            return PatternProviderMappingData.get(world);
        };
    }

    public void updateProviderVisibility() {
        IGrid grid = this.gridSupplier.get();
        updateProviderVisibility(grid == null ? null : ProviderDiscoverySnapshot.discover(grid));
    }

    public void updateProviderVisibility(@Nullable ProviderDiscoverySnapshot discovery) {
        IGrid grid = this.gridSupplier.get();
        ShowPatternProviders shownProviders = getShownProviders();
        if (grid == null || discovery == null) {
            updateDisconnectedDirectory(shownProviders);
            return;
        }

        PatternProviderMappingData mappingData = this.mappingDataSupplier.get();
        boolean contextChanged = !this.providerDirectoryInitialized
            || grid != this.observedGrid
            || shownProviders != this.observedShownProviders;
        long mappingRevision = mappingData.getRevision();
        boolean mappingChanged = mappingRevision != this.observedMappingRevision;
        boolean scheduledScan = --this.ticksUntilProviderDirectoryScan <= 0;
        if (!contextChanged && !mappingChanged && !scheduledScan) {
            sendIncrementalUpdate();
            return;
        }
        if (!contextChanged && mappingChanged && !scheduledScan) {
            this.observedMappingRevision = mappingRevision;
            incrementDirectoryRevision();
            sendMappingMetadataUpdate(mappingData);
            return;
        }

        List<ProviderDirectoryEntry> providers = collectPatternAccessProviders(discovery.providers(), shownProviders);
        List<ProviderStamp> signature = createProviderSignature(providers);
        boolean directoryChanged = contextChanged
            || mappingChanged
            || !this.providerDirectorySignature.equals(signature);
        rememberProviderDirectory(grid, shownProviders, mappingRevision, providers, signature);
        if (directoryChanged) {
            incrementDirectoryRevision();
            sendFullUpdate(grid, mappingData, providers);
            return;
        }

        sendIncrementalUpdate();
    }

    private void updateDisconnectedDirectory(ShowPatternProviders shownProviders) {
        boolean directoryChanged = this.observedGrid != null || !this.diList.isEmpty();
        this.pinnedHosts.clear();
        this.providerIdentityOrdinals.clear();
        this.providerDirectorySnapshot = List.of();
        this.providerDirectorySignature = List.of();
        this.observedGrid = null;
        this.observedShownProviders = shownProviders;
        this.observedMappingRevision = Long.MIN_VALUE;
        this.ticksUntilProviderDirectoryScan = 0;
        this.providerDirectoryInitialized = true;
        if (directoryChanged) {
            incrementDirectoryRevision();
            sendFullUpdate(null, null, List.of());
        } else {
            sendIncrementalUpdate();
        }
    }

    private void rememberProviderDirectory(IGrid grid, ShowPatternProviders shownProviders, long mappingRevision,
                                           List<ProviderDirectoryEntry> providers, List<ProviderStamp> signature) {
        this.observedGrid = Objects.requireNonNull(grid, "grid");
        this.observedShownProviders = Objects.requireNonNull(shownProviders, "shownProviders");
        this.observedMappingRevision = mappingRevision;
        this.providerDirectorySnapshot = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.providerDirectorySignature = List.copyOf(Objects.requireNonNull(signature, "signature"));
        this.ticksUntilProviderDirectoryScan = PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS;
        this.providerDirectoryInitialized = true;
    }

    private void incrementDirectoryRevision() {
        this.directoryRevision = Math.incrementExact(this.directoryRevision);
    }

    public void openProvider(EntityPlayer player, long inventoryId) {
        ProviderActionContext actionContext = createProviderActionContext();
        ContainerTracker tracker = getCurrentProviderTracker(actionContext, inventoryId);
        if (tracker == null) {
            return;
        }
        if (this.ownerContainer == null) {
            AELog.warn("Cannot open pattern provider GUI without an owner container");
            return;
        }

        PatternContainerGuiReturnContext.openFromPatternAccessTerminal(player, this.ownerContainer,
            () -> tracker.container.openTerminalPatternContainerGui(player));
    }

    public void renameProvider(long inventoryId, @Nullable String name) {
        if (!isValidCustomName(name)) {
            return;
        }

        ProviderActionContext actionContext = createProviderActionContext();
        ContainerTracker tracker = getCurrentProviderTracker(actionContext, inventoryId);
        if (tracker == null || !tracker.container.canEditTerminalName()) {
            return;
        }

        tracker.container.setTerminalCustomName(name);
        sendFullUpdate(actionContext.grid());
    }

    public void renameGroup(long @Nullable [] inventoryIds, @Nullable String name) {
        if (inventoryIds == null || !isValidCustomName(name)) {
            return;
        }
        if (inventoryIds.length > this.byId.size()) {
            return;
        }
        ProviderActionContext actionContext = createProviderActionContext();
        if (actionContext == null) {
            return;
        }

        LongOpenHashSet visited = new LongOpenHashSet(inventoryIds.length);
        boolean changedAny = false;
        for (long inventoryId : inventoryIds) {
            if (!visited.add(inventoryId)) {
                continue;
            }
            ContainerTracker tracker = getCurrentProviderTracker(actionContext, inventoryId);
            if (tracker == null || !tracker.container.canEditTerminalName()) {
                continue;
            }
            tracker.container.setTerminalCustomName(name);
            changedAny = true;
        }

        if (changedAny) {
            sendFullUpdate(actionContext.grid());
        }
    }

    public void renameGroup(@Nullable RenamePatternGroupPayload payload) {
        if (payload == null) {
            return;
        }
        renameGroup(payload.inventoryIds(), payload.name());
    }

    public void toggleProviderVisibility(long inventoryId) {
        ProviderActionContext actionContext = createProviderActionContext();
        ContainerTracker tracker = getCurrentProviderTracker(actionContext, inventoryId);
        if (tracker == null || !tracker.container.canModifyTerminalVisibility()) {
            return;
        }

        boolean visible = tracker.container.isVisibleInTerminal();
        tracker.container.setTerminalVisibility(!visible);
        sendFullUpdate(actionContext.grid());
    }

    public void renameProvider(@Nullable RenamePatternProviderPayload payload) {
        if (payload == null) {
            return;
        }
        renameProvider(payload.inventoryId(), payload.name());
    }

    public boolean doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        ProviderActionContext actionContext = createProviderActionContext();
        ContainerTracker inv = getCurrentProviderTracker(actionContext, id);
        if (inv == null) {
            return false;
        }
        if (slot < 0 || slot >= inv.server.size()) {
            return true;
        }

        ItemStack stackInSlot = inv.server.getStackInSlot(slot);
        FilteredInternalInventory patternSlot = new FilteredInternalInventory(inv.server.getSlotInv(slot),
            new PatternSlotFilter(inv.container, player.world, this.patternDecoder));
        ItemStack carried = this.playerHandAccess.getCarried();

        switch (action) {
            case PICKUP_OR_SET_DOWN -> {
                if (!carried.isEmpty()) {
                    ItemStack inSlot = patternSlot.getStackInSlot(0);
                    if (inSlot.isEmpty()) {
                        this.playerHandAccess.setCarried(patternSlot.addItems(carried));
                    } else {
                        inSlot = inSlot.copy();
                        ItemStack inHand = carried.copy();

                        patternSlot.setItemDirect(0, ItemStack.EMPTY);
                        this.playerHandAccess.setCarried(ItemStack.EMPTY);

                        this.playerHandAccess.setCarried(patternSlot.addItems(inHand.copy()));

                        if (this.playerHandAccess.getCarried().isEmpty()) {
                            this.playerHandAccess.setCarried(inSlot);
                        } else {
                            this.playerHandAccess.setCarried(inHand);
                            patternSlot.setItemDirect(0, inSlot);
                        }
                    }
                } else {
                    this.playerHandAccess.setCarried(patternSlot.getStackInSlot(0));
                    patternSlot.setItemDirect(0, ItemStack.EMPTY);
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                if (!carried.isEmpty()) {
                    ItemStack extra = carried.splitStack(1);
                    if (!extra.isEmpty()) {
                        extra = patternSlot.addItems(extra);
                    }
                    if (!extra.isEmpty()) {
                        carried.grow(extra.getCount());
                    }
                } else if (!stackInSlot.isEmpty()) {
                    this.playerHandAccess.setCarried(patternSlot.extractItem(0, (stackInSlot.getCount() + 1) / 2,
                        false));
                }
            }
            case SHIFT_CLICK -> {
                ItemStack stack = patternSlot.getStackInSlot(0).copy();
                if (!player.inventory.addItemStackToInventory(stack)) {
                    patternSlot.setItemDirect(0, stack);
                } else {
                    patternSlot.setItemDirect(0, ItemStack.EMPTY);
                }
            }
            case MOVE_REGION -> {
                for (int x = 0; x < inv.server.size(); x++) {
                    FilteredInternalInventory slotInventory = new FilteredInternalInventory(inv.server.getSlotInv(x),
                        new PatternSlotFilter(inv.container, player.world, this.patternDecoder));
                    ItemStack slotStack = slotInventory.getStackInSlot(0);
                    if (!player.inventory.addItemStackToInventory(slotStack)) {
                        slotInventory.setItemDirect(0, slotStack);
                    } else {
                        slotInventory.setItemDirect(0, ItemStack.EMPTY);
                    }
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.capabilities.isCreativeMode && carried.isEmpty()) {
                    this.playerHandAccess.setCarried(stackInSlot.isEmpty() ? ItemStack.EMPTY : stackInSlot.copy());
                }
            }
            default -> {
            }
        }
        return true;
    }

    public void quickMovePattern(@Nullable EntityPlayerMP player, Slot sourceSlot, LongList allowedPatternContainerIds,
                                 LongList allowedPatternSlots) {
        if (!this.sourceSlotAllowed.test(sourceSlot)) {
            return;
        }
        ProviderActionContext actionContext = createProviderActionContext();
        if (actionContext == null) {
            return;
        }

        ItemStack sourceStack = sourceSlot.getStack();
        World level = player != null ? player.world : this.worldSupplier.get();
        IPatternDetails pattern = this.patternDecoder.decode(sourceStack, level);
        if (pattern == null) {
            return;
        }
        AEItemKey sourcePattern = AEItemKey.of(sourceStack);
        if (sourcePattern == null) {
            return;
        }

        boolean assemblerPattern = pattern instanceof IAssemblerPattern;
        if (assemblerPattern) {
            quickMoveAssemblerPattern(actionContext, player, sourceSlot, sourcePattern);
            return;
        }

        List<QuickMoveTarget> targets = new ObjectArrayList<>();
        int targetCount = Math.min(allowedPatternContainerIds.size(), allowedPatternSlots.size());
        for (int i = 0; i < targetCount; i++) {
            ContainerTracker targetInventory = getCurrentProviderTracker(actionContext,
                allowedPatternContainerIds.getLong(i));
            if (targetInventory != null
                && targetInventory.container.isAssemblerPatternContainer() == assemblerPattern) {
                targets.add(new QuickMoveTarget(targetInventory, (int) allowedPatternSlots.getLong(i)));
            }
        }

        if (targets.stream().map(target -> target.container().group).distinct().count() != 1) {
            return;
        }

        ReferenceSet<ContainerTracker> usedContainers = new ReferenceOpenHashSet<>();
        for (QuickMoveTarget target : targets) {
            if (movePatternToTarget(player, sourceSlot, sourcePattern, usedContainers, target.container(),
                target.slot())) {
                return;
            }
        }
    }

    void sendIncrementalUpdate() {
        for (ContainerTracker inv : this.diList.values()) {
            PatternAccessTerminalPacket packet = inv.createUpdatePacket();
            if (packet != null) {
                this.packetSender.accept(packet);
            }
        }
    }

    private void sendMappingMetadataUpdate(PatternProviderMappingData mappingData) {
        Objects.requireNonNull(mappingData, "mappingData");
        if (this.providerDirectorySnapshot.size() != this.diList.size()) {
            throw new IllegalStateException("Pinned pattern provider directory does not match active trackers");
        }

        this.packetSender.accept(new ClearPatternAccessTerminalPacket());
        for (ProviderDirectoryEntry provider : this.providerDirectorySnapshot) {
            ContainerTracker tracker = this.diList.get(provider.container());
            if (tracker == null || this.byId.get(tracker.serverId) != tracker) {
                throw new IllegalStateException("Pinned pattern provider is missing its active tracker");
            }
            tracker.refreshDisplayMetadata(provider, mappingData);
            this.packetSender.accept(tracker.createCachedFullPacket());
            PatternAccessTerminalInfoPacket infoPacket = tracker.createInfoPacket();
            if (infoPacket != null) {
                this.packetSender.accept(infoPacket);
            }
        }
    }

    void sendFullUpdate(@Nullable IGrid grid) {
        ShowPatternProviders shownProviders = getShownProviders();
        if (grid == null) {
            this.pinnedHosts.clear();
            this.providerIdentityOrdinals.clear();
            this.providerDirectorySnapshot = List.of();
            this.providerDirectorySignature = List.of();
            this.observedGrid = null;
            this.observedShownProviders = shownProviders;
            this.observedMappingRevision = Long.MIN_VALUE;
            this.ticksUntilProviderDirectoryScan = 0;
            this.providerDirectoryInitialized = true;
            incrementDirectoryRevision();
            sendFullUpdate(null, null, List.of());
            return;
        }

        PatternProviderMappingData mappingData = this.mappingDataSupplier.get();
        List<ProviderDirectoryEntry> providers = collectPatternAccessProviders(ProviderDiscoverySnapshot.discover(grid).providers(), shownProviders);
        List<ProviderStamp> signature = createProviderSignature(providers);
        rememberProviderDirectory(grid, shownProviders, mappingData.getRevision(), providers, signature);
        incrementDirectoryRevision();
        sendFullUpdate(grid, mappingData, providers);
    }

    private void sendFullUpdate(@Nullable IGrid grid, @Nullable PatternProviderMappingData mappingData,
                                List<ProviderDirectoryEntry> providers) {
        Objects.requireNonNull(providers, "providers");
        Reference2LongOpenHashMap<PatternContainer> previousIds = new Reference2LongOpenHashMap<>();
        for (ContainerTracker tracker : this.diList.values()) {
            previousIds.put(tracker.container, tracker.serverId);
        }

        this.byId.clear();
        this.diList.clear();

        this.packetSender.accept(new ClearPatternAccessTerminalPacket());

        if (grid == null) {
            return;
        }
        Objects.requireNonNull(mappingData, "mappingData");

        for (ProviderDirectoryEntry provider : providers) {
            long serverId = previousIds.containsKey(provider.container())
                ? previousIds.getLong(provider.container())
                : inventorySerial++;
            this.diList.put(provider.container(), new ContainerTracker(provider, this.worldSupplier.get(),
                this.patternDecoder, mappingData, serverId));
        }

        for (ContainerTracker inv : this.diList.values()) {
            this.byId.put(inv.serverId, inv);
            this.packetSender.accept(inv.createFullPacket());
            PatternAccessTerminalInfoPacket infoPacket = inv.createInfoPacket();
            if (infoPacket != null) {
                this.packetSender.accept(infoPacket);
            }
        }
    }

    private static boolean isAcceptedByContainer(PatternContainer container, @Nullable IPatternDetails details) {
        return details != null && (details instanceof IAssemblerPattern) == container.isAssemblerPatternContainer();
    }

    private List<ProviderDirectoryEntry> collectPatternAccessProviders(List<PatternContainer> discoveredProviders,
                                                                       ShowPatternProviders shownProviders) {
        Objects.requireNonNull(discoveredProviders, "discoveredProviders");
        Objects.requireNonNull(shownProviders, "shownProviders");

        if (shownProviders != ShowPatternProviders.NOT_FULL) {
            this.pinnedHosts.clear();
        }

        List<ProviderDirectoryEntry> providers = new ObjectArrayList<>();
        ReferenceSet<PatternContainer> activeProviders = new ReferenceOpenHashSet<>();
        for (PatternContainer container : discoveredProviders) {
            activeProviders.add(container);
            long identityOrdinal = getOrCreateProviderIdentityOrdinal(container);
            ProviderDirectoryEntry provider = ProviderDirectoryEntry.of(container, identityOrdinal);
            if (!isVisibleInPatternAccess(provider, shownProviders, this.pinnedHosts)) {
                continue;
            }

            providers.add(provider);
            if (shownProviders == ShowPatternProviders.NOT_FULL) {
                this.pinnedHosts.add(container);
            }
        }

        this.pinnedHosts.removeIf(container -> !activeProviders.contains(container));
        this.providerIdentityOrdinals.keySet().removeIf(container -> !activeProviders.contains(container));
        providers.sort(PatternAccessSupport::compareProviderEntries);
        return List.copyOf(providers);
    }

    private static boolean isVisibleInPatternAccess(ProviderDirectoryEntry provider,
                                                    ShowPatternProviders shownProviders,
                                                    ReferenceSet<PatternContainer> pinnedProviders) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(shownProviders, "shownProviders");
        Objects.requireNonNull(pinnedProviders, "pinnedProviders");

        boolean visible = provider.visibleInTerminal();
        return switch (shownProviders) {
            case VISIBLE -> visible;
            case HIDDEN -> !visible;
            case NOT_FULL -> visible
                && (pinnedProviders.contains(provider.container()) || provider.emptySlots() > 0);
            case ALL -> true;
        };
    }

    private long getOrCreateProviderIdentityOrdinal(PatternContainer container) {
        if (this.providerIdentityOrdinals.containsKey(container)) {
            return this.providerIdentityOrdinals.getLong(container);
        }
        long ordinal = this.nextProviderIdentityOrdinal;
        this.nextProviderIdentityOrdinal = Math.incrementExact(this.nextProviderIdentityOrdinal);
        this.providerIdentityOrdinals.put(container, ordinal);
        return ordinal;
    }

    private static int compareProviderEntries(ProviderDirectoryEntry left, ProviderDirectoryEntry right) {
        int comparison = Long.compare(left.sortBy(), right.sortBy());
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareProviderReferences(left.reference(), right.reference());
        if (comparison != 0) {
            return comparison;
        }
        return Long.compare(left.identityOrdinal(), right.identityOrdinal());
    }

    private static int compareProviderReferences(@Nullable ProviderReference left,
                                                 @Nullable ProviderReference right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int comparison = Integer.compare(left.dimension(), right.dimension());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(left.pos(), right.pos());
        return comparison != 0 ? comparison : Integer.compare(left.side(), right.side());
    }

    private static List<ProviderStamp> createProviderSignature(List<ProviderDirectoryEntry> providers) {
        List<ProviderStamp> signature = new ObjectArrayList<>(providers.size());
        for (ProviderDirectoryEntry provider : providers) {
            signature.add(new ProviderStamp(provider));
        }
        return List.copyOf(signature);
    }

    private static DisplayMetadata createDisplayMetadata(ProviderDirectoryEntry provider,
                                                         PatternProviderMappingData mappingData) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(mappingData, "mappingData");

        PatternContainer container = provider.container();
        ProviderReference reference = provider.reference();
        int emptySlots = provider.emptySlots();
        String providerName = limitProviderName(getProviderName(provider), emptySlots, container, reference);
        List<String> mappedRecipeTypes = reference == null || !provider.acceptsProcessingPatterns()
            ? Collections.emptyList()
            : new ObjectArrayList<>(mappingData.getRecipeTypes(reference));
        List<String> displayRecipeTypes = mappedRecipeTypes.isEmpty()
            ? Collections.emptyList()
            : limitDisplayRecipeTypes(mappedRecipeTypes, providerName, emptySlots, container, reference);
        String label = formatProviderLabel(displayRecipeTypes, providerName, emptySlots);
        return new DisplayMetadata(label, buildSearchText(label, providerName, displayRecipeTypes));
    }

    private static int countEmptySlots(InternalInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        int emptySlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    private static String getProviderName(ProviderDirectoryEntry provider) {
        Objects.requireNonNull(provider, "provider");

        var name = provider.group().name();
        if (name == null) {
            return provider.container().getClass().getSimpleName();
        }
        return name.getUnformattedText();
    }

    private static String limitProviderName(String providerName, int emptySlots, PatternContainer container,
                                            @Nullable ProviderReference reference) {
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(container, "container");

        String labelSuffix = formatProviderLabel(Collections.emptyList(), "", emptySlots);
        int fixedSearchBytes = getUtf8Length(labelSuffix) + getUtf8Length("\n");
        int maximumNameBytes = Math.max(0, (MAX_PROVIDER_DISPLAY_STRING_BYTES - fixedSearchBytes) / 2);
        String displayName = truncateUtf8(providerName, maximumNameBytes);
        if (!displayName.equals(providerName)
            && shouldLogDisplayWarning(container, reference, DisplayWarningKind.PROVIDER_NAME)) {
            AELog.warn("Pattern access display for %s truncated its provider name to keep packet strings under "
                    + "%d UTF-8 bytes", describeProvider(container, reference),
                MAX_PROVIDER_DISPLAY_STRING_BYTES);
        }
        return displayName;
    }

    private static List<String> limitDisplayRecipeTypes(List<String> recipeTypes, String providerName, int emptySlots,
                                                        PatternContainer container, ProviderReference reference) {
        Objects.requireNonNull(recipeTypes, "recipeTypes");
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(reference, "reference");

        List<String> displayRecipeTypes = new ObjectArrayList<>(recipeTypes.size());
        for (String recipeType : recipeTypes) {
            displayRecipeTypes.add(recipeType);

            String label = formatProviderLabel(displayRecipeTypes, providerName, emptySlots);
            String searchText = buildSearchText(label, providerName, displayRecipeTypes);
            if (isProviderDisplayStringWithinBudget(label) && isProviderDisplayStringWithinBudget(searchText)) {
                continue;
            }

            displayRecipeTypes.removeLast();
            if (shouldLogDisplayWarning(container, reference, DisplayWarningKind.RECIPE_TYPES)) {
                AELog.warn("Pattern access display for %s omitted %d mapped recipe types to keep packet strings under "
                        + "%d UTF-8 bytes", reference, recipeTypes.size() - displayRecipeTypes.size(),
                    MAX_PROVIDER_DISPLAY_STRING_BYTES);
            }
            break;
        }
        return List.copyOf(displayRecipeTypes);
    }

    private static boolean shouldLogDisplayWarning(PatternContainer container,
                                                   @Nullable ProviderReference reference,
                                                   DisplayWarningKind kind) {
        return DISPLAY_WARNING_LIMITER.shouldLog(new DisplayWarningKey(container, reference, kind), System.nanoTime());
    }

    private static Object describeProvider(PatternContainer container, @Nullable ProviderReference reference) {
        return reference == null ? container.getClass().getName() : reference;
    }

    private static boolean isProviderDisplayStringWithinBudget(String value) {
        return getUtf8Length(value) <= MAX_PROVIDER_DISPLAY_STRING_BYTES;
    }

    private static int getUtf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String truncateUtf8(String value, int maximumBytes) {
        Objects.requireNonNull(value, "value");
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("maximumBytes must not be negative");
        }
        if (getUtf8Length(value) <= maximumBytes) {
            return value;
        }

        String suffix = "...";
        int suffixBytes = getUtf8Length(suffix);
        boolean appendSuffix = suffixBytes <= maximumBytes;
        int prefixBudget = appendSuffix ? maximumBytes - suffixBytes : maximumBytes;
        StringBuilder result = new StringBuilder(Math.min(value.length(), prefixBudget));
        int usedBytes = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int codePointBytes = getUtf8CodePointLength(codePoint);
            if (usedBytes + codePointBytes > prefixBudget) {
                break;
            }
            result.appendCodePoint(codePoint);
            usedBytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        if (appendSuffix) {
            result.append(suffix);
        }
        return result.toString();
    }

    private static int getUtf8CodePointLength(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint <= 0xffff) {
            return 3;
        }
        return 4;
    }

    private static String buildSearchText(String label, String providerName, List<String> recipeTypes) {
        StringBuilder searchText = new StringBuilder(label).append('\n').append(providerName);
        for (String recipeType : recipeTypes) {
            searchText.append('\n').append(recipeType);
        }
        return searchText.toString();
    }

    private static String formatProviderLabel(List<String> recipeTypes, String name, int emptySlots) {
        StringBuilder label = new StringBuilder();
        for (String recipeType : recipeTypes) {
            label.append("[").append(recipeType).append("]");
        }
        label.append(name).append(" (").append(emptySlots).append(")");
        return label.toString();
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

    @Nullable
    private static Class<? extends PatternContainer> tryCastMachineToContainer(Class<?> machineClass) {
        if (PatternContainer.class.isAssignableFrom(machineClass)) {
            return machineClass.asSubclass(PatternContainer.class);
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isValidCustomName(@Nullable String name) {
        return name != null && name.length() <= MAX_CUSTOM_NAME_LENGTH;
    }

    private ShowPatternProviders getShownProviders() {
        return this.shownProvidersSupplier.get();
    }

    @Nullable
    private ProviderActionContext createProviderActionContext() {
        IGrid grid = this.gridSupplier.get();
        if (grid == null) {
            return null;
        }

        ReferenceSet<PatternContainer> activeProviders = new ReferenceOpenHashSet<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            Class<? extends PatternContainer> containerClass = tryCastMachineToContainer(machineClass);
            if (containerClass == null) {
                continue;
            }
            activeProviders.addAll(grid.getActiveMachines(containerClass));
        }
        return new ProviderActionContext(grid, getShownProviders(), activeProviders);
    }

    @Nullable
    private ContainerTracker getCurrentProviderTracker(@Nullable ProviderActionContext actionContext,
                                                       long inventoryId) {
        if (actionContext == null) {
            return null;
        }
        ContainerTracker tracker = this.byId.get(inventoryId);
        if (tracker == null
            || !actionContext.activeProviders().contains(tracker.container)
            || !isVisibleInCurrentPatternAccess(tracker.container, actionContext.shownProviders())) {
            return null;
        }
        return tracker;
    }

    private boolean isVisibleInCurrentPatternAccess(PatternContainer container,
                                                    ShowPatternProviders shownProviders) {
        Objects.requireNonNull(container, "container");
        boolean visible = container.isVisibleInTerminal();
        return switch (shownProviders) {
            case VISIBLE -> visible;
            case HIDDEN -> !visible;
            case NOT_FULL -> visible && (this.pinnedHosts.contains(container)
                || countEmptySlots(container.getTerminalPatternInventory()) > 0);
            case ALL -> true;
        };
    }

    private void quickMoveAssemblerPattern(ProviderActionContext actionContext,
                                           @Nullable EntityPlayerMP player, Slot sourceSlot,
                                           AEItemKey sourcePattern) {
        ReferenceSet<ContainerTracker> usedContainers = new ReferenceOpenHashSet<>();
        for (ContainerTracker targetInventory : this.diList.values()) {
            if (getCurrentProviderTracker(actionContext, targetInventory.serverId) == null
                || !targetInventory.container.isAssemblerPatternContainer()) {
                continue;
            }
            for (int slot = 0; slot < targetInventory.server.size(); slot++) {
                if (movePatternToTarget(player, sourceSlot, sourcePattern, usedContainers, targetInventory, slot)) {
                    return;
                }
                if (usedContainers.contains(targetInventory)) {
                    break;
                }
            }
        }
    }

    private boolean movePatternToTarget(@Nullable EntityPlayerMP player, Slot sourceSlot, AEItemKey sourcePattern,
                                        ReferenceSet<ContainerTracker> usedContainers, ContainerTracker container,
                                        int slot) {
        if (usedContainers.contains(container)) {
            return false;
        }
        if (container.container.containsPattern(sourcePattern)) {
            return false;
        }
        if (slot < 0 || slot >= container.server.size()) {
            return false;
        }

        World level = player != null ? player.world : this.worldSupplier.get();
        FilteredInternalInventory targetSlot = new FilteredInternalInventory(
            container.server.getSlotInv(slot),
            new PatternSlotFilter(container.container, level, this.patternDecoder));
        ItemStack movedPattern = sourceSlot.getStack().copy();
        movedPattern.setCount(1);
        if (!targetSlot.addItems(movedPattern).isEmpty()) {
            return false;
        }

        sourceSlot.decrStackSize(1);
        usedContainers.add(container);
        return sourceSlot.getStack().isEmpty();
    }

    public interface PlayerHandAccess {
        ItemStack getCarried();

        void setCarried(ItemStack stack);
    }

    public record RenamePatternProviderPayload(long inventoryId, String name) {
    }

    public record RenamePatternGroupPayload(long[] inventoryIds, String name) {
    }

    @FunctionalInterface
    interface PatternDecoder {
        @Nullable
        IPatternDetails decode(ItemStack stack, @Nullable World level);
    }

    private record QuickMoveTarget(ContainerTracker container, int slot) {
    }

    private record ProviderActionContext(IGrid grid, ShowPatternProviders shownProviders,
                                         ReferenceSet<PatternContainer> activeProviders) {
        private ProviderActionContext {
            Objects.requireNonNull(grid, "grid");
            Objects.requireNonNull(shownProviders, "shownProviders");
            Objects.requireNonNull(activeProviders, "activeProviders");
        }
    }

    private enum DisplayWarningKind {
        PROVIDER_NAME,
        RECIPE_TYPES
    }

    private static final class DisplayWarningKey {
        private final PatternContainer container;
        @Nullable
        private final ProviderReference reference;
        private final DisplayWarningKind kind;

        private DisplayWarningKey(PatternContainer container, @Nullable ProviderReference reference,
                                  DisplayWarningKind kind) {
            this.container = Objects.requireNonNull(container, "container");
            this.reference = reference;
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DisplayWarningKey that) || this.kind != that.kind) {
                return false;
            }
            if (this.reference != null) {
                return this.reference.equals(that.reference);
            }
            return that.reference == null && this.container == that.container;
        }

        @Override
        public int hashCode() {
            int providerHash = this.reference == null
                ? System.identityHashCode(this.container)
                : this.reference.hashCode();
            return 31 * providerHash + this.kind.hashCode();
        }
    }

    static final class DisplayWarningLimiter {
        private final int maximumTrackedKeys;
        private final long intervalNanos;
        private final LinkedHashMap<Object, Long> lastWarningNanos = new LinkedHashMap<>(16, 0.75f, true);

        DisplayWarningLimiter(int maximumTrackedKeys, long intervalNanos) {
            if (maximumTrackedKeys <= 0) {
                throw new IllegalArgumentException("maximumTrackedKeys must be positive");
            }
            if (intervalNanos <= 0) {
                throw new IllegalArgumentException("intervalNanos must be positive");
            }
            this.maximumTrackedKeys = maximumTrackedKeys;
            this.intervalNanos = intervalNanos;
        }

        synchronized boolean shouldLog(Object key, long nowNanos) {
            Objects.requireNonNull(key, "key");
            Long lastWarning = this.lastWarningNanos.get(key);
            if (lastWarning != null && nowNanos - lastWarning < this.intervalNanos) {
                return false;
            }

            if (lastWarning == null && this.lastWarningNanos.size() >= this.maximumTrackedKeys) {
                Object oldestKey = this.lastWarningNanos.keySet().iterator().next();
                this.lastWarningNanos.remove(oldestKey);
            }
            this.lastWarningNanos.put(key, nowNanos);
            return true;
        }

    }

    private record ProviderLocation(int dimensionId, long pos, int side) {
    }

    private record ProviderDirectoryEntry(PatternContainer container, long identityOrdinal, long sortBy,
                                          PatternContainerGroup group, int inventorySize, int emptySlots,
                                          boolean visibleInTerminal, boolean acceptsProcessingPatterns,
                                          boolean canEditTerminalName, boolean canModifyTerminalVisibility,
                                          @Nullable ProviderReference reference, boolean hasLocation,
                                          int locationDimension, long locationPos, int locationSide) {
        private ProviderDirectoryEntry {
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(group, "group");
            if (inventorySize < 0) {
                throw new IllegalArgumentException("inventorySize must not be negative");
            }
            if (emptySlots < 0 || emptySlots > inventorySize) {
                throw new IllegalArgumentException("emptySlots must be between zero and inventorySize");
            }
        }

        private static ProviderDirectoryEntry of(PatternContainer container, long identityOrdinal) {
            Objects.requireNonNull(container, "container");

            ProviderLocation location = getProviderLocation(container);
            ProviderReference reference = location == null
                ? null
                : new ProviderReference(location.dimensionId(), location.pos(), location.side());
            InternalInventory inventory = container.getTerminalPatternInventory();
            return new ProviderDirectoryEntry(container, identityOrdinal, container.getTerminalSortOrder(),
                container.getTerminalGroup(), inventory.size(), countEmptySlots(inventory),
                container.isVisibleInTerminal(), !container.isAssemblerPatternContainer(), container.canEditTerminalName(),
                container.canModifyTerminalVisibility(), reference,
                location != null, location == null ? 0 : location.dimensionId(),
                location == null ? 0L : location.pos(), location == null ? -1 : location.side());
        }
    }

    private static final class ProviderStamp {
        private final ProviderDirectoryEntry provider;

        private ProviderStamp(ProviderDirectoryEntry provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProviderStamp that)) {
                return false;
            }
            ProviderDirectoryEntry left = this.provider;
            ProviderDirectoryEntry right = that.provider;
            return left.container() == right.container()
                && left.identityOrdinal() == right.identityOrdinal()
                && left.sortBy() == right.sortBy()
                && left.inventorySize() == right.inventorySize()
                && left.emptySlots() == right.emptySlots()
                && left.visibleInTerminal() == right.visibleInTerminal()
                && left.acceptsProcessingPatterns() == right.acceptsProcessingPatterns()
                && left.canEditTerminalName() == right.canEditTerminalName()
                && left.canModifyTerminalVisibility() == right.canModifyTerminalVisibility()
                && left.hasLocation() == right.hasLocation()
                && left.locationDimension() == right.locationDimension()
                && left.locationPos() == right.locationPos()
                && left.locationSide() == right.locationSide()
                && Objects.equals(left.group(), right.group())
                && Objects.equals(left.reference(), right.reference());
        }

        @Override
        public int hashCode() {
            ProviderDirectoryEntry value = this.provider;
            return Objects.hash(System.identityHashCode(value.container()), value.identityOrdinal(), value.sortBy(),
                value.group(), value.inventorySize(), value.emptySlots(), value.visibleInTerminal(),
                value.acceptsProcessingPatterns(), value.canEditTerminalName(), value.canModifyTerminalVisibility(),
                value.reference(), value.hasLocation(), value.locationDimension(), value.locationPos(),
                value.locationSide());
        }
    }

    private record DisplayMetadata(String label, String searchText) {
        private DisplayMetadata {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(searchText, "searchText");
        }
    }

    private static final class ContainerTracker {
        private final PatternContainer container;
        private final long sortBy;
        private final long serverId;
        private final PatternContainerGroup group;
        private final InternalInventory client;
        private final InternalInventory server;
        private final boolean canEditTerminalName;
        private final boolean canModifyTerminalVisibility;
        private final boolean acceptsProcessingPatterns;
        private final boolean hasLocation;
        private String providerLabel;
        private String providerSearchText;
        private final int locationDimension;
        private final long locationPos;
        private final int locationSide;
        @Nullable
        private final ProviderReference reference;
        @Nullable
        private final World level;
        private final PatternDecoder patternDecoder;

        private ContainerTracker(ProviderDirectoryEntry provider,
                                 @Nullable World level, PatternDecoder patternDecoder,
                                 PatternProviderMappingData mappingData, long serverId) {
            this.container = provider.container();
            this.serverId = serverId;
            this.server = provider.container().getTerminalPatternInventory();
            this.client = new AppEngInternalInventory(this.server.size());
            this.group = provider.group();
            this.sortBy = provider.sortBy();
            this.canEditTerminalName = provider.canEditTerminalName();
            this.canModifyTerminalVisibility = provider.canModifyTerminalVisibility();
            this.acceptsProcessingPatterns = provider.acceptsProcessingPatterns();
            this.hasLocation = provider.hasLocation();
            DisplayMetadata metadata = createDisplayMetadata(provider, mappingData);
            this.providerLabel = metadata.label();
            this.providerSearchText = metadata.searchText();
            this.locationDimension = provider.locationDimension();
            this.locationPos = provider.locationPos();
            this.locationSide = provider.locationSide();
            this.reference = provider.reference();
            this.level = level;
            this.patternDecoder = patternDecoder;
        }

        private static boolean isDifferent(ItemStack a, ItemStack b) {
            if (a.isEmpty() && b.isEmpty()) {
                return false;
            }

            if (a.isEmpty() || b.isEmpty()) {
                return true;
            }

            return !ItemStack.areItemsEqual(a, b) || !ItemStack.areItemStackTagsEqual(a, b);
        }

        private void refreshDisplayMetadata(ProviderDirectoryEntry provider,
                                            PatternProviderMappingData mappingData) {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(mappingData, "mappingData");
            if (provider.container() != this.container || !Objects.equals(provider.reference(), this.reference)) {
                throw new IllegalStateException("Pinned pattern provider metadata does not match its tracker");
            }
            DisplayMetadata metadata = createDisplayMetadata(provider, mappingData);
            this.providerLabel = metadata.label();
            this.providerSearchText = metadata.searchText();
        }

        private PatternAccessTerminalPacket createCachedFullPacket() {
            Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(this.client.size());
            for (int i = 0; i < this.client.size(); i++) {
                ItemStack stack = this.client.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    slots.put(i, stack.copy());
                }
            }

            return PatternAccessTerminalPacket.fullUpdate(this.serverId, this.client.size(), this.sortBy,
                this.canEditTerminalName, this.canModifyTerminalVisibility, this.acceptsProcessingPatterns,
                this.group, this.providerLabel, this.providerSearchText, slots);
        }

        private PatternAccessTerminalPacket createFullPacket() {
            Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(this.server.size());
            for (int i = 0; i < this.server.size(); i++) {
                ItemStack stack = this.getVisibleStack(i);
                if (!stack.isEmpty()) {
                    this.client.setItemDirect(i, stack.copy());
                    slots.put(i, stack);
                }
            }

            return PatternAccessTerminalPacket.fullUpdate(this.serverId, this.server.size(), this.sortBy,
                this.canEditTerminalName, this.canModifyTerminalVisibility, this.acceptsProcessingPatterns,
                this.group, this.providerLabel, this.providerSearchText, slots);
        }

        @Nullable
        private PatternAccessTerminalPacket createUpdatePacket() {
            IntList changedSlots = detectChangedSlots();
            if (changedSlots == null) {
                return null;
            }

            Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(changedSlots.size());
            for (int i = 0; i < changedSlots.size(); i++) {
                int slot = changedSlots.getInt(i);
                ItemStack stack = this.getVisibleStack(slot);
                this.client.setItemDirect(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                slots.put(slot, stack);
            }

            return PatternAccessTerminalPacket.incrementalUpdate(this.serverId, slots);
        }

        @Nullable
        private PatternAccessTerminalInfoPacket createInfoPacket() {
            if (!this.hasLocation) {
                return null;
            }
            EnumFacing face = this.locationSide < 0 ? null : EnumFacing.byIndex(this.locationSide);
            return new PatternAccessTerminalInfoPacket(this.serverId, this.locationDimension,
                BlockPos.fromLong(this.locationPos), face);
        }

        @Nullable
        private IntList detectChangedSlots() {
            IntList changedSlots = null;
            for (int i = 0; i < this.server.size(); i++) {
                if (isDifferent(this.getVisibleStack(i), this.client.getStackInSlot(i))) {
                    if (changedSlots == null) {
                        changedSlots = new IntArrayList();
                    }
                    changedSlots.add(i);
                }
            }
            return changedSlots;
        }

        private ItemStack getVisibleStack(int slot) {
            ItemStack stack = this.server.getStackInSlot(slot);
            return isAcceptedByContainer(this.container, this.patternDecoder.decode(stack, this.level))
                ? stack
                : ItemStack.EMPTY;
        }
    }

    private record PatternSlotFilter(PatternContainer container, @Nullable World level,
                                     PatternDecoder patternDecoder) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty()
                && isAcceptedByContainer(this.container, this.patternDecoder.decode(stack, this.level));
        }
    }

    public record ProviderDiscoverySnapshot(List<PatternContainer> providers) {
        public ProviderDiscoverySnapshot {
            providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        }

        public static ProviderDiscoverySnapshot discover(IGrid grid) {
            Objects.requireNonNull(grid, "grid");
            List<PatternContainer> result = new ArrayList<>();
            for (Class<?> type : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(type)) {
                    for (Object object : grid.getActiveMachines(type.asSubclass(PatternContainer.class))) {
                        if (object instanceof PatternContainer provider) {
                            result.add(provider);
                        }
                    }
                }
            }
            return new ProviderDiscoverySnapshot(result);
        }
    }
}
