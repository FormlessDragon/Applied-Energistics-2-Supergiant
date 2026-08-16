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
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.helpers.InventoryAction;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.me.service.ActivePatternProviderDirectory;
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

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared server-side behavior for containers that expose pattern provider inventories.
 * <p>
 * PAT and PEAT own their GUI-specific state. This support owns only provider discovery, provider state indexes,
 * provider inventory interaction, server validation, and packet emission.
 */
public final class PatternAccessSession<C extends AEBaseContainer & IPatternAccess> {
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private static final int PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS = 10;
    private static long inventorySerial = Long.MIN_VALUE;

    private final Supplier<@Nullable IGrid> gridSupplier;
    private final Supplier<ShowPatternProviders> shownProvidersSupplier;
    private final Supplier<@Nullable World> worldSupplier;
    private final Predicate<Slot> sourceSlotAllowed;
    private final Consumer<ClientboundPacket> packetSender;
    private final PlayerHandAccess playerHandAccess;
    private final PatternDecoder patternDecoder;
    private final C ownerContainer;
    private final Reference2ObjectMap<PatternContainer, ContainerTracker> diList = new Reference2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ContainerTracker> byId = new Long2ObjectOpenHashMap<>();
    private final ReferenceSet<PatternContainer> pinnedHosts = new ReferenceOpenHashSet<>();
    private final Reference2LongOpenHashMap<PatternContainer> providerIdentityOrdinals = new Reference2LongOpenHashMap<>();
    private List<ProviderStamp> providerDirectorySignature = List.of();
    @Nullable
    private IGrid observedGrid;
    @Nullable
    private ShowPatternProviders observedShownProviders;
    private long nextProviderIdentityOrdinal;
    private int ticksUntilProviderDirectoryScan;
    private boolean providerDirectoryInitialized;

    public PatternAccessSession(Supplier<@Nullable IGrid> gridSupplier,
                                Supplier<ShowPatternProviders> shownProvidersSupplier,
                                Supplier<@Nullable World> worldSupplier,
                                Predicate<Slot> sourceSlotAllowed,
                                Consumer<ClientboundPacket> packetSender,
                                PlayerHandAccess playerHandAccess,
                                C ownerContainer) {
        this.gridSupplier = gridSupplier;
        this.shownProvidersSupplier = shownProvidersSupplier;
        this.worldSupplier = worldSupplier;
        this.sourceSlotAllowed = sourceSlotAllowed;
        this.packetSender = packetSender;
        this.playerHandAccess = playerHandAccess;
        this.patternDecoder = PatternDetailsHelper::decodePattern;
        this.ownerContainer = ownerContainer;
    }

    public void updateProviderVisibility() {
        IGrid grid = this.gridSupplier.get();
        updateProviderVisibility(grid == null ? null
            : grid.getService(ActivePatternProviderDirectory.class).getActiveProviders());
    }

    public void updateProviderVisibility(@Nullable List<PatternContainer> discovery) {
        IGrid grid = this.gridSupplier.get();
        ShowPatternProviders shownProviders = getShownProviders();
        if (grid == null || discovery == null) {
            updateDisconnectedDirectory(shownProviders);
            return;
        }

        boolean rebuildTrackers = !this.providerDirectoryInitialized
            || grid != this.observedGrid
            || shownProviders != this.observedShownProviders
            || hasTrackerInventorySizeChanged();
        boolean scheduledScan = --this.ticksUntilProviderDirectoryScan <= 0;
        if (!rebuildTrackers && !scheduledScan) {
            if (sendIncrementalUpdate()) {
                return;
            }
            rebuildTrackers = true;
        }

        List<ProviderDirectoryEntry> providers = collectPatternAccessProviders(discovery, shownProviders);
        List<ProviderStamp> signature = createProviderSignature(providers);
        boolean directoryChanged = rebuildTrackers
            || !this.providerDirectorySignature.equals(signature);
        rememberProviderDirectory(grid, shownProviders, signature);
        if (directoryChanged) {
            sendFullUpdate(grid, providers);
            return;
        }

        if (!sendIncrementalUpdate()) {
            sendFullUpdate(grid, providers);
        }
    }

    private void updateDisconnectedDirectory(ShowPatternProviders shownProviders) {
        boolean directoryChanged = this.observedGrid != null || !this.diList.isEmpty();
        this.pinnedHosts.clear();
        this.providerIdentityOrdinals.clear();
        this.providerDirectorySignature = List.of();
        this.observedGrid = null;
        this.observedShownProviders = shownProviders;
        this.ticksUntilProviderDirectoryScan = 0;
        this.providerDirectoryInitialized = true;
        if (directoryChanged) {
            sendFullUpdate(null, List.of());
        } else {
            sendIncrementalUpdate();
        }
    }

    private void rememberProviderDirectory(IGrid grid, ShowPatternProviders shownProviders, List<ProviderStamp> signature) {
        this.observedGrid = Objects.requireNonNull(grid, "grid");
        this.observedShownProviders = Objects.requireNonNull(shownProviders, "shownProviders");
        this.providerDirectorySignature = List.copyOf(Objects.requireNonNull(signature, "signature"));
        this.ticksUntilProviderDirectoryScan = PROVIDER_DIRECTORY_SCAN_INTERVAL_TICKS;
        this.providerDirectoryInitialized = true;
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

    private boolean sendIncrementalUpdate() {
        for (ContainerTracker inv : this.diList.values()) {
            PatternAccessTerminalPacket packet = inv.createUpdatePacket();
            if (packet != null && !sendPatternAccessPacket(packet)) {
                return false;
            }
            if (packet != null) {
                inv.synchronizeClientSnapshot();
            }
        }
        return true;
    }

    void sendFullUpdate(@Nullable IGrid grid) {
        ShowPatternProviders shownProviders = getShownProviders();
        if (grid == null) {
            this.pinnedHosts.clear();
            this.providerIdentityOrdinals.clear();
            this.providerDirectorySignature = List.of();
            this.observedGrid = null;
            this.observedShownProviders = shownProviders;
            this.ticksUntilProviderDirectoryScan = 0;
            this.providerDirectoryInitialized = true;
            sendFullUpdate(null, List.of());
            return;
        }

        List<ProviderDirectoryEntry> providers = collectPatternAccessProviders(
            grid.getService(ActivePatternProviderDirectory.class).getActiveProviders(), shownProviders);
        List<ProviderStamp> signature = createProviderSignature(providers);
        rememberProviderDirectory(grid, shownProviders, signature);
        sendFullUpdate(grid, providers);
    }

    private void sendFullUpdate(@Nullable IGrid grid, List<ProviderDirectoryEntry> providers) {
        Objects.requireNonNull(providers, "providers");
        Reference2LongOpenHashMap<PatternContainer> previousIds = new Reference2LongOpenHashMap<>();
        for (ContainerTracker tracker : this.diList.values()) {
            previousIds.put(tracker.container, tracker.serverId);
        }

        if (grid == null) {
            this.byId.clear();
            this.diList.clear();
            this.packetSender.accept(new ClearPatternAccessTerminalPacket());
            return;
        }

        Reference2ObjectMap<PatternContainer, ContainerTracker> nextTrackers =
            new Reference2ObjectLinkedOpenHashMap<>();
        Long2ObjectOpenHashMap<ContainerTracker> nextTrackersById = new Long2ObjectOpenHashMap<>();
        List<ContainerTracker> trackers = new ObjectArrayList<>(providers.size());
        List<ClientboundPacket> packets = new ObjectArrayList<>();
        for (ProviderDirectoryEntry provider : providers) {
            long serverId = previousIds.containsKey(provider.container())
                ? previousIds.getLong(provider.container())
                : inventorySerial++;
            ContainerTracker tracker = new ContainerTracker(provider, this.worldSupplier.get(), this.patternDecoder, serverId);
            nextTrackers.put(provider.container(), tracker);
            nextTrackersById.put(serverId, tracker);
            trackers.add(tracker);
        }

        for (ContainerTracker tracker : trackers) {
            List<ClientboundPacket> updatePackets = PatternAccessTerminalPacket.createPackets(
                tracker.createFullPacket(), this.ownerContainer.windowId);
            if (updatePackets == null) {
                scheduleProviderDirectoryRebuild();
                return;
            }
            packets.addAll(updatePackets);
            PatternAccessTerminalInfoPacket infoPacket = tracker.createInfoPacket();
            if (infoPacket != null) {
                packets.add(infoPacket);
            }
        }

        this.byId.clear();
        this.byId.putAll(nextTrackersById);
        this.diList.clear();
        this.diList.putAll(nextTrackers);
        this.packetSender.accept(new ClearPatternAccessTerminalPacket());
        for (ClientboundPacket packet : packets) {
            this.packetSender.accept(packet);
        }
        for (ContainerTracker tracker : trackers) {
            tracker.synchronizeClientSnapshot();
        }
    }

    private boolean hasTrackerInventorySizeChanged() {
        for (ContainerTracker tracker : this.diList.values()) {
            if (tracker.hasInventorySizeChanged()) {
                return true;
            }
        }
        return false;
    }

    private boolean sendPatternAccessPacket(PatternAccessTerminalPacket packet) {
        List<ClientboundPacket> packets = PatternAccessTerminalPacket.createPackets(packet, this.ownerContainer.windowId);
        if (packets == null) {
            scheduleProviderDirectoryRebuild();
            return false;
        }
        for (ClientboundPacket wirePacket : packets) {
            this.packetSender.accept(wirePacket);
        }
        return true;
    }

    private void scheduleProviderDirectoryRebuild() {
        this.providerDirectoryInitialized = false;
        this.ticksUntilProviderDirectoryScan = 0;
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
        providers.sort(PatternAccessSession::compareProviderEntries);
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

    private record ProviderLocation(int dimensionId, long pos, int side) {
    }

    private record ProviderDirectoryEntry(PatternContainer container, long identityOrdinal, long sortBy,
                                          PatternContainerGroup group, int inventorySize, int emptySlots,
                                          boolean visibleInTerminal,
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
                container.isVisibleInTerminal(), container.canEditTerminalName(),
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
                && left.visibleInTerminal() == right.visibleInTerminal()
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
                value.group(), value.inventorySize(), value.visibleInTerminal(),
                value.canEditTerminalName(), value.canModifyTerminalVisibility(),
                value.reference(), value.hasLocation(), value.locationDimension(), value.locationPos(),
                value.locationSide());
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
        private final boolean hasLocation;
        private final int locationDimension;
        private final long locationPos;
        private final int locationSide;
        @Nullable
        private final World level;
        private final PatternDecoder patternDecoder;

        private ContainerTracker(ProviderDirectoryEntry provider,
                                 @Nullable World level, PatternDecoder patternDecoder,
                                 long serverId) {
            this.container = provider.container();
            this.serverId = serverId;
            this.server = provider.container().getTerminalPatternInventory();
            this.client = new AppEngInternalInventory(this.server.size());
            this.group = provider.group();
            this.sortBy = provider.sortBy();
            this.canEditTerminalName = provider.canEditTerminalName();
            this.canModifyTerminalVisibility = provider.canModifyTerminalVisibility();
            this.hasLocation = provider.hasLocation();
            this.locationDimension = provider.locationDimension();
            this.locationPos = provider.locationPos();
            this.locationSide = provider.locationSide();
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

        private PatternAccessTerminalPacket createFullPacket() {
            Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(this.server.size());
            for (int i = 0; i < this.server.size(); i++) {
                ItemStack stack = this.getVisibleStack(i);
                if (!stack.isEmpty()) {
                    slots.put(i, stack);
                }
            }

            return PatternAccessTerminalPacket.fullUpdate(this.serverId, this.server.size(), this.sortBy,
                this.canEditTerminalName, this.canModifyTerminalVisibility, this.group, slots);
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
                slots.put(slot, stack);
            }

            return PatternAccessTerminalPacket.incrementalUpdate(this.serverId, slots);
        }

        private boolean hasInventorySizeChanged() {
            return this.client.size() != this.server.size();
        }

        private void synchronizeClientSnapshot() {
            for (int i = 0; i < this.client.size(); i++) {
                ItemStack stack = this.getVisibleStack(i);
                this.client.setItemDirect(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
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

}
