package ae2.container.implementations;

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
import ae2.helpers.InventoryAction;
import ae2.helpers.patternprovider.PatternContainer;
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
public final class PatternAccessSupport<C extends AEBaseContainer & IPatternAccess> {
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
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

    public PatternAccessSupport(Supplier<@Nullable IGrid> gridSupplier,
                                Supplier<ShowPatternProviders> shownProvidersSupplier,
                                Supplier<@Nullable World> worldSupplier,
                                Predicate<Slot> sourceSlotAllowed,
                                Consumer<ClientboundPacket> packetSender,
                                PlayerHandAccess playerHandAccess,
                                C ownerContainer) {
        this(gridSupplier, shownProvidersSupplier, worldSupplier, sourceSlotAllowed, packetSender, playerHandAccess,
            PatternDetailsHelper::decodePattern, ownerContainer);
    }

    PatternAccessSupport(Supplier<@Nullable IGrid> gridSupplier,
                         Supplier<ShowPatternProviders> shownProvidersSupplier,
                         Supplier<@Nullable World> worldSupplier,
                         Predicate<Slot> sourceSlotAllowed,
                         Consumer<ClientboundPacket> packetSender,
                         PlayerHandAccess playerHandAccess,
                         PatternDecoder patternDecoder) {
        this(gridSupplier, shownProvidersSupplier, worldSupplier, sourceSlotAllowed, packetSender, playerHandAccess,
            patternDecoder, null);
    }

    private PatternAccessSupport(Supplier<@Nullable IGrid> gridSupplier,
                                 Supplier<ShowPatternProviders> shownProvidersSupplier,
                                 Supplier<@Nullable World> worldSupplier,
                                 Predicate<Slot> sourceSlotAllowed,
                                 Consumer<ClientboundPacket> packetSender,
                                 PlayerHandAccess playerHandAccess,
                                 PatternDecoder patternDecoder,
                                 @Nullable C ownerContainer) {
        this.gridSupplier = Objects.requireNonNull(gridSupplier);
        this.shownProvidersSupplier = Objects.requireNonNull(shownProvidersSupplier);
        this.worldSupplier = Objects.requireNonNull(worldSupplier);
        this.sourceSlotAllowed = Objects.requireNonNull(sourceSlotAllowed);
        this.packetSender = Objects.requireNonNull(packetSender);
        this.playerHandAccess = Objects.requireNonNull(playerHandAccess);
        this.patternDecoder = Objects.requireNonNull(patternDecoder);
        this.ownerContainer = ownerContainer;
    }

    public void updateProviderVisibility() {
        if (getShownProviders() != ShowPatternProviders.NOT_FULL) {
            this.pinnedHosts.clear();
        }

        IGrid grid = this.gridSupplier.get();
        VisitorState state = new VisitorState();
        if (grid != null) {
            for (Class<?> machineClass : grid.getMachineClasses()) {
                Class<? extends PatternContainer> containerClass = tryCastMachineToContainer(machineClass);
                if (containerClass != null) {
                    visitPatternProviderHosts(grid, containerClass, state);
                }
            }

            this.pinnedHosts.removeIf(container -> container.getGrid() != grid);
        } else {
            this.pinnedHosts.clear();
        }

        if (!hasSameVisibleContainers(state.visibleContainers)) {
            state.forceFullUpdate = true;
        }

        if (state.total != this.diList.size() || state.forceFullUpdate) {
            sendFullUpdate(grid);
        } else {
            sendIncrementalUpdate();
        }
    }

    public void openProvider(EntityPlayer player, long inventoryId) {
        if (this.ownerContainer == null) {
            AELog.warn("Cannot open pattern provider GUI without an owner container");
            return;
        }

        ContainerTracker inv = this.byId.get(inventoryId);
        if (inv == null) {
            return;
        }

        PatternContainerGuiReturnContext.openFromPatternAccessTerminal(player, this.ownerContainer,
            () -> inv.container.openTerminalPatternContainerGui(player));
    }

    public boolean renameProvider(long inventoryId, @Nullable String name) {
        if (!isValidCustomName(name)) {
            return false;
        }

        ContainerTracker tracker = this.byId.get(inventoryId);
        if (tracker == null || !tracker.container.canEditTerminalName()) {
            return false;
        }

        tracker.container.setTerminalCustomName(name);
        sendFullUpdate(this.gridSupplier.get());
        return true;
    }

    public void renameGroup(long @Nullable [] inventoryIds, @Nullable String name) {
        if (inventoryIds == null || !isValidCustomName(name)) {
            return;
        }
        if (inventoryIds.length > this.byId.size()) {
            return;
        }

        LongOpenHashSet visited = new LongOpenHashSet(inventoryIds.length);
        boolean changedAny = false;
        for (long inventoryId : inventoryIds) {
            if (!visited.add(inventoryId)) {
                continue;
            }
            ContainerTracker tracker = this.byId.get(inventoryId);
            if (tracker == null || !tracker.container.canEditTerminalName()) {
                continue;
            }
            tracker.container.setTerminalCustomName(name);
            changedAny = true;
        }

        if (changedAny) {
            sendFullUpdate(this.gridSupplier.get());
        }
    }

    public void renameGroup(@Nullable RenamePatternGroupPayload payload) {
        if (payload == null) {
            return;
        }
        renameGroup(payload.inventoryIds(), payload.name());
    }

    public boolean toggleProviderVisibility(long inventoryId) {
        ContainerTracker tracker = this.byId.get(inventoryId);
        if (tracker == null || !tracker.container.canModifyTerminalVisibility()) {
            return false;
        }

        boolean visible = tracker.container.isVisibleInTerminal();
        tracker.container.setTerminalVisibility(!visible);
        sendFullUpdate(this.gridSupplier.get());
        return true;
    }

    public void renameProvider(@Nullable RenamePatternProviderPayload payload) {
        if (payload == null) {
            return;
        }
        renameProvider(payload.inventoryId(), payload.name());
    }

    public boolean doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        ContainerTracker inv = this.byId.get(id);
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

    public boolean quickMovePattern(@Nullable EntityPlayerMP player, Slot sourceSlot, LongList allowedPatternContainerIds,
                                    LongList allowedPatternSlots) {
        if (!this.sourceSlotAllowed.test(sourceSlot)) {
            return false;
        }

        ItemStack sourceStack = sourceSlot.getStack();
        World level = player != null ? player.world : this.worldSupplier.get();
        IPatternDetails pattern = this.patternDecoder.decode(sourceStack, level);
        if (pattern == null) {
            return false;
        }
        AEItemKey sourcePattern = AEItemKey.of(sourceStack);
        if (sourcePattern == null) {
            return false;
        }

        boolean assemblerPattern = pattern instanceof IAssemblerPattern;
        if (assemblerPattern) {
            return quickMoveAssemblerPattern(player, sourceSlot, sourcePattern);
        }

        List<QuickMoveTarget> targets = new ObjectArrayList<>();
        int targetCount = Math.min(allowedPatternContainerIds.size(), allowedPatternSlots.size());
        for (int i = 0; i < targetCount; i++) {
            ContainerTracker targetInventory = this.byId.get(allowedPatternContainerIds.getLong(i));
            if (targetInventory != null && isVisible(targetInventory.container)
                && targetInventory.container.isAssemblerPatternContainer() == assemblerPattern) {
                targets.add(new QuickMoveTarget(targetInventory, (int) allowedPatternSlots.getLong(i)));
            }
        }

        if (targets.stream().map(target -> target.container().group).distinct().count() != 1) {
            return false;
        }

        ReferenceSet<ContainerTracker> usedContainers = new ReferenceOpenHashSet<>();
        for (QuickMoveTarget target : targets) {
            if (movePatternToTarget(player, sourceSlot, sourcePattern, usedContainers, target.container(),
                target.slot())) {
                return true;
            }
        }
        return false;
    }

    void sendIncrementalUpdate() {
        for (ContainerTracker inv : this.diList.values()) {
            PatternAccessTerminalPacket packet = inv.createUpdatePacket();
            if (packet != null) {
                this.packetSender.accept(packet);
            }
        }
    }

    void sendFullUpdate(@Nullable IGrid grid) {
        this.byId.clear();
        this.diList.clear();

        this.packetSender.accept(new ClearPatternAccessTerminalPacket());

        if (grid == null) {
            return;
        }

        for (Class<?> machineClass : grid.getMachineClasses()) {
            Class<? extends PatternContainer> containerClass = tryCastMachineToContainer(machineClass);
            if (containerClass == null) {
                continue;
            }

            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (isVisible(container)) {
                    this.diList.put(container, new ContainerTracker(container, container.getTerminalPatternInventory(),
                        container.getTerminalGroup(), this.worldSupplier.get(), this.patternDecoder));
                }
            }
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

    boolean hasProvider(long inventoryId) {
        return this.byId.containsKey(inventoryId);
    }

    long getInventoryId(PatternContainer container) {
        ContainerTracker tracker = this.diList.get(container);
        if (tracker == null) {
            throw new IllegalArgumentException("Unknown pattern provider container: " + container);
        }
        return tracker.serverId;
    }

    @Nullable
    private static Class<? extends PatternContainer> tryCastMachineToContainer(Class<?> machineClass) {
        if (PatternContainer.class.isAssignableFrom(machineClass)) {
            return machineClass.asSubclass(PatternContainer.class);
        }
        return null;
    }

    private static boolean isAcceptedByContainer(PatternContainer container, @Nullable IPatternDetails details) {
        return details != null && (details instanceof IAssemblerPattern) == container.isAssemblerPatternContainer();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isValidCustomName(@Nullable String name) {
        return name != null && name.length() <= MAX_CUSTOM_NAME_LENGTH;
    }

    private ShowPatternProviders getShownProviders() {
        return this.shownProvidersSupplier.get();
    }

    private boolean isFull(PatternContainer container) {
        for (int i = 0; i < container.getTerminalPatternInventory().size(); i++) {
            if (container.getTerminalPatternInventory().getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isVisible(PatternContainer container) {
        boolean visible = container.isVisibleInTerminal();
        return switch (getShownProviders()) {
            case VISIBLE -> visible;
            case HIDDEN -> !visible;
            case NOT_FULL -> visible && (this.pinnedHosts.contains(container) || !isFull(container));
            case ALL -> true;
        };
    }

    private boolean hasSameVisibleContainers(ReferenceSet<PatternContainer> visibleContainers) {
        if (visibleContainers.size() != this.diList.size()) {
            return false;
        }

        for (PatternContainer container : this.diList.keySet()) {
            if (!visibleContainers.contains(container)) {
                return false;
            }
        }

        return true;
    }

    private <T extends PatternContainer> void visitPatternProviderHosts(IGrid grid, Class<T> machineClass,
                                                                        VisitorState state) {
        for (T container : grid.getActiveMachines(machineClass)) {
            if (!isVisible(container)) {
                continue;
            }

            state.visibleContainers.add(container);

            if (getShownProviders() == ShowPatternProviders.NOT_FULL) {
                this.pinnedHosts.add(container);
            }

            ContainerTracker tracker = this.diList.get(container);
            if (tracker == null || tracker.server.size() != container.getTerminalPatternInventory().size()
                || !tracker.group.equals(container.getTerminalGroup())) {
                state.forceFullUpdate = true;
            }

            state.total++;
        }
    }

    private boolean quickMoveAssemblerPattern(@Nullable EntityPlayerMP player, Slot sourceSlot, AEItemKey sourcePattern) {
        ReferenceSet<ContainerTracker> usedContainers = new ReferenceOpenHashSet<>();
        for (ContainerTracker targetInventory : this.diList.values()) {
            if (!isVisible(targetInventory.container)
                || !targetInventory.container.isAssemblerPatternContainer()) {
                continue;
            }
            for (int slot = 0; slot < targetInventory.server.size(); slot++) {
                if (movePatternToTarget(player, sourceSlot, sourcePattern, usedContainers, targetInventory, slot)) {
                    return true;
                }
                if (usedContainers.contains(targetInventory)) {
                    break;
                }
            }
        }
        return false;
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

    private static final class VisitorState {
        private final ReferenceSet<PatternContainer> visibleContainers = new ReferenceOpenHashSet<>();
        private int total;
        private boolean forceFullUpdate;
    }

    private record QuickMoveTarget(ContainerTracker container, int slot) {
    }

    private record ProviderLocation(int dimensionId, BlockPos pos, @Nullable EnumFacing face) {
    }

    private static final class ContainerTracker {
        private final PatternContainer container;
        private final long sortBy;
        private final long serverId = inventorySerial++;
        private final PatternContainerGroup group;
        private final InternalInventory client;
        private final InternalInventory server;
        @Nullable
        private final World level;
        private final PatternDecoder patternDecoder;

        private ContainerTracker(PatternContainer container, InternalInventory patterns, PatternContainerGroup group,
                                 @Nullable World level, PatternDecoder patternDecoder) {
            this.container = container;
            this.server = patterns;
            this.client = new AppEngInternalInventory(this.server.size());
            this.group = group;
            this.sortBy = container.getTerminalSortOrder();
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

        @Nullable
        private static ProviderLocation resolveLocation(PatternContainer container) {
            if (container instanceof TileEntity tile) {
                if (tile.getWorld() == null) {
                    return null;
                }
                return new ProviderLocation(tile.getWorld().provider.getDimension(), tile.getPos(), null);
            }
            if (container instanceof AEBasePart part && part.getTileEntity() != null
                && part.getTileEntity().getWorld() != null) {
                TileEntity tile = part.getTileEntity();
                return new ProviderLocation(tile.getWorld().provider.getDimension(), tile.getPos(), part.getSide());
            }
            return null;
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
                this.container.canEditTerminalName(), this.container.canModifyTerminalVisibility(), this.group, slots);
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
            ProviderLocation location = resolveLocation(this.container);
            if (location == null) {
                return null;
            }
            return new PatternAccessTerminalInfoPacket(this.serverId, location.dimensionId(), location.pos(),
                location.face());
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
