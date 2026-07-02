package ae2.container.implementations;

import ae2.api.cellterminal.CellTerminalBusPartitionMode;
import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalBusTextPartitionSnapshot;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalContentSnapshot;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalSubnetConnection;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.config.AccessRestriction;
import ae2.api.config.FuzzyMode;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.cells.StorageCell;
import ae2.cellterminal.server.CellTerminalActionFailure;
import ae2.cellterminal.server.CellTerminalActionResult;
import ae2.cellterminal.server.CellTerminalNetworkToolOperation;
import ae2.cellterminal.server.CellTerminalNetworkToolPreview;
import ae2.cellterminal.server.CellTerminalPartitionPlan;
import ae2.cellterminal.server.CellTerminalSnapshot;
import ae2.cellterminal.server.CellTerminalStorageNameData;
import ae2.cellterminal.server.CellTerminalSubnetHandle;
import ae2.cellterminal.server.CellTerminalSubnetLedger;
import ae2.cellterminal.server.CellTerminalSubnetNameData;
import ae2.core.AELog;
import ae2.me.cells.BasicCellInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static ae2.core.localization.GuiText.CellTerminalSubnetMainNetwork;

/**
 * Client-side data model for the Cell Terminal GUI.
 * <p>
 * The state is a serialized display snapshot. Server writes still use stable handles resolved from live targets.
 */
public record CellTerminalClientState(String contextId,
                                      long cacheRevision,
                                      CellTerminalTab tab,
                                      EnumSet<CellTerminalTab> enabledTabs,
                                      boolean connected,
                                      @Nullable ITextComponent linkStatus,
                                      List<StorageEntry> storages,
                                      List<BusEntry> buses,
                                      List<SubnetEntry> subnets,
                                      @Nullable ContentPage contentPage,
                                      @Nullable ToolPreview preview,
                                      @Nullable String selectedTargetId,
                                      @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                      int selectedSlotIndex) {
    private static final CellTerminalClientState empty = new CellTerminalClientState("", 0, CellTerminalTab.OVERVIEW,
        EnumSet.allOf(CellTerminalTab.class), false, null,
        List.of(), List.of(), List.of(), null, null, null, null, -1);
    private static final String TAG_CONTEXT = "context";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_TAB = "tab";
    private static final String TAG_CONNECTED = "connected";
    private static final String TAG_ENABLED_TABS = "enabledTabs";
    private static final String TAG_LINK_STATUS = "linkStatus";
    private static final String TAG_STORAGES = "storages";
    private static final String TAG_BUSES = "buses";
    private static final String TAG_SUBNETS = "subnets";
    private static final String TAG_CONTENT_PAGE = "contentPage";
    private static final String TAG_PREVIEW = "preview";
    private static final String TAG_SELECTED_TARGET = "selectedTarget";
    private static final String TAG_SELECTED_LOCATOR = "selectedLocator";
    private static final String TAG_SELECTED_SLOT = "selectedSlot";
    private static final int MAX_DISPLAY_CONTENT_ENTRIES = 256;
    private static final int MAX_DISPLAY_TOOL_PLANS = 128;
    private static final int MAX_DISPLAY_TOOL_FAILURES = 128;
    private static final int UNLOADED_COUNT = -1;

    public CellTerminalClientState {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(tab, "tab");
        enabledTabs = copyEnabledTabs(enabledTabs);
        storages = List.copyOf(Objects.requireNonNull(storages, "storages"));
        buses = List.copyOf(Objects.requireNonNull(buses, "buses"));
        subnets = List.copyOf(Objects.requireNonNull(subnets, "subnets"));
    }

    public static CellTerminalClientState empty() {
        return empty;
    }

    public static CellTerminalClientState offline(CellTerminalTab tab, ILinkStatus linkStatus) {
        return new CellTerminalClientState("", 0, tab, EnumSet.allOf(CellTerminalTab.class), false,
            linkStatus.statusDescription(), List.of(), List.of(), List.of(), null, null, null, null, -1);
    }

    public static CellTerminalClientState fromSnapshot(CellTerminalSnapshot snapshot,
                                                       CellTerminalTab tab,
                                                       EnumSet<CellTerminalTab> enabledTabs,
                                                       ILinkStatus linkStatus,
                                                       CellTerminalSubnetLedger ledger,
                                                       CellTerminalSubnetNameData subnetNameData,
                                                       CellTerminalStorageNameData storageNameData,
                                                       @Nullable ContentPage contentPage,
                                                       @Nullable ToolPreview preview,
                                                       @Nullable String selectedTargetId,
                                                       @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                                       int selectedSlotIndex,
                                                       UUID playerId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(linkStatus, "linkStatus");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(subnetNameData, "subnetNameData");
        Objects.requireNonNull(storageNameData, "storageNameData");
        Objects.requireNonNull(playerId, "playerId");

        var buses = new ArrayList<BusEntry>(snapshot.busTargets().size());
        for (var bus : snapshot.busTargets()) {
            BusEntry entry = BusEntry.fromTarget(bus, tab, selectedTargetId, selectedTargetLocator, selectedSlotIndex);
            buses.add(entry.withStoredName(storageNameData.getDisplayName(targetNameKey(entry.stableTargetId(),
                entry.locator()))));
        }

        var storages = new ArrayList<StorageEntry>(snapshot.storageTargets().size());
        for (var storage : snapshot.storageTargets()) {
            StorageEntry entry = StorageEntry.fromTarget(storage, tab, selectedTargetId, selectedTargetLocator,
                selectedSlotIndex);
            storages.add(entry.withStoredName(storageNameData.getDisplayName(targetNameKey(entry.stableTargetId(),
                entry.locator()))));
        }

        var subnets = new ArrayList<SubnetEntry>(snapshot.subnetTargets().size() + 1);
        boolean loadConnections = tab == CellTerminalTab.SUBNETS;
        subnets.add(SubnetEntry.mainNetwork(ledger.getLastLoadedHandle(playerId) == null));
        for (var subnet : snapshot.subnetTargets()) {
            subnets.add(SubnetEntry.fromTarget(subnet, ledger, subnetNameData, loadConnections, playerId));
        }

        return new CellTerminalClientState(
            snapshot.contextId(),
            snapshot.cacheRevision(),
            tab,
            enabledTabs,
            linkStatus.connected(),
            linkStatus.statusDescription(),
            storages,
            buses,
            subnets,
            contentPage,
            preview,
            selectedTargetId,
            selectedTargetLocator,
            selectedSlotIndex);
    }

    public static String targetNameKey(String stableTargetId, CellTerminalTargetLocator locator) {
        return stableTargetId + "|" + locator;
    }

    public static CellTerminalClientState fromTag(@Nullable NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) {
            return empty();
        }
        return new CellTerminalClientState(
            tag.getString(TAG_CONTEXT),
            tag.getLong(TAG_REVISION),
            readEnum(tag.getString(TAG_TAB), CellTerminalTab.class, CellTerminalTab.OVERVIEW),
            readEnums(tag.getTagList(TAG_ENABLED_TABS, Constants.NBT.TAG_STRING)),
            tag.getBoolean(TAG_CONNECTED),
            readText(tag, TAG_LINK_STATUS),
            readList(tag.getTagList(TAG_STORAGES, Constants.NBT.TAG_COMPOUND), StorageEntry::fromTag),
            readList(tag.getTagList(TAG_BUSES, Constants.NBT.TAG_COMPOUND), BusEntry::fromTag),
            readList(tag.getTagList(TAG_SUBNETS, Constants.NBT.TAG_COMPOUND), SubnetEntry::fromTag),
            tag.hasKey(TAG_CONTENT_PAGE, Constants.NBT.TAG_COMPOUND)
                ? ContentPage.fromTag(tag.getCompoundTag(TAG_CONTENT_PAGE))
                : null,
            tag.hasKey(TAG_PREVIEW, Constants.NBT.TAG_COMPOUND)
                ? ToolPreview.fromTag(tag.getCompoundTag(TAG_PREVIEW))
                : null,
            tag.hasKey(TAG_SELECTED_TARGET, Constants.NBT.TAG_STRING) ? tag.getString(TAG_SELECTED_TARGET) : null,
            tag.hasKey(TAG_SELECTED_LOCATOR, Constants.NBT.TAG_COMPOUND)
                ? readLocator(tag.getCompoundTag(TAG_SELECTED_LOCATOR))
                : null,
            tag.getInteger(TAG_SELECTED_SLOT));
    }

    private static <T> NBTTagList writeList(List<T> values, TagWriter<T> writer) {
        var list = new NBTTagList();
        for (var value : values) {
            list.appendTag(writer.write(value));
        }
        return list;
    }

    private static <T> List<T> readList(NBTTagList list, TagReader<T> reader) {
        var result = new ArrayList<T>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            result.add(reader.read(list.getCompoundTagAt(index)));
        }
        return List.copyOf(result);
    }

    private static List<String> readStrings(NBTTagList list) {
        var result = new ArrayList<String>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            result.add(list.getStringTagAt(index));
        }
        return List.copyOf(result);
    }

    private static EnumSet<CellTerminalTab> copyEnabledTabs(EnumSet<CellTerminalTab> values) {
        Objects.requireNonNull(values, "enabledTabs");
        return values.isEmpty() ? EnumSet.noneOf(CellTerminalTab.class) : EnumSet.copyOf(values);
    }

    private static NBTTagList writeEnums(EnumSet<CellTerminalTab> values) {
        var list = new NBTTagList();
        for (CellTerminalTab value : values) {
            list.appendTag(new NBTTagString(value.name()));
        }
        return list;
    }

    private static EnumSet<CellTerminalTab> readEnums(NBTTagList list) {
        EnumSet<CellTerminalTab> result = EnumSet.noneOf(CellTerminalTab.class);
        for (int index = 0; index < list.tagCount(); index++) {
            String value = list.getStringTagAt(index);
            CellTerminalTab tab = readEnum(value, CellTerminalTab.class, null);
            if (tab == null) {
                AELog.warn("Cell Terminal client state received unknown enabled tab '%s'", value);
            } else {
                result.add(tab);
            }
        }
        return result.isEmpty() ? EnumSet.allOf(CellTerminalTab.class) : result;
    }

    private static void writeText(NBTTagCompound tag, String key, @Nullable ITextComponent component) {
        if (component != null) {
            tag.setString(key, ITextComponent.Serializer.componentToJson(component));
        }
    }

    private static @Nullable ITextComponent readText(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_STRING)) {
            return null;
        }
        ITextComponent component = ITextComponent.Serializer.jsonToComponent(tag.getString(key));
        return component == null ? new TextComponentString(tag.getString(key)) : component;
    }

    private static <E extends Enum<E>> E readEnum(String value, Class<E> enumClass, E fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    static NBTTagCompound writeLocator(CellTerminalTargetLocator locator) {
        var tag = new NBTTagCompound();
        tag.setString("kind", locator.kindId().toString());
        tag.setInteger("dim", locator.dimensionId());
        tag.setInteger("x", locator.pos().getX());
        tag.setInteger("y", locator.pos().getY());
        tag.setInteger("z", locator.pos().getZ());
        tag.setInteger("side", locator.side() == null ? -1 : locator.side().getIndex());
        return tag;
    }

    static CellTerminalTargetLocator readLocator(NBTTagCompound tag) {
        int sideIndex = tag.getInteger("side");
        return new CellTerminalTargetLocator(
            new ResourceLocation(tag.getString("kind")),
            tag.getInteger("dim"),
            new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")),
            sideIndex < 0 ? null : EnumFacing.byIndex(sideIndex));
    }

    private static NBTTagList writeStacks(List<ItemStack> stacks) {
        var list = new NBTTagList();
        for (var stack : stacks) {
            list.appendTag(stack.serializeNBT());
        }
        return list;
    }

    private static List<ItemStack> readStacks(NBTTagList list) {
        var result = new ArrayList<ItemStack>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            result.add(new ItemStack(list.getCompoundTagAt(index)));
        }
        return List.copyOf(result);
    }

    private static List<ContentEntry> displayContentEntries(List<GenericStack> entries) {
        int limit = Math.min(entries.size(), MAX_DISPLAY_CONTENT_ENTRIES);
        var result = new ArrayList<ContentEntry>(limit);
        for (int index = 0; index < limit; index++) {
            result.add(ContentEntry.fromStack(entries.get(index)));
        }
        return List.copyOf(result);
    }

    private static int readContentEntryCount(NBTTagCompound tag, String key, List<ContentEntry> content) {
        return tag.hasKey(key, Constants.NBT.TAG_INT) ? tag.getInteger(key) : content.size();
    }

    private static boolean shouldLoadStorageContent(CellTerminalTab tab) {
        return tab == CellTerminalTab.OVERVIEW
            || tab == CellTerminalTab.TEMP_CELLS
            || tab == CellTerminalTab.CELL_CONTENT;
    }

    private static boolean shouldLoadCellContent(CellTerminalTab tab) {
        return tab == CellTerminalTab.OVERVIEW
            || tab == CellTerminalTab.CELL_CONTENT
            || tab == CellTerminalTab.CELL_PARTITION
            || tab == CellTerminalTab.TEMP_CELLS;
    }

    private static boolean shouldLoadCellPartition(CellTerminalTab tab) {
        return tab == CellTerminalTab.OVERVIEW
            || tab == CellTerminalTab.CELL_CONTENT
            || tab == CellTerminalTab.CELL_PARTITION
            || tab == CellTerminalTab.TEMP_CELLS;
    }

    private static boolean shouldLoadBusContent(CellTerminalTab tab) {
        return tab == CellTerminalTab.BUS_CONTENT
            || tab == CellTerminalTab.BUS_PARTITION;
    }

    private static boolean shouldLoadBusPartition(CellTerminalTab tab) {
        return tab == CellTerminalTab.BUS_CONTENT
            || tab == CellTerminalTab.BUS_PARTITION;
    }

    private static boolean shouldLoadCellUpgrades(CellTerminalTab tab,
                                                  @Nullable String selectedTargetId,
                                                  @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                                  int selectedSlotIndex,
                                                  String ownerStableTargetId,
                                                  CellTerminalTargetLocator ownerLocator,
                                                  int slotIndex) {
        return (tab == CellTerminalTab.OVERVIEW
            || tab == CellTerminalTab.CELL_CONTENT
            || tab == CellTerminalTab.CELL_PARTITION
            || tab == CellTerminalTab.TEMP_CELLS)
            || selectedSlotIndex == slotIndex
            && Objects.equals(selectedTargetId, ownerStableTargetId)
            && Objects.equals(selectedTargetLocator, ownerLocator);
    }

    private static boolean shouldLoadBusUpgrades(CellTerminalTab tab,
                                                 @Nullable String selectedTargetId,
                                                 @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                                 int selectedSlotIndex,
                                                 String stableTargetId,
                                                 CellTerminalTargetLocator locator) {
        return tab == CellTerminalTab.BUS_CONTENT
            || tab == CellTerminalTab.BUS_PARTITION
            || selectedSlotIndex < 0
            && Objects.equals(selectedTargetId, stableTargetId)
            && Objects.equals(selectedTargetLocator, locator);
    }

    public CellTerminalClientState lightSnapshot() {
        return new CellTerminalClientState(
            this.contextId,
            this.cacheRevision,
            this.tab,
            this.enabledTabs,
            this.connected,
            this.linkStatus,
            this.storages.stream().map(StorageEntry::lightSnapshot).toList(),
            this.buses.stream().map(BusEntry::lightSnapshot).toList(),
            this.subnets,
            null,
            null,
            this.selectedTargetId,
            this.selectedTargetLocator,
            this.selectedSlotIndex);
    }

    public NBTTagCompound toTag() {
        var tag = new NBTTagCompound();
        tag.setString(TAG_CONTEXT, this.contextId);
        tag.setLong(TAG_REVISION, this.cacheRevision);
        tag.setString(TAG_TAB, this.tab.name());
        tag.setTag(TAG_ENABLED_TABS, writeEnums(this.enabledTabs));
        tag.setBoolean(TAG_CONNECTED, this.connected);
        writeText(tag, TAG_LINK_STATUS, this.linkStatus);
        tag.setTag(TAG_STORAGES, writeList(this.storages, StorageEntry::toTag));
        tag.setTag(TAG_BUSES, writeList(this.buses, BusEntry::toTag));
        tag.setTag(TAG_SUBNETS, writeList(this.subnets, SubnetEntry::toTag));
        if (this.contentPage != null) {
            tag.setTag(TAG_CONTENT_PAGE, this.contentPage.toTag());
        }
        if (this.preview != null) {
            tag.setTag(TAG_PREVIEW, this.preview.toTag());
        }
        if (this.selectedTargetId != null) {
            tag.setString(TAG_SELECTED_TARGET, this.selectedTargetId);
        }
        if (this.selectedTargetLocator != null) {
            tag.setTag(TAG_SELECTED_LOCATOR, writeLocator(this.selectedTargetLocator));
        }
        tag.setInteger(TAG_SELECTED_SLOT, this.selectedSlotIndex);
        return tag;
    }

    public enum CellTerminalTab {
        OVERVIEW,
        CELL_CONTENT,
        CELL_PARTITION,
        TEMP_CELLS,
        BUS_CONTENT,
        BUS_PARTITION,
        SUBNETS,
        NETWORK_TOOLS
    }

    private interface TagWriter<T> {
        NBTTagCompound write(T value);
    }

    private interface TagReader<T> {
        T read(NBTTagCompound tag);
    }

    public record ContentEntry(GenericStack stack) {
        private static final String TAG_STACK = "stack";

        public ContentEntry {
            Objects.requireNonNull(stack, "stack");
        }

        static ContentEntry fromStack(GenericStack stack) {
            return new ContentEntry(stack);
        }

        static ContentEntry fromTag(NBTTagCompound tag) {
            GenericStack stack = GenericStack.readTag(tag.getCompoundTag(TAG_STACK));
            if (stack == null) {
                throw new IllegalArgumentException("Cell Terminal content entry lost its stack");
            }
            return new ContentEntry(stack);
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_STACK, GenericStack.writeTag(this.stack));
            return tag;
        }
    }

    public record ContentPage(String stableTargetId,
                              CellTerminalTargetLocator locator,
                              int slotIndex,
                              int firstIndex,
                              int totalEntries,
                              String contentRevision,
                              List<ContentEntry> content) {
        private static final String TAG_STABLE_ID = "stableId";
        private static final String TAG_LOCATOR = "locator";
        private static final String TAG_SLOT = "slot";
        private static final String TAG_FIRST = "first";
        private static final String TAG_TOTAL = "total";
        private static final String TAG_REVISION = "revision";
        private static final String TAG_CONTENT = "content";

        public ContentPage {
            Objects.requireNonNull(stableTargetId, "stableTargetId");
            Objects.requireNonNull(locator, "locator");
            Objects.requireNonNull(contentRevision, "contentRevision");
            content = List.copyOf(content);
            if (firstIndex < 0) {
                throw new IllegalArgumentException("firstIndex must be >= 0");
            }
            if (totalEntries < 0) {
                throw new IllegalArgumentException("totalEntries must be >= 0");
            }
        }

        public static ContentPage fromSnapshot(String stableTargetId,
                                               CellTerminalTargetLocator locator,
                                               int slotIndex,
                                               int firstIndex,
                                               int pageSize,
                                               CellTerminalContentSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            int safeFirst = Math.max(0, firstIndex);
            int safePageSize = Math.max(1, pageSize);
            int end = Math.min(snapshot.entries().size(), safeFirst + safePageSize);
            var entries = new ArrayList<ContentEntry>(Math.max(0, end - safeFirst));
            for (int index = safeFirst; index < end; index++) {
                entries.add(ContentEntry.fromStack(snapshot.entries().get(index)));
            }
            return new ContentPage(
                stableTargetId,
                locator,
                slotIndex,
                safeFirst,
                snapshot.entries().size(),
                snapshot.contentRevision(),
                entries);
        }

        static ContentPage fromTag(NBTTagCompound tag) {
            return new ContentPage(
                tag.getString(TAG_STABLE_ID),
                readLocator(tag.getCompoundTag(TAG_LOCATOR)),
                tag.getInteger(TAG_SLOT),
                tag.getInteger(TAG_FIRST),
                tag.getInteger(TAG_TOTAL),
                tag.getString(TAG_REVISION),
                readList(tag.getTagList(TAG_CONTENT, Constants.NBT.TAG_COMPOUND), ContentEntry::fromTag));
        }

        public boolean matches(String stableTargetId, CellTerminalTargetLocator locator, int slotIndex) {
            return this.stableTargetId.equals(stableTargetId)
                && this.locator.equals(locator)
                && this.slotIndex == slotIndex;
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setTag(TAG_LOCATOR, writeLocator(this.locator));
            tag.setInteger(TAG_SLOT, this.slotIndex);
            tag.setInteger(TAG_FIRST, this.firstIndex);
            tag.setInteger(TAG_TOTAL, this.totalEntries);
            tag.setString(TAG_REVISION, this.contentRevision);
            tag.setTag(TAG_CONTENT, writeList(this.content, ContentEntry::toTag));
            return tag;
        }
    }

    public record CellSlotEntry(int slotIndex,
                                ItemStack cellStack,
                                boolean mounted,
                                String cellState,
                                long usedBytes,
                                long totalBytes,
                                int usedTypes,
                                int totalTypes,
                                List<ContentEntry> content,
                                int contentEntryCount,
                                boolean contentTruncated,
                                String contentRevision,
                                List<@Nullable GenericStack> partition,
                                boolean upgradesLoaded,
                                List<ItemStack> upgrades) {
        private static final String TAG_SLOT = "slot";
        private static final String TAG_CELL_STACK = "cellStack";
        private static final String TAG_MOUNTED = "mounted";
        private static final String TAG_CELL_STATE = "cellState";
        private static final String TAG_USED_BYTES = "usedBytes";
        private static final String TAG_TOTAL_BYTES = "totalBytes";
        private static final String TAG_USED_TYPES = "usedTypes";
        private static final String TAG_TOTAL_TYPES = "totalTypes";
        private static final String TAG_CONTENT = "content";
        private static final String TAG_CONTENT_COUNT = "contentCount";
        private static final String TAG_CONTENT_TRUNCATED = "contentTruncated";
        private static final String TAG_CONTENT_REVISION = "contentRevision";
        private static final String TAG_PARTITION = "partition";
        private static final String TAG_UPGRADES_LOADED = "upgradesLoaded";
        private static final String TAG_UPGRADES = "upgrades";

        public CellSlotEntry {
            cellStack = cellStack.copy();
            content = List.copyOf(content);
            partition = ObjectLists.unmodifiable(new ObjectArrayList<>(partition));
            upgrades = upgrades.stream().map(ItemStack::copy).collect(ObjectArrayList.toList());
        }

        static CellSlotEntry fromTarget(CellTerminalCellSlotTarget slot, CellTerminalTab tab,
                                        @Nullable String selectedTargetId,
                                        @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                        int selectedSlotIndex,
                                        String ownerStableTargetId,
                                        CellTerminalTargetLocator ownerLocator) {
            var content = shouldLoadCellContent(tab) ? slot.previewContent() : null;
            boolean loadUpgrades = shouldLoadCellUpgrades(tab, selectedTargetId, selectedTargetLocator, selectedSlotIndex,
                ownerStableTargetId, ownerLocator, slot.slotIndex());
            return new CellSlotEntry(
                slot.slotIndex(),
                slot.getCellStack(),
                slot.isMounted(),
                slot.getCellState().name(),
                readUsedBytes(slot.getCellInventory()),
                readTotalBytes(slot.getCellInventory()),
                readUsedTypes(slot.getCellInventory()),
                readTotalTypes(slot.getCellInventory()),
                content == null ? List.of() : displayContentEntries(content.entries()),
                content == null ? UNLOADED_COUNT : content.entries().size(),
                content != null && content.entries().size() > MAX_DISPLAY_CONTENT_ENTRIES,
                content == null ? "" : content.contentRevision(),
                shouldLoadCellPartition(tab) ? slot.getPartitionSnapshot().slots() : List.of(),
                loadUpgrades,
                loadUpgrades ? slot.getUpgradeSnapshot().slots() : List.of());
        }

        static CellSlotEntry fromTag(NBTTagCompound tag) {
            List<ContentEntry> content = readList(tag.getTagList(TAG_CONTENT, Constants.NBT.TAG_COMPOUND),
                ContentEntry::fromTag);
            return new CellSlotEntry(
                tag.getInteger(TAG_SLOT),
                new ItemStack(tag.getCompoundTag(TAG_CELL_STACK)),
                tag.getBoolean(TAG_MOUNTED),
                tag.getString(TAG_CELL_STATE),
                tag.getLong(TAG_USED_BYTES),
                tag.getLong(TAG_TOTAL_BYTES),
                tag.getInteger(TAG_USED_TYPES),
                tag.getInteger(TAG_TOTAL_TYPES),
                content,
                readContentEntryCount(tag, TAG_CONTENT_COUNT, content),
                tag.getBoolean(TAG_CONTENT_TRUNCATED),
                tag.getString(TAG_CONTENT_REVISION),
                GenericStack.readList(tag.getTagList(TAG_PARTITION, Constants.NBT.TAG_COMPOUND)),
                tag.getBoolean(TAG_UPGRADES_LOADED),
                readStacks(tag.getTagList(TAG_UPGRADES, Constants.NBT.TAG_COMPOUND)));
        }

        private static long readUsedBytes(@Nullable StorageCell cell) {
            return cell instanceof BasicCellInventory basicCell ? basicCell.getUsedBytes() : 0L;
        }

        private static long readTotalBytes(@Nullable StorageCell cell) {
            return cell instanceof BasicCellInventory basicCell ? basicCell.getTotalBytes() : 0L;
        }

        private static int readUsedTypes(@Nullable StorageCell cell) {
            if (cell instanceof BasicCellInventory basicCell) {
                return Math.toIntExact(Math.min(Integer.MAX_VALUE, basicCell.getStoredItemTypes()));
            }
            return 0;
        }

        private static int readTotalTypes(@Nullable StorageCell cell) {
            if (cell instanceof BasicCellInventory basicCell) {
                return Math.toIntExact(Math.min(Integer.MAX_VALUE, basicCell.getTotalItemTypes()));
            }
            return 0;
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setInteger(TAG_SLOT, this.slotIndex);
            tag.setTag(TAG_CELL_STACK, this.cellStack.serializeNBT());
            tag.setBoolean(TAG_MOUNTED, this.mounted);
            tag.setString(TAG_CELL_STATE, this.cellState);
            tag.setLong(TAG_USED_BYTES, this.usedBytes);
            tag.setLong(TAG_TOTAL_BYTES, this.totalBytes);
            tag.setInteger(TAG_USED_TYPES, this.usedTypes);
            tag.setInteger(TAG_TOTAL_TYPES, this.totalTypes);
            tag.setTag(TAG_CONTENT, writeList(this.content, ContentEntry::toTag));
            tag.setInteger(TAG_CONTENT_COUNT, this.contentEntryCount);
            tag.setBoolean(TAG_CONTENT_TRUNCATED, this.contentTruncated);
            tag.setString(TAG_CONTENT_REVISION, this.contentRevision);
            tag.setTag(TAG_PARTITION, GenericStack.writeList(this.partition));
            tag.setBoolean(TAG_UPGRADES_LOADED, this.upgradesLoaded);
            tag.setTag(TAG_UPGRADES, writeStacks(this.upgrades));
            return tag;
        }

        CellSlotEntry lightSnapshot() {
            return new CellSlotEntry(
                this.slotIndex,
                this.cellStack,
                this.mounted,
                this.cellState,
                this.usedBytes,
                this.totalBytes,
                this.usedTypes,
                this.totalTypes,
                List.of(),
                this.contentEntryCount,
                this.contentEntryCount > 0,
                this.contentRevision,
                List.of(),
                false,
                List.of());
        }
    }

    public record StorageEntry(String stableTargetId,
                               CellTerminalTargetLocator locator,
                               ITextComponent displayName,
                               @Nullable String renamedDisplayName,
                               int priority,
                               int cellSlotCount,
                               int mountedCellCount,
                               List<CellSlotEntry> cellSlots,
                               List<ContentEntry> content,
                               int contentEntryCount,
                               boolean contentTruncated,
                               String contentRevision,
                               boolean storageBus,
                               ItemStack icon) {
        private static final String TAG_STABLE_ID = "stableId";
        private static final String TAG_LOCATOR = "locator";
        private static final String TAG_DISPLAY = "display";
        private static final String TAG_RENAMED = "renamed";
        private static final String TAG_PRIORITY = "priority";
        private static final String TAG_CELL_SLOTS = "cellSlotCount";
        private static final String TAG_MOUNTED = "mountedCellCount";
        private static final String TAG_SLOT_ENTRIES = "slotEntries";
        private static final String TAG_CONTENT = "content";
        private static final String TAG_CONTENT_COUNT = "contentCount";
        private static final String TAG_CONTENT_TRUNCATED = "contentTruncated";
        private static final String TAG_CONTENT_REVISION = "contentRevision";
        private static final String TAG_BUS = "bus";
        private static final String TAG_ICON = "icon";

        public StorageEntry {
            Objects.requireNonNull(stableTargetId, "stableTargetId");
            Objects.requireNonNull(locator, "locator");
            Objects.requireNonNull(displayName, "displayName");
            cellSlots = List.copyOf(cellSlots);
            content = List.copyOf(content);
            icon = icon == null ? ItemStack.EMPTY : icon.copy();
        }

        static StorageEntry fromTarget(CellTerminalStorageTarget target, CellTerminalTab tab,
                                       @Nullable String selectedTargetId,
                                       @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                       int selectedSlotIndex) {
            var content = shouldLoadStorageContent(tab) ? target.previewContent() : null;
            return new StorageEntry(
                target.stableTargetId(),
                target.locator(),
                target.displayName(),
                null,
                target.getPriority(),
                target.getCellSlotCount(),
                target.getMountedCellCount(),
                target.getCellSlots().stream()
                      .map(slot -> CellSlotEntry.fromTarget(slot, tab, selectedTargetId, selectedTargetLocator,
                          selectedSlotIndex, target.stableTargetId(), target.locator()))
                      .toList(),
                content == null ? List.of() : displayContentEntries(content.entries()),
                content == null ? UNLOADED_COUNT : content.entries().size(),
                content != null && content.entries().size() > MAX_DISPLAY_CONTENT_ENTRIES,
                content == null ? "" : content.contentRevision(),
                false,
                target.icon());
        }

        static StorageEntry fromTag(NBTTagCompound tag) {
            ITextComponent display = readText(tag, TAG_DISPLAY);
            List<ContentEntry> content = readList(tag.getTagList(TAG_CONTENT, Constants.NBT.TAG_COMPOUND),
                ContentEntry::fromTag);
            return new StorageEntry(
                tag.getString(TAG_STABLE_ID),
                readLocator(tag.getCompoundTag(TAG_LOCATOR)),
                display == null ? new TextComponentString(tag.getString(TAG_STABLE_ID)) : display,
                tag.hasKey(TAG_RENAMED, Constants.NBT.TAG_STRING) ? tag.getString(TAG_RENAMED) : null,
                tag.getInteger(TAG_PRIORITY),
                tag.getInteger(TAG_CELL_SLOTS),
                tag.getInteger(TAG_MOUNTED),
                readList(tag.getTagList(TAG_SLOT_ENTRIES, Constants.NBT.TAG_COMPOUND), CellSlotEntry::fromTag),
                content,
                readContentEntryCount(tag, TAG_CONTENT_COUNT, content),
                tag.getBoolean(TAG_CONTENT_TRUNCATED),
                tag.getString(TAG_CONTENT_REVISION),
                tag.getBoolean(TAG_BUS),
                tag.hasKey(TAG_ICON, Constants.NBT.TAG_COMPOUND)
                    ? new ItemStack(tag.getCompoundTag(TAG_ICON)) : ItemStack.EMPTY);
        }

        StorageEntry withStoredName(@Nullable String storedName) {
            if (storedName == null || storedName.isEmpty()) {
                return this;
            }
            return new StorageEntry(this.stableTargetId, this.locator, new TextComponentString(storedName),
                storedName, this.priority, this.cellSlotCount, this.mountedCellCount, this.cellSlots, this.content,
                this.contentEntryCount, this.contentTruncated, this.contentRevision, this.storageBus, this.icon);
        }

        public ITextComponent visibleName() {
            return this.renamedDisplayName == null || this.renamedDisplayName.isEmpty()
                ? this.displayName
                : new TextComponentString(this.renamedDisplayName);
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setTag(TAG_LOCATOR, writeLocator(this.locator));
            writeText(tag, TAG_DISPLAY, this.displayName);
            if (this.renamedDisplayName != null) {
                tag.setString(TAG_RENAMED, this.renamedDisplayName);
            }
            tag.setInteger(TAG_PRIORITY, this.priority);
            tag.setInteger(TAG_CELL_SLOTS, this.cellSlotCount);
            tag.setInteger(TAG_MOUNTED, this.mountedCellCount);
            tag.setTag(TAG_SLOT_ENTRIES, writeList(this.cellSlots, CellSlotEntry::toTag));
            tag.setTag(TAG_CONTENT, writeList(this.content, ContentEntry::toTag));
            tag.setInteger(TAG_CONTENT_COUNT, this.contentEntryCount);
            tag.setBoolean(TAG_CONTENT_TRUNCATED, this.contentTruncated);
            tag.setString(TAG_CONTENT_REVISION, this.contentRevision);
            tag.setBoolean(TAG_BUS, this.storageBus);
            if (!this.icon.isEmpty()) {
                tag.setTag(TAG_ICON, this.icon.serializeNBT());
            }
            return tag;
        }

        StorageEntry lightSnapshot() {
            return new StorageEntry(
                this.stableTargetId,
                this.locator,
                this.displayName,
                this.renamedDisplayName,
                this.priority,
                this.cellSlotCount,
                this.mountedCellCount,
                this.cellSlots.stream().map(CellSlotEntry::lightSnapshot).toList(),
                List.of(),
                this.contentEntryCount,
                this.contentEntryCount > 0,
                this.contentRevision,
                this.storageBus,
                this.icon);
        }
    }

    public record BusEntry(String stableTargetId,
                           CellTerminalTargetLocator locator,
                           ITextComponent displayName,
                           @Nullable ITextComponent connectedDisplayName,
                           @Nullable String renamedDisplayName,
                           int priority,
                           AccessRestriction accessRestriction,
                           StorageFilter storageFilter,
                           YesNo filterOnExtract,
                           FuzzyMode fuzzyMode,
                           boolean extractableOnly,
                           List<ContentEntry> content,
                           int contentEntryCount,
                           boolean contentTruncated,
                           String contentRevision,
                           CellTerminalBusPartitionMode partitionMode,
                           int partitionSlotCapacity,
                           List<@Nullable GenericStack> partition,
                           String textPartitionPrimary,
                           String textPartitionSecondary,
                           boolean upgradesLoaded,
                           List<ItemStack> upgrades,
                           ItemStack icon) {
        private static final String TAG_STABLE_ID = "stableId";
        private static final String TAG_LOCATOR = "locator";
        private static final String TAG_DISPLAY = "display";
        private static final String TAG_CONNECTED_DISPLAY = "connectedDisplay";
        private static final String TAG_RENAMED = "renamed";
        private static final String TAG_PRIORITY = "priority";
        private static final String TAG_ACCESS = "access";
        private static final String TAG_STORAGE_FILTER = "storageFilter";
        private static final String TAG_FILTER_ON_EXTRACT = "filterOnExtract";
        private static final String TAG_FUZZY = "fuzzy";
        private static final String TAG_EXTRACT_ONLY = "extractOnly";
        private static final String TAG_CONTENT = "content";
        private static final String TAG_CONTENT_COUNT = "contentCount";
        private static final String TAG_CONTENT_TRUNCATED = "contentTruncated";
        private static final String TAG_CONTENT_REVISION = "contentRevision";
        private static final String TAG_PARTITION_MODE = "partitionMode";
        private static final String TAG_PARTITION_SLOT_CAPACITY = "partitionSlotCapacity";
        private static final String TAG_PARTITION = "partition";
        private static final String TAG_TEXT_PARTITION_PRIMARY = "textPartitionPrimary";
        private static final String TAG_TEXT_PARTITION_SECONDARY = "textPartitionSecondary";
        private static final String TAG_UPGRADES_LOADED = "upgradesLoaded";
        private static final String TAG_UPGRADES = "upgrades";
        private static final String TAG_ICON = "icon";

        public BusEntry {
            Objects.requireNonNull(stableTargetId, "stableTargetId");
            Objects.requireNonNull(locator, "locator");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(accessRestriction, "accessRestriction");
            Objects.requireNonNull(storageFilter, "storageFilter");
            Objects.requireNonNull(filterOnExtract, "filterOnExtract");
            Objects.requireNonNull(fuzzyMode, "fuzzyMode");
            Objects.requireNonNull(partitionMode, "partitionMode");
            content = List.copyOf(content);
            partition = ObjectLists.unmodifiable(new ObjectArrayList<>(partition));
            textPartitionPrimary = Objects.requireNonNullElse(textPartitionPrimary, "");
            textPartitionSecondary = Objects.requireNonNullElse(textPartitionSecondary, "");
            upgrades = upgrades.stream().map(ItemStack::copy).toList();
            icon = icon == null ? ItemStack.EMPTY : icon.copy();
        }

        static BusEntry fromTarget(CellTerminalBusTarget target, CellTerminalTab tab,
                                   @Nullable String selectedTargetId,
                                   @Nullable CellTerminalTargetLocator selectedTargetLocator,
                                   int selectedSlotIndex) {
            var content = shouldLoadBusContent(tab) ? target.previewContent() : null;
            CellTerminalBusTextPartitionSnapshot textPartition = shouldLoadBusPartition(tab)
                ? target.getTextPartitionSnapshot()
                : CellTerminalBusTextPartitionSnapshot.empty();
            boolean loadUpgrades = shouldLoadBusUpgrades(tab, selectedTargetId, selectedTargetLocator,
                selectedSlotIndex, target.stableTargetId(), target.locator());
            return new BusEntry(
                target.stableTargetId(),
                target.locator(),
                target.displayName(),
                target.connectedDisplayName(),
                null,
                target.getPriority(),
                target.getAccessRestriction(),
                target.getStorageFilter(),
                target.getFilterOnExtract(),
                target.getFuzzyMode(),
                target.isExtractableOnly(),
                content == null ? List.of() : displayContentEntries(content.entries()),
                content == null ? UNLOADED_COUNT : content.entries().size(),
                content != null && content.entries().size() > MAX_DISPLAY_CONTENT_ENTRIES,
                content == null ? "" : content.contentRevision(),
                target.getPartitionMode(),
                target.getPartitionSlotCapacity(),
                shouldLoadBusPartition(tab) ? target.getPartitionSnapshot().slots() : List.of(),
                textPartition.primaryExpression(),
                textPartition.secondaryExpression(),
                loadUpgrades,
                loadUpgrades ? target.getUpgradeSnapshot().slots() : List.of(),
                target.icon());
        }

        static BusEntry fromTag(NBTTagCompound tag) {
            ITextComponent display = readText(tag, TAG_DISPLAY);
            ITextComponent connectedDisplay = readText(tag, TAG_CONNECTED_DISPLAY);
            List<ContentEntry> content = readList(tag.getTagList(TAG_CONTENT, Constants.NBT.TAG_COMPOUND),
                ContentEntry::fromTag);
            return new BusEntry(
                tag.getString(TAG_STABLE_ID),
                readLocator(tag.getCompoundTag(TAG_LOCATOR)),
                display == null ? new TextComponentString(tag.getString(TAG_STABLE_ID)) : display,
                connectedDisplay,
                tag.hasKey(TAG_RENAMED, Constants.NBT.TAG_STRING) ? tag.getString(TAG_RENAMED) : null,
                tag.getInteger(TAG_PRIORITY),
                readEnum(tag.getString(TAG_ACCESS), AccessRestriction.class, AccessRestriction.READ_WRITE),
                readEnum(tag.getString(TAG_STORAGE_FILTER), StorageFilter.class, StorageFilter.EXTRACTABLE_ONLY),
                readEnum(tag.getString(TAG_FILTER_ON_EXTRACT), YesNo.class, YesNo.NO),
                readEnum(tag.getString(TAG_FUZZY), FuzzyMode.class, FuzzyMode.IGNORE_ALL),
                tag.getBoolean(TAG_EXTRACT_ONLY),
                content,
                readContentEntryCount(tag, TAG_CONTENT_COUNT, content),
                tag.getBoolean(TAG_CONTENT_TRUNCATED),
                tag.getString(TAG_CONTENT_REVISION),
                readEnum(tag.getString(TAG_PARTITION_MODE), CellTerminalBusPartitionMode.class,
                    CellTerminalBusPartitionMode.SLOTS),
                tag.getInteger(TAG_PARTITION_SLOT_CAPACITY),
                GenericStack.readList(tag.getTagList(TAG_PARTITION, Constants.NBT.TAG_COMPOUND)),
                tag.getString(TAG_TEXT_PARTITION_PRIMARY),
                tag.getString(TAG_TEXT_PARTITION_SECONDARY),
                tag.getBoolean(TAG_UPGRADES_LOADED),
                readStacks(tag.getTagList(TAG_UPGRADES, Constants.NBT.TAG_COMPOUND)),
                tag.hasKey(TAG_ICON, Constants.NBT.TAG_COMPOUND)
                    ? new ItemStack(tag.getCompoundTag(TAG_ICON)) : ItemStack.EMPTY);
        }

        BusEntry withStoredName(@Nullable String storedName) {
            if (storedName == null || storedName.isEmpty()) {
                return this;
            }
            return new BusEntry(this.stableTargetId, this.locator, new TextComponentString(storedName),
                this.connectedDisplayName, storedName, this.priority, this.accessRestriction, this.storageFilter,
                this.filterOnExtract, this.fuzzyMode, this.extractableOnly, this.content, this.contentEntryCount,
                this.contentTruncated, this.contentRevision, this.partitionMode, this.partitionSlotCapacity,
                this.partition, this.textPartitionPrimary, this.textPartitionSecondary, this.upgradesLoaded,
                this.upgrades, this.icon);
        }

        public ITextComponent visibleName() {
            return this.renamedDisplayName == null || this.renamedDisplayName.isEmpty()
                ? this.displayName
                : new TextComponentString(this.renamedDisplayName);
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setTag(TAG_LOCATOR, writeLocator(this.locator));
            writeText(tag, TAG_DISPLAY, this.displayName);
            writeText(tag, TAG_CONNECTED_DISPLAY, this.connectedDisplayName);
            if (this.renamedDisplayName != null) {
                tag.setString(TAG_RENAMED, this.renamedDisplayName);
            }
            tag.setInteger(TAG_PRIORITY, this.priority);
            tag.setString(TAG_ACCESS, this.accessRestriction.name());
            tag.setString(TAG_STORAGE_FILTER, this.storageFilter.name());
            tag.setString(TAG_FILTER_ON_EXTRACT, this.filterOnExtract.name());
            tag.setString(TAG_FUZZY, this.fuzzyMode.name());
            tag.setBoolean(TAG_EXTRACT_ONLY, this.extractableOnly);
            tag.setTag(TAG_CONTENT, writeList(this.content, ContentEntry::toTag));
            tag.setInteger(TAG_CONTENT_COUNT, this.contentEntryCount);
            tag.setBoolean(TAG_CONTENT_TRUNCATED, this.contentTruncated);
            tag.setString(TAG_CONTENT_REVISION, this.contentRevision);
            tag.setString(TAG_PARTITION_MODE, this.partitionMode.name());
            tag.setInteger(TAG_PARTITION_SLOT_CAPACITY, this.partitionSlotCapacity);
            tag.setTag(TAG_PARTITION, GenericStack.writeList(this.partition));
            tag.setString(TAG_TEXT_PARTITION_PRIMARY, this.textPartitionPrimary);
            tag.setString(TAG_TEXT_PARTITION_SECONDARY, this.textPartitionSecondary);
            tag.setBoolean(TAG_UPGRADES_LOADED, this.upgradesLoaded);
            tag.setTag(TAG_UPGRADES, writeStacks(this.upgrades));
            if (!this.icon.isEmpty()) {
                tag.setTag(TAG_ICON, this.icon.serializeNBT());
            }
            return tag;
        }

        BusEntry lightSnapshot() {
            return new BusEntry(
                this.stableTargetId,
                this.locator,
                this.displayName,
                this.connectedDisplayName,
                this.renamedDisplayName,
                this.priority,
                this.accessRestriction,
                this.storageFilter,
                this.filterOnExtract,
                this.fuzzyMode,
                this.extractableOnly,
                List.of(),
                this.contentEntryCount,
                this.contentEntryCount > 0,
                this.contentRevision,
                this.partitionMode,
                this.partitionSlotCapacity,
                List.of(),
                this.textPartitionPrimary,
                this.textPartitionSecondary,
                false,
                List.of(),
                this.icon);
        }
    }

    public record SubnetEntry(String stableTargetId,
                              CellTerminalTargetLocator locator,
                              String subnetId,
                              ITextComponent displayName,
                              @Nullable String renamedDisplayName,
                              boolean favorite,
                              boolean lastLoaded,
                              boolean mainNetwork,
                              List<ConnectionEntry> connections) {
        private static final String TAG_STABLE_ID = "stableId";
        private static final String TAG_LOCATOR = "locator";
        private static final String TAG_SUBNET_ID = "subnetId";
        private static final String TAG_DISPLAY = "display";
        private static final String TAG_RENAMED = "renamed";
        private static final String TAG_FAVORITE = "favorite";
        private static final String TAG_LAST_LOADED = "lastLoaded";
        private static final String TAG_MAIN_NETWORK = "mainNetwork";
        private static final String TAG_CONNECTIONS = "connections";
        private static final String MAIN_NETWORK_ID = "cell_terminal:main_network";
        private static final ResourceLocation MAIN_NETWORK_KIND =
            new ResourceLocation("ae2", "cell_terminal/main_network");

        public SubnetEntry {
            Objects.requireNonNull(stableTargetId, "stableTargetId");
            Objects.requireNonNull(locator, "locator");
            Objects.requireNonNull(subnetId, "subnetId");
            Objects.requireNonNull(displayName, "displayName");
            connections = List.copyOf(connections);
        }

        static SubnetEntry fromTarget(CellTerminalSubnetTarget target, CellTerminalSubnetLedger ledger,
                                      CellTerminalSubnetNameData subnetNameData,
                                      boolean loadConnections,
                                      UUID playerId) {
            String subnetId = target.subnetId();
            var handle = CellTerminalSubnetHandle.fromTarget(target);
            String renamedDisplayName = subnetNameData.getOrMigrateDisplayName(handle, ledger.getDisplayName(handle));
            var pos = target.locator().pos();
            List<ConnectionEntry> connections = loadConnections
                ? target.getConnections().stream().map(ConnectionEntry::fromConnection).toList()
                : List.of();
            return new SubnetEntry(
                target.stableTargetId(),
                target.locator(),
                subnetId,
                new TextComponentTranslation("gui.ae2.CellTerminal.subnet.default_name",
                    pos.getX(), pos.getY(), pos.getZ()),
                renamedDisplayName,
                ledger.isFavorite(playerId, handle),
                ledger.isLastLoaded(playerId, handle),
                false,
                connections);
        }

        static SubnetEntry mainNetwork(boolean lastLoaded) {
            return new SubnetEntry(
                MAIN_NETWORK_ID,
                new CellTerminalTargetLocator(MAIN_NETWORK_KIND, 0, BlockPos.ORIGIN, null),
                MAIN_NETWORK_ID,
                CellTerminalSubnetMainNetwork.text(),
                null,
                false,
                lastLoaded,
                true,
                List.of());
        }

        static SubnetEntry fromTag(NBTTagCompound tag) {
            ITextComponent display = readText(tag, TAG_DISPLAY);
            return new SubnetEntry(
                tag.getString(TAG_STABLE_ID),
                readLocator(tag.getCompoundTag(TAG_LOCATOR)),
                tag.getString(TAG_SUBNET_ID),
                display == null ? new TextComponentString(tag.getString(TAG_SUBNET_ID)) : display,
                tag.hasKey(TAG_RENAMED, Constants.NBT.TAG_STRING) ? tag.getString(TAG_RENAMED) : null,
                tag.getBoolean(TAG_FAVORITE),
                tag.getBoolean(TAG_LAST_LOADED),
                tag.getBoolean(TAG_MAIN_NETWORK),
                readList(tag.getTagList(TAG_CONNECTIONS, Constants.NBT.TAG_COMPOUND), ConnectionEntry::fromTag));
        }

        public ITextComponent visibleName() {
            return this.renamedDisplayName == null || this.renamedDisplayName.isEmpty()
                ? this.displayName
                : new TextComponentString(this.renamedDisplayName);
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setTag(TAG_LOCATOR, writeLocator(this.locator));
            tag.setString(TAG_SUBNET_ID, this.subnetId);
            writeText(tag, TAG_DISPLAY, this.displayName);
            if (this.renamedDisplayName != null) {
                tag.setString(TAG_RENAMED, this.renamedDisplayName);
            }
            tag.setBoolean(TAG_FAVORITE, this.favorite);
            tag.setBoolean(TAG_LAST_LOADED, this.lastLoaded);
            tag.setBoolean(TAG_MAIN_NETWORK, this.mainNetwork);
            tag.setTag(TAG_CONNECTIONS, writeList(this.connections, ConnectionEntry::toTag));
            return tag;
        }
    }

    public record ConnectionEntry(String stableTargetId,
                                  CellTerminalTargetLocator locator,
                                  ITextComponent displayName,
                                  boolean outbound,
                                  List<ContentEntry> content,
                                  int partitionSlotCapacity,
                                  List<@Nullable GenericStack> partition) {
        private static final String TAG_STABLE_ID = "stableId";
        private static final String TAG_LOCATOR = "locator";
        private static final String TAG_DISPLAY = "display";
        private static final String TAG_OUTBOUND = "outbound";
        private static final String TAG_CONTENT = "content";
        private static final String TAG_PARTITION_CAPACITY = "partitionCapacity";
        private static final String TAG_PARTITION = "partition";

        public ConnectionEntry {
            Objects.requireNonNull(stableTargetId, "stableTargetId");
            Objects.requireNonNull(locator, "locator");
            Objects.requireNonNull(displayName, "displayName");
            content = List.copyOf(content);
            partition = ObjectLists.unmodifiable(new ObjectArrayList<>(partition));
        }

        static ConnectionEntry fromConnection(CellTerminalSubnetConnection connection) {
            CellTerminalBusTarget bus = connection.target();
            var content = bus.previewContent();
            return new ConnectionEntry(
                bus.stableTargetId(),
                bus.locator(),
                bus.displayName(),
                connection.outbound(),
                displayContentEntries(content.entries()),
                bus.getPartitionSlotCapacity(),
                bus.getPartitionSnapshot().slots());
        }

        static ConnectionEntry fromTag(NBTTagCompound tag) {
            ITextComponent display = readText(tag, TAG_DISPLAY);
            List<@Nullable GenericStack> partition =
                GenericStack.readList(tag.getTagList(TAG_PARTITION, Constants.NBT.TAG_COMPOUND));
            return new ConnectionEntry(
                tag.getString(TAG_STABLE_ID),
                readLocator(tag.getCompoundTag(TAG_LOCATOR)),
                display == null ? new TextComponentString("") : display,
                tag.getBoolean(TAG_OUTBOUND),
                readList(tag.getTagList(TAG_CONTENT, Constants.NBT.TAG_COMPOUND), ContentEntry::fromTag),
                tag.hasKey(TAG_PARTITION_CAPACITY, Constants.NBT.TAG_INT)
                    ? tag.getInteger(TAG_PARTITION_CAPACITY)
                    : partition.size(),
                partition);
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setTag(TAG_LOCATOR, writeLocator(this.locator));
            writeText(tag, TAG_DISPLAY, this.displayName);
            tag.setBoolean(TAG_OUTBOUND, this.outbound);
            tag.setTag(TAG_CONTENT, writeList(this.content, ContentEntry::toTag));
            tag.setInteger(TAG_PARTITION_CAPACITY, this.partitionSlotCapacity);
            tag.setTag(TAG_PARTITION, GenericStack.writeList(this.partition));
            return tag;
        }
    }

    public record ToolPreview(CellTerminalNetworkToolOperation operation,
                              String contextId,
                              String token,
                              String planSignature,
                              @Nullable UniqueTypeSummary uniqueTypeSummary,
                              List<TargetBreakdown> targetBreakdown,
                              List<ToolPlan> plans,
                              List<ToolFailureEntry> failures) {
        private static final String TAG_OPERATION = "operation";
        private static final String TAG_CONTEXT = "context";
        private static final String TAG_TOKEN = "token";
        private static final String TAG_SIGNATURE = "signature";
        private static final String TAG_UNIQUE_TYPE_SUMMARY = "uniqueTypeSummary";
        private static final String TAG_TARGET_BREAKDOWN = "targetBreakdown";
        private static final String TAG_PLANS = "plans";
        private static final String TAG_FAILURES = "failures";

        public ToolPreview {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(contextId, "contextId");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(planSignature, "planSignature");
            targetBreakdown = List.copyOf(targetBreakdown);
            plans = List.copyOf(plans);
            failures = List.copyOf(failures);
        }

        public static ToolPreview fromPreview(CellTerminalNetworkToolPreview preview) {
            List<ToolPlan> plans = preview.plans().stream()
                                          .limit(MAX_DISPLAY_TOOL_PLANS)
                                          .map(ToolPlan::fromPlan)
                                          .toList();
            List<ToolFailureEntry> failures = preview.failures().stream()
                                                     .limit(MAX_DISPLAY_TOOL_FAILURES)
                                                     .map(ToolFailureEntry::fromFailure)
                                                     .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            addToolPreviewTruncationFailure(failures, preview.plans().size(), preview.failures().size());
            return new ToolPreview(
                preview.operation(),
                preview.contextId(),
                preview.token().value(),
                preview.planSignature(),
                UniqueTypeSummary.fromPreview(preview.uniqueTypeSummary()),
                preview.targetBreakdown().stream().map(TargetBreakdown::fromPreview).toList(),
                plans,
                failures);
        }

        public static ToolPreview fromResult(ToolPreview preview, CellTerminalActionResult result) {
            List<ToolPlan> plans = result.appliedPlans().stream()
                                         .limit(MAX_DISPLAY_TOOL_PLANS)
                                         .map(ToolPlan::fromPlan)
                                         .toList();
            List<ToolFailureEntry> failures = result.failures().stream()
                                                    .limit(MAX_DISPLAY_TOOL_FAILURES)
                                                    .map(ToolFailureEntry::fromFailure)
                                                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            addToolPreviewTruncationFailure(failures, result.appliedPlans().size(), result.failures().size());
            return new ToolPreview(
                preview.operation(),
                preview.contextId(),
                preview.token(),
                preview.planSignature(),
                preview.uniqueTypeSummary(),
                preview.targetBreakdown(),
                plans,
                failures);
        }

        private static void addToolPreviewTruncationFailure(List<ToolFailureEntry> failures, int planCount,
                                                            int failureCount) {
            if (planCount > MAX_DISPLAY_TOOL_PLANS || failureCount > MAX_DISPLAY_TOOL_FAILURES) {
                failures.add(ToolFailureEntry.of(
                    "tool_preview_truncated",
                    "gui.ae2.CellTerminal.networktools.failure.preview_truncated"));
            }
        }

        static ToolPreview fromTag(NBTTagCompound tag) {
            return new ToolPreview(
                readEnum(tag.getString(TAG_OPERATION), CellTerminalNetworkToolOperation.class,
                    CellTerminalNetworkToolOperation.PARTITION_CELLS_BY_CONTENT),
                tag.getString(TAG_CONTEXT),
                tag.getString(TAG_TOKEN),
                tag.getString(TAG_SIGNATURE),
                tag.hasKey(TAG_UNIQUE_TYPE_SUMMARY, Constants.NBT.TAG_COMPOUND)
                    ? UniqueTypeSummary.fromTag(tag.getCompoundTag(TAG_UNIQUE_TYPE_SUMMARY))
                    : null,
                readList(tag.getTagList(TAG_TARGET_BREAKDOWN, Constants.NBT.TAG_COMPOUND), TargetBreakdown::fromTag),
                readList(tag.getTagList(TAG_PLANS, Constants.NBT.TAG_COMPOUND), ToolPlan::fromTag),
                readList(tag.getTagList(TAG_FAILURES, Constants.NBT.TAG_COMPOUND), ToolFailureEntry::fromTag));
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_OPERATION, this.operation.name());
            tag.setString(TAG_CONTEXT, this.contextId);
            tag.setString(TAG_TOKEN, this.token);
            tag.setString(TAG_SIGNATURE, this.planSignature);
            if (this.uniqueTypeSummary != null) {
                tag.setTag(TAG_UNIQUE_TYPE_SUMMARY, this.uniqueTypeSummary.toTag());
            }
            tag.setTag(TAG_TARGET_BREAKDOWN, writeList(this.targetBreakdown, TargetBreakdown::toTag));
            tag.setTag(TAG_PLANS, writeList(this.plans, ToolPlan::toTag));
            tag.setTag(TAG_FAILURES, writeList(this.failures, ToolFailureEntry::toTag));
            return tag;
        }
    }

    public record UniqueTypeSummary(int availableCellCount,
                                    int uniqueTypeCount,
                                    List<TypeBreakdown> breakdown) {
        private static final String TAG_AVAILABLE = "available";
        private static final String TAG_UNIQUE = "unique";
        private static final String TAG_BREAKDOWN = "breakdown";

        public UniqueTypeSummary {
            breakdown = List.copyOf(breakdown);
        }

        static @Nullable UniqueTypeSummary fromPreview(@Nullable CellTerminalNetworkToolPreview.UniqueTypeSummary summary) {
            if (summary == null) {
                return null;
            }
            return new UniqueTypeSummary(
                summary.availableCellCount(),
                summary.uniqueTypeCount(),
                summary.breakdown().stream().map(TypeBreakdown::fromPreview).toList());
        }

        static UniqueTypeSummary fromTag(NBTTagCompound tag) {
            return new UniqueTypeSummary(
                tag.getInteger(TAG_AVAILABLE),
                tag.getInteger(TAG_UNIQUE),
                readList(tag.getTagList(TAG_BREAKDOWN, Constants.NBT.TAG_COMPOUND), TypeBreakdown::fromTag));
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setInteger(TAG_AVAILABLE, this.availableCellCount);
            tag.setInteger(TAG_UNIQUE, this.uniqueTypeCount);
            tag.setTag(TAG_BREAKDOWN, writeList(this.breakdown, TypeBreakdown::toTag));
            return tag;
        }
    }

    public record TypeBreakdown(String typeId, int availableCellCount, int uniqueTypeCount) {
        private static final String TAG_TYPE = "type";
        private static final String TAG_AVAILABLE = "available";
        private static final String TAG_UNIQUE = "unique";

        static TypeBreakdown fromPreview(CellTerminalNetworkToolPreview.TypeBreakdown breakdown) {
            return new TypeBreakdown(
                breakdown.typeId(),
                breakdown.availableCellCount(),
                breakdown.uniqueTypeCount());
        }

        static TypeBreakdown fromTag(NBTTagCompound tag) {
            return new TypeBreakdown(
                tag.getString(TAG_TYPE),
                tag.getInteger(TAG_AVAILABLE),
                tag.getInteger(TAG_UNIQUE));
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_TYPE, this.typeId);
            tag.setInteger(TAG_AVAILABLE, this.availableCellCount);
            tag.setInteger(TAG_UNIQUE, this.uniqueTypeCount);
            return tag;
        }
    }

    public record TargetBreakdown(String label, int count) {
        private static final String TAG_LABEL = "label";
        private static final String TAG_COUNT = "count";

        static TargetBreakdown fromPreview(CellTerminalNetworkToolPreview.TargetBreakdown breakdown) {
            return new TargetBreakdown(breakdown.label(), breakdown.count());
        }

        static TargetBreakdown fromTag(NBTTagCompound tag) {
            return new TargetBreakdown(tag.getString(TAG_LABEL), tag.getInteger(TAG_COUNT));
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_LABEL, this.label);
            tag.setInteger(TAG_COUNT, this.count);
            return tag;
        }
    }

    public record ToolPlan(String stableTargetId,
                           int slotIndex,
                           int expectedCapacity,
                           List<@Nullable GenericStack> partition) {
        private static final String TAG_STABLE_ID = "stableId";
        private static final String TAG_SLOT = "slot";
        private static final String TAG_CAPACITY = "capacity";
        private static final String TAG_PARTITION = "partition";

        public ToolPlan {
            Objects.requireNonNull(stableTargetId, "stableTargetId");
            partition = ObjectLists.unmodifiable(new ObjectArrayList<>(partition));
        }

        static ToolPlan fromPlan(CellTerminalPartitionPlan plan) {
            return new ToolPlan(plan.stableTargetId(), plan.slotIndex(), plan.expectedCapacity(), plan.partitionSlots());
        }

        static ToolPlan fromTag(NBTTagCompound tag) {
            return new ToolPlan(
                tag.getString(TAG_STABLE_ID),
                tag.getInteger(TAG_SLOT),
                tag.getInteger(TAG_CAPACITY),
                GenericStack.readList(tag.getTagList(TAG_PARTITION, Constants.NBT.TAG_COMPOUND)));
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setInteger(TAG_SLOT, this.slotIndex);
            tag.setInteger(TAG_CAPACITY, this.expectedCapacity);
            tag.setTag(TAG_PARTITION, GenericStack.writeList(this.partition));
            return tag;
        }
    }

    public record ToolFailureEntry(String reason, String message, List<String> messageArgs) {
        private static final String TAG_REASON = "reason";
        private static final String TAG_MESSAGE = "message";
        private static final String TAG_MESSAGE_ARGS = "messageArgs";

        public ToolFailureEntry {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(message, "message");
            messageArgs = List.copyOf(messageArgs);
        }

        public static ToolFailureEntry of(String reason, String message) {
            return new ToolFailureEntry(reason, message, List.of());
        }

        public static ToolFailureEntry of(String reason, String message, List<String> messageArgs) {
            return new ToolFailureEntry(reason, message, messageArgs);
        }

        static ToolFailureEntry fromFailure(CellTerminalActionFailure failure) {
            String localizedMessage = failure.message().isEmpty()
                ? "gui.ae2.CellTerminal.networktools.failure.target_failed"
                : failure.message();
            if (localizedMessage.contains("|")) {
                String[] parts = localizedMessage.split("\\|");
                return of(failure.reason(), parts[0], List.of(parts).subList(1, parts.length));
            }
            return of(failure.reason(), localizedMessage);
        }

        static ToolFailureEntry fromTag(NBTTagCompound tag) {
            return new ToolFailureEntry(
                tag.getString(TAG_REASON),
                tag.getString(TAG_MESSAGE),
                readStrings(tag.getTagList(TAG_MESSAGE_ARGS, Constants.NBT.TAG_STRING)));
        }

        NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_REASON, this.reason);
            tag.setString(TAG_MESSAGE, this.message);
            var args = new NBTTagList();
            for (var arg : this.messageArgs) {
                args.appendTag(new NBTTagString(arg));
            }
            tag.setTag(TAG_MESSAGE_ARGS, args);
            return tag;
        }
    }
}
