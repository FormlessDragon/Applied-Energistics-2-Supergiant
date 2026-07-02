package ae2.container.implementations;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.ISubGuiHost;
import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.container.guisync.GuiSync;
import ae2.container.guisync.PacketWritable;
import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.parts.AEBasePart;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ContainerProviderSelect extends AEBaseContainer implements ISubGui {

    private static final short SYNC_PROVIDER_ENTRIES = 1;
    private static final short SYNC_INITIAL_SEARCH_TEXT = 2;
    private static final short SYNC_MAPPING_MODE = 3;
    private static final short SYNC_INITIAL_STATE_VERSION = 4;
    private static final String ACTION_UPLOAD_TO_PROVIDER = "uploadToProvider";
    private static final String ACTION_BIND_PROVIDER_MAPPING = "bindProviderMapping";
    private static final String ACTION_UNBIND_PROVIDER_MAPPING = "unbindProviderMapping";
    private static final String ACTION_SET_MAPPING_MODE = "setMappingMode";
    private static final int MAX_MAPPING_ACTION_PAYLOAD_LENGTH = 1024;

    private final IPatternTerminalGuiHost host;
    private final Long2ObjectMap<ProviderEntry> entriesById = new Long2ObjectLinkedOpenHashMap<>();
    private final Reference2LongMap<PatternContainer> idsByContainer = new Reference2LongOpenHashMap<>();
    private final IntList filteredEntryIndexes = new IntArrayList();
    @GuiSync(SYNC_PROVIDER_ENTRIES)
    private ProviderEntries providerEntries = ProviderEntries.empty();
    @GuiSync(SYNC_INITIAL_SEARCH_TEXT)
    private String initialSearchText = "";
    @GuiSync(SYNC_MAPPING_MODE)
    private boolean mappingMode;
    @GuiSync(SYNC_INITIAL_STATE_VERSION)
    private int initialStateVersion;
    private String searchText = "";
    private boolean providerEntriesChanged;
    private long nextEntryId;

    public ContainerProviderSelect(InventoryPlayer playerInventory, @Nullable IPatternTerminalGuiHost host) {
        super(playerInventory, host);
        this.host = host;

        registerClientAction(ACTION_UPLOAD_TO_PROVIDER, Long.class, this::uploadToProvider);
        registerClientAction(ACTION_BIND_PROVIDER_MAPPING, ProviderMappingAction.class,
            MAX_MAPPING_ACTION_PAYLOAD_LENGTH, this::bindProviderMapping);
        registerClientAction(ACTION_UNBIND_PROVIDER_MAPPING, ProviderMappingAction.class,
            MAX_MAPPING_ACTION_PAYLOAD_LENGTH, this::unbindProviderMapping);
        registerClientAction(ACTION_SET_MAPPING_MODE, Boolean.class, this::setMappingMode);
        if (isServerSide()) {
            rebuildEntries(getGrid());
        }
    }

    public int getVisibleEntryCount() {
        return this.filteredEntryIndexes.size();
    }

    public String getVisibleEntryLabel(int visibleIndex) {
        return getVisibleEntry(visibleIndex).label();
    }

    public long getVisibleEntryId(int visibleIndex) {
        return getVisibleEntry(visibleIndex).id();
    }

    public ProviderReference getVisibleEntryReference(int visibleIndex) {
        return getVisibleEntry(visibleIndex).reference();
    }

    public void setSearchText(@Nullable String searchText) {
        String updatedSearchText = searchText == null ? "" : searchText;
        if (this.searchText.equals(updatedSearchText)) {
            return;
        }

        this.searchText = updatedSearchText;
        rebuildFilteredEntries();
    }

    public String getInitialSearchText() {
        return this.initialSearchText;
    }

    public boolean isMappingMode() {
        return this.mappingMode;
    }

    public int getInitialStateVersion() {
        return this.initialStateVersion;
    }

    public void setInitialState(@Nullable String initialSearchText, boolean mappingMode) {
        if (isClientSide()) {
            return;
        }

        this.initialSearchText = initialSearchText == null ? "" : initialSearchText.trim();
        this.searchText = this.initialSearchText;
        setMappingModeState(mappingMode);
        this.initialStateVersion++;
        rebuildFilteredEntries();
    }

    public boolean consumeProviderEntriesChanged() {
        boolean changed = this.providerEntriesChanged;
        this.providerEntriesChanged = false;
        return changed;
    }

    public void uploadToProvider(long providerId) {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_TO_PROVIDER, providerId);
            return;
        }

        IGrid grid = getGrid();
        rebuildEntries(grid);
        ProviderEntry entry = this.entriesById.get(providerId);
        if (entry == null) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return;
        }

        ItemStack encodedPattern = this.host.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (tryUploadProcessingPatternToProvider(getPlayer(), this.host, grid, entry.container, encodedPattern)) {
            this.host.returnToMainContainer(getPlayer(), this);
        }
    }

    public void bindProviderMapping(ProviderReference reference, String searchText) {
        ProviderMappingAction action = new ProviderMappingAction(reference, searchText);
        if (isClientSide()) {
            sendClientAction(ACTION_BIND_PROVIDER_MAPPING, action);
            return;
        }
        bindProviderMapping(action);
    }

    public void unbindProviderMapping(ProviderReference reference) {
        ProviderMappingAction action = new ProviderMappingAction(reference);
        if (isClientSide()) {
            sendClientAction(ACTION_UNBIND_PROVIDER_MAPPING, action);
            return;
        }
        unbindProviderMapping(action);
    }

    public void setMappingMode(boolean mappingMode) {
        if (isClientSide()) {
            setMappingModeState(mappingMode);
            sendClientAction(ACTION_SET_MAPPING_MODE, mappingMode);
            return;
        }
        setMappingModeState(mappingMode);
    }

    private void bindProviderMapping(ProviderMappingAction action) {
        if (action == null) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingBlank.text(), false);
            return;
        }

        String recipeType = getMappingActionRecipeType(action);
        if (recipeType.isEmpty()) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingBlank.text(), false);
            return;
        }

        ProviderEntry entry = getMappingActionEntry(action);
        if (entry == null) {
            return;
        }
        PatternProviderMappingData.get(getPlayer().world).bind(recipeType, entry.reference);
        rebuildEntries(getGrid());
    }

    private void unbindProviderMapping(ProviderMappingAction action) {
        ProviderEntry entry = getMappingActionEntry(action);
        if (entry == null) {
            return;
        }
        PatternProviderMappingData.get(getPlayer().world).unbindAll(entry.reference);
        rebuildEntries(getGrid());
    }

    @Nullable
    private ProviderEntry getMappingActionEntry(ProviderMappingAction action) {
        if (action == null) {
            getPlayer().sendStatusMessage(PlayerMessages.PatternProviderMappingBlank.text(), false);
            return null;
        }

        rebuildEntries(getGrid());
        if (action.hasReference()) {
            ProviderReference reference = action.reference();
            for (ProviderEntry entry : this.entriesById.values()) {
                if (entry.reference.equals(reference)) {
                    return entry;
                }
            }
        } else {
            AELog.warn("Ignoring provider mapping action without a complete provider reference");
            getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return null;
        }

        getPlayer().sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
        return null;
    }

    private String getMappingActionRecipeType(ProviderMappingAction action) {
        return action.searchText() == null ? "" : action.searchText().trim();
    }

    public static boolean tryUploadProcessingPatternToProvider(EntityPlayer player,
                                                              @Nullable IPatternTerminalGuiHost host,
                                                              @Nullable IGrid grid,
                                                              PatternContainer container,
                                                              ItemStack encodedPattern) {
        if (host == null) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return false;
        }

        ILinkStatus linkStatus = host.getLinkStatus();
        if (!linkStatus.connected()) {
            if (linkStatus.statusDescription() != null) {
                player.sendStatusMessage(linkStatus.statusDescription(), false);
            }
            return false;
        }

        if (grid == null || !isSelectableProvider(container)) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return false;
        }

        if (!PatternDetailsHelper.isEncodedPattern(encodedPattern)) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return false;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(encodedPattern, player.world);
        if (details == null || details instanceof IAssemblerPattern) {
            player.sendStatusMessage(PlayerMessages.PatternUploadProcessingOnly.text(), false);
            return false;
        }

        AEItemKey patternKey = AEItemKey.of(encodedPattern);
        if (patternKey == null) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoEncodedPattern.text(), false);
            return false;
        }

        if (container.containsPattern(patternKey)) {
            player.sendStatusMessage(PlayerMessages.PatternUploadDuplicateInContainer.text(), false);
            return false;
        }

        if (!movePatternToFirstAvailableSlot(player, container, encodedPattern.copy())) {
            player.sendStatusMessage(PlayerMessages.PatternUploadNoProviderTarget.text(), false);
            return false;
        }

        host.getLogic().getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
        return true;
    }

    public static List<PatternContainer> findProcessingPatternUploadTargets(World world, IGrid grid, String recipeType) {
        if (recipeType.isEmpty()) {
            AELog.warn("recipeType must not be blank");
        }

        List<PatternContainer> selectableProviders = collectSelectableProviders(grid);
        MappingSnapshot mappings = cleanAndSnapshotMappings(world, selectableProviders);
        List<PatternContainer> uploadTargets = new ObjectArrayList<>();
        for (PatternContainer container : selectableProviders) {
            if (countEmptySlots(container) <= 0) {
                continue;
            }

            ProviderReference reference = createProviderReference(container);
            if (recipeType.equals(getProviderName(container))
                || reference != null && mappings.getRecipeTypes(reference).contains(recipeType)) {
                uploadTargets.add(container);
            }
        }
        return uploadTargets;
    }

    public static boolean hasAvailableProvider(IGrid grid) {
        for (PatternContainer container : collectSelectableProviders(grid)) {
            if (countEmptySlots(container) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ISubGuiHost getHost() {
        return this.host;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            rebuildEntries(getGrid());
        }

        super.broadcastChanges();
    }

    @Override
    public void onClientDataSync(ShortSet updatedFields) {
        super.onClientDataSync(updatedFields);
        boolean rebuildFilteredEntries = false;
        if (updatedFields.contains(SYNC_PROVIDER_ENTRIES)) {
            this.providerEntriesChanged = true;
            rebuildFilteredEntries = true;
        }
        if (updatedFields.contains(SYNC_INITIAL_SEARCH_TEXT) || updatedFields.contains(SYNC_INITIAL_STATE_VERSION)) {
            this.searchText = this.initialSearchText;
            rebuildFilteredEntries = true;
        }
        if (updatedFields.contains(SYNC_MAPPING_MODE)) {
            rebuildFilteredEntries = true;
        }
        if (rebuildFilteredEntries) {
            rebuildFilteredEntries();
        }
    }

    public static List<PatternContainer> collectSelectableProviders(IGrid grid) {
        List<PatternContainer> containers = new ObjectArrayList<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }

            Class<? extends PatternContainer> containerClass = machineClass.asSubclass(PatternContainer.class);
            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (isSelectableProvider(container)) {
                    containers.add(container);
                }
            }
        }
        containers.sort(Comparator.comparingLong(PatternContainer::getTerminalSortOrder));
        return containers;
    }

    private void setProviderEntries(List<DisplayEntry> displayEntries) {
        this.providerEntries = new ProviderEntries(displayEntries);
        rebuildFilteredEntries();
    }

    private void setMappingModeState(boolean mappingMode) {
        if (this.mappingMode == mappingMode) {
            return;
        }

        this.mappingMode = mappingMode;
        rebuildFilteredEntries();
    }

    private void rebuildFilteredEntries() {
        this.filteredEntryIndexes.clear();
        String search = normalizeSearchText(this.searchText);
        if (this.mappingMode) {
            search = "";
        }

        List<DisplayEntry> entries = this.providerEntries.entries();
        for (int i = 0; i < entries.size(); i++) {
            String entrySearchText = entries.get(i).searchText().toLowerCase(Locale.ROOT);
            if (search.isEmpty() || entrySearchText.contains(search)) {
                this.filteredEntryIndexes.add(i);
            }
        }
    }

    private static MappingSnapshot cleanAndSnapshotMappings(World world, List<PatternContainer> containers) {
        PatternProviderMappingData mappingData = PatternProviderMappingData.get(world);
        Set<ProviderReference> availableReferences = new ObjectLinkedOpenHashSet<>();
        for (PatternContainer container : containers) {
            ProviderReference reference = createProviderReference(container);
            if (reference != null) {
                availableReferences.add(reference);
            }
        }
        mappingData.removeUnavailableReferences(availableReferences);
        return new MappingSnapshot(mappingData, availableReferences);
    }

    private static String normalizeSearchText(String searchText) {
        return searchText.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private IGrid getGrid() {
        if (this.host == null) {
            return null;
        }

        if (!(this.host instanceof IActionHost actionHost)) {
            return null;
        }

        IGridNode node = actionHost.getActionableNode();
        if (node == null || !node.isActive()) {
            return null;
        }

        return node.grid();
    }

    private void rebuildEntries(@Nullable IGrid grid) {
        this.entriesById.clear();

        List<DisplayEntry> displayEntries = new ObjectArrayList<>();
        if (grid == null) {
            setProviderEntries(displayEntries);
            return;
        }

        List<PatternContainer> containers = collectSelectableProviders(grid);
        MappingSnapshot mappings = cleanAndSnapshotMappings(getPlayer().world, containers);
        for (PatternContainer container : containers) {
            ProviderReference reference = createProviderReference(container);
            if (reference == null) {
                continue;
            }

            int emptySlots = countEmptySlots(container);
            long providerId = this.idsByContainer.computeIfAbsent(container, ignored -> this.nextEntryId++);
            String providerName = getProviderName(container);
            List<String> recipeTypes = mappings.getRecipeTypes(reference);
            String label = formatProviderLabel(recipeTypes, providerName, emptySlots);
            ProviderEntry entry = new ProviderEntry(providerId, container, reference);
            this.entriesById.put(entry.id, entry);
            displayEntries.add(new DisplayEntry(providerId, reference, label, providerName,
                buildSearchText(label, providerName, recipeTypes)));
        }

        setProviderEntries(displayEntries);
    }

    private DisplayEntry getVisibleEntry(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= this.filteredEntryIndexes.size()) {
            AELog.warn("Invalid visible provider entry index: {} of {}", visibleIndex, this.filteredEntryIndexes.size());
        }

        int entryIndex = this.filteredEntryIndexes.getInt(visibleIndex);
        List<DisplayEntry> entries = this.providerEntries.entries();
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            AELog.warn("Invalid provider entry index from filtered entries: {} of {}", entryIndex, entries.size());
        }

        return entries.get(entryIndex);
    }

    @Nullable
    private static ProviderReference createProviderReference(PatternContainer container) {
        if (!(container instanceof PatternProviderLogicHost host)) {
            return null;
        }

        TileEntity tile = host.getTileEntity();
        if (tile == null || tile.getWorld() == null) {
            return null;
        }

        EnumFacing side = container instanceof AEBasePart part ? part.getSide() : null;
        return new ProviderReference(tile.getWorld().provider.getDimension(), tile.getPos().toLong(),
            side == null ? -1 : side.ordinal());
    }

    private static boolean isSelectableProvider(PatternContainer container) {
        return container.isVisibleInTerminal() && !container.isAssemblerPatternContainer();
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
        if (name == null) {
            return container.getClass().getSimpleName();
        }
        return name.getFormattedText();
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
        for(String recipeType : recipeTypes) {
            label.append("[").append(recipeType).append("]");
        }
        label.append(name).append(" (").append(emptySlots).append(")");
        return label.toString();
    }

    private static boolean movePatternToFirstAvailableSlot(EntityPlayer player, PatternContainer container,
                                                           ItemStack encodedPattern) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }

            InternalInventory targetSlot = new FilteredInternalInventory(inventory.getSlotInv(slot),
                new PatternSlotFilter(container, player.world));
            ItemStack remainder = targetSlot.addItems(encodedPattern.copy());
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private record ProviderEntry(long id, PatternContainer container, ProviderReference reference) {
    }

    public static final class ProviderMappingAction {
        private Integer dimension;
        private Long pos;
        private Integer side;
        private String searchText;

        @SuppressWarnings("unused")
        public ProviderMappingAction() {
        }

        public ProviderMappingAction(ProviderReference reference) {
            this(reference, null);
        }

        public ProviderMappingAction(ProviderReference reference, String searchText) {
            Objects.requireNonNull(reference, "reference");
            this.dimension = reference.dimension();
            this.pos = reference.pos();
            this.side = reference.side();
            this.searchText = searchText;
        }

        public boolean hasReference() {
            return this.dimension != null && this.pos != null && this.side != null;
        }

        public ProviderReference reference() {
            if (!hasReference()) {
                AELog.warn("Provider mapping action has no complete provider reference");
            }
            return new ProviderReference(this.dimension, this.pos, this.side);
        }

        public String searchText() {
            return this.searchText;
        }
    }

    private record MappingSnapshot(PatternProviderMappingData mappingData, Set<ProviderReference> availableReferences) {
        private List<String> getRecipeTypes(ProviderReference reference) {
            if (!this.availableReferences.contains(reference)) {
                return Collections.emptyList();
            }
            return new ObjectArrayList<>(this.mappingData.getRecipeTypes(reference));
        }
    }

    private record PatternSlotFilter(PatternContainer container, World level) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty()
                && isAcceptedByContainer(this.container, PatternDetailsHelper.decodePattern(stack, this.level));
        }
    }

    private static boolean isAcceptedByContainer(PatternContainer container, @Nullable IPatternDetails details) {
        return details != null && (details instanceof IAssemblerPattern) == container.isAssemblerPatternContainer();
    }

    public record DisplayEntry(long id, ProviderReference reference, String label, String providerName,
                               String searchText) {
        public DisplayEntry {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(providerName, "providerName");
            Objects.requireNonNull(searchText, "searchText");
        }
    }

    public static final class ProviderEntries implements PacketWritable {
        private static final ProviderEntries EMPTY = new ProviderEntries(Collections.emptyList());
        private static final int ENTRY_HEADER_BYTES = 2 * Long.BYTES + 5 * Integer.BYTES;

        private final List<DisplayEntry> entries;

        @SuppressWarnings("unused")
        public ProviderEntries(ByteBuf data) {
            int entryCount = data.readInt();
            if (entryCount < 0 || entryCount > data.readableBytes() / ENTRY_HEADER_BYTES) {
                AELog.warn("Invalid provider entry count: {}", entryCount);
            }

            List<DisplayEntry> entries = new ObjectArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                long id = data.readLong();
                ProviderReference reference = new ProviderReference(data.readInt(), data.readLong(), data.readInt());
                entries.add(new DisplayEntry(id, reference, readString(data), readString(data), readString(data)));
            }
            this.entries = List.copyOf(entries);
        }

        private ProviderEntries(List<DisplayEntry> entries) {
            this.entries = List.copyOf(entries);
        }

        public static ProviderEntries empty() {
            return EMPTY;
        }

        public List<DisplayEntry> entries() {
            return this.entries;
        }

        @Override
        public void writeToPacket(ByteBuf data) {
            data.writeInt(this.entries.size());
            for (DisplayEntry entry : this.entries) {
                data.writeLong(entry.id());
                data.writeInt(entry.reference().dimension());
                data.writeLong(entry.reference().pos());
                data.writeInt(entry.reference().side());
                writeString(data, entry.label());
                writeString(data, entry.providerName());
                writeString(data, entry.searchText());
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof ProviderEntries that && this.entries.equals(that.entries);
        }

        @Override
        public int hashCode() {
            return this.entries.hashCode();
        }

        private static void writeString(ByteBuf data, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            data.writeInt(bytes.length);
            data.writeBytes(bytes);
        }

        private static String readString(ByteBuf data) {
            int length = data.readInt();
            if (length < 0 || length > data.readableBytes()) {
                AELog.warn("Invalid provider entry label length: {}", length);
            }

            byte[] bytes = new byte[length];
            data.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
