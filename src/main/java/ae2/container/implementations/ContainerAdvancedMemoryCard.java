package ae2.container.implementations;

import ae2.api.features.P2PTunnelAttunementInternal;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.container.AEBaseContainer;
import ae2.core.network.clientbound.AdvancedMemoryCardP2PSnapshotPacket;
import ae2.items.contents.AdvancedMemoryCardGuiHost;
import ae2.items.parts.P2PPartItem;
import ae2.items.tools.AdvancedMemoryCardItem;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardAction;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PEntry;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardP2PSnapshot;
import ae2.items.tools.advancedmemorycard.AdvancedMemoryCardTypeChange;
import ae2.parts.p2p.P2PTunnelMemoryActions;
import ae2.parts.p2p.P2PTunnelPart;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class ContainerAdvancedMemoryCard extends AEBaseContainer {
    private static final String ACTION_APPLY = "apply";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_CHANGE_TYPE = "changeType";
    private static final String ACTION_RENAME = "rename";
    private static final String ACTION_SET_MODE = "setMode";
    private static final int REFRESH_TICKS = 10;
    private static final int MAX_CUSTOM_NAME_LENGTH = 32;

    private final AdvancedMemoryCardGuiHost host;
    private final Int2ObjectMap<P2PTunnelPart<?>> tunnelsByEntryId = new Int2ObjectOpenHashMap<>();
    private AdvancedMemoryCardP2PSnapshot snapshot = new AdvancedMemoryCardP2PSnapshot(List.of());
    private int refreshDelay;
    private boolean initialFocusPending = true;

    public ContainerAdvancedMemoryCard(InventoryPlayer playerInventory, AdvancedMemoryCardGuiHost host) {
        super(playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_APPLY, AdvancedMemoryCardAction.class, this::applyAction);
        registerClientAction(ACTION_REFRESH, this::refreshSnapshot);
        registerClientAction(ACTION_CHANGE_TYPE, AdvancedMemoryCardTypeChange.class, this::changeTypeAction);
        registerClientAction(ACTION_RENAME, AdvancedMemoryCardRename.class, this::renameAction);
        registerClientAction(ACTION_SET_MODE, String.class, this::setModeAction);
        if (isServerSide()) {
            refreshSnapshot();
        }
    }

    static int findInitialFocusEntryId(List<AdvancedMemoryCardP2PEntry> entries, BlockPos clickedPos,
                                       EnumFacing focusedSide) {
        if (clickedPos == null) {
            return -1;
        }

        if (focusedSide != null) {
            for (AdvancedMemoryCardP2PEntry entry : entries) {
                if (clickedPos.equals(entry.pos()) && focusedSide == entry.side()) {
                    return entry.entryId();
                }
            }
        }

        int matchedEntryId = -1;
        for (AdvancedMemoryCardP2PEntry entry : entries) {
            if (!clickedPos.equals(entry.pos())) {
                continue;
            }
            if (matchedEntryId != -1) {
                return -1;
            }
            matchedEntryId = entry.entryId();
        }
        return matchedEntryId;
    }

    static IGrid selectGrid(IGridNode clickedNode, boolean preferClickedNode, IInWorldGridNodeHost gridHost) {
        IGrid clickedGrid = preferClickedNode ? safeGrid(clickedNode) : null;
        if (clickedGrid != null) {
            return clickedGrid;
        }

        IGrid centerGrid = gridHost == null ? null : safeGrid(gridHost.getGridNode(null));
        if (centerGrid != null) {
            return centerGrid;
        }

        if (gridHost != null) {
            for (EnumFacing side : EnumFacing.VALUES) {
                IGrid grid = safeGrid(gridHost.getGridNode(side));
                if (grid != null) {
                    return grid;
                }
            }
        }
        return safeGrid(clickedNode);
    }

    static IGrid safeGrid(IGridNode node) {
        if (node == null) {
            return null;
        }
        try {
            return node.grid();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && ++refreshDelay >= REFRESH_TICKS) {
            refreshDelay = 0;
            refreshSnapshotIfChanged();
        }
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        if (isServerSide() && listener instanceof EntityPlayerMP) {
            sendSnapshot();
            refreshDelay = 0;
        }
    }

    public AdvancedMemoryCardP2PSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(AdvancedMemoryCardP2PSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void apply(AdvancedMemoryCardAction.Mode mode, int sourceEntryId, int targetEntryId) {
        if (isClientSide()) {
            sendClientAction(ACTION_APPLY, new AdvancedMemoryCardAction(mode, sourceEntryId, targetEntryId));
            return;
        }
        applyAction(new AdvancedMemoryCardAction(mode, sourceEntryId, targetEntryId));
    }

    public void refresh() {
        if (isClientSide()) {
            sendClientAction(ACTION_REFRESH);
            return;
        }
        refreshSnapshot();
    }

    public void changeType(int entryId, ResourceLocation tunnelType) {
        AdvancedMemoryCardTypeChange change = new AdvancedMemoryCardTypeChange(entryId, tunnelType.toString());
        if (isClientSide()) {
            sendClientAction(ACTION_CHANGE_TYPE, change);
            return;
        }
        changeTypeAction(change);
    }

    public void rename(int entryId, String name) {
        AdvancedMemoryCardRename rename = new AdvancedMemoryCardRename(entryId, name);
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME, rename);
            return;
        }
        renameAction(rename);
    }

    public void setMode(AdvancedMemoryCardAction.Mode mode) {
        if (mode == null) {
            return;
        }
        writeMode(mode);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_MODE, mode.name());
        }
    }

    private void setModeAction(String modeName) {
        if (modeName == null) {
            return;
        }
        try {
            writeMode(AdvancedMemoryCardAction.Mode.valueOf(modeName));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void writeMode(AdvancedMemoryCardAction.Mode mode) {
        AdvancedMemoryCardItem.setMode(host.getItemStack(), mode);
    }

    private void applyAction(AdvancedMemoryCardAction action) {
        if (isClientSide()) {
            return;
        }

        P2PTunnelPart<?> source = tunnelsByEntryId.get(action.sourceEntryId());
        P2PTunnelPart<?> target = tunnelsByEntryId.get(action.targetEntryId());
        AdvancedMemoryCardP2PEntry sourceEntry = findEntry(action.sourceEntryId());
        AdvancedMemoryCardP2PEntry targetEntry = findEntry(action.targetEntryId());
        if (source == null || target == null || sourceEntry == null || targetEntry == null
            || !isValidSnapshotEntry(source, sourceEntry) || !isValidSnapshotEntry(target, targetEntry)) {
            refreshSnapshot();
            return;
        }

        switch (action.mode()) {
            case BIND_OUTPUT -> bindOutput(source, target);
            case BIND_INPUT -> bindInput(source, target);
            case COPY_OUTPUT -> copyOutput(source, target);
            case DELETE_BINDING -> deleteBinding(target);
        }
        refreshSnapshot();
    }

    private void bindOutput(P2PTunnelPart<?> input, P2PTunnelPart<?> output) {
        output = convertOutputToInputType(input, output);
        if (output != null) {
            P2PTunnelMemoryActions.bindOutput(input, output);
        }
    }

    private void bindInput(P2PTunnelPart<?> output, P2PTunnelPart<?> input) {
        output = convertOutputToInputType(input, output);
        if (output != null) {
            P2PTunnelMemoryActions.bindInput(output, input);
        }
    }

    private void copyOutput(P2PTunnelPart<?> input, P2PTunnelPart<?> output) {
        output = convertOutputToInputType(input, output);
        if (output != null) {
            P2PTunnelMemoryActions.copyOutput(input, output);
        }
    }

    private void deleteBinding(P2PTunnelPart<?> tunnel) {
        P2PTunnelMemoryActions.clearBinding(tunnel);
    }

    private P2PTunnelPart<?> convertOutputToInputType(P2PTunnelPart<?> input, P2PTunnelPart<?> output) {
        if (isSameTunnelType(input, output)) {
            return output;
        }
        return P2PTunnelMemoryActions.changeType(output, input.getPartItem(), getPlayer());
    }

    private void changeTypeAction(AdvancedMemoryCardTypeChange change) {
        if (isClientSide()) {
            return;
        }

        ResourceLocation targetType;
        try {
            targetType = new ResourceLocation(change.tunnelType());
        } catch (RuntimeException ignored) {
            refreshSnapshot();
            return;
        }
        P2PTunnelPart<?> tunnel = tunnelsByEntryId.get(change.entryId());
        AdvancedMemoryCardP2PEntry entry = findEntry(change.entryId());
        IPartItem<?> targetPart = IPartItem.byId(targetType);
        if (tunnel == null || entry == null || targetPart == null
            || targetPart.asItem() == tunnel.getPartItem().asItem()
            || !isValidSnapshotEntry(tunnel, entry)
            || !isManageable(targetPart.asItem())
            || !P2PTunnelPart.class.isAssignableFrom(targetPart.getPartClass())) {
            refreshSnapshot();
            return;
        }

        P2PTunnelMemoryActions.changeTypeAndClearBinding(tunnel, targetPart, getPlayer());
        refreshSnapshot();
    }

    private void renameAction(AdvancedMemoryCardRename rename) {
        if (isClientSide()) {
            return;
        }
        if (rename.name() == null || rename.name().length() > MAX_CUSTOM_NAME_LENGTH) {
            refreshSnapshot();
            return;
        }

        P2PTunnelPart<?> tunnel = tunnelsByEntryId.get(rename.entryId());
        AdvancedMemoryCardP2PEntry entry = findEntry(rename.entryId());
        if (tunnel == null || entry == null || !isValidSnapshotEntry(tunnel, entry)) {
            refreshSnapshot();
            return;
        }

        P2PTunnelMemoryActions.renameFrequency(tunnel, rename.name());
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        refreshSnapshot(true);
    }

    private void refreshSnapshotIfChanged() {
        refreshSnapshot(false);
    }

    private void refreshSnapshot(boolean forceSend) {
        tunnelsByEntryId.clear();
        AdvancedMemoryCardP2PSnapshot previousSnapshot = snapshot;
        IGrid grid = findGrid();
        if (grid == null) {
            snapshot = new AdvancedMemoryCardP2PSnapshot(List.of(), consumeInitialFocusEntryId(List.of()));
            sendSnapshotIfNeeded(previousSnapshot, forceSend);
            return;
        }

        var entries = new ObjectArrayList<AdvancedMemoryCardP2PEntry>();
        int entryId = 0;
        for (IGridNode node : grid.getNodes()) {
            if (!(node.getOwner() instanceof P2PTunnelPart<?> tunnel) || !isManageable(tunnel)) {
                continue;
            }
            if (entries.size() >= AdvancedMemoryCardP2PSnapshot.MAX_ENTRIES) {
                break;
            }
            int id = entryId++;
            tunnelsByEntryId.put(id, tunnel);
            entries.add(createEntry(id, tunnel, node));
        }

        snapshot = AdvancedMemoryCardP2PSnapshot.of(entries, consumeInitialFocusEntryId(entries));
        sendSnapshotIfNeeded(previousSnapshot, forceSend);
    }

    private void sendSnapshotIfNeeded(AdvancedMemoryCardP2PSnapshot previousSnapshot, boolean forceSend) {
        if (forceSend || !snapshot.equals(previousSnapshot)) {
            sendSnapshot();
        }
    }

    private void sendSnapshot() {
        sendPacketToClient(new AdvancedMemoryCardP2PSnapshotPacket(snapshot));
    }

    private int consumeInitialFocusEntryId(List<AdvancedMemoryCardP2PEntry> entries) {
        if (!initialFocusPending) {
            return -1;
        }

        initialFocusPending = false;
        return findInitialFocusEntryId(entries, host.getClickedPos(), host.getFocusedSide());
    }

    private AdvancedMemoryCardP2PEntry createEntry(int entryId, P2PTunnelPart<?> tunnel, IGridNode node) {
        ResourceLocation tunnelType = tunnel.getPartItem().asItem().getRegistryName();
        if (tunnelType == null) {
            tunnelType = new ResourceLocation("minecraft", "air");
        }
        return new AdvancedMemoryCardP2PEntry(
            entryId,
            tunnelType,
            getTunnelTypeDisplayNameKey(tunnel),
            tunnel.getCustomName(),
            !tunnel.isOutput(),
            tunnel.getFrequency(),
            !node.meetsChannelRequirements(),
            tunnel.getFrequency() == 0,
            node.getLevel().provider.getDimension(),
            tunnel.getHost().getLocation().getPos(),
            tunnel.getSide());
    }

    private String getTunnelTypeDisplayNameKey(P2PTunnelPart<?> tunnel) {
        Item item = tunnel.getPartItem().asItem();
        if (item instanceof P2PPartItem<?> p2pPartItem) {
            return p2pPartItem.getP2PTypeTranslationKey(tunnel.getPartItem().asItemStack());
        }
        return tunnel.getPartItem().asItemStack().getTranslationKey() + ".name";
    }

    private AdvancedMemoryCardP2PEntry findEntry(int entryId) {
        for (AdvancedMemoryCardP2PEntry entry : snapshot.entries()) {
            if (entry.entryId() == entryId) {
                return entry;
            }
        }
        return null;
    }

    private boolean isValidSnapshotEntry(P2PTunnelPart<?> tunnel, AdvancedMemoryCardP2PEntry entry) {
        if (!isManageable(tunnel)) {
            return false;
        }
        ResourceLocation registryName = tunnel.getPartItem().asItem().getRegistryName();
        IPartHost host = tunnel.getHost();
        return registryName != null
            && registryName.equals(entry.tunnelType())
            && tunnel.getSide() == entry.side()
            && host.getLocation().getPos().equals(entry.pos())
            && host.getLocation().getLevel().provider.getDimension() == entry.dimension();
    }

    private boolean isSameTunnelType(P2PTunnelPart<?> source, P2PTunnelPart<?> target) {
        return source.getClass() == target.getClass()
            && source.getPartItem().asItem() == target.getPartItem().asItem();
    }

    private boolean isManageable(P2PTunnelPart<?> tunnel) {
        return isManageable(tunnel.getPartItem().asItem());
    }

    private boolean isManageable(Item item) {
        return P2PTunnelAttunementInternal.getManageableTunnels().contains(item);
    }

    private IGrid findGrid() {
        return selectGrid(host.getClickedGridNode(), host.preferClickedNodeForGrid(), host.getGridHost());
    }

    public record AdvancedMemoryCardRename(int entryId, String name) {
    }
}
