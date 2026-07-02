package ae2.container.implementations;

import ae2.api.cellterminal.CellTerminalBusPartitionMode;
import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalCapability;
import ae2.api.cellterminal.CellTerminalCellSlotMutation;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalContainerHost;
import ae2.api.cellterminal.CellTerminalContentSnapshot;
import ae2.api.cellterminal.CellTerminalIoFilterMode;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalSubnetConnection;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.config.AccessRestriction;
import ae2.api.config.FuzzyMode;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.StorageCells;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.DimensionalBlockPos;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.cellterminal.internal.TempCellStorageTarget;
import ae2.cellterminal.server.CellTerminalActionResult;
import ae2.cellterminal.server.CellTerminalActionStatus;
import ae2.cellterminal.server.CellTerminalActionToken;
import ae2.cellterminal.server.CellTerminalCellSlotHandle;
import ae2.cellterminal.server.CellTerminalNetworkTool;
import ae2.cellterminal.server.CellTerminalNetworkToolImpl;
import ae2.cellterminal.server.CellTerminalNetworkToolOperation;
import ae2.cellterminal.server.CellTerminalNetworkToolPreview;
import ae2.cellterminal.server.CellTerminalScannerCore;
import ae2.cellterminal.server.CellTerminalServerConfig;
import ae2.cellterminal.server.CellTerminalSession;
import ae2.cellterminal.server.CellTerminalSnapshot;
import ae2.cellterminal.server.CellTerminalStorageNameData;
import ae2.cellterminal.server.CellTerminalSubnetActionResult;
import ae2.cellterminal.server.CellTerminalSubnetHandle;
import ae2.cellterminal.server.CellTerminalSubnetLedger;
import ae2.cellterminal.server.CellTerminalSubnetNameData;
import ae2.cellterminal.server.CellTerminalTargetAccess;
import ae2.cellterminal.server.CellTerminalTargetHandle;
import ae2.cellterminal.server.CellTerminalTargetLookup;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.CellTerminalSubnetHighlightPacket;
import ae2.core.network.clientbound.CellTerminalSyncPacket;
import ae2.core.network.serverbound.GuiActionPacket;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server container for the Cell Terminal first GUI loop.
 * <p>
 * The container owns live session state and exposes client actions that resolve all write targets through stable
 * locator handles before mutating server-side data.
 */
public class ContainerCellTerminal extends AEBaseContainer implements IKeyTypeSelectionContainer {
    private static final String ACTION_SET_TAB = "cellTerminal.setTab";
    private static final String ACTION_REFRESH = "cellTerminal.refresh";
    private static final String ACTION_LOAD_SUBNET = "cellTerminal.loadSubnet";
    private static final String ACTION_RETURN_PARENT = "cellTerminal.returnParent";
    private static final String ACTION_RENAME_SUBNET = "cellTerminal.renameSubnet";
    private static final String ACTION_FAVORITE_SUBNET = "cellTerminal.favoriteSubnet";
    private static final String ACTION_RESTORE_LAST_SUBNET = "cellTerminal.restoreLastSubnet";
    private static final String ACTION_HIGHLIGHT_SUBNET = "cellTerminal.highlightSubnet";
    private static final String ACTION_HIGHLIGHT_SUBNET_CONNECTION = "cellTerminal.highlightSubnetConnection";
    private static final String ACTION_HIGHLIGHT_TARGET = "cellTerminal.highlightTarget";
    private static final String ACTION_PREVIEW_TOOL = "cellTerminal.previewTool";
    private static final String ACTION_EXECUTE_TOOL = "cellTerminal.executeTool";
    private static final String ACTION_WRITE_PARTITION = "cellTerminal.writePartition";
    private static final String ACTION_WRITE_BUS_TEXT_PARTITION = "cellTerminal.writeBusTextPartition";
    private static final String ACTION_WRITE_BUS_PRECISE_PARTITION_AMOUNT =
        "cellTerminal.writeBusPrecisePartitionAmount";
    private static final String ACTION_WRITE_PRIORITY = "cellTerminal.writePriority";
    private static final String ACTION_RENAME_STORAGE = "cellTerminal.renameStorage";
    private static final String ACTION_RENAME_BUS = "cellTerminal.renameBus";
    private static final String ACTION_WRITE_BUS_MODE = "cellTerminal.writeBusMode";
    private static final String ACTION_WRITE_CELL_SLOT = "cellTerminal.writeCellSlot";
    private static final String ACTION_SEND_TEMP_CELL = "cellTerminal.sendTempCell";
    private static final String ACTION_SELECT_TARGET_UPGRADES = "cellTerminal.selectTargetUpgrades";
    private static final String ACTION_INSTALL_TARGET_UPGRADE = "cellTerminal.installTargetUpgrade";
    private static final String ACTION_INSTALL_VISIBLE_UPGRADE = "cellTerminal.installVisibleUpgrade";
    private static final String ACTION_INTERACT_TARGET_UPGRADE = "cellTerminal.interactTargetUpgrade";
    private static final String ACTION_REQUEST_CONTENT_PAGE = "cellTerminal.requestContentPage";
    private static final int ACTION_MAX_PAYLOAD = GuiActionPacket.MAX_JSON_PAYLOAD_LENGTH - 4_096;
    private static final int MAX_CLIENT_PARTITION_SLOTS = 63;
    private static final int MAX_TARGET_UPGRADE_SLOTS = 8;
    private static final int TARGET_CONTENT_PAGE_SIZE = 128;
    private static final CellTerminalServerConfig SERVER_CONFIG = CellTerminalServerConfig.loadFromConfig();
    private static final ResourceLocation TEMP_CELL_KIND = AppEng.makeId("cell_terminal/temp_cells");
    private static final int TEMP_CELL_LOCATOR_DIMENSION = Integer.MIN_VALUE;
    private static final BlockPos TEMP_CELL_LOCATOR_POS = BlockPos.ORIGIN;

    private final CellTerminalContainerHost host;
    private final CellTerminalScannerCore scanner;
    private final CellTerminalNetworkTool networkTool;
    private final CellTerminalTargetAccess worldTargetAccess;
    private final CellTerminalTargetLookup targetAccess;
    private final KeyTypeSelection fallbackKeyTypeSelection = new KeyTypeSelection(() -> {
    }, keyType -> true);
    private final TargetUpgradeInventory targetUpgradeInventory = new TargetUpgradeInventory();
    private final List<AppEngSlot> targetUpgradeSlots = new ArrayList<>(MAX_TARGET_UPGRADE_SLOTS);
    private final ItemStack[] clientTargetUpgradeMirror = new ItemStack[MAX_TARGET_UPGRADE_SLOTS];
    @GuiSync(150)
    public IKeyTypeSelectionContainer.SyncedKeyTypes visibleKeyTypes = new IKeyTypeSelectionContainer.SyncedKeyTypes();
    @Nullable
    private CellTerminalSession session;
    @Nullable
    private CellTerminalSnapshot lastSnapshot;
    @Nullable
    private CellTerminalNetworkToolPreview lastPreview;
    private CellTerminalClientState state = CellTerminalClientState.empty();
    private CellTerminalClientState.CellTerminalTab selectedTab = CellTerminalClientState.CellTerminalTab.OVERVIEW;
    @Nullable
    private CellTerminalClientState.ToolPreview toolPreview;
    @Nullable
    private CellTerminalClientState.ContentPage contentPage;
    @Nullable
    private String selectedTargetId;
    @Nullable
    private CellTerminalTargetLocator selectedTargetLocator;
    private int selectedSlotIndex = -1;
    private long lastSentRevision = Long.MIN_VALUE;
    @Nullable
    private String lastSentContextId;
    @Nullable
    private CellTerminalClientState.CellTerminalTab lastSentTab;
    private long lastBuiltRevision = Long.MIN_VALUE;
    @Nullable
    private String lastBuiltContextId;
    @Nullable
    private CellTerminalClientState.CellTerminalTab lastBuiltTab;
    @Nullable
    private CellTerminalClientState.ToolPreview lastBuiltPreview;
    @Nullable
    private CellTerminalClientState.ContentPage lastBuiltContentPage;
    @Nullable
    private String lastBuiltSelectedTargetId;
    @Nullable
    private CellTerminalTargetLocator lastBuiltSelectedTargetLocator;
    private int lastBuiltSelectedSlotIndex = Integer.MIN_VALUE;
    private boolean syncRequested = true;
    private boolean scanRequested = true;
    private int ticksSinceLastScan = SERVER_CONFIG.periodicScanIntervalTicks();
    private long lastScannedCacheRevision = Long.MIN_VALUE;

    public ContainerCellTerminal(InventoryPlayer inventoryPlayer, CellTerminalContainerHost host) {
        super(inventoryPlayer, host);
        this.host = Objects.requireNonNull(host, "host");
        this.scanner = new CellTerminalScannerCore();
        this.worldTargetAccess = new CellTerminalTargetAccess();
        this.targetAccess = new ContainerTargetLookup();
        this.networkTool = new CellTerminalNetworkToolImpl(this.targetAccess);
        Arrays.fill(this.clientTargetUpgradeMirror, ItemStack.EMPTY);

        addTempCellSlots(host.getTempCellStorage());
        addWirelessSlots(host);
        addTargetUpgradeSlots();
        addPlayerInventorySlots(24, 288);

        registerClientAction(ACTION_SET_TAB, String.class, this::setTab);
        registerClientAction(ACTION_REFRESH, this::refreshFromClient);
        registerClientAction(ACTION_LOAD_SUBNET, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> loadSubnet(TargetAction.fromPayload(payload))));
        registerClientAction(ACTION_RETURN_PARENT, this::returnToParent);
        registerClientAction(ACTION_RENAME_SUBNET, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> renameSubnet(RenameSubnetAction.fromPayload(payload))));
        registerClientAction(ACTION_FAVORITE_SUBNET, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> favoriteSubnet(FavoriteSubnetAction.fromPayload(payload))));
        registerClientAction(ACTION_RESTORE_LAST_SUBNET, this::restoreLastSubnet);
        registerClientAction(ACTION_HIGHLIGHT_SUBNET, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> highlightSubnet(TargetAction.fromPayload(payload))));
        registerClientAction(ACTION_HIGHLIGHT_SUBNET_CONNECTION, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> highlightSubnetConnection(SubnetConnectionAction.fromPayload(payload))));
        registerClientAction(ACTION_HIGHLIGHT_TARGET, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> highlightTarget(TargetAction.fromPayload(payload))));
        registerClientAction(ACTION_PREVIEW_TOOL, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> previewTool(ToolAction.fromPayload(payload))));
        registerClientAction(ACTION_EXECUTE_TOOL, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> executeTool(ExecuteToolAction.fromPayload(payload))));
        registerClientAction(ACTION_WRITE_PARTITION, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> writePartition(WritePartitionAction.fromPayload(payload))));
        registerClientAction(ACTION_WRITE_BUS_TEXT_PARTITION, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> writeBusTextPartition(WriteBusTextPartitionAction.fromPayload(payload))));
        registerClientAction(ACTION_WRITE_BUS_PRECISE_PARTITION_AMOUNT, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> writeBusPrecisePartitionAmount(
                WriteBusPrecisePartitionAmountAction.fromPayload(payload))));
        registerClientAction(ACTION_WRITE_PRIORITY, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> writePriority(WritePriorityAction.fromPayload(payload))));
        registerClientAction(ACTION_RENAME_STORAGE, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> renameStorage(RenameTargetAction.fromPayload(payload))));
        registerClientAction(ACTION_RENAME_BUS, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> renameBus(RenameTargetAction.fromPayload(payload))));
        registerClientAction(ACTION_WRITE_BUS_MODE, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> writeBusMode(WriteBusModeAction.fromPayload(payload))));
        registerClientAction(ACTION_WRITE_CELL_SLOT, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> writeCellSlot(WriteCellSlotAction.fromPayload(payload))));
        registerClientAction(ACTION_SEND_TEMP_CELL, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> sendTempCell(SlotAction.fromTag(readPayload(payload).getCompoundTag("slot")))));
        registerClientAction(ACTION_SELECT_TARGET_UPGRADES, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> selectTargetUpgrades(TargetUpgradeSelection.fromPayload(payload))));
        registerClientAction(ACTION_INSTALL_TARGET_UPGRADE, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> installTargetUpgrade(TargetUpgradeSelection.fromPayload(payload))));
        registerClientAction(ACTION_INSTALL_VISIBLE_UPGRADE, this::installVisibleUpgrade);
        registerClientAction(ACTION_INTERACT_TARGET_UPGRADE, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> interactTargetUpgrade(TargetUpgradeInteraction.fromPayload(payload))));
        registerClientAction(ACTION_REQUEST_CONTENT_PAGE, String.class, ACTION_MAX_PAYLOAD,
            payload -> runClientAction(() -> requestContentPage(ContentPageAction.fromPayload(payload))));
    }

    private static CellTerminalStorageTarget requireVisibleStorage(CellTerminalSnapshot snapshot,
                                                                   CellTerminalTargetHandle handle) {
        for (var storage : snapshot.storageTargets()) {
            if (targetMatches(storage.stableTargetId(), storage.locator(), handle)) {
                return storage;
            }
        }
        throw new IllegalStateException("Cell Terminal storage target is not visible in current snapshot: " + handle);
    }

    private static CellTerminalBusTarget requireVisibleBus(CellTerminalSnapshot snapshot, CellTerminalTargetHandle handle) {
        for (var bus : snapshot.busTargets()) {
            if (targetMatches(bus.stableTargetId(), bus.locator(), handle)) {
                return bus;
            }
        }
        throw new IllegalStateException("Cell Terminal storage bus target is not visible in current snapshot: " + handle);
    }

    private static CellTerminalCellSlotTarget requireVisibleCellSlot(CellTerminalSnapshot snapshot,
                                                                     CellTerminalCellSlotHandle handle) {
        for (var storage : snapshot.storageTargets()) {
            if (!targetMatches(storage.stableTargetId(), storage.locator(), handle.owner())) {
                continue;
            }
            for (var slot : storage.getCellSlots()) {
                if (slot.slotIndex() == handle.slotIndex()) {
                    return slot;
                }
            }
        }
        throw new IllegalStateException("Cell Terminal cell slot is not visible in current snapshot: " + handle);
    }

    private static CellTerminalSubnetTarget requireVisibleSubnet(CellTerminalSnapshot snapshot, TargetAction action) {
        for (var subnet : snapshot.subnetTargets()) {
            if (action.subnetId != null && Objects.equals(action.subnetId, subnet.subnetId())) {
                return subnet;
            }
            if (action.subnetId == null && action.matches(subnet.stableTargetId(), subnet.locator())) {
                return subnet;
            }
        }
        throw new IllegalStateException("Cell Terminal subnet target is not visible in current snapshot: "
            + action.stableTargetId);
    }

    private static CellTerminalSubnetConnection requireVisibleSubnetConnection(CellTerminalSnapshot snapshot,
                                                                               WritePartitionAction action) {
        if (action.subnetTarget == null) {
            throw new IllegalArgumentException("Cell Terminal subnet connection partition action is missing subnet target");
        }
        CellTerminalSubnetTarget subnet = requireVisibleSubnet(snapshot, action.subnetTarget);
        List<CellTerminalSubnetConnection> connections = subnet.getConnections();
        if (action.connectionIndex < 0 || action.connectionIndex >= connections.size()) {
            throw new IllegalStateException("Cell Terminal subnet connection index is not visible: "
                + action.connectionIndex + " for " + action.subnetTarget);
        }
        CellTerminalSubnetConnection connection = connections.get(action.connectionIndex);
        CellTerminalBusTarget target = connection.target();
        if (!target.locator().equals(action.locator)) {
            throw new IllegalStateException("Cell Terminal subnet connection locator changed. requested="
                + action.locator + ", resolved=" + target.locator());
        }
        if (!target.stableTargetId().equals(action.stableTargetId)) {
            throw new IllegalStateException("Cell Terminal subnet connection stable id changed. requested="
                + action.stableTargetId + ", resolved=" + target.stableTargetId());
        }
        return connection;
    }

    private static CellTerminalTargetLocator resolveHighlightLocator(CellTerminalSnapshot snapshot,
                                                                     TargetAction action) {
        CellTerminalTargetHandle handle = action.toTargetHandle();
        for (var storage : snapshot.storageTargets()) {
            if (targetMatches(storage.stableTargetId(), storage.locator(), handle)) {
                return storage.locator();
            }
        }
        for (var bus : snapshot.busTargets()) {
            if (targetMatches(bus.stableTargetId(), bus.locator(), handle)) {
                return bus.locator();
            }
        }
        throw new IllegalStateException("Cell Terminal highlight target is not visible in current snapshot: " + handle);
    }

    private static void validateBusTextPartitionField(CellTerminalBusTarget bus, String fieldId, String expression) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(fieldId, "fieldId");
        expression = expression == null ? "" : expression;
        switch (bus.getPartitionMode()) {
            case MOD_EXPRESSION -> {
                if (!"mod".equals(fieldId)) {
                    throw new IllegalArgumentException("Invalid mod storage bus text field: " + fieldId);
                }
                if (expression.length() > 1024) {
                    throw new IllegalArgumentException("Mod storage bus expression exceeds 1024 characters");
                }
            }
            case ORE_DICTIONARY_EXPRESSIONS -> {
                if (!"odWhite".equals(fieldId) && !"odBlack".equals(fieldId)) {
                    throw new IllegalArgumentException("Invalid ore dictionary storage bus text field: " + fieldId);
                }
                if (expression.length() > 1024 || countODExpressionTokens(expression) > 128) {
                    throw new IllegalArgumentException(
                        "Ore dictionary storage bus expression exceeds 1024 characters or 128 tokens");
                }
            }
            default -> throw new IllegalArgumentException(
                "Storage bus partition mode does not use text expressions: " + bus.getPartitionMode());
        }
    }

    private static int countODExpressionTokens(String expression) {
        int tokens = 0;
        boolean inTag = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (isODTagChar(c)) {
                inTag = true;
                continue;
            }
            if (inTag) {
                tokens++;
                inTag = false;
            }
            if (!Character.isWhitespace(c)) {
                tokens++;
            }
        }
        return inTag ? tokens + 1 : tokens;
    }

    private static boolean isODTagChar(char c) {
        return c == ':' || c == '*' || c == '_' || c == '-' || c == '/' || c == '.'
            || Character.isLetterOrDigit(c);
    }

    private static List<ItemStack> copyUpgradeList(List<ItemStack> upgrades) {
        List<ItemStack> result = new ArrayList<>(upgrades.size());
        for (ItemStack stack : upgrades) {
            result.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return result;
    }

    private static boolean sameItemStack(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() && right.isEmpty();
        }
        return ItemStack.areItemsEqual(left, right)
            && ItemStack.areItemStackTagsEqual(left, right)
            && left.getCount() == right.getCount();
    }

    private static boolean sameUpgradeList(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int slot = 0; slot < left.size(); slot++) {
            if (!sameItemStack(left.get(slot), right.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static boolean targetMatches(String stableTargetId, CellTerminalTargetLocator locator,
                                         CellTerminalTargetHandle handle) {
        return stableTargetId.equals(handle.stableTargetId()) && locator.equals(handle.locator());
    }

    private static void requireClientPartitionSize(List<@Nullable GenericStack> partition) {
        if (partition.size() > MAX_CLIENT_PARTITION_SLOTS) {
            throw new IllegalArgumentException("Cell Terminal partition action has too many slots: "
                + partition.size());
        }
    }

    private static void requirePartitionCapacity(List<@Nullable GenericStack> partition, int capacity, Object target) {
        if (partition.size() > capacity) {
            throw new IllegalArgumentException("Cell Terminal partition action exceeds target capacity: "
                + partition.size() + " > " + capacity + " for " + target);
        }
    }

    private static List<@Nullable GenericStack> resolvePartitionAction(PartitionWriteMode mode,
                                                                       List<@Nullable GenericStack> baseline,
                                                                       int capacity,
                                                                       @Nullable GenericStack key,
                                                                       int partitionSlotIndex,
                                                                       CellTerminalContentSnapshot content,
                                                                       boolean preserveContentAmounts) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(content, "content");
        if (capacity < 0) {
            throw new IllegalArgumentException("Cell Terminal partition capacity must be non-negative");
        }
        return switch (mode) {
            case SET -> paddedPartition(baseline, capacity);
            case ADD -> addPartitionKey(baseline, capacity, requirePartitionKey(key, mode));
            case ADD_AT -> setPartitionSlot(baseline, capacity, partitionSlotIndex,
                requirePartitionKey(key, mode));
            case REMOVE -> removePartitionKey(baseline, capacity, requirePartitionKey(key, mode));
            case REMOVE_AT -> setPartitionSlot(baseline, capacity, partitionSlotIndex, null);
            case TOGGLE -> togglePartitionKey(baseline, capacity, requirePartitionKey(key, mode));
            case CLEAR -> emptyPartition(capacity);
            case SET_FROM_CONTENT -> firstContentAsPartition(content, capacity, preserveContentAmounts);
        };
    }

    private static List<@Nullable GenericStack> setPartitionSlot(List<@Nullable GenericStack> baseline,
                                                                 int capacity,
                                                                 int partitionSlotIndex,
                                                                 @Nullable GenericStack key) {
        var result = paddedPartition(baseline, capacity);
        if (partitionSlotIndex < 0 || partitionSlotIndex >= result.size()) {
            throw new IllegalArgumentException("Cell Terminal partition slot index out of range: "
                + partitionSlotIndex);
        }
        if (key != null && containsPartitionKey(result, key)) {
            for (int slot = 0; slot < result.size(); slot++) {
                GenericStack current = result.get(slot);
                if (slot != partitionSlotIndex && current != null && current.what().equals(key.what())) {
                    result.set(slot, null);
                }
            }
        }
        result.set(partitionSlotIndex, key);
        return result;
    }

    private static GenericStack requirePartitionKey(@Nullable GenericStack key, PartitionWriteMode mode) {
        if (key == null) {
            throw new IllegalArgumentException("Cell Terminal partition " + mode + " action requires a key");
        }
        return new GenericStack(key.what(), Math.max(0, key.amount()));
    }

    private static List<@Nullable GenericStack> paddedPartition(List<@Nullable GenericStack> source, int capacity) {
        var result = new ArrayList<@Nullable GenericStack>(capacity);
        for (int slot = 0; slot < capacity; slot++) {
            GenericStack stack = slot < source.size() ? source.get(slot) : null;
            result.add(stack == null ? null : new GenericStack(stack.what(), stack.amount()));
        }
        return result;
    }

    private static List<@Nullable GenericStack> addPartitionKey(List<@Nullable GenericStack> baseline,
                                                                int capacity,
                                                                GenericStack key) {
        var result = paddedPartition(baseline, capacity);
        if (containsPartitionKey(result, key)) {
            return result;
        }
        for (int slot = 0; slot < result.size(); slot++) {
            if (result.get(slot) == null) {
                result.set(slot, key);
                return result;
            }
        }
        throw new IllegalStateException("Cell Terminal partition has no empty slot for " + key.what());
    }

    private static List<@Nullable GenericStack> removePartitionKey(List<@Nullable GenericStack> baseline,
                                                                   int capacity,
                                                                   GenericStack key) {
        var result = paddedPartition(baseline, capacity);
        for (int slot = 0; slot < result.size(); slot++) {
            GenericStack current = result.get(slot);
            if (current != null && current.what().equals(key.what())) {
                result.set(slot, null);
            }
        }
        return result;
    }

    private static List<@Nullable GenericStack> togglePartitionKey(List<@Nullable GenericStack> baseline,
                                                                   int capacity,
                                                                   GenericStack key) {
        var result = paddedPartition(baseline, capacity);
        if (containsPartitionKey(result, key)) {
            return removePartitionKey(result, capacity, key);
        }
        return addPartitionKey(result, capacity, key);
    }

    private static boolean containsPartitionKey(List<@Nullable GenericStack> partition, GenericStack key) {
        for (var current : partition) {
            if (current != null && current.what().equals(key.what())) {
                return true;
            }
        }
        return false;
    }

    private static List<@Nullable GenericStack> emptyPartition(int capacity) {
        var result = new ArrayList<@Nullable GenericStack>(capacity);
        for (int slot = 0; slot < capacity; slot++) {
            result.add(null);
        }
        return result;
    }

    private static List<@Nullable GenericStack> firstContentAsPartition(CellTerminalContentSnapshot content,
                                                                        int capacity,
                                                                        boolean preserveAmounts) {
        var result = new ArrayList<@Nullable GenericStack>(capacity);
        var stacks = content.firstUniqueStacks(capacity, preserveAmounts);
        for (int slot = 0; slot < capacity; slot++) {
            result.add(slot < stacks.size() ? stacks.get(slot) : null);
        }
        if (content.uniqueKeyCount() > capacity) {
            throw new IllegalStateException("Cell Terminal partition content exceeds writable slot capacity");
        }
        return result;
    }

    private static void requireCapability(CellTerminalTarget target, CellTerminalCapability capability, Object actionTarget) {
        if (!target.supportsCapability(capability)) {
            throw new IllegalStateException("Cell Terminal target does not support " + capability + ": " + actionTarget);
        }
    }

    private static void requireCapability(CellTerminalCellSlotTarget slot, CellTerminalCapability capability,
                                          Object actionTarget) {
        if (!slot.supportsCapability(capability)) {
            throw new IllegalStateException("Cell Terminal cell slot does not support " + capability + ": "
                + actionTarget);
        }
    }

    private static ItemStack singleStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack single = stack.copy();
        single.setCount(1);
        return single;
    }

    private static String tempAreaKey(String suffix) {
        return "gui.ae2.CellTerminal.tempArea." + suffix;
    }

    private static NBTTagCompound readPayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Cell Terminal client action payload is empty");
        }
        if (payload.length() > ACTION_MAX_PAYLOAD) {
            throw new IllegalArgumentException("Cell Terminal client action payload is too large");
        }
        try {
            return JsonToNBT.getTagFromJson(payload);
        } catch (NBTException e) {
            AELog.warn(e, "Ignoring malformed Cell Terminal client action payload");
            throw new IllegalArgumentException("Malformed Cell Terminal client action payload", e);
        }
    }

    private static <E extends Enum<E>> E readEnum(String value, Class<E> enumClass) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing enum value for " + enumClass.getName());
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid enum value for " + enumClass.getName() + ": " + value, e);
        }
    }

    private static <T> NBTTagList writeList(List<T> values, TagWriter<T> writer) {
        var list = new NBTTagList();
        for (T value : values) {
            list.appendTag(writer.write(value));
        }
        return list;
    }

    private void addTempCellSlots(InternalInventory tempCells) {
        int columns = 8;
        for (int slotIndex = 0; slotIndex < tempCells.size(); slotIndex++) {
            int x = 24 + (slotIndex % columns) * 18;
            int y = 242 + (slotIndex / columns) * 18;
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS,
                tempCells, slotIndex, x, y), SlotSemantics.TEMP_CELL);
        }
    }

    private void addWirelessSlots(CellTerminalContainerHost host) {
        if (host instanceof WirelessTerminalGuiHost<?> wirelessHost) {
            setupUpgrades(wirelessHost.getUpgrades());
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.QE_SINGULARITY,
                wirelessHost.getSingularityStorage(), 0, 0, 0);
            slot.setStackLimit(1);
            addSlot(slot, SlotSemantics.WIRELESS_SINGULARITY);
        }
    }

    private void addTargetUpgradeSlots() {
        for (int slotIndex = 0; slotIndex < MAX_TARGET_UPGRADE_SLOTS; slotIndex++) {
            RestrictedInputSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES,
                this.targetUpgradeInventory, slotIndex, 0, 0);
            slot.setAllowEdit(false);
            slot.setNotDraggable();
            slot.setSlotEnabled(false);
            addSlot(slot, SlotSemantics.CELL_TERMINAL_TARGET_UPGRADE);
            this.targetUpgradeSlots.add(slot);
        }
    }

    public CellTerminalClientState getState() {
        return this.state;
    }

    private EnumSet<CellTerminalClientState.CellTerminalTab> getEnabledClientTabs() {
        EnumSet<CellTerminalClientState.CellTerminalTab> enabledTabs =
            EnumSet.noneOf(CellTerminalClientState.CellTerminalTab.class);
        for (CellTerminalClientState.CellTerminalTab tab : CellTerminalClientState.CellTerminalTab.values()) {
            if (SERVER_CONFIG.isTabEnabled(CellTerminalServerConfig.ServerTab.fromSerializedName(tab.name()))) {
                enabledTabs.add(tab);
            }
        }
        return enabledTabs;
    }

    public CellTerminalContainerHost getCellTerminalHost() {
        return this.host;
    }

    public void applyClientState(CellTerminalClientState state) {
        this.state = Objects.requireNonNull(state, "state");
        this.selectedTab = state.tab();
        this.toolPreview = state.preview();
        this.contentPage = state.contentPage();
        this.selectedTargetId = state.selectedTargetId();
        this.selectedTargetLocator = state.selectedTargetLocator();
        this.selectedSlotIndex = state.selectedSlotIndex();
        syncClientTargetUpgradeMirror();
        updateTargetUpgradeSlots();
    }

    public void setTabFromClient(CellTerminalClientState.CellTerminalTab tab) {
        sendClientAction(ACTION_SET_TAB, tab.name());
    }

    public void refreshFromClient() {
        if (isClientSide()) {
            sendClientAction(ACTION_REFRESH);
            return;
        }
        if (!requireActionEnabled(CellTerminalServerConfig.Action.REFRESH, "refresh")) {
            return;
        }
        requestScan();
        requestSync();
    }

    public void loadSubnetFromClient(CellTerminalClientState.SubnetEntry subnet) {
        sendClientAction(ACTION_LOAD_SUBNET, TargetAction.fromSubnet(subnet).toPayload());
    }

    public void returnToParentFromClient() {
        sendClientAction(ACTION_RETURN_PARENT);
    }

    public void restoreLastSubnetFromClient() {
        sendClientAction(ACTION_RESTORE_LAST_SUBNET);
    }

    public void highlightSubnetFromClient(CellTerminalClientState.SubnetEntry subnet) {
        sendClientAction(ACTION_HIGHLIGHT_SUBNET, TargetAction.fromSubnet(subnet).toPayload());
    }

    public void highlightSubnetConnectionFromClient(CellTerminalClientState.SubnetEntry subnet,
                                                    CellTerminalClientState.ConnectionEntry connection,
                                                    int connectionIndex) {
        sendClientAction(ACTION_HIGHLIGHT_SUBNET_CONNECTION,
            SubnetConnectionAction.fromConnection(subnet, connection, connectionIndex).toPayload());
    }

    public void highlightStorageFromClient(CellTerminalClientState.StorageEntry storage) {
        sendClientAction(ACTION_HIGHLIGHT_TARGET, TargetAction.fromStorage(storage).toPayload());
    }

    public void highlightBusFromClient(CellTerminalClientState.BusEntry bus) {
        sendClientAction(ACTION_HIGHLIGHT_TARGET, TargetAction.fromBus(bus).toPayload());
    }

    @SuppressWarnings("unused")
    public void renameSubnetFromClient(CellTerminalClientState.SubnetEntry subnet, String name) {
        sendClientAction(ACTION_RENAME_SUBNET,
            new RenameSubnetAction(TargetAction.fromSubnet(subnet), name).toPayload());
    }

    public void favoriteSubnetFromClient(CellTerminalClientState.SubnetEntry subnet, boolean favorite) {
        sendClientAction(ACTION_FAVORITE_SUBNET,
            new FavoriteSubnetAction(TargetAction.fromSubnet(subnet), favorite).toPayload());
    }

    public void previewToolFromClient(CellTerminalNetworkToolOperation operation,
                                      List<ToolCellSlotSelection> cellSlots,
                                      List<CellTerminalClientState.BusEntry> buses) {
        sendClientAction(ACTION_PREVIEW_TOOL, ToolAction.fromSelection(operation, cellSlots, buses).toPayload());
    }

    public void executeToolFromClient(CellTerminalClientState.ToolPreview preview) {
        sendClientAction(ACTION_EXECUTE_TOOL, ExecuteToolAction.fromPreview(preview).toPayload());
    }

    @SuppressWarnings("unused")
    public void writeCellPartitionFromClient(CellTerminalClientState.StorageEntry storage,
                                             CellTerminalClientState.CellSlotEntry slot,
                                             List<@Nullable GenericStack> partition) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlot(storage, slot.slotIndex(), partition).toPayload());
    }

    @SuppressWarnings("unused")
    public void writeBusPartitionFromClient(CellTerminalClientState.BusEntry bus,
                                            List<@Nullable GenericStack> partition) {
        sendClientAction(ACTION_WRITE_PARTITION, WritePartitionAction.fromBus(bus, partition).toPayload());
    }

    @SuppressWarnings("unused")
    public void addCellPartitionFromClient(CellTerminalClientState.StorageEntry storage,
                                           CellTerminalClientState.CellSlotEntry slot,
                                           GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotDelta(storage, slot.slotIndex(), PartitionWriteMode.ADD, stack)
                                .toPayload());
    }

    @SuppressWarnings("unused")
    public void removeCellPartitionFromClient(CellTerminalClientState.StorageEntry storage,
                                              CellTerminalClientState.CellSlotEntry slot,
                                              GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotDelta(storage, slot.slotIndex(), PartitionWriteMode.REMOVE, stack)
                                .toPayload());
    }

    public void addCellPartitionAtFromClient(CellTerminalClientState.StorageEntry storage,
                                             CellTerminalClientState.CellSlotEntry slot,
                                             int partitionSlotIndex,
                                             GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotAt(storage, slot.slotIndex(), PartitionWriteMode.ADD_AT,
                partitionSlotIndex, stack).toPayload());
    }

    public void removeCellPartitionAtFromClient(CellTerminalClientState.StorageEntry storage,
                                                CellTerminalClientState.CellSlotEntry slot,
                                                int partitionSlotIndex) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotAt(storage, slot.slotIndex(), PartitionWriteMode.REMOVE_AT,
                partitionSlotIndex, null).toPayload());
    }

    public void addBusPartitionAtFromClient(CellTerminalClientState.BusEntry bus, int partitionSlotIndex,
                                            GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusAt(bus, PartitionWriteMode.ADD_AT, partitionSlotIndex, stack).toPayload());
    }

    public void removeBusPartitionAtFromClient(CellTerminalClientState.BusEntry bus, int partitionSlotIndex) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusAt(bus, PartitionWriteMode.REMOVE_AT, partitionSlotIndex, null).toPayload());
    }

    public void writeBusTextPartitionFromClient(CellTerminalClientState.BusEntry bus, String fieldId,
                                                String expression) {
        sendClientAction(ACTION_WRITE_BUS_TEXT_PARTITION,
            WriteBusTextPartitionAction.fromBus(bus, fieldId, expression).toPayload());
    }

    public void writeBusPrecisePartitionAmountFromClient(CellTerminalClientState.BusEntry bus, int partitionSlotIndex,
                                                         GenericStack stack) {
        sendClientAction(ACTION_WRITE_BUS_PRECISE_PARTITION_AMOUNT,
            WriteBusPrecisePartitionAmountAction.fromBus(bus, partitionSlotIndex, stack).toPayload());
    }

    @SuppressWarnings("unused")
    public void toggleCellPartitionFromClient(CellTerminalClientState.StorageEntry storage,
                                              CellTerminalClientState.CellSlotEntry slot,
                                              GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotDelta(storage, slot.slotIndex(), PartitionWriteMode.TOGGLE, stack)
                                .toPayload());
    }

    @SuppressWarnings("unused")
    public void clearCellPartitionFromClient(CellTerminalClientState.StorageEntry storage,
                                             CellTerminalClientState.CellSlotEntry slot) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotMode(storage, slot.slotIndex(), PartitionWriteMode.CLEAR).toPayload());
    }

    @SuppressWarnings("unused")
    public void partitionCellFromContentFromClient(CellTerminalClientState.StorageEntry storage,
                                                   CellTerminalClientState.CellSlotEntry slot) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromCellSlotMode(storage, slot.slotIndex(), PartitionWriteMode.SET_FROM_CONTENT)
                                .toPayload());
    }

    @SuppressWarnings("unused")
    public void addBusPartitionFromClient(CellTerminalClientState.BusEntry bus, GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusDelta(bus, PartitionWriteMode.ADD, stack).toPayload());
    }

    @SuppressWarnings("unused")
    public void removeBusPartitionFromClient(CellTerminalClientState.BusEntry bus, GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusDelta(bus, PartitionWriteMode.REMOVE, stack).toPayload());
    }

    @SuppressWarnings("unused")
    public void toggleBusPartitionFromClient(CellTerminalClientState.BusEntry bus, GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusDelta(bus, PartitionWriteMode.TOGGLE, stack).toPayload());
    }

    @SuppressWarnings("unused")
    public void clearBusPartitionFromClient(CellTerminalClientState.BusEntry bus) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusMode(bus, PartitionWriteMode.CLEAR).toPayload());
    }

    @SuppressWarnings("unused")
    public void partitionBusFromContentFromClient(CellTerminalClientState.BusEntry bus) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromBusMode(bus, PartitionWriteMode.SET_FROM_CONTENT).toPayload());
    }

    public void addSubnetConnectionPartitionAtFromClient(CellTerminalClientState.SubnetEntry subnet,
                                                         CellTerminalClientState.ConnectionEntry connection,
                                                         int connectionIndex,
                                                         int partitionSlotIndex,
                                                         GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromSubnetConnectionAt(subnet, connection, connectionIndex, PartitionWriteMode.ADD_AT,
                partitionSlotIndex, stack).toPayload());
    }

    public void removeSubnetConnectionPartitionAtFromClient(CellTerminalClientState.SubnetEntry subnet,
                                                            CellTerminalClientState.ConnectionEntry connection,
                                                            int connectionIndex,
                                                            int partitionSlotIndex) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromSubnetConnectionAt(subnet, connection, connectionIndex,
                PartitionWriteMode.REMOVE_AT, partitionSlotIndex, null).toPayload());
    }

    public void toggleSubnetConnectionPartitionFromClient(CellTerminalClientState.SubnetEntry subnet,
                                                          CellTerminalClientState.ConnectionEntry connection,
                                                          int connectionIndex,
                                                          GenericStack stack) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromSubnetConnectionDelta(subnet, connection, connectionIndex,
                PartitionWriteMode.TOGGLE, stack).toPayload());
    }

    public void clearSubnetConnectionPartitionFromClient(CellTerminalClientState.SubnetEntry subnet,
                                                         CellTerminalClientState.ConnectionEntry connection,
                                                         int connectionIndex) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromSubnetConnectionMode(subnet, connection, connectionIndex,
                PartitionWriteMode.CLEAR).toPayload());
    }

    public void partitionSubnetConnectionFromContentFromClient(CellTerminalClientState.SubnetEntry subnet,
                                                               CellTerminalClientState.ConnectionEntry connection,
                                                               int connectionIndex) {
        sendClientAction(ACTION_WRITE_PARTITION,
            WritePartitionAction.fromSubnetConnectionMode(subnet, connection, connectionIndex,
                PartitionWriteMode.SET_FROM_CONTENT).toPayload());
    }

    @SuppressWarnings("unused")
    public void writeStoragePriorityFromClient(CellTerminalClientState.StorageEntry storage, int priority) {
        sendClientAction(ACTION_WRITE_PRIORITY, WritePriorityAction.fromStorage(storage, priority).toPayload());
    }

    @SuppressWarnings("unused")
    public void writeBusPriorityFromClient(CellTerminalClientState.BusEntry bus, int priority) {
        sendClientAction(ACTION_WRITE_PRIORITY, WritePriorityAction.fromBus(bus, priority).toPayload());
    }

    public void renameStorageFromClient(CellTerminalClientState.StorageEntry storage, String name) {
        sendClientAction(ACTION_RENAME_STORAGE,
            new RenameTargetAction(TargetAction.fromStorage(storage), name).toPayload());
    }

    public void renameBusFromClient(CellTerminalClientState.BusEntry bus, String name) {
        sendClientAction(ACTION_RENAME_BUS,
            new RenameTargetAction(TargetAction.fromBus(bus), name).toPayload());
    }

    @SuppressWarnings("unused")
    public void writeBusModeFromClient(CellTerminalClientState.BusEntry bus,
                                       AccessRestriction accessRestriction,
                                       StorageFilter storageFilter,
                                       YesNo filterOnExtract,
                                       FuzzyMode fuzzyMode) {
        sendClientAction(ACTION_WRITE_BUS_MODE,
            WriteBusModeAction.fromBus(bus, accessRestriction, storageFilter, filterOnExtract, fuzzyMode).toPayload());
    }

    public void insertCellFromClient(CellTerminalClientState.StorageEntry storage,
                                     CellTerminalClientState.CellSlotEntry slot) {
        sendClientAction(ACTION_WRITE_CELL_SLOT,
            WriteCellSlotAction.fromCellSlot(storage, slot, CellSlotWriteMode.INSERT).toPayload());
    }

    public void replaceCellFromClient(CellTerminalClientState.StorageEntry storage,
                                      CellTerminalClientState.CellSlotEntry slot) {
        sendClientAction(ACTION_WRITE_CELL_SLOT,
            WriteCellSlotAction.fromCellSlot(storage, slot, CellSlotWriteMode.REPLACE).toPayload());
    }

    public void ejectCellFromClient(CellTerminalClientState.StorageEntry storage,
                                    CellTerminalClientState.CellSlotEntry slot) {
        sendClientAction(ACTION_WRITE_CELL_SLOT,
            WriteCellSlotAction.fromCellSlot(storage, slot, CellSlotWriteMode.EJECT).toPayload());
    }

    public void selectTargetUpgradesFromClient(@Nullable CellTerminalClientState.StorageEntry storage,
                                               @Nullable CellTerminalClientState.CellSlotEntry slot,
                                               @Nullable CellTerminalClientState.BusEntry bus) {
        sendClientAction(ACTION_SELECT_TARGET_UPGRADES,
            TargetUpgradeSelection.fromSelection(storage, slot, bus).toPayload());
    }

    public void installTargetUpgradeFromClient(@Nullable CellTerminalClientState.StorageEntry storage,
                                               @Nullable CellTerminalClientState.CellSlotEntry slot,
                                               @Nullable CellTerminalClientState.BusEntry bus) {
        sendClientAction(ACTION_INSTALL_TARGET_UPGRADE,
            TargetUpgradeSelection.fromSelection(storage, slot, bus).toPayload());
    }

    public void installVisibleUpgradeFromClient() {
        sendClientAction(ACTION_INSTALL_VISIBLE_UPGRADE);
    }

    public void interactTargetUpgradeFromClient(@Nullable CellTerminalClientState.StorageEntry storage,
                                                @Nullable CellTerminalClientState.CellSlotEntry slot,
                                                @Nullable CellTerminalClientState.BusEntry bus,
                                                int upgradeSlot,
                                                boolean quickMove) {
        sendClientAction(ACTION_INTERACT_TARGET_UPGRADE,
            TargetUpgradeInteraction.fromSelection(storage, slot, bus, upgradeSlot, quickMove).toPayload());
    }

    public void requestCellContentPageFromClient(CellTerminalClientState.StorageEntry storage,
                                                 CellTerminalClientState.CellSlotEntry slot,
                                                 int firstIndex) {
        sendClientAction(ACTION_REQUEST_CONTENT_PAGE,
            ContentPageAction.fromCellSlot(storage, slot.slotIndex(), firstIndex).toPayload());
    }

    public void requestBusContentPageFromClient(CellTerminalClientState.BusEntry bus, int firstIndex) {
        sendClientAction(ACTION_REQUEST_CONTENT_PAGE,
            ContentPageAction.fromBus(bus, firstIndex).toPayload());
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP player) {
            updateServerState();
            sendState(player);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            updateVisibleKeyTypes();
            updateServerState();
            if (shouldSendState()) {
                for (IContainerListener listener : this.listeners) {
                    if (listener instanceof EntityPlayerMP player) {
                        sendState(player);
                    }
                }
                markStateSent();
            }
        }
        super.broadcastChanges();
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        if (this.host instanceof KeyTypeSelectionHost selectionHost) {
            return selectionHost.getKeyTypeSelection();
        }
        return this.fallbackKeyTypeSelection;
    }

    @Override
    public IKeyTypeSelectionContainer.SyncedKeyTypes getClientKeyTypeSelection() {
        return this.visibleKeyTypes;
    }

    private void updateVisibleKeyTypes() {
        this.visibleKeyTypes = new IKeyTypeSelectionContainer.SyncedKeyTypes(
            getServerKeyTypeSelection().enabled());
    }

    public boolean canConfigureTypeFilter() {
        return this.host instanceof KeyTypeSelectionHost;
    }

    @Override
    public void onSlotChange(Slot slot) {
        super.onSlotChange(slot);
        if (isServerSide() && slot instanceof AppEngSlot appEngSlot
            && appEngSlot.getInventory() == this.host.getTempCellStorage()) {
            CellTerminalSession activeSession = this.session;
            if (activeSession != null) {
                activeSession.markCacheStale();
            }
            requestScan();
            requestSync();
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (isClientSide() || index < 0 || index >= this.inventorySlots.size()) {
            return ItemStack.EMPTY;
        }

        Slot clickSlot = this.inventorySlots.get(index);
        if (!isValidQuickMoveSource(clickSlot, player) || !isPlayerSideSlot(clickSlot)
            || !Upgrades.isUpgradeCardItem(clickSlot.getStack())) {
            return super.transferStackInSlot(player, index);
        }

        return transferUpgradeStackFromPlayerSlot(player, index, clickSlot);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot candidateSlot, ItemStack stackToMove, boolean fromPlayerSide) {
        if (getSlotSemantic(candidateSlot) == SlotSemantics.CELL_TERMINAL_TARGET_UPGRADE) {
            return false;
        }
        return super.isValidQuickMoveDestination(candidateSlot, stackToMove, fromPlayerSide);
    }

    @Override
    protected int transferStackToContainer(ItemStack input) {
        if (Upgrades.isUpgradeCardItem(input)) {
            return 0;
        }
        ItemStack remainder = input.copy();
        int before = remainder.getCount();
        for (Slot slot : getSlots(SlotSemantics.TEMP_CELL)) {
            if (remainder.isEmpty()) {
                break;
            }
            if (slot.isItemValid(remainder)) {
                remainder = insertIntoCellSlot(slot, remainder);
            }
        }
        return before - remainder.getCount();
    }

    private ItemStack transferUpgradeStackFromPlayerSlot(EntityPlayer player, int index, Slot sourceSlot) {
        ItemStack originalStack = sourceSlot.getStack().copy();
        if (originalStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!sourceSlot.getStack().isEmpty() && transferSingleUpgradeFromSourceSlot(sourceSlot, sourceSlot.getStack())) {
            sourceSlot.onSlotChanged();
            detectAndSendChanges();
            return originalStack;
        }
        return ItemStack.EMPTY;
    }

    private boolean transferSingleUpgradeFromSourceSlot(Slot sourceSlot, ItemStack input) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.TARGET_UPGRADE,
            "transfer target upgrade")) {
            return false;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return false;
        }
        ItemStack sourceBefore = sourceSlot.getStack().copy();
        ItemStack inserted = singleStack(input);
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            SelectedTargetUpgrade originalSelection = findSelectedTargetUpgrade(snapshot);
            SelectedTargetUpgrade target = findFirstVisibleUpgradeTarget(snapshot, input);
            if (target == null) {
                return false;
            }
            int upgradeSlot = findFirstAcceptingUpgradeSlot(target, input);
            if (upgradeSlot < 0) {
                return false;
            }
            List<ItemStack> previousUpgrades = readLiveTargetUpgrades(target);
            if (upgradeSlot >= previousUpgrades.size()) {
                throw new IllegalArgumentException("Cell Terminal upgrade slot out of live range: " + upgradeSlot);
            }
            if (!previousUpgrades.get(upgradeSlot).isEmpty()) {
                AELog.warn("Cell Terminal rejected quick move upgrade install because live slot is occupied. player=%s slot=%s target=%s stack=%s live=%s",
                    getPlayer().getName(), upgradeSlot, target.describe(), inserted, previousUpgrades.get(upgradeSlot));
                requestSync();
                return false;
            }
            List<ItemStack> updatedUpgrades = copyUpgradeList(previousUpgrades);
            updatedUpgrades.set(upgradeSlot, inserted);
            if (!isValidUpgradeSnapshot(target, updatedUpgrades)) {
                AELog.warn("Cell Terminal rejected quick move upgrade install with invalid target snapshot. player=%s slot=%s target=%s stack=%s",
                    getPlayer().getName(), upgradeSlot, target.describe(), inserted);
                requestSync();
                return false;
            }

            ItemStack removed = sourceSlot.decrStackSize(1);
            if (!sameItemStack(removed, inserted)) {
                restoreSourceSlotAfterFailedUpgradeTransfer(sourceSlot, sourceBefore, target, upgradeSlot);
                AELog.error("Cell Terminal quick move upgrade source deduction mismatch. player=%s slot=%s target=%s expected=%s removed=%s",
                    getPlayer().getName(), sourceSlot.slotNumber, target.describe(), inserted, removed);
                requestSync();
                return false;
            }

            try {
                if (!commitLiveTargetUpgrades(target, previousUpgrades, updatedUpgrades, upgradeSlot, inserted)) {
                    restoreSourceSlotAfterFailedUpgradeTransfer(sourceSlot, sourceBefore, target, upgradeSlot);
                    requestSync();
                    return false;
                }
                activeSession.markCacheStale();
                requestScan();
                requestSync();
                return true;
            } catch (RuntimeException e) {
                restoreSourceSlotAfterFailedUpgradeTransfer(sourceSlot, sourceBefore, target, upgradeSlot);
                throw e;
            } finally {
                restoreSelectedTargetUpgrade(originalSelection);
                updateTargetUpgradeSlots();
            }
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal quick move upgrade install failed");
            requestSync();
            return false;
        }
    }

    private void restoreSourceSlotAfterFailedUpgradeTransfer(Slot sourceSlot,
                                                             ItemStack sourceBefore,
                                                             SelectedTargetUpgrade target,
                                                             int upgradeSlot) {
        ItemStack current = sourceSlot.getStack();
        if (sameItemStack(current, sourceBefore)) {
            return;
        }

        if (current.isEmpty() || ItemStack.areItemsEqual(current, sourceBefore)
            && ItemStack.areItemStackTagsEqual(current, sourceBefore)
            && current.getCount() <= sourceBefore.getCount()) {
            sourceSlot.putStack(sourceBefore.copy());
            return;
        }

        AELog.error("Cell Terminal quick move upgrade source rollback failed. player=%s slot=%s target=%s upgradeSlot=%s expectedSource=%s currentSource=%s",
            getPlayer().getName(), sourceSlot.slotNumber, target.describe(), upgradeSlot, sourceBefore, current);
    }

    private ItemStack insertIntoCellSlot(Slot slot, ItemStack stack) {
        ItemStack existing = slot.getStack();
        int limit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());
        if (existing.isEmpty()) {
            int amount = Math.min(stack.getCount(), limit);
            ItemStack inserted = stack.copy();
            inserted.setCount(amount);
            slot.putStack(inserted);
            stack.shrink(amount);
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        }
        if (!ItemStack.areItemsEqual(existing, stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
            return stack;
        }
        int amount = Math.min(stack.getCount(), limit - existing.getCount());
        if (amount <= 0) {
            return stack;
        }
        existing.grow(amount);
        stack.shrink(amount);
        slot.onSlotChanged();
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private void setTab(String tabName) {
        try {
            CellTerminalServerConfig.ServerTab tab = CellTerminalServerConfig.ServerTab.fromSerializedName(tabName);
            if (!SERVER_CONFIG.isTabEnabled(tab)) {
                rejectConfiguredEntry("tab", tab.name(), "tab_disabled");
                return;
            }
            this.selectedTab = CellTerminalClientState.CellTerminalTab.valueOf(tab.name());
            requestSync();
        } catch (IllegalArgumentException e) {
            AELog.warn(e, "Ignoring invalid Cell Terminal tab action: %s", tabName);
        }
    }

    private void updateServerState() {
        ILinkStatus linkStatus = this.host.getLinkStatus();
        IGridNode gridNode = this.host.getGridNode();
        if (gridNode == null || !linkStatus.connected()) {
            this.session = null;
            this.lastSnapshot = null;
            this.toolPreview = null;
            clearContentPage();
            this.lastScannedCacheRevision = Long.MIN_VALUE;
            this.lastBuiltRevision = Long.MIN_VALUE;
            this.scanRequested = true;
            this.ticksSinceLastScan = SERVER_CONFIG.periodicScanIntervalTicks();
            this.state = CellTerminalClientState.offline(this.selectedTab, linkStatus);
            clearSelectedTargetUpgrades();
            updateTargetUpgradeSlots();
            return;
        }
        IGrid grid = gridNode.grid();
        if (grid == null) {
            this.session = null;
            this.lastSnapshot = null;
            this.toolPreview = null;
            clearContentPage();
            this.lastScannedCacheRevision = Long.MIN_VALUE;
            this.lastBuiltRevision = Long.MIN_VALUE;
            this.scanRequested = true;
            this.ticksSinceLastScan = SERVER_CONFIG.periodicScanIntervalTicks();
            this.state = CellTerminalClientState.offline(this.selectedTab, ILinkStatus.ofDisconnected());
            clearSelectedTargetUpgrades();
            updateTargetUpgradeSlots();
            return;
        }
        if (this.session == null || this.session.getMainGrid() != grid) {
            this.session = new CellTerminalSession(grid,
                CellTerminalSubnetLedger.fromTag(this.host.loadCellTerminalSubnetLedgerTag()));
            this.lastSnapshot = null;
            this.toolPreview = null;
            clearContentPage();
            this.lastScannedCacheRevision = Long.MIN_VALUE;
            this.lastBuiltRevision = Long.MIN_VALUE;
            this.ticksSinceLastScan = SERVER_CONFIG.periodicScanIntervalTicks();
            requestScan();
            requestSync();
        }
        CellTerminalSession activeSession = this.session;
        boolean scanned = false;
        if (shouldScan(activeSession)) {
            this.lastSnapshot = withTempCellTarget(this.scanner.scan(activeSession));
            this.lastScannedCacheRevision = this.lastSnapshot.cacheRevision();
            this.scanRequested = false;
            this.ticksSinceLastScan = 0;
            scanned = true;
        } else {
            this.ticksSinceLastScan++;
        }
        CellTerminalSnapshot snapshot = this.lastSnapshot;
        if (snapshot == null) {
            throw new IllegalStateException("Cell Terminal has no snapshot after scan evaluation");
        }
        updateSelectedTargetFromSnapshot(snapshot);
        if (shouldBuildState(snapshot, scanned)) {
            this.state = CellTerminalClientState.fromSnapshot(snapshot,
                this.selectedTab,
                getEnabledClientTabs(),
                linkStatus,
                activeSession.getSubnetLedger(),
                CellTerminalSubnetNameData.get(getPlayer().world),
                CellTerminalStorageNameData.get(getPlayer().world),
                this.contentPage,
                this.toolPreview,
                this.selectedTargetId,
                this.selectedTargetLocator,
                this.selectedSlotIndex,
                getPlayer().getUniqueID());
            markStateBuilt();
        }
        updateTargetUpgradeSlots();
    }

    private boolean shouldScan(CellTerminalSession activeSession) {
        return this.lastSnapshot == null
            || this.scanRequested
            || activeSession.getCacheRevision() != this.lastScannedCacheRevision
            || this.ticksSinceLastScan >= SERVER_CONFIG.periodicScanIntervalTicks();
    }

    private boolean shouldSendState() {
        return this.syncRequested
            || this.state.cacheRevision() != this.lastSentRevision
            || !Objects.equals(this.state.contextId(), this.lastSentContextId)
            || this.state.tab() != this.lastSentTab;
    }

    private boolean shouldBuildState(CellTerminalSnapshot snapshot, boolean scanned) {
        return scanned
            || this.syncRequested
            || snapshot.cacheRevision() != this.lastBuiltRevision
            || !Objects.equals(snapshot.contextId(), this.lastBuiltContextId)
            || this.selectedTab != this.lastBuiltTab
            || !Objects.equals(this.toolPreview, this.lastBuiltPreview)
            || !Objects.equals(this.contentPage, this.lastBuiltContentPage)
            || !Objects.equals(this.selectedTargetId, this.lastBuiltSelectedTargetId)
            || !Objects.equals(this.selectedTargetLocator, this.lastBuiltSelectedTargetLocator)
            || this.selectedSlotIndex != this.lastBuiltSelectedSlotIndex;
    }

    private void markStateBuilt() {
        this.lastBuiltRevision = this.state.cacheRevision();
        this.lastBuiltContextId = this.state.contextId();
        this.lastBuiltTab = this.state.tab();
        this.lastBuiltPreview = this.toolPreview;
        this.lastBuiltContentPage = this.contentPage;
        this.lastBuiltSelectedTargetId = this.selectedTargetId;
        this.lastBuiltSelectedTargetLocator = this.selectedTargetLocator;
        this.lastBuiltSelectedSlotIndex = this.selectedSlotIndex;
    }

    private void markStateSent() {
        this.syncRequested = false;
        this.lastSentRevision = this.state.cacheRevision();
        this.lastSentContextId = this.state.contextId();
        this.lastSentTab = this.state.tab();
    }

    private void sendState(EntityPlayerMP player) {
        CellTerminalSyncPacket.sendToClient(player, this.windowId, this.state);
    }

    private void requestSync() {
        this.syncRequested = true;
    }

    private void requestScan() {
        this.scanRequested = true;
    }

    private CellTerminalSnapshot withTempCellTarget(CellTerminalSnapshot snapshot) {
        var storages = new ArrayList<CellTerminalStorageTarget>(snapshot.storageTargets().size() + 1);
        storages.add(createTempCellStorageTarget());
        storages.addAll(snapshot.storageTargets());
        return new CellTerminalSnapshot(
            snapshot.contextId(),
            snapshot.cacheRevision(),
            storages,
            snapshot.busTargets(),
            snapshot.subnetTargets());
    }

    private TempCellStorageTarget createTempCellStorageTarget() {
        return new TempCellStorageTarget(
            this.host.getTempCellStorage(),
            tempCellStableTargetId(),
            new CellTerminalTargetLocator(
                TEMP_CELL_KIND,
                TEMP_CELL_LOCATOR_DIMENSION,
                TEMP_CELL_LOCATOR_POS,
                null));
    }

    private String tempCellStableTargetId() {
        return "temp_cells@" + this.windowId;
    }

    private boolean isTempCellTarget(CellTerminalTargetHandle handle) {
        return tempCellStableTargetId().equals(handle.stableTargetId())
            && TEMP_CELL_KIND.equals(handle.locator().kindId());
    }

    private void runClientAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            AELog.warn(e, "Ignoring invalid Cell Terminal client action");
            requestSync();
        }
    }

    private @Nullable CellTerminalSession requireSession() {
        if (this.session == null) {
            requestSync();
        }
        return this.session;
    }

    private @Nullable CellTerminalSnapshot requireSnapshot() {
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return null;
        }
        if (shouldScan(activeSession)) {
            this.lastSnapshot = withTempCellTarget(this.scanner.scan(activeSession));
            this.lastScannedCacheRevision = this.lastSnapshot.cacheRevision();
            this.scanRequested = false;
            this.ticksSinceLastScan = 0;
        }
        return this.lastSnapshot;
    }

    private boolean requireActionEnabled(CellTerminalServerConfig.Action action, String entryName) {
        if (SERVER_CONFIG.isActionEnabled(action)) {
            return true;
        }
        rejectConfiguredEntry("action", entryName, "action_disabled");
        return false;
    }

    private boolean requireNetworkToolEnabled(CellTerminalNetworkToolOperation operation, String entryName) {
        if (SERVER_CONFIG.isNetworkToolEnabled(operation)) {
            return true;
        }
        this.lastPreview = null;
        this.toolPreview = null;
        rejectConfiguredEntry("network_tool", entryName, "network_tool_disabled");
        return false;
    }

    private boolean requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation operation, String entryName) {
        if (SERVER_CONFIG.isWriteOperationEnabled(operation)) {
            return true;
        }
        rejectConfiguredEntry("write", entryName, "write_disabled");
        return false;
    }

    private void rejectConfiguredEntry(String category, String entryName, String reason) {
        AELog.warn("Cell Terminal denied disabled server config entry %s.%s for player %s",
            category,
            entryName,
            getPlayer().getName());
        requestSync();
    }

    private void returnToParent() {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.RETURN_TO_PARENT, "return to parent")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        if (activeSession.returnToParentGrid()) {
            saveSubnetLedger(activeSession);
            requestScan();
        }
        requestSync();
    }

    public void sendTempCellFromClient(CellTerminalClientState.StorageEntry storage,
                                       CellTerminalClientState.CellSlotEntry slot) {
        var tag = new NBTTagCompound();
        tag.setTag("slot", SlotAction.fromStorageSlot(storage, slot).toTag());
        sendClientAction(ACTION_SEND_TEMP_CELL, tag.toString());
    }

    private void loadSubnet(TargetAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.LOAD_SUBNET, "load subnet")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        CellTerminalSnapshot snapshot = requireSnapshot();
        if (activeSession == null || snapshot == null) {
            return;
        }
        CellTerminalSubnetTarget subnet = requireVisibleSubnet(snapshot, action);
        requireWritePermission(subnet.locator(), "load subnet");
        activeSession.loadSubnet(this.worldTargetAccess.resolveSubnet(CellTerminalSubnetHandle.fromTarget(subnet)),
            getPlayer().getUniqueID());
        saveSubnetLedger(activeSession);
        requestScan();
        requestSync();
    }

    private void renameSubnet(RenameSubnetAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.RENAME_SUBNET, "rename subnet")
            || !requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.SUBNET_METADATA, "rename subnet")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        CellTerminalSubnetTarget subnet = requireVisibleSubnet(requireSnapshotForAction(), action.target);
        CellTerminalSubnetHandle handle = CellTerminalSubnetHandle.fromTarget(subnet);
        requireWritePermission(subnet.locator(), "rename subnet");
        CellTerminalSubnetNameData.get(getPlayer().world)
                                  .setDisplayName(handle, action.displayName == null ? "" : action.displayName.trim());
        activeSession.markCacheStale();
        requestScan();
        requestSync();
    }

    private void executeTool(ExecuteToolAction action) {
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        CellTerminalNetworkToolPreview preview = this.lastPreview;
        if (preview != null
            && (!requireNetworkToolEnabled(preview.operation(), "execute " + preview.operation().name())
            || !requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.NETWORK_TOOL_EXECUTE,
            "execute network tool"))) {
            return;
        }
        if (preview == null || !action.matches(preview)) {
            requestSync();
            return;
        }
        try {
            requireToolExecutionPermissions(preview);
            CellTerminalActionToken token = new CellTerminalActionToken(action.token);
            CellTerminalActionResult result = switch (preview.operation()) {
                case UNIQUE_TYPE_REALLOCATION -> this.networkTool.executeUniqueTypeReallocation(
                    activeSession, preview, token);
                case PARTITION_CELLS_BY_CONTENT -> this.networkTool.executePartitionCellsByContent(
                    activeSession, preview, token);
                case PARTITION_STORAGE_BUSES_BY_CONTENT -> this.networkTool.executePartitionStorageBusesByContent(
                    activeSession, preview, token);
            };
            this.lastPreview = null;
            this.toolPreview = CellTerminalClientState.ToolPreview.fromResult(
                CellTerminalClientState.ToolPreview.fromPreview(preview),
                result);
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal tool execute failed");
        }
        requestSync();
    }

    private void favoriteSubnet(FavoriteSubnetAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.FAVORITE_SUBNET, "favorite subnet")
            || !requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.SUBNET_METADATA, "favorite subnet")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        CellTerminalSubnetTarget subnet = requireVisibleSubnet(requireSnapshotForAction(), action.target);
        requireWritePermission(subnet.locator(), "favorite subnet");
        activeSession.getSubnetLedger().setFavorite(getPlayer().getUniqueID(), CellTerminalSubnetHandle.fromTarget(subnet),
            action.favorite);
        saveSubnetLedger(activeSession);
        activeSession.markCacheStale();
        requestScan();
        requestSync();
    }

    private void restoreLastSubnet() {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.RESTORE_LAST_SUBNET, "restore last subnet")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        CellTerminalSnapshot snapshot = requireSnapshot();
        if (activeSession == null || snapshot == null) {
            return;
        }
        CellTerminalSubnetHandle lastLoadedHandle =
            activeSession.getSubnetLedger().getLastLoadedHandle(getPlayer().getUniqueID());
        if (lastLoadedHandle != null) {
            CellTerminalSubnetTarget subnet = requireVisibleSubnet(snapshot, TargetAction.fromSubnetHandle(lastLoadedHandle));
            requireWritePermission(subnet.locator(), "restore subnet");
        }
        CellTerminalSubnetActionResult result = activeSession.restoreLastLoadedSubnet(snapshot,
            getPlayer().getUniqueID(), this.worldTargetAccess);
        if (result.status() == CellTerminalActionStatus.SUCCESS) {
            saveSubnetLedger(activeSession);
            requestScan();
        }
        requestSync();
    }

    private void highlightSubnet(TargetAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.HIGHLIGHT_SUBNET, "highlight subnet")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        CellTerminalSubnetTarget subnet = requireVisibleSubnet(requireSnapshotForAction(), action);
        CellTerminalTargetLocator locator = subnet.locator();
        requireSameDimension(locator);
        requireWritePermission(locator, "highlight subnet");
        if (getPlayer() instanceof EntityPlayerMP player) {
            InitNetwork.sendToClient(player, new CellTerminalSubnetHighlightPacket(locator.dimensionId(), locator.pos(),
                locator.side()));
        }
        activeSession.getSubnetLedger().recordHighlightSuccess(CellTerminalSubnetHandle.fromTarget(subnet));
        saveSubnetLedger(activeSession);
        activeSession.markCacheStale();
        requestScan();
        requestSync();
    }

    private void highlightSubnetConnection(SubnetConnectionAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.HIGHLIGHT_SUBNET, "highlight subnet connection")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        CellTerminalSnapshot snapshot = requireSnapshotForAction();
        CellTerminalSubnetConnection connection = requireVisibleSubnetConnection(snapshot, action.toPartitionAction());
        CellTerminalTargetLocator locator = connection.target().locator();
        requireSameDimension(locator);
        requireWritePermission(locator, "highlight subnet connection");
        if (getPlayer() instanceof EntityPlayerMP player) {
            InitNetwork.sendToClient(player, new CellTerminalSubnetHighlightPacket(locator.dimensionId(), locator.pos(),
                locator.side()));
        }
        activeSession.getSubnetLedger().recordHighlightSuccess(CellTerminalSubnetHandle.fromTarget(
            requireVisibleSubnet(snapshot, action.subnetTarget())));
        saveSubnetLedger(activeSession);
        activeSession.markCacheStale();
        requestScan();
        requestSync();
    }

    private void highlightTarget(TargetAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.HIGHLIGHT_SUBNET, "highlight target")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        CellTerminalSnapshot snapshot = requireSnapshotForAction();
        CellTerminalTargetLocator locator = resolveHighlightLocator(snapshot, action);
        requireSameDimension(locator);
        requireWritePermission(locator, "highlight target");
        if (getPlayer() instanceof EntityPlayerMP player) {
            InitNetwork.sendToClient(player, new CellTerminalSubnetHighlightPacket(locator.dimensionId(), locator.pos(),
                locator.side()));
        }
        requestSync();
    }

    private void previewTool(ToolAction action) {
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalNetworkToolOperation operation = CellTerminalNetworkToolOperation.valueOf(action.operation);
            if (!requireNetworkToolEnabled(operation, "preview " + operation.name())) {
                return;
            }
            switch (operation) {
                case UNIQUE_TYPE_REALLOCATION, PARTITION_CELLS_BY_CONTENT -> {
                    for (var handle : action.toCellSlotHandles()) {
                        requireWritePermission(requireVisibleCellSlot(snapshot, handle).getStorageTarget().locator(),
                            "preview tool cell target");
                    }
                }
                case PARTITION_STORAGE_BUSES_BY_CONTENT -> {
                    for (var handle : action.toTargetHandles()) {
                        requireWritePermission(requireVisibleBus(snapshot, handle).locator(),
                            "preview tool storage bus target");
                    }
                }
            }
            CellTerminalNetworkToolPreview preview = switch (operation) {
                case UNIQUE_TYPE_REALLOCATION -> this.networkTool.previewUniqueTypeReallocation(
                    activeSession, action.toCellSlotHandles());
                case PARTITION_CELLS_BY_CONTENT -> this.networkTool.previewPartitionCellsByContent(
                    activeSession, action.toCellSlotHandles());
                case PARTITION_STORAGE_BUSES_BY_CONTENT -> this.networkTool.previewPartitionStorageBusesByContent(
                    activeSession, action.toTargetHandles());
            };
            this.lastPreview = preview;
            this.toolPreview = CellTerminalClientState.ToolPreview.fromPreview(preview);
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal tool preview failed");
        }
        requestSync();
    }

    private void writePartition(WritePartitionAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.PARTITION, "write partition")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            if (action.isSubnetConnectionAction()) {
                CellTerminalSubnetConnection connection = requireVisibleSubnetConnection(snapshot, action);
                CellTerminalBusTarget snapshotBus = connection.target();
                requireCapability(snapshotBus, CellTerminalCapability.PARTITION_WRITE, action.toSubnetConnectionHandle());
                requireWritePermission(snapshotBus.locator(), "write subnet connection partition");
                List<@Nullable GenericStack> partition = action.resolveBusPartition(snapshotBus);
                requireClientPartitionSize(partition);
                requirePartitionCapacity(partition, snapshotBus.getPartitionSnapshot().slots().size(),
                    action.toSubnetConnectionHandle());
                this.worldTargetAccess.resolveStorageBus(action.toSubnetConnectionHandle()).setPartition(partition);
            } else if (action.slotIndex >= 0) {
                CellTerminalCellSlotTarget snapshotSlot = requireVisibleCellSlot(snapshot, action.toCellSlotHandle());
                requireCapability(snapshotSlot, CellTerminalCapability.PARTITION_WRITE, action.toCellSlotHandle());
                requireWritePermission(snapshotSlot.getStorageTarget().locator(), "write cell partition");
                List<@Nullable GenericStack> partition = action.resolveCellSlotPartition(snapshotSlot);
                requireClientPartitionSize(partition);
                requirePartitionCapacity(partition, snapshotSlot.getPartitionSnapshot().slots().size(), action.toCellSlotHandle());
                this.targetAccess.resolveCellSlot(action.toCellSlotHandle()).setPartition(partition);
            } else {
                CellTerminalBusTarget snapshotBus = requireVisibleBus(snapshot, action.toTargetHandle());
                requireCapability(snapshotBus, CellTerminalCapability.PARTITION_WRITE, action.toTargetHandle());
                requireWritePermission(snapshotBus.locator(), "write storage bus partition");
                List<@Nullable GenericStack> partition = action.resolveBusPartition(snapshotBus);
                requireClientPartitionSize(partition);
                requirePartitionCapacity(partition, snapshotBus.getPartitionSnapshot().slots().size(), action.toTargetHandle());
                this.targetAccess.resolveStorageBus(action.toTargetHandle()).setPartition(partition);
            }
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal partition write failed");
        }
        requestSync();
    }

    private void writePriority(WritePriorityAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.PRIORITY, "write priority")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalStorageTarget snapshotTarget = requireVisibleStorage(snapshot, action.target.toTargetHandle());
            requireCapability(snapshotTarget, CellTerminalCapability.PRIORITY_WRITE, action.target.toTargetHandle());
            requireWritePermission(snapshotTarget.locator(), "write storage priority");
            this.targetAccess.resolveStorage(action.target.toTargetHandle()).setPriority(action.priority);
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal priority write failed");
        }
        requestSync();
    }

    private void renameStorage(RenameTargetAction action) {
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalStorageTarget target = requireVisibleStorage(snapshot, action.target.toTargetHandle());
            requireWritePermission(target.locator(), "rename storage");
            String key = CellTerminalClientState.targetNameKey(target.stableTargetId(), target.locator());
            CellTerminalStorageNameData.get(getPlayer().world)
                                       .setDisplayName(key, action.displayName == null ? "" : action.displayName.trim());
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal storage rename failed");
        }
        requestSync();
    }

    private void renameBus(RenameTargetAction action) {
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalBusTarget target = requireVisibleBus(snapshot, action.target.toTargetHandle());
            requireWritePermission(target.locator(), "rename storage bus");
            String key = CellTerminalClientState.targetNameKey(target.stableTargetId(), target.locator());
            CellTerminalStorageNameData.get(getPlayer().world)
                                       .setDisplayName(key, action.displayName == null ? "" : action.displayName.trim());
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal storage bus rename failed");
        }
        requestSync();
    }

    private void writeBusMode(WriteBusModeAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.BUS_MODE, "write bus mode")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalBusTarget snapshotBus = requireVisibleBus(snapshot, action.target.toTargetHandle());
            requireCapability(snapshotBus, CellTerminalCapability.IO_FILTER_MODE_WRITE, action.target.toTargetHandle());
            requireWritePermission(snapshotBus.locator(), "write storage bus mode");
            this.targetAccess.resolveStorageBus(action.target.toTargetHandle()).setIoFilterMode(action.mode);
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal storage bus mode write failed");
        }
        requestSync();
    }

    private void requestContentPage(ContentPageAction action) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.REQUEST_CONTENT_PAGE, "request content page")) {
            clearContentPage();
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            int firstIndex = Math.max(0, action.firstIndex);
            CellTerminalClientState.ContentPage page;
            if (action.slotIndex >= 0) {
                CellTerminalCellSlotHandle handle =
                    new CellTerminalCellSlotHandle(action.target.stableTargetId, action.target.locator,
                        action.slotIndex);
                requireVisibleCellSlot(snapshot, handle);
                var liveSlot = this.targetAccess.resolveCellSlot(handle);
                requireCapability(liveSlot, CellTerminalCapability.CONTENT_PREVIEW, handle);
                page = CellTerminalClientState.ContentPage.fromSnapshot(
                    action.target.stableTargetId,
                    action.target.locator,
                    action.slotIndex,
                    firstIndex,
                    TARGET_CONTENT_PAGE_SIZE,
                    liveSlot.previewContent());
            } else {
                CellTerminalTargetHandle handle = action.target.toTargetHandle();
                requireVisibleBus(snapshot, handle);
                var liveBus = this.targetAccess.resolveStorageBus(handle);
                requireCapability(liveBus, CellTerminalCapability.CONTENT_PREVIEW, handle);
                page = CellTerminalClientState.ContentPage.fromSnapshot(
                    action.target.stableTargetId,
                    action.target.locator,
                    -1,
                    firstIndex,
                    TARGET_CONTENT_PAGE_SIZE,
                    liveBus.previewContent());
            }
            this.contentPage = page;
            requestSync();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal content page request failed");
            clearContentPage();
            requestSync();
        }
    }

    private void clearContentPage() {
        this.contentPage = null;
    }

    private void saveSubnetLedger(CellTerminalSession activeSession) {
        this.host.saveCellTerminalSubnetLedgerTag(activeSession.getSubnetLedger().writeToTag());
    }

    private void writeCellSlot(WriteCellSlotAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.CELL_SLOT, "write cell slot")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalCellSlotMutation mutation;
            CellTerminalCellSlotTarget snapshotSlot = requireVisibleCellSlot(snapshot, action.slot.toCellSlotHandle());
            requireCapability(snapshotSlot, CellTerminalCapability.CELL_SLOT_WRITE, action.slot.toCellSlotHandle());
            requireWritePermission(snapshotSlot.getStorageTarget().locator(), "write cell slot");
            boolean tempAreaSlot = TEMP_CELL_KIND.equals(snapshotSlot.getStorageTarget().locator().kindId());
            if (action.mode == CellSlotWriteMode.EJECT) {
                mutation = this.targetAccess.resolveCellSlot(action.slot.toCellSlotHandle()).ejectCell();
            } else {
                ItemStack input = takeSingleCarriedStack();
                if (input.isEmpty()) {
                    requestSync();
                    return;
                }
                if (tempAreaSlot && !StorageCells.isCellHandled(input)) {
                    restoreCarriedInput(input);
                    requestSync();
                    return;
                }
                try {
                    mutation = action.mode == CellSlotWriteMode.INSERT
                        ? this.targetAccess.resolveCellSlot(action.slot.toCellSlotHandle()).insertCell(input)
                        : this.targetAccess.resolveCellSlot(action.slot.toCellSlotHandle()).replaceCell(input);
                } catch (RuntimeException e) {
                    restoreCarriedInput(input);
                    throw e;
                }
                if (!mutation.remainderStack().isEmpty()) {
                    restoreCarriedInput(mutation.remainderStack());
                    if (mutation.changedStack().isEmpty()) {
                        requestSync();
                        return;
                    }
                }
            }
            giveMutationChangedStack(mutation);
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal cell slot write failed");
        }
        syncCarriedStack();
        requestSync();
    }

    private void requireWritePermission(CellTerminalTargetLocator locator, String action) {
        if (isClientSide()) {
            return;
        }
        if (TEMP_CELL_KIND.equals(locator.kindId())) {
            return;
        }
        WorldServer level = DimensionManager.getWorld(locator.dimensionId());
        if (level == null) {
            throw new IllegalStateException("Cell Terminal " + action + " failed because target world is unavailable: "
                + locator);
        }
        if (!Platform.hasPermissions(new DimensionalBlockPos(level, locator.pos()), getPlayer())) {
            AELog.warn("Cell Terminal denied %s for player %s at %s", action, getPlayer().getName(), locator);
            throw new IllegalStateException("Cell Terminal player lacks permission for " + action);
        }
    }

    private void requireSameDimension(CellTerminalTargetLocator locator) {
        if (getPlayer().world.provider.getDimension() != locator.dimensionId()) {
            AELog.warn("Cell Terminal denied %s for player %s across dimensions. playerDim=%s target=%s",
                "highlight subnet",
                getPlayer().getName(),
                getPlayer().world.provider.getDimension(),
                locator);
            throw new IllegalStateException("Cell Terminal cannot highlight subnet across dimensions");
        }
    }

    private void requireToolExecutionPermissions(CellTerminalNetworkToolPreview preview) {
        for (var plan : preview.plans()) {
            requireWritePermission(plan.locator(), "execute network tool");
            for (var movement : plan.resourceMovements()) {
                requireWritePermission(movement.targetLocator(), "execute network tool resource movement");
            }
        }
    }

    private CellTerminalSnapshot requireSnapshotForAction() {
        CellTerminalSnapshot snapshot = requireSnapshot();
        if (snapshot == null) {
            throw new IllegalStateException("Cell Terminal has no active snapshot for client action");
        }
        return snapshot;
    }

    private IUpgradeInventory requireLiveUpgradeInventory(SelectedTargetUpgrade target) {
        if (target.cellSlotHandle != null) {
            IUpgradeInventory upgrades = this.targetAccess.resolveCellSlot(target.cellSlotHandle).getUpgradeInventory();
            if (upgrades == null) {
                throw new IllegalStateException("Cell Terminal selected cell slot has no upgrade inventory: "
                    + target.cellSlotHandle);
            }
            return upgrades;
        }
        return this.targetAccess.resolveStorageBus(target.targetHandle).getUpgradeInventory();
    }

    private void sendTempCell(SlotAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.CELL_SLOT, "send temp cell")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalCellSlotHandle tempHandle = action.toCellSlotHandle();
            CellTerminalCellSlotTarget tempSlot = requireVisibleCellSlot(snapshot, tempHandle);
            requireCapability(tempSlot, CellTerminalCapability.CELL_SLOT_WRITE, tempHandle);
            requireWritePermission(tempSlot.getStorageTarget().locator(), "send temp cell");

            ItemStack tempCell = tempSlot.getCellStack().copy();
            if (tempCell.isEmpty()) {
                requestSync();
                return;
            }

            CellTerminalCellSlotTarget destination = findWritableNetworkCellSlot(snapshot, tempHandle);
            if (destination == null) {
                requestSync();
                return;
            }

            CellTerminalCellSlotHandle destinationHandle = new CellTerminalCellSlotHandle(
                destination.getStorageTarget().stableTargetId(),
                destination.getStorageTarget().locator(),
                destination.slotIndex());

            CellTerminalCellSlotMutation ejectMutation = this.targetAccess.resolveCellSlot(tempHandle).ejectCell();
            if (!ejectMutation.remainderStack().isEmpty()) {
                AELog.error("Cell Terminal temp cell send source eject produced unexpected remainder: %s",
                    ejectMutation.remainderStack());
                requestSync();
                return;
            }
            ItemStack ejectedCell = ejectMutation.changedStack();
            if (ejectedCell.isEmpty()) {
                AELog.error("Cell Terminal temp cell send source eject produced no cell. slot=%s", tempHandle);
                requestSync();
                return;
            }

            CellTerminalCellSlotMutation insertMutation;
            try {
                insertMutation = this.targetAccess.resolveCellSlot(destinationHandle).insertCell(ejectedCell);
            } catch (RuntimeException e) {
                rollbackTempCellSend(tempHandle, ejectedCell);
                throw e;
            }
            if (!insertMutation.remainderStack().isEmpty()) {
                rollbackTempCellSend(tempHandle, ejectedCell);
                requestSync();
                return;
            }

            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal temp cell send failed");
        }
        requestSync();
    }

    private void writeBusTextPartition(WriteBusTextPartitionAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.PARTITION, "write bus text partition")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalBusTarget snapshotBus = requireVisibleBus(snapshot, action.target.toTargetHandle());
            requireCapability(snapshotBus, CellTerminalCapability.TEXT_PARTITION_WRITE, action.target.toTargetHandle());
            requireWritePermission(snapshotBus.locator(), "write storage bus text partition");
            validateBusTextPartitionField(snapshotBus, action.fieldId, action.expression);
            this.targetAccess.resolveStorageBus(action.target.toTargetHandle())
                             .setTextPartition(action.fieldId, action.expression);
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal storage bus text partition write failed");
        }
        requestSync();
    }

    private void writeBusPrecisePartitionAmount(WriteBusPrecisePartitionAmountAction action) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.PARTITION,
            "write precise bus partition amount")) {
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            CellTerminalBusTarget snapshotBus = requireVisibleBus(snapshot, action.target.toTargetHandle());
            requireCapability(snapshotBus, CellTerminalCapability.PRECISE_PARTITION_WRITE,
                action.target.toTargetHandle());
            requireWritePermission(snapshotBus.locator(), "write precise storage bus partition amount");
            if (snapshotBus.getPartitionMode() != CellTerminalBusPartitionMode.PRECISE_SLOTS) {
                throw new IllegalStateException("Storage bus is not precise mode: " + action.target.toTargetHandle());
            }
            List<@Nullable GenericStack> partition = new ArrayList<>(snapshotBus.getPartitionSnapshot().slots());
            int slot = action.partitionSlotIndex;
            if (slot < 0 || slot >= partition.size()) {
                throw new IllegalArgumentException("Precise storage bus partition slot out of range: " + slot);
            }
            GenericStack current = partition.get(slot);
            if (current != null && !current.what().equals(action.stack.what())) {
                throw new IllegalStateException("Precise storage bus partition slot changed before amount write");
            }
            partition.set(slot, action.stack.amount() <= 0 ? null : action.stack);
            requireClientPartitionSize(partition);
            requirePartitionCapacity(partition, snapshotBus.getPartitionSnapshot().slots().size(),
                action.target.toTargetHandle());
            this.targetAccess.resolveStorageBus(action.target.toTargetHandle()).setPartition(partition);
            activeSession.markCacheStale();
            requestScan();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal precise storage bus partition amount write failed");
        }
        requestSync();
    }

    private void rollbackTempCellSend(CellTerminalCellSlotHandle tempHandle, ItemStack ejectedCell) {
        try {
            CellTerminalCellSlotMutation rollbackMutation = this.targetAccess.resolveCellSlot(tempHandle)
                                                                             .insertCell(ejectedCell);
            if (!rollbackMutation.remainderStack().isEmpty()) {
                AELog.error("Cell Terminal temp cell send rollback left remainder. slot=%s, cell=%s, remainder=%s",
                    tempHandle, ejectedCell, rollbackMutation.remainderStack());
            }
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal temp cell send rollback failed");
            AELog.error("Cell Terminal temp cell send rollback context. slot=%s, cell=%s", tempHandle, ejectedCell);
        }
    }

    private @Nullable CellTerminalCellSlotTarget findWritableNetworkCellSlot(CellTerminalSnapshot snapshot,
                                                                             CellTerminalCellSlotHandle tempHandle) {
        for (var storage : snapshot.storageTargets()) {
            if (targetMatches(storage.stableTargetId(), storage.locator(), tempHandle.owner())) {
                continue;
            }
            if (!storage.supportsCapability(CellTerminalCapability.CELL_SLOT_WRITE)) {
                continue;
            }
            requireWritePermission(storage.locator(), "send temp cell destination");
            for (var slot : storage.getCellSlots()) {
                if (!slot.supportsCapability(CellTerminalCapability.CELL_SLOT_WRITE) || !slot.getCellStack().isEmpty()) {
                    continue;
                }
                return slot;
            }
        }
        return null;
    }

    private void selectTargetUpgrades(TargetUpgradeSelection selection) {
        if (!requireActionEnabled(CellTerminalServerConfig.Action.SELECT_TARGET_UPGRADES, "select target upgrades")) {
            clearSelectedTargetUpgrades();
            updateTargetUpgradeSlots();
            return;
        }
        CellTerminalSnapshot snapshot = requireSnapshot();
        if (snapshot == null) {
            clearSelectedTargetUpgrades();
            updateTargetUpgradeSlots();
            requestSync();
            return;
        }
        if (selection.target == null) {
            clearSelectedTargetUpgrades();
            updateTargetUpgradeSlots();
            requestSync();
            return;
        }
        if (selection.slotIndex >= 0) {
            CellTerminalCellSlotTarget snapshotSlot = requireVisibleCellSlot(snapshot,
                new CellTerminalCellSlotHandle(selection.target.stableTargetId, selection.target.locator,
                    selection.slotIndex));
            requireCapability(snapshotSlot, CellTerminalCapability.UPGRADE_WRITE, selection);
            requireWritePermission(snapshotSlot.getStorageTarget().locator(), "select cell upgrades");
        } else {
            CellTerminalBusTarget snapshotBus = requireVisibleBus(snapshot, selection.target.toTargetHandle());
            requireCapability(snapshotBus, CellTerminalCapability.UPGRADE_WRITE, selection.target.toTargetHandle());
            requireWritePermission(snapshotBus.locator(), "select bus upgrades");
        }
        this.selectedTargetId = selection.target.stableTargetId;
        this.selectedTargetLocator = selection.target.locator;
        this.selectedSlotIndex = selection.slotIndex;
        updateTargetUpgradeSlots();
        requestSync();
    }

    private void installTargetUpgrade(TargetUpgradeSelection selection) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.TARGET_UPGRADE,
            "install target upgrade")) {
            syncCarriedStack();
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            ItemStack input = takeSingleCarriedStack();
            if (input.isEmpty()) {
                requestSync();
                return;
            }
            if (!Upgrades.isUpgradeCardItem(input)) {
                restoreCarriedInput(input);
                requestSync();
                return;
            }

            SelectedTargetUpgrade originalSelection = this.lastSnapshot == null ? null : findSelectedTargetUpgrade(this.lastSnapshot);
            try {
                CellTerminalSnapshot snapshot = requireSnapshotForAction();
                SelectedTargetUpgrade target = refreshTargetUpgrade(resolveTargetUpgradeSelectionForInstall(snapshot,
                    selection, input));
                int upgradeSlot = findFirstAcceptingUpgradeSlot(target, input);
                if (upgradeSlot < 0) {
                    AELog.warn("Cell Terminal target upgrade install found no accepting slot. player=%s target=%s stack=%s",
                        getPlayer().getName(), target.describe(), input);
                    restoreCarriedInput(input);
                    requestSync();
                    return;
                }
                if (!writeTargetUpgrade(target, upgradeSlot, input, "install target upgrade", true)) {
                    restoreCarriedInput(input);
                    requestSync();
                    return;
                }
                syncCarriedStack();
            } catch (RuntimeException e) {
                restoreCarriedInput(input);
                throw e;
            } finally {
                restoreSelectedTargetUpgrade(originalSelection);
                updateTargetUpgradeSlots();
            }
            activeSession.markCacheStale();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal target upgrade install failed");
            syncCarriedStack();
            requestSync();
        }
    }

    private void installVisibleUpgrade() {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.TARGET_UPGRADE,
            "install visible upgrade")) {
            syncCarriedStack();
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            ItemStack input = takeSingleCarriedStack();
            if (input.isEmpty()) {
                requestSync();
                return;
            }
            if (!Upgrades.isUpgradeCardItem(input)) {
                restoreCarriedInput(input);
                requestSync();
                return;
            }

            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            SelectedTargetUpgrade originalSelection = findSelectedTargetUpgrade(snapshot);
            try {
                SelectedTargetUpgrade target = findFirstVisibleUpgradeTarget(snapshot, input);
                if (target == null) {
                    restoreCarriedInput(input);
                    requestSync();
                    return;
                }
                target = refreshTargetUpgrade(target);
                restoreSelectedTargetUpgrade(target);
                int upgradeSlot = findFirstAcceptingUpgradeSlot(target, input);
                if (upgradeSlot < 0) {
                    AELog.warn("Cell Terminal visible upgrade install found no accepting slot. player=%s target=%s stack=%s",
                        getPlayer().getName(), target.describe(), input);
                    restoreCarriedInput(input);
                    requestSync();
                    return;
                }
                if (!writeTargetUpgrade(target, upgradeSlot, input, "install visible upgrade", true)) {
                    restoreCarriedInput(input);
                    requestSync();
                    return;
                }
                syncCarriedStack();
            } catch (RuntimeException e) {
                restoreCarriedInput(input);
                throw e;
            } finally {
                restoreSelectedTargetUpgrade(originalSelection);
                updateTargetUpgradeSlots();
            }
            activeSession.markCacheStale();
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal visible upgrade install failed");
            syncCarriedStack();
            requestSync();
        }
    }

    private void interactTargetUpgrade(TargetUpgradeInteraction interaction) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.TARGET_UPGRADE,
            "interact target upgrade")) {
            syncCarriedStack();
            return;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            SelectedTargetUpgrade target = resolveTargetUpgradeSelection(snapshot, interaction.selection());
            validateTargetUpgradeAccess(snapshot, target, interaction.upgradeSlot(), "interact target upgrade");
            List<ItemStack> upgrades = readLiveTargetUpgrades(target);
            if (interaction.upgradeSlot() >= upgrades.size()) {
                throw new IllegalArgumentException("Cell Terminal target upgrade slot is out of live range: "
                    + interaction.upgradeSlot());
            }
            ItemStack installed = upgrades.get(interaction.upgradeSlot());
            if (installed.isEmpty()) {
                AELog.warn("Cell Terminal rejected target upgrade interaction on empty slot. player=%s slot=%s target=%s",
                    getPlayer().getName(), interaction.upgradeSlot(), target.describe());
                requestSync();
                return;
            }

            if (interaction.quickMove()) {
                extractTargetUpgradeToInventory(activeSession, target, interaction.upgradeSlot(), upgrades, installed);
            } else {
                extractTargetUpgradeToCarriedOrInventory(activeSession, target, interaction.upgradeSlot(), upgrades,
                    installed);
            }
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal target upgrade interaction failed");
            syncCarriedStack();
            requestSync();
        }
    }

    private void extractTargetUpgradeToInventory(CellTerminalSession activeSession,
                                                 SelectedTargetUpgrade target,
                                                 int upgradeSlot,
                                                 List<ItemStack> upgrades,
                                                 ItemStack installed) {
        ItemStack extracted = singleStack(installed);
        if (!canAddStackToPlayerInventory(extracted)) {
            AELog.warn("Cell Terminal rejected target upgrade extraction because player inventory is full. player=%s slot=%s target=%s stack=%s",
                getPlayer().getName(), upgradeSlot, target.describe(), extracted);
            requestSync();
            return;
        }
        List<ItemStack> updatedUpgrades = copyUpgradeList(upgrades);
        updatedUpgrades.set(upgradeSlot, ItemStack.EMPTY);
        if (!commitLiveTargetUpgrades(target, upgrades, updatedUpgrades, upgradeSlot, ItemStack.EMPTY)) {
            requestSync();
            return;
        }
        if (!addStackToPlayerInventoryStrict(extracted)) {
            rollbackLiveTargetUpgrades(target, upgrades, upgradeSlot);
            AELog.error("Cell Terminal target upgrade extraction rollback after inventory insert failure. player=%s slot=%s target=%s stack=%s",
                getPlayer().getName(), upgradeSlot, target.describe(), extracted);
            requestSync();
            return;
        }
        activeSession.markCacheStale();
        requestScan();
        syncCarriedStack();
        requestSync();
    }

    private void extractTargetUpgradeToCarriedOrInventory(CellTerminalSession activeSession,
                                                          SelectedTargetUpgrade target,
                                                          int upgradeSlot,
                                                          List<ItemStack> upgrades,
                                                          ItemStack installed) {
        ItemStack carried = getCarried();
        ItemStack extracted = singleStack(installed);
        UpgradeExtractDestination destination = resolveUpgradeExtractDestination(carried, extracted);
        if (destination == UpgradeExtractDestination.NONE) {
            AELog.warn("Cell Terminal rejected target upgrade extraction because the player cannot receive the card. player=%s slot=%s target=%s carried=%s extracted=%s",
                getPlayer().getName(), upgradeSlot, target.describe(), carried, extracted);
            syncCarriedStack();
            requestSync();
            return;
        }

        List<ItemStack> updatedUpgrades = copyUpgradeList(upgrades);
        updatedUpgrades.set(upgradeSlot, ItemStack.EMPTY);
        if (!commitLiveTargetUpgrades(target, upgrades, updatedUpgrades, upgradeSlot, ItemStack.EMPTY)) {
            syncCarriedStack();
            requestSync();
            return;
        }

        boolean delivered = switch (destination) {
            case CARRIED_EMPTY -> {
                setCarried(extracted);
                yield true;
            }
            case CARRIED_STACK -> {
                ItemStack updatedCarried = carried.copy();
                updatedCarried.grow(extracted.getCount());
                setCarried(updatedCarried);
                yield true;
            }
            case INVENTORY -> addStackToPlayerInventoryStrict(extracted);
            case NONE ->
                throw new IllegalStateException("Upgrade extraction destination was validated before delivery");
        };
        if (!delivered) {
            rollbackLiveTargetUpgrades(target, upgrades, upgradeSlot);
            setCarried(carried);
            AELog.error("Cell Terminal target upgrade extraction rollback after delivery failure. player=%s slot=%s target=%s extracted=%s carried=%s destination=%s",
                getPlayer().getName(), upgradeSlot, target.describe(), extracted, carried, destination);
            syncCarriedStack();
            requestSync();
            return;
        }

        activeSession.markCacheStale();
        requestScan();
        syncCarriedStack();
        requestSync();
    }

    private UpgradeExtractDestination resolveUpgradeExtractDestination(ItemStack carried, ItemStack extracted) {
        if (carried.isEmpty()) {
            return UpgradeExtractDestination.CARRIED_EMPTY;
        }
        if (ItemStack.areItemsEqual(carried, extracted) && ItemStack.areItemStackTagsEqual(carried, extracted)
            && carried.getCount() + extracted.getCount() <= carried.getMaxStackSize()) {
            return UpgradeExtractDestination.CARRIED_STACK;
        }
        return canAddStackToPlayerInventory(extracted) ? UpgradeExtractDestination.INVENTORY
            : UpgradeExtractDestination.NONE;
    }

    private boolean writeTargetUpgrade(SelectedTargetUpgrade target,
                                       int upgradeSlot,
                                       ItemStack replacement,
                                       String actionName,
                                       boolean requireEmptySlot) {
        if (!requireWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.TARGET_UPGRADE,
            actionName)) {
            syncCarriedStack();
            return false;
        }
        CellTerminalSession activeSession = requireSession();
        if (activeSession == null) {
            return false;
        }
        try {
            CellTerminalSnapshot snapshot = requireSnapshotForAction();
            validateTargetUpgradeAccess(snapshot, target, upgradeSlot, actionName);
            ItemStack safeReplacement = replacement.isEmpty() ? ItemStack.EMPTY : replacement.copy();
            if (!safeReplacement.isEmpty()) {
                safeReplacement.setCount(1);
            }
            List<ItemStack> previousUpgrades = readLiveTargetUpgrades(target);
            if (upgradeSlot < 0 || upgradeSlot >= previousUpgrades.size()) {
                throw new IllegalArgumentException("Cell Terminal upgrade slot out of range: " + upgradeSlot);
            }
            if (requireEmptySlot && !previousUpgrades.get(upgradeSlot).isEmpty()) {
                throw new IllegalStateException("Cell Terminal target upgrade live slot is occupied: " + upgradeSlot);
            }
            List<ItemStack> updatedUpgrades = copyUpgradeList(previousUpgrades);
            updatedUpgrades.set(upgradeSlot, safeReplacement);
            if (!isValidUpgradeSnapshot(target, updatedUpgrades)) {
                throw new IllegalArgumentException("Cell Terminal target upgrade is not valid for selected target: "
                    + safeReplacement);
            }
            if (!commitLiveTargetUpgrades(target, previousUpgrades, updatedUpgrades, upgradeSlot, safeReplacement)) {
                return false;
            }
            activeSession.markCacheStale();
            requestScan();
            return true;
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal upgrade write failed");
            return false;
        } finally {
            requestSync();
        }
    }

    private SelectedTargetUpgrade resolveTargetUpgradeSelection(CellTerminalSnapshot snapshot,
                                                                TargetUpgradeSelection selection) {
        if (selection.target == null) {
            throw new IllegalArgumentException("Cell Terminal target upgrade action is missing target");
        }
        if (selection.slotIndex >= 0) {
            CellTerminalCellSlotHandle handle = new CellTerminalCellSlotHandle(
                selection.target.stableTargetId, selection.target.locator, selection.slotIndex);
            CellTerminalCellSlotTarget slot = requireVisibleCellSlot(snapshot, handle);
            return SelectedTargetUpgrade.cellSlot(handle, slot.getUpgradeSnapshot().slots());
        }
        CellTerminalTargetHandle handle = selection.target.toTargetHandle();
        CellTerminalBusTarget bus = requireVisibleBus(snapshot, handle);
        return SelectedTargetUpgrade.bus(handle, bus.getUpgradeSnapshot().slots());
    }

    private SelectedTargetUpgrade resolveTargetUpgradeSelectionForInstall(CellTerminalSnapshot snapshot,
                                                                          TargetUpgradeSelection selection,
                                                                          ItemStack input) {
        if (selection.target == null) {
            throw new IllegalArgumentException("Cell Terminal target upgrade action is missing target");
        }
        if (selection.slotIndex >= 0) {
            return resolveTargetUpgradeSelection(snapshot, selection);
        }
        CellTerminalTargetHandle handle = selection.target.toTargetHandle();
        for (var storage : snapshot.storageTargets()) {
            if (!targetMatches(storage.stableTargetId(), storage.locator(), handle)) {
                continue;
            }
            SelectedTargetUpgrade target = findFirstAcceptingCellTarget(List.of(storage), input, false);
            if (target != null) {
                return target;
            }
            throw new IllegalStateException("Cell Terminal storage has no cell that accepts upgrade: " + handle);
        }
        return resolveTargetUpgradeSelection(snapshot, selection);
    }

    private SelectedTargetUpgrade refreshTargetUpgrade(SelectedTargetUpgrade target) {
        List<ItemStack> liveUpgrades = readLiveTargetUpgrades(target);
        return target.cellSlotHandle != null
            ? SelectedTargetUpgrade.cellSlot(target.cellSlotHandle, liveUpgrades)
            : SelectedTargetUpgrade.bus(target.targetHandle, liveUpgrades);
    }

    private void validateTargetUpgradeAccess(CellTerminalSnapshot snapshot,
                                             SelectedTargetUpgrade target,
                                             int upgradeSlot,
                                             String actionName) {
        int snapshotUpgradeSlots;
        if (target.cellSlotHandle != null) {
            CellTerminalCellSlotTarget snapshotSlot = requireVisibleCellSlot(snapshot, target.cellSlotHandle);
            requireCapability(snapshotSlot, CellTerminalCapability.UPGRADE_WRITE, target.cellSlotHandle);
            requireWritePermission(snapshotSlot.getStorageTarget().locator(), actionName);
            snapshotUpgradeSlots = snapshotSlot.getUpgradeSnapshot().slots().size();
        } else {
            CellTerminalBusTarget snapshotBus = requireVisibleBus(snapshot, target.targetHandle);
            requireCapability(snapshotBus, CellTerminalCapability.UPGRADE_WRITE, target.targetHandle);
            requireWritePermission(snapshotBus.locator(), actionName);
            snapshotUpgradeSlots = snapshotBus.getUpgradeSnapshot().slots().size();
        }
        if (upgradeSlot < 0 || upgradeSlot >= snapshotUpgradeSlots) {
            throw new IllegalArgumentException("Cell Terminal upgrade slot is not visible in current snapshot: "
                + upgradeSlot);
        }
    }

    private List<ItemStack> readLiveTargetUpgrades(SelectedTargetUpgrade target) {
        List<ItemStack> upgrades = target.cellSlotHandle != null
            ? new ArrayList<>(this.targetAccess.resolveCellSlot(target.cellSlotHandle).getUpgradeSnapshot().slots())
            : new ArrayList<>(this.targetAccess.resolveStorageBus(target.targetHandle).getUpgradeSnapshot().slots());
        List<ItemStack> result = new ArrayList<>(upgrades.size());
        for (ItemStack stack : upgrades) {
            result.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return result;
    }

    private void setLiveTargetUpgrades(SelectedTargetUpgrade target, List<ItemStack> upgrades) {
        if (target.cellSlotHandle != null) {
            this.targetAccess.resolveCellSlot(target.cellSlotHandle).setUpgrades(upgrades);
        } else {
            this.targetAccess.resolveStorageBus(target.targetHandle).setUpgrades(upgrades);
        }
    }

    private boolean commitLiveTargetUpgrades(SelectedTargetUpgrade target,
                                             List<ItemStack> previousUpgrades,
                                             List<ItemStack> updatedUpgrades,
                                             int upgradeSlot,
                                             ItemStack expectedStack) {
        try {
            setLiveTargetUpgrades(target, updatedUpgrades);
            if (liveUpgradeListMatches(target, updatedUpgrades)) {
                return true;
            }
            AELog.error("Cell Terminal target upgrade write verification failed. player=%s slot=%s target=%s expectedStack=%s expectedList=%s",
                getPlayer().getName(), upgradeSlot, target.describe(), expectedStack, updatedUpgrades);
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal target upgrade live write threw");
            AELog.error("Cell Terminal target upgrade live write context. player=%s slot=%s target=%s expectedStack=%s expectedList=%s",
                getPlayer().getName(), upgradeSlot, target.describe(), expectedStack, updatedUpgrades);
            if (liveUpgradeListMatches(target, updatedUpgrades)) {
                AELog.error("Cell Terminal target upgrade live write committed despite exception. player=%s slot=%s target=%s",
                    getPlayer().getName(), upgradeSlot, target.describe());
                return true;
            }
        }
        rollbackLiveTargetUpgrades(target, previousUpgrades, upgradeSlot);
        return false;
    }

    private boolean liveUpgradeListMatches(SelectedTargetUpgrade target, List<ItemStack> expectedUpgrades) {
        try {
            List<ItemStack> live = readLiveTargetUpgrades(target);
            if (sameUpgradeList(live, expectedUpgrades)) {
                return true;
            }
            AELog.error("Cell Terminal target upgrade live verification mismatch. player=%s target=%s expected=%s live=%s",
                getPlayer().getName(), target.describe(), expectedUpgrades, live);
            return false;
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal target upgrade live verification failed");
            AELog.error("Cell Terminal target upgrade live verification context. player=%s target=%s expected=%s",
                getPlayer().getName(), target.describe(), expectedUpgrades);
            return false;
        }
    }

    private void rollbackLiveTargetUpgrades(SelectedTargetUpgrade target,
                                            List<ItemStack> previousUpgrades,
                                            int upgradeSlot) {
        try {
            setLiveTargetUpgrades(target, previousUpgrades);
            if (!liveUpgradeListMatches(target, previousUpgrades)) {
                AELog.error("Cell Terminal target upgrade rollback verification failed. player=%s slot=%s target=%s expected=%s",
                    getPlayer().getName(), upgradeSlot, target.describe(), previousUpgrades);
            }
        } catch (RuntimeException e) {
            AELog.error(e, "Cell Terminal target upgrade rollback failed");
            AELog.error("Cell Terminal target upgrade rollback context. player=%s slot=%s target=%s",
                getPlayer().getName(), upgradeSlot, target.describe());
            liveUpgradeListMatches(target, previousUpgrades);
        }
    }

    private int findFirstAcceptingUpgradeSlot(SelectedTargetUpgrade target, ItemStack input) {
        int limit = Math.min(MAX_TARGET_UPGRADE_SLOTS, target.getUpgradeCount());
        for (int slotIndex = 0; slotIndex < limit; slotIndex++) {
            ItemStack existing = target.upgrades.get(slotIndex);
            if (!existing.isEmpty()) {
                continue;
            }
            List<ItemStack> updatedUpgrades = copyUpgradeList(target.upgrades);
            updatedUpgrades.set(slotIndex, singleStack(input));
            if (isValidUpgradeSnapshot(target, updatedUpgrades)) {
                return slotIndex;
            }
        }
        return -1;
    }

    private boolean isValidUpgradeSnapshot(SelectedTargetUpgrade target, List<ItemStack> upgrades) {
        IUpgradeInventory liveUpgrades = requireLiveUpgradeInventory(target);
        if (upgrades.size() > liveUpgrades.size()) {
            return false;
        }

        Map<Item, Integer> installed = new IdentityHashMap<>();
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack stack = upgrades.get(slot);
            if (stack == null) {
                return false;
            }
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() != 1 || !Upgrades.isUpgradeCardItem(stack)) {
                return false;
            }

            Item item = stack.getItem();
            int maxInstalled = liveUpgrades.getMaxInstalled(item);
            if (maxInstalled <= 0) {
                return false;
            }
            int count = installed.merge(item, 1, Integer::sum);
            if (count > maxInstalled) {
                return false;
            }
        }
        return true;
    }

    private @Nullable SelectedTargetUpgrade findFirstVisibleUpgradeTarget(CellTerminalSnapshot snapshot, ItemStack input) {
        if (this.selectedTab == CellTerminalClientState.CellTerminalTab.TEMP_CELLS) {
            SelectedTargetUpgrade tempTarget = findFirstAcceptingCellTarget(snapshot.storageTargets(), input, true);
            if (tempTarget != null) {
                return tempTarget;
            }
        }
        if (this.selectedTab == CellTerminalClientState.CellTerminalTab.BUS_CONTENT
            || this.selectedTab == CellTerminalClientState.CellTerminalTab.BUS_PARTITION) {
            SelectedTargetUpgrade busTarget = findFirstAcceptingBusTarget(snapshot.busTargets(), input);
            if (busTarget != null) {
                return busTarget;
            }
        }
        SelectedTargetUpgrade cellTarget = findFirstAcceptingCellTarget(snapshot.storageTargets(), input, false);
        if (cellTarget != null) {
            return cellTarget;
        }
        if (this.selectedTab != CellTerminalClientState.CellTerminalTab.BUS_CONTENT
            && this.selectedTab != CellTerminalClientState.CellTerminalTab.BUS_PARTITION) {
            return findFirstAcceptingBusTarget(snapshot.busTargets(), input);
        }
        return null;
    }

    private @Nullable SelectedTargetUpgrade findFirstAcceptingCellTarget(List<CellTerminalStorageTarget> storages,
                                                                         ItemStack input,
                                                                         boolean tempOnly) {
        for (var storage : storages) {
            boolean tempTarget = TEMP_CELL_KIND.equals(storage.locator().kindId());
            if (tempOnly != tempTarget) {
                continue;
            }
            for (var slot : storage.getCellSlots()) {
                if (!slot.supportsCapability(CellTerminalCapability.UPGRADE_WRITE)) {
                    continue;
                }
                CellTerminalCellSlotHandle handle = new CellTerminalCellSlotHandle(storage.stableTargetId(),
                    storage.locator(), slot.slotIndex());
                SelectedTargetUpgrade target = refreshTargetUpgrade(SelectedTargetUpgrade.cellSlot(handle,
                    slot.getUpgradeSnapshot().slots()));
                if (findFirstAcceptingUpgradeSlot(target, input) >= 0) {
                    return target;
                }
            }
        }
        return null;
    }

    private @Nullable SelectedTargetUpgrade findFirstAcceptingBusTarget(List<CellTerminalBusTarget> buses, ItemStack input) {
        for (var bus : buses) {
            if (!bus.supportsCapability(CellTerminalCapability.UPGRADE_WRITE)) {
                continue;
            }
            SelectedTargetUpgrade target = refreshTargetUpgrade(SelectedTargetUpgrade.bus(
                new CellTerminalTargetHandle(bus.stableTargetId(), bus.locator()),
                bus.getUpgradeSnapshot().slots()));
            if (findFirstAcceptingUpgradeSlot(target, input) >= 0) {
                return target;
            }
        }
        return null;
    }

    private void clearSelectedTargetUpgrades() {
        this.selectedTargetId = null;
        this.selectedTargetLocator = null;
        this.selectedSlotIndex = -1;
    }

    private void restoreSelectedTargetUpgrade(@Nullable SelectedTargetUpgrade target) {
        if (target == null) {
            clearSelectedTargetUpgrades();
            return;
        }
        this.selectedTargetId = target.targetHandle.stableTargetId();
        this.selectedTargetLocator = target.targetHandle.locator();
        this.selectedSlotIndex = target.cellSlotHandle == null ? -1 : target.cellSlotHandle.slotIndex();
    }

    private void updateSelectedTargetFromSnapshot(CellTerminalSnapshot snapshot) {
        if (this.selectedTargetId == null) {
            return;
        }
        SelectedTargetUpgrade target = findSelectedTargetUpgrade(snapshot);
        if (target == null || target.getUpgradeCount() <= 0) {
            clearSelectedTargetUpgrades();
        }
    }

    private void updateTargetUpgradeSlots() {
        int visibleSlots = SERVER_CONFIG.isWriteOperationEnabled(CellTerminalServerConfig.WriteOperation.TARGET_UPGRADE)
            ? getSelectedTargetUpgradeCount()
            : 0;
        for (int index = 0; index < this.targetUpgradeSlots.size(); index++) {
            this.targetUpgradeSlots.get(index).setSlotEnabled(index < visibleSlots);
        }
    }

    private int getSelectedTargetUpgradeCount() {
        if (isClientSide()) {
            return getClientSelectedTargetUpgradeCount();
        }
        CellTerminalSnapshot snapshot = this.lastSnapshot;
        if (snapshot == null || this.selectedTargetId == null) {
            return 0;
        }
        SelectedTargetUpgrade target = findSelectedTargetUpgrade(snapshot);
        return target == null ? 0 : Math.min(MAX_TARGET_UPGRADE_SLOTS, target.getUpgradeCount());
    }

    private int getClientSelectedTargetUpgradeCount() {
        if (this.selectedTargetId == null || this.selectedTargetLocator == null) {
            return 0;
        }
        CellTerminalTargetHandle selectedHandle =
            new CellTerminalTargetHandle(this.selectedTargetId, this.selectedTargetLocator);
        if (this.selectedSlotIndex >= 0) {
            for (CellTerminalClientState.StorageEntry storage : this.state.storages()) {
                if (!targetMatches(storage.stableTargetId(), storage.locator(), selectedHandle)) {
                    continue;
                }
                for (CellTerminalClientState.CellSlotEntry slot : storage.cellSlots()) {
                    if (slot.slotIndex() == this.selectedSlotIndex) {
                        if (!slot.upgradesLoaded()) {
                            return 0;
                        }
                        return Math.min(MAX_TARGET_UPGRADE_SLOTS, slot.upgrades().size());
                    }
                }
            }
            return 0;
        }
        for (CellTerminalClientState.BusEntry bus : this.state.buses()) {
            if (targetMatches(bus.stableTargetId(), bus.locator(), selectedHandle)) {
                if (!bus.upgradesLoaded()) {
                    return 0;
                }
                return Math.min(MAX_TARGET_UPGRADE_SLOTS, bus.upgrades().size());
            }
        }
        return 0;
    }

    private void syncClientTargetUpgradeMirror() {
        Arrays.fill(this.clientTargetUpgradeMirror, ItemStack.EMPTY);
        if (!isClientSide() || this.selectedTargetId == null || this.selectedTargetLocator == null) {
            return;
        }

        CellTerminalTargetHandle selectedHandle =
            new CellTerminalTargetHandle(this.selectedTargetId, this.selectedTargetLocator);
        List<ItemStack> upgrades = List.of();
        if (this.selectedSlotIndex >= 0) {
            for (CellTerminalClientState.StorageEntry storage : this.state.storages()) {
                if (!targetMatches(storage.stableTargetId(), storage.locator(), selectedHandle)) {
                    continue;
                }
                for (CellTerminalClientState.CellSlotEntry slot : storage.cellSlots()) {
                    if (slot.slotIndex() == this.selectedSlotIndex) {
                        upgrades = slot.upgradesLoaded() ? slot.upgrades() : List.of();
                        break;
                    }
                }
            }
        } else {
            for (CellTerminalClientState.BusEntry bus : this.state.buses()) {
                if (targetMatches(bus.stableTargetId(), bus.locator(), selectedHandle)) {
                    upgrades = bus.upgradesLoaded() ? bus.upgrades() : List.of();
                    break;
                }
            }
        }

        int copyCount = Math.min(this.clientTargetUpgradeMirror.length, upgrades.size());
        for (int index = 0; index < copyCount; index++) {
            ItemStack stack = upgrades.get(index);
            this.clientTargetUpgradeMirror[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }

    private SelectedTargetUpgrade requireSelectedTargetUpgrade(CellTerminalSnapshot snapshot) {
        SelectedTargetUpgrade target = findSelectedTargetUpgrade(snapshot);
        if (target == null) {
            throw new IllegalStateException("Cell Terminal target upgrade selection is not visible");
        }
        return target;
    }

    private @Nullable SelectedTargetUpgrade findSelectedTargetUpgrade(CellTerminalSnapshot snapshot) {
        if (this.selectedTargetId == null || this.selectedTargetLocator == null) {
            return null;
        }
        CellTerminalTargetHandle selectedHandle =
            new CellTerminalTargetHandle(this.selectedTargetId, this.selectedTargetLocator);
        if (this.selectedSlotIndex >= 0) {
            for (var storage : snapshot.storageTargets()) {
                if (!targetMatches(storage.stableTargetId(), storage.locator(), selectedHandle)) {
                    continue;
                }
                for (var slot : storage.getCellSlots()) {
                    if (slot.slotIndex() == this.selectedSlotIndex) {
                        return SelectedTargetUpgrade.cellSlot(new CellTerminalCellSlotHandle(
                                storage.stableTargetId(), storage.locator(), slot.slotIndex()),
                            slot.getUpgradeSnapshot().slots());
                    }
                }
            }
            return null;
        }
        for (var bus : snapshot.busTargets()) {
            if (targetMatches(bus.stableTargetId(), bus.locator(), selectedHandle)) {
                return SelectedTargetUpgrade.bus(new CellTerminalTargetHandle(bus.stableTargetId(), bus.locator()),
                    bus.getUpgradeSnapshot().slots());
            }
        }
        return null;
    }

    private ItemStack takeSingleCarriedStack() {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack single = carried.copy();
        single.setCount(1);
        carried.shrink(1);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        return single;
    }

    private void restoreCarriedInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            setCarried(stack.copy());
            return;
        }
        if (ItemStack.areItemsEqual(carried, stack) && ItemStack.areItemStackTagsEqual(carried, stack)
            && carried.getCount() + stack.getCount() <= carried.getMaxStackSize()) {
            carried.grow(stack.getCount());
            setCarried(carried);
            return;
        }
        giveStackToPlayer(stack);
    }

    private void giveMutationChangedStack(CellTerminalCellSlotMutation mutation) {
        giveStackToPlayer(mutation.changedStack());
    }

    private void giveStackToPlayer(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack output = stack.copy();
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            setCarried(output);
            return;
        }
        if (ItemStack.areItemsEqual(carried, output) && ItemStack.areItemStackTagsEqual(carried, output)
            && carried.getCount() + output.getCount() <= carried.getMaxStackSize()) {
            carried.grow(output.getCount());
            setCarried(carried);
            return;
        }
        if (!getPlayerInventory().addItemStackToInventory(output) && !output.isEmpty()) {
            getPlayer().dropItem(output, false);
        }
    }

    private boolean canAddStackToPlayerInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack remaining = stack.copy();
        for (ItemStack existing : getPlayerInventory().mainInventory) {
            if (remaining.isEmpty()) {
                return true;
            }
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.areItemsEqual(existing, remaining) && ItemStack.areItemStackTagsEqual(existing, remaining)) {
                int limit = Math.min(existing.getMaxStackSize(), getPlayerInventory().getInventoryStackLimit());
                int free = limit - existing.getCount();
                if (free > 0) {
                    remaining.shrink(Math.min(free, remaining.getCount()));
                }
            }
        }
        return remaining.isEmpty();
    }

    private boolean addStackToPlayerInventoryStrict(ItemStack stack) {
        if (!canAddStackToPlayerInventory(stack)) {
            return false;
        }
        ItemStack remaining = stack.copy();
        for (ItemStack existing : getPlayerInventory().mainInventory) {
            if (remaining.isEmpty()) {
                getPlayerInventory().markDirty();
                return true;
            }
            if (existing.isEmpty()) {
                continue;
            }
            if (ItemStack.areItemsEqual(existing, remaining) && ItemStack.areItemStackTagsEqual(existing, remaining)) {
                int limit = Math.min(existing.getMaxStackSize(), getPlayerInventory().getInventoryStackLimit());
                int moved = Math.min(limit - existing.getCount(), remaining.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < getPlayerInventory().mainInventory.size(); slot++) {
            if (remaining.isEmpty()) {
                getPlayerInventory().markDirty();
                return true;
            }
            if (getPlayerInventory().mainInventory.get(slot).isEmpty()) {
                getPlayerInventory().mainInventory.set(slot, remaining.copy());
                remaining = ItemStack.EMPTY;
            }
        }
        getPlayerInventory().markDirty();
        return remaining.isEmpty();
    }

    private void syncCarriedStack() {
        if (getPlayer() instanceof EntityPlayerMP player) {
            syncInventoryActionState(player);
        }
    }

    private enum PartitionWriteMode {
        SET,
        ADD,
        ADD_AT,
        REMOVE,
        REMOVE_AT,
        TOGGLE,
        CLEAR,
        SET_FROM_CONTENT
    }

    private enum UpgradeExtractDestination {
        CARRIED_EMPTY,
        CARRIED_STACK,
        INVENTORY,
        NONE
    }

    private enum CellSlotWriteMode {
        INSERT,
        REPLACE,
        EJECT
    }

    private interface TagWriter<T> {
        NBTTagCompound write(T value);
    }

    private record SelectedTargetUpgrade(CellTerminalTargetHandle targetHandle,
                                         @Nullable CellTerminalCellSlotHandle cellSlotHandle,
                                         List<ItemStack> upgrades) {
        private SelectedTargetUpgrade {
            upgrades = upgrades.stream().map(ItemStack::copy).toList();
        }

        static SelectedTargetUpgrade bus(CellTerminalTargetHandle handle, List<ItemStack> upgrades) {
            return new SelectedTargetUpgrade(handle, null, upgrades);
        }

        static SelectedTargetUpgrade cellSlot(CellTerminalCellSlotHandle handle, List<ItemStack> upgrades) {
            return new SelectedTargetUpgrade(handle.owner(), handle, upgrades);
        }

        int getUpgradeCount() {
            return this.upgrades.size();
        }

        String describe() {
            return this.cellSlotHandle == null ? this.targetHandle.toString() : this.cellSlotHandle.toString();
        }
    }

    private record TargetAction(String stableTargetId,
                                CellTerminalTargetLocator locator,
                                @Nullable String subnetId) {
        private static final String TAG_STABLE_ID = "stableTargetId";
        private static final String TAG_LOCATOR = "locator";
        private static final String TAG_SUBNET_ID = "subnetId";

        private static TargetAction fromSubnet(CellTerminalClientState.SubnetEntry subnet) {
            return new TargetAction(subnet.stableTargetId(), subnet.locator(), subnet.subnetId());
        }

        private static TargetAction fromBus(CellTerminalClientState.BusEntry bus) {
            return new TargetAction(bus.stableTargetId(), bus.locator(), null);
        }

        private static TargetAction fromSubnetHandle(CellTerminalSubnetHandle handle) {
            return new TargetAction(handle.stableTargetId(), handle.locator(), handle.subnetId());
        }

        private static TargetAction fromStorage(CellTerminalClientState.StorageEntry storage) {
            return new TargetAction(storage.stableTargetId(), storage.locator(), null);
        }

        private static TargetAction fromPayload(String payload) {
            return fromTag(readPayload(payload));
        }

        private static TargetAction fromTag(NBTTagCompound tag) {
            String stableTargetId = tag.getString(TAG_STABLE_ID);
            if (stableTargetId.isEmpty()) {
                throw new IllegalArgumentException("Cell Terminal target action is missing stableTargetId");
            }
            String subnetId = tag.hasKey(TAG_SUBNET_ID, Constants.NBT.TAG_STRING) ? tag.getString(TAG_SUBNET_ID) : null;
            return new TargetAction(stableTargetId,
                CellTerminalClientState.readLocator(tag.getCompoundTag(TAG_LOCATOR)),
                subnetId);
        }

        private String toPayload() {
            return toTag().toString();
        }

        private NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_STABLE_ID, this.stableTargetId);
            tag.setTag(TAG_LOCATOR, CellTerminalClientState.writeLocator(this.locator));
            if (this.subnetId != null) {
                tag.setString(TAG_SUBNET_ID, this.subnetId);
            }
            return tag;
        }

        private boolean matches(String stableTargetId, CellTerminalTargetLocator locator) {
            return this.stableTargetId.equals(stableTargetId) && this.locator.equals(locator);
        }

        private CellTerminalTargetHandle toTargetHandle() {
            return new CellTerminalTargetHandle(this.stableTargetId, this.locator);
        }
    }

    private record SubnetConnectionAction(TargetAction subnetTarget,
                                          String stableTargetId,
                                          CellTerminalTargetLocator locator,
                                          int connectionIndex) {
        private static final String TAG_SUBNET_TARGET = "subnetTarget";
        private static final String TAG_CONNECTION_TARGET = "connectionTarget";
        private static final String TAG_CONNECTION_INDEX = "connectionIndex";

        private static SubnetConnectionAction fromConnection(CellTerminalClientState.SubnetEntry subnet,
                                                             CellTerminalClientState.ConnectionEntry connection,
                                                             int connectionIndex) {
            return new SubnetConnectionAction(TargetAction.fromSubnet(subnet), connection.stableTargetId(),
                connection.locator(), connectionIndex);
        }

        private static SubnetConnectionAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            TargetAction connectionTarget = TargetAction.fromTag(tag.getCompoundTag(TAG_CONNECTION_TARGET));
            return new SubnetConnectionAction(
                TargetAction.fromTag(tag.getCompoundTag(TAG_SUBNET_TARGET)),
                connectionTarget.stableTargetId,
                connectionTarget.locator,
                tag.getInteger(TAG_CONNECTION_INDEX));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_SUBNET_TARGET, this.subnetTarget.toTag());
            tag.setTag(TAG_CONNECTION_TARGET, new TargetAction(this.stableTargetId, this.locator, null).toTag());
            tag.setInteger(TAG_CONNECTION_INDEX, this.connectionIndex);
            return tag.toString();
        }

        private WritePartitionAction toPartitionAction() {
            return new WritePartitionAction(this.stableTargetId, this.locator, -1, this.subnetTarget,
                this.connectionIndex, PartitionWriteMode.SET, List.of(), null, -1);
        }
    }

    private record SlotAction(String stableTargetId,
                              CellTerminalTargetLocator locator,
                              int slotIndex) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_SLOT_INDEX = "slotIndex";

        private static SlotAction fromStorageSlot(CellTerminalClientState.StorageEntry storage,
                                                  CellTerminalClientState.CellSlotEntry slot) {
            return new SlotAction(storage.stableTargetId(), storage.locator(), slot.slotIndex());
        }

        private static SlotAction fromTag(NBTTagCompound tag) {
            TargetAction target = TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET));
            return new SlotAction(target.stableTargetId(), target.locator(), tag.getInteger(TAG_SLOT_INDEX));
        }

        private NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, new TargetAction(this.stableTargetId, this.locator, null).toTag());
            tag.setInteger(TAG_SLOT_INDEX, this.slotIndex);
            return tag;
        }

        private CellTerminalCellSlotHandle toCellSlotHandle() {
            return new CellTerminalCellSlotHandle(this.stableTargetId, this.locator, this.slotIndex);
        }
    }

    private record RenameSubnetAction(TargetAction target, @Nullable String displayName) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_DISPLAY_NAME = "displayName";

        private static RenameSubnetAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new RenameSubnetAction(TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.hasKey(TAG_DISPLAY_NAME, Constants.NBT.TAG_STRING) ? tag.getString(TAG_DISPLAY_NAME) : "");
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setString(TAG_DISPLAY_NAME, this.displayName == null ? "" : this.displayName);
            return tag.toString();
        }
    }

    private record RenameTargetAction(TargetAction target, @Nullable String displayName) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_DISPLAY_NAME = "displayName";

        private static RenameTargetAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new RenameTargetAction(TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.hasKey(TAG_DISPLAY_NAME, Constants.NBT.TAG_STRING) ? tag.getString(TAG_DISPLAY_NAME) : "");
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setString(TAG_DISPLAY_NAME, this.displayName == null ? "" : this.displayName);
            return tag.toString();
        }
    }

    private record FavoriteSubnetAction(TargetAction target, boolean favorite) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_FAVORITE = "favorite";

        private static FavoriteSubnetAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new FavoriteSubnetAction(TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.getBoolean(TAG_FAVORITE));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setBoolean(TAG_FAVORITE, this.favorite);
            return tag.toString();
        }
    }

    public record ToolCellSlotSelection(CellTerminalClientState.StorageEntry storage,
                                        CellTerminalClientState.CellSlotEntry slot) {
        public ToolCellSlotSelection {
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(slot, "slot");
        }
    }

    private record ToolAction(String operation,
                              List<SlotAction> cellSlots,
                              List<TargetAction> targets) {
        private static final String TAG_OPERATION = "operation";
        private static final String TAG_CELL_SLOTS = "cellSlots";
        private static final String TAG_TARGETS = "targets";

        private static ToolAction fromSelection(CellTerminalNetworkToolOperation operation,
                                                List<ToolCellSlotSelection> cellSlotSelections,
                                                List<CellTerminalClientState.BusEntry> buses) {
            var slots = new ArrayList<SlotAction>(cellSlotSelections.size());
            for (var selection : cellSlotSelections) {
                slots.add(SlotAction.fromStorageSlot(selection.storage(), selection.slot()));
            }
            return new ToolAction(operation.name(), slots,
                buses.stream().map(TargetAction::fromBus).toList());
        }

        private static ToolAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            var slotsTag = tag.getTagList(TAG_CELL_SLOTS, Constants.NBT.TAG_COMPOUND);
            var slots = new ArrayList<SlotAction>(slotsTag.tagCount());
            for (int index = 0; index < slotsTag.tagCount(); index++) {
                slots.add(SlotAction.fromTag(slotsTag.getCompoundTagAt(index)));
            }
            var targetsTag = tag.getTagList(TAG_TARGETS, Constants.NBT.TAG_COMPOUND);
            var targets = new ArrayList<TargetAction>(targetsTag.tagCount());
            for (int index = 0; index < targetsTag.tagCount(); index++) {
                targets.add(TargetAction.fromTag(targetsTag.getCompoundTagAt(index)));
            }
            return new ToolAction(tag.getString(TAG_OPERATION), slots, targets);
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_OPERATION, this.operation);
            tag.setTag(TAG_CELL_SLOTS, writeList(this.cellSlots, SlotAction::toTag));
            tag.setTag(TAG_TARGETS, writeList(this.targets, TargetAction::toTag));
            return tag.toString();
        }

        private List<CellTerminalCellSlotHandle> toCellSlotHandles() {
            return this.cellSlots.stream().map(SlotAction::toCellSlotHandle).toList();
        }

        private List<CellTerminalTargetHandle> toTargetHandles() {
            return this.targets.stream().map(TargetAction::toTargetHandle).toList();
        }
    }

    private record ExecuteToolAction(String operation,
                                     String contextId,
                                     String token,
                                     String planSignature) {
        private static final String TAG_OPERATION = "operation";
        private static final String TAG_CONTEXT = "contextId";
        private static final String TAG_TOKEN = "token";
        private static final String TAG_SIGNATURE = "planSignature";

        private static ExecuteToolAction fromPreview(CellTerminalClientState.ToolPreview preview) {
            return new ExecuteToolAction(preview.operation().name(), preview.contextId(),
                preview.token(), preview.planSignature());
        }

        private static ExecuteToolAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new ExecuteToolAction(
                tag.getString(TAG_OPERATION),
                tag.getString(TAG_CONTEXT),
                tag.getString(TAG_TOKEN),
                tag.getString(TAG_SIGNATURE));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setString(TAG_OPERATION, this.operation);
            tag.setString(TAG_CONTEXT, this.contextId);
            tag.setString(TAG_TOKEN, this.token);
            tag.setString(TAG_SIGNATURE, this.planSignature);
            return tag.toString();
        }

        private boolean matches(CellTerminalNetworkToolPreview preview) {
            return preview.operation().name().equals(this.operation)
                && preview.contextId().equals(this.contextId)
                && preview.token().value().equals(this.token)
                && preview.planSignature().equals(this.planSignature);
        }
    }

    private record WriteBusTextPartitionAction(TargetAction target, String fieldId, String expression) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_FIELD = "field";
        private static final String TAG_EXPRESSION = "expression";

        private static WriteBusTextPartitionAction fromBus(CellTerminalClientState.BusEntry bus, String fieldId,
                                                           String expression) {
            return new WriteBusTextPartitionAction(TargetAction.fromBus(bus), fieldId, expression);
        }

        private static WriteBusTextPartitionAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new WriteBusTextPartitionAction(
                TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.getString(TAG_FIELD),
                tag.hasKey(TAG_EXPRESSION, Constants.NBT.TAG_STRING) ? tag.getString(TAG_EXPRESSION) : "");
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setString(TAG_FIELD, Objects.requireNonNull(this.fieldId, "fieldId"));
            tag.setString(TAG_EXPRESSION, this.expression == null ? "" : this.expression);
            return tag.toString();
        }
    }

    private record WriteBusPrecisePartitionAmountAction(TargetAction target,
                                                        int partitionSlotIndex,
                                                        GenericStack stack) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_PARTITION_SLOT = "partitionSlot";
        private static final String TAG_STACK = "stack";

        private static WriteBusPrecisePartitionAmountAction fromBus(CellTerminalClientState.BusEntry bus,
                                                                    int partitionSlotIndex,
                                                                    GenericStack stack) {
            return new WriteBusPrecisePartitionAmountAction(
                TargetAction.fromBus(bus),
                partitionSlotIndex,
                Objects.requireNonNull(stack, "stack"));
        }

        private static WriteBusPrecisePartitionAmountAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            GenericStack stack = GenericStack.readTag(tag.getCompoundTag(TAG_STACK));
            if (stack == null) {
                throw new IllegalArgumentException("Precise storage bus amount action is missing stack");
            }
            return new WriteBusPrecisePartitionAmountAction(
                TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.getInteger(TAG_PARTITION_SLOT),
                stack);
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setInteger(TAG_PARTITION_SLOT, this.partitionSlotIndex);
            tag.setTag(TAG_STACK, GenericStack.writeTag(this.stack));
            return tag.toString();
        }
    }

    private record WritePartitionAction(String stableTargetId,
                                        CellTerminalTargetLocator locator,
                                        int slotIndex,
                                        @Nullable TargetAction subnetTarget,
                                        int connectionIndex,
                                        PartitionWriteMode mode,
                                        List<@Nullable GenericStack> partition,
                                        @Nullable GenericStack key,
                                        int partitionSlotIndex) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_SLOT_INDEX = "slotIndex";
        private static final String TAG_SUBNET_TARGET = "subnetTarget";
        private static final String TAG_CONNECTION_INDEX = "connectionIndex";
        private static final String TAG_MODE = "mode";
        private static final String TAG_PARTITION = "partition";
        private static final String TAG_KEY = "key";
        private static final String TAG_PARTITION_SLOT = "partitionSlot";

        private static WritePartitionAction fromCellSlot(CellTerminalClientState.StorageEntry storage,
                                                         int slotIndex,
                                                         List<@Nullable GenericStack> partition) {
            return new WritePartitionAction(storage.stableTargetId(),
                storage.locator(),
                slotIndex,
                null,
                -1,
                PartitionWriteMode.SET,
                partition,
                null,
                -1);
        }

        private static WritePartitionAction fromCellSlotMode(CellTerminalClientState.StorageEntry storage,
                                                             int slotIndex,
                                                             PartitionWriteMode mode) {
            return new WritePartitionAction(storage.stableTargetId(), storage.locator(), slotIndex, null, -1, mode,
                List.of(), null, -1);
        }

        private static WritePartitionAction fromCellSlotDelta(CellTerminalClientState.StorageEntry storage,
                                                              int slotIndex,
                                                              PartitionWriteMode mode,
                                                              GenericStack key) {
            return new WritePartitionAction(storage.stableTargetId(), storage.locator(), slotIndex, null, -1, mode,
                List.of(), Objects.requireNonNull(key, "key"), -1);
        }

        private static WritePartitionAction fromCellSlotAt(CellTerminalClientState.StorageEntry storage,
                                                           int slotIndex,
                                                           PartitionWriteMode mode,
                                                           int partitionSlotIndex,
                                                           @Nullable GenericStack key) {
            return new WritePartitionAction(storage.stableTargetId(), storage.locator(), slotIndex, null, -1, mode,
                List.of(), key, partitionSlotIndex);
        }

        private static WritePartitionAction fromBus(CellTerminalClientState.BusEntry bus,
                                                    List<@Nullable GenericStack> partition) {
            return new WritePartitionAction(bus.stableTargetId(),
                bus.locator(),
                -1,
                null,
                -1,
                PartitionWriteMode.SET,
                partition,
                null,
                -1);
        }

        private static WritePartitionAction fromBusMode(CellTerminalClientState.BusEntry bus,
                                                        PartitionWriteMode mode) {
            return new WritePartitionAction(bus.stableTargetId(), bus.locator(), -1, null, -1, mode, List.of(),
                null, -1);
        }

        private static WritePartitionAction fromBusDelta(CellTerminalClientState.BusEntry bus,
                                                         PartitionWriteMode mode,
                                                         GenericStack key) {
            return new WritePartitionAction(bus.stableTargetId(), bus.locator(), -1, null, -1, mode, List.of(),
                Objects.requireNonNull(key, "key"), -1);
        }

        private static WritePartitionAction fromBusAt(CellTerminalClientState.BusEntry bus,
                                                      PartitionWriteMode mode,
                                                      int partitionSlotIndex,
                                                      @Nullable GenericStack key) {
            return new WritePartitionAction(bus.stableTargetId(), bus.locator(), -1, null, -1, mode, List.of(), key,
                partitionSlotIndex);
        }

        private static WritePartitionAction fromSubnetConnectionMode(CellTerminalClientState.SubnetEntry subnet,
                                                                     CellTerminalClientState.ConnectionEntry connection,
                                                                     int connectionIndex,
                                                                     PartitionWriteMode mode) {
            return new WritePartitionAction(connection.stableTargetId(),
                connection.locator(), -1, TargetAction.fromSubnet(subnet), connectionIndex, mode, List.of(),
                null, -1);
        }

        private static WritePartitionAction fromSubnetConnectionDelta(CellTerminalClientState.SubnetEntry subnet,
                                                                      CellTerminalClientState.ConnectionEntry connection,
                                                                      int connectionIndex,
                                                                      PartitionWriteMode mode,
                                                                      GenericStack key) {
            return new WritePartitionAction(connection.stableTargetId(),
                connection.locator(), -1, TargetAction.fromSubnet(subnet), connectionIndex, mode, List.of(),
                Objects.requireNonNull(key, "key"), -1);
        }

        private static WritePartitionAction fromSubnetConnectionAt(CellTerminalClientState.SubnetEntry subnet,
                                                                   CellTerminalClientState.ConnectionEntry connection,
                                                                   int connectionIndex,
                                                                   PartitionWriteMode mode,
                                                                   int partitionSlotIndex,
                                                                   @Nullable GenericStack key) {
            return new WritePartitionAction(connection.stableTargetId(),
                connection.locator(), -1, TargetAction.fromSubnet(subnet), connectionIndex, mode, List.of(), key,
                partitionSlotIndex);
        }

        private static WritePartitionAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            TargetAction target = TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET));
            PartitionWriteMode mode = tag.hasKey(TAG_MODE, Constants.NBT.TAG_STRING)
                ? readEnum(tag.getString(TAG_MODE), PartitionWriteMode.class)
                : PartitionWriteMode.SET;
            return new WritePartitionAction(target.stableTargetId(),
                target.locator(),
                tag.getInteger(TAG_SLOT_INDEX),
                tag.hasKey(TAG_SUBNET_TARGET, Constants.NBT.TAG_COMPOUND)
                    ? TargetAction.fromTag(tag.getCompoundTag(TAG_SUBNET_TARGET))
                    : null,
                tag.hasKey(TAG_CONNECTION_INDEX, Constants.NBT.TAG_INT) ? tag.getInteger(TAG_CONNECTION_INDEX) : -1,
                mode,
                GenericStack.readList(tag.getTagList(TAG_PARTITION, Constants.NBT.TAG_COMPOUND)),
                tag.hasKey(TAG_KEY, Constants.NBT.TAG_COMPOUND) ? GenericStack.readTag(tag.getCompoundTag(TAG_KEY))
                    : null,
                tag.hasKey(TAG_PARTITION_SLOT, Constants.NBT.TAG_INT) ? tag.getInteger(TAG_PARTITION_SLOT) : -1);
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, new TargetAction(this.stableTargetId, this.locator, null).toTag());
            tag.setInteger(TAG_SLOT_INDEX, this.slotIndex);
            if (this.subnetTarget != null) {
                tag.setTag(TAG_SUBNET_TARGET, this.subnetTarget.toTag());
                tag.setInteger(TAG_CONNECTION_INDEX, this.connectionIndex);
            }
            tag.setString(TAG_MODE, this.mode.name());
            if (!this.partition.isEmpty()) {
                tag.setTag(TAG_PARTITION, GenericStack.writeList(this.partition));
            }
            if (this.key != null) {
                tag.setTag(TAG_KEY, GenericStack.writeTag(this.key));
            }
            if (this.partitionSlotIndex >= 0) {
                tag.setInteger(TAG_PARTITION_SLOT, this.partitionSlotIndex);
            }
            return tag.toString();
        }

        private CellTerminalTargetHandle toTargetHandle() {
            return new CellTerminalTargetHandle(this.stableTargetId, this.locator);
        }

        private boolean isSubnetConnectionAction() {
            return this.subnetTarget != null;
        }

        private CellTerminalTargetHandle toSubnetConnectionHandle() {
            if (this.subnetTarget == null) {
                throw new IllegalStateException("Cell Terminal partition action has no subnet connection handle");
            }
            return toTargetHandle();
        }

        private CellTerminalCellSlotHandle toCellSlotHandle() {
            return new CellTerminalCellSlotHandle(this.stableTargetId, this.locator, this.slotIndex);
        }

        private List<@Nullable GenericStack> toPartition() {
            return new ArrayList<>(this.partition);
        }

        private List<@Nullable GenericStack> resolveCellSlotPartition(CellTerminalCellSlotTarget slot) {
            Objects.requireNonNull(slot, "slot");
            if (this.mode == PartitionWriteMode.SET_FROM_CONTENT
                && !slot.supportsCapability(CellTerminalCapability.AUTO_PARTITION_FROM_CONTENT)) {
                throw new IllegalStateException("Cell Terminal cell slot does not support auto partition from content: "
                    + toCellSlotHandle());
            }
            int capacity = slot.getPartitionSnapshot().slots().size();
            List<@Nullable GenericStack> baseline = this.mode == PartitionWriteMode.SET
                ? toPartition()
                : slot.getPartitionSnapshot().slots();
            return resolvePartitionAction(this.mode, baseline, capacity, this.key, this.partitionSlotIndex,
                slot.previewContent(), false);
        }

        private List<@Nullable GenericStack> resolveBusPartition(CellTerminalBusTarget bus) {
            Objects.requireNonNull(bus, "bus");
            int capacity = bus.getPartitionSnapshot().slots().size();
            List<@Nullable GenericStack> baseline = this.mode == PartitionWriteMode.SET
                ? toPartition()
                : bus.getPartitionSnapshot().slots();
            return resolvePartitionAction(this.mode, baseline, capacity, this.key, this.partitionSlotIndex,
                bus.previewContent(), bus.getPartitionMode() == CellTerminalBusPartitionMode.PRECISE_SLOTS);
        }
    }

    private record WritePriorityAction(TargetAction target, int priority) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_PRIORITY = "priority";

        private static WritePriorityAction fromStorage(CellTerminalClientState.StorageEntry storage, int priority) {
            return new WritePriorityAction(TargetAction.fromStorage(storage), priority);
        }

        private static WritePriorityAction fromBus(CellTerminalClientState.BusEntry bus, int priority) {
            return new WritePriorityAction(TargetAction.fromBus(bus), priority);
        }

        private static WritePriorityAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new WritePriorityAction(TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.getInteger(TAG_PRIORITY));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setInteger(TAG_PRIORITY, this.priority);
            return tag.toString();
        }
    }

    private record WriteBusModeAction(TargetAction target, CellTerminalIoFilterMode mode) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_ACCESS = "access";
        private static final String TAG_STORAGE_FILTER = "storageFilter";
        private static final String TAG_FILTER_ON_EXTRACT = "filterOnExtract";
        private static final String TAG_FUZZY = "fuzzy";

        private static WriteBusModeAction fromBus(CellTerminalClientState.BusEntry bus,
                                                  AccessRestriction accessRestriction,
                                                  StorageFilter storageFilter,
                                                  YesNo filterOnExtract,
                                                  FuzzyMode fuzzyMode) {
            return new WriteBusModeAction(TargetAction.fromBus(bus),
                new CellTerminalIoFilterMode(accessRestriction, storageFilter, filterOnExtract, fuzzyMode));
        }

        private static WriteBusModeAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new WriteBusModeAction(
                TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                new CellTerminalIoFilterMode(
                    readEnum(tag.getString(TAG_ACCESS), AccessRestriction.class),
                    readEnum(tag.getString(TAG_STORAGE_FILTER), StorageFilter.class),
                    readEnum(tag.getString(TAG_FILTER_ON_EXTRACT), YesNo.class),
                    readEnum(tag.getString(TAG_FUZZY), FuzzyMode.class)));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setString(TAG_ACCESS, this.mode.accessRestriction().name());
            tag.setString(TAG_STORAGE_FILTER, this.mode.storageFilter().name());
            tag.setString(TAG_FILTER_ON_EXTRACT, this.mode.filterOnExtract().name());
            tag.setString(TAG_FUZZY, this.mode.fuzzyMode().name());
            return tag.toString();
        }
    }

    private record WriteCellSlotAction(SlotAction slot, CellSlotWriteMode mode) {
        private static final String TAG_SLOT = "slot";
        private static final String TAG_MODE = "mode";

        private static WriteCellSlotAction fromCellSlot(CellTerminalClientState.StorageEntry storage,
                                                        CellTerminalClientState.CellSlotEntry slot,
                                                        CellSlotWriteMode mode) {
            return new WriteCellSlotAction(SlotAction.fromStorageSlot(storage, slot), mode);
        }

        private static WriteCellSlotAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new WriteCellSlotAction(SlotAction.fromTag(tag.getCompoundTag(TAG_SLOT)),
                readEnum(tag.getString(TAG_MODE), CellSlotWriteMode.class));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_SLOT, this.slot.toTag());
            tag.setString(TAG_MODE, this.mode.name());
            return tag.toString();
        }
    }

    private record TargetUpgradeSelection(@Nullable TargetAction target,
                                          int slotIndex) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_SLOT_INDEX = "slotIndex";

        private static TargetUpgradeSelection fromSelection(@Nullable CellTerminalClientState.StorageEntry storage,
                                                            @Nullable CellTerminalClientState.CellSlotEntry slot,
                                                            @Nullable CellTerminalClientState.BusEntry bus) {
            if (storage != null && slot != null) {
                return new TargetUpgradeSelection(TargetAction.fromStorage(storage), slot.slotIndex());
            }
            if (storage != null) {
                return new TargetUpgradeSelection(TargetAction.fromStorage(storage), -1);
            }
            if (bus != null) {
                return new TargetUpgradeSelection(TargetAction.fromBus(bus), -1);
            }
            return new TargetUpgradeSelection(null, -1);
        }

        private static TargetUpgradeSelection fromPayload(String payload) {
            return fromTag(readPayload(payload));
        }

        private static TargetUpgradeSelection fromTag(NBTTagCompound tag) {
            TargetAction target = tag.hasKey(TAG_TARGET, Constants.NBT.TAG_COMPOUND)
                ? TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET))
                : null;
            return new TargetUpgradeSelection(target, tag.getInteger(TAG_SLOT_INDEX));
        }

        private String toPayload() {
            return toTag().toString();
        }

        private NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            if (this.target != null) {
                tag.setTag(TAG_TARGET, this.target.toTag());
            }
            tag.setInteger(TAG_SLOT_INDEX, this.slotIndex);
            return tag;
        }
    }

    private record TargetUpgradeInteraction(TargetUpgradeSelection selection,
                                            int upgradeSlot,
                                            boolean quickMove) {
        private static final String TAG_SELECTION = "selection";
        private static final String TAG_UPGRADE_SLOT = "upgradeSlot";
        private static final String TAG_QUICK_MOVE = "quickMove";

        private static TargetUpgradeInteraction fromSelection(@Nullable CellTerminalClientState.StorageEntry storage,
                                                              @Nullable CellTerminalClientState.CellSlotEntry slot,
                                                              @Nullable CellTerminalClientState.BusEntry bus,
                                                              int upgradeSlot,
                                                              boolean quickMove) {
            return new TargetUpgradeInteraction(TargetUpgradeSelection.fromSelection(storage, slot, bus),
                upgradeSlot,
                quickMove);
        }

        private static TargetUpgradeInteraction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new TargetUpgradeInteraction(
                TargetUpgradeSelection.fromTag(tag.getCompoundTag(TAG_SELECTION)),
                tag.getInteger(TAG_UPGRADE_SLOT),
                tag.getBoolean(TAG_QUICK_MOVE));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_SELECTION, this.selection.toTag());
            tag.setInteger(TAG_UPGRADE_SLOT, this.upgradeSlot);
            tag.setBoolean(TAG_QUICK_MOVE, this.quickMove);
            return tag.toString();
        }
    }

    private record ContentPageAction(TargetAction target,
                                     int slotIndex,
                                     int firstIndex) {
        private static final String TAG_TARGET = "target";
        private static final String TAG_SLOT_INDEX = "slotIndex";
        private static final String TAG_FIRST_INDEX = "firstIndex";

        private static ContentPageAction fromCellSlot(CellTerminalClientState.StorageEntry storage,
                                                      int slotIndex,
                                                      int firstIndex) {
            return new ContentPageAction(TargetAction.fromStorage(storage), slotIndex, firstIndex);
        }

        private static ContentPageAction fromBus(CellTerminalClientState.BusEntry bus, int firstIndex) {
            return new ContentPageAction(TargetAction.fromBus(bus), -1, firstIndex);
        }

        private static ContentPageAction fromPayload(String payload) {
            NBTTagCompound tag = readPayload(payload);
            return new ContentPageAction(
                TargetAction.fromTag(tag.getCompoundTag(TAG_TARGET)),
                tag.getInteger(TAG_SLOT_INDEX),
                tag.getInteger(TAG_FIRST_INDEX));
        }

        private String toPayload() {
            var tag = new NBTTagCompound();
            tag.setTag(TAG_TARGET, this.target.toTag());
            tag.setInteger(TAG_SLOT_INDEX, this.slotIndex);
            tag.setInteger(TAG_FIRST_INDEX, this.firstIndex);
            return tag.toString();
        }
    }

    private final class ContainerTargetLookup implements CellTerminalTargetLookup {
        @Override
        public CellTerminalStorageTarget resolveStorage(CellTerminalTargetHandle handle) {
            Objects.requireNonNull(handle, "handle");
            if (isTempCellTarget(handle)) {
                TempCellStorageTarget target = createTempCellStorageTarget();
                requireStableTempTarget(handle, target);
                return target;
            }
            return worldTargetAccess.resolveStorage(handle);
        }

        @Override
        public CellTerminalBusTarget resolveStorageBus(CellTerminalTargetHandle handle) {
            Objects.requireNonNull(handle, "handle");
            if (isTempCellTarget(handle)) {
                throw new IllegalStateException("Temporary cells are not storage-bus targets: " + handle);
            }
            return worldTargetAccess.resolveStorageBus(handle);
        }

        @Override
        public CellTerminalCellSlotTarget resolveCellSlot(CellTerminalCellSlotHandle handle) {
            Objects.requireNonNull(handle, "handle");
            if (isTempCellTarget(handle.owner())) {
                CellTerminalStorageTarget target = resolveStorage(handle.owner());
                List<? extends CellTerminalCellSlotTarget> slots = target.getCellSlots();
                if (handle.slotIndex() < 0 || handle.slotIndex() >= slots.size()) {
                    throw new IllegalStateException("Temporary cell slot no longer exists: " + handle);
                }
                return slots.get(handle.slotIndex());
            }
            return worldTargetAccess.resolveCellSlot(handle);
        }

        private void requireStableTempTarget(CellTerminalTargetHandle handle, CellTerminalStorageTarget target) {
            if (!handle.stableTargetId().equals(target.stableTargetId())) {
                throw new IllegalStateException("Temporary cell target stable id mismatch: " + handle);
            }
            if (!handle.locator().equals(target.locator())) {
                throw new IllegalStateException("Temporary cell target locator mismatch: " + handle);
            }
        }
    }

    private final class TargetUpgradeInventory implements InternalInventory {
        @Override
        public int size() {
            return MAX_TARGET_UPGRADE_SLOTS;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            if (isClientSide()) {
                if (slotIndex < 0 || slotIndex >= clientTargetUpgradeMirror.length) {
                    return ItemStack.EMPTY;
                }
                ItemStack stack = clientTargetUpgradeMirror[slotIndex];
                return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            }
            SelectedTargetUpgrade target = currentSelectedTargetUpgrade();
            if (target == null || slotIndex < 0 || slotIndex >= target.upgrades.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = target.upgrades.get(slotIndex);
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (slotIndex < 0 || slotIndex >= size()) {
                throw new IllegalArgumentException("Cell Terminal target upgrade slot out of range: " + slotIndex);
            }
            if (isClientSide()) {
                clientTargetUpgradeMirror[slotIndex] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
                return;
            }
            AELog.warn("Rejected direct Cell Terminal target upgrade inventory write. player=%s slot=%s stack=%s",
                getPlayer().getName(), slotIndex, stack);
            requestSync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getSelectedTargetUpgradeCount() || stack.isEmpty()
                || !Upgrades.isUpgradeCardItem(stack)) {
                return false;
            }
            if (isClientSide()) {
                return true;
            }
            SelectedTargetUpgrade target = currentSelectedTargetUpgrade();
            return target != null && requireLiveUpgradeInventory(target).isItemValid(slot, stack);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = getStackInSlot(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            current.setCount(Math.min(amount, current.getCount()));
            if (!simulate) {
                AELog.warn("Rejected direct Cell Terminal target upgrade inventory extract. player=%s slot=%s amount=%s",
                    getPlayer().getName(), slot, amount);
                requestSync();
                return ItemStack.EMPTY;
            }
            return current;
        }

        private @Nullable SelectedTargetUpgrade currentSelectedTargetUpgrade() {
            CellTerminalSnapshot snapshot = lastSnapshot;
            if (snapshot == null) {
                return null;
            }
            return findSelectedTargetUpgrade(snapshot);
        }
    }
}
