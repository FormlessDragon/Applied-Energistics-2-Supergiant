package ae2.container.me.common;

import ae2.core.network.clientbound.RequesterSyncPacket;
import ae2.tile.crafting.TileRequester;
import ae2.tile.crafting.requester.Request;
import ae2.tile.crafting.requester.RequestList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Consumer;

/**
 * Simplified representation of a {@link Request} and its parent {@link TileRequester} for synchronization in menus.
 */
public final class RequesterServerView {

    private final long id;
    private final long[] lastSentRevisions;
    private final long[] pendingRevisions;
    private final TileRequester requester;
    private final RequestList server;
    private final NBTTagCompound[] lastSentData;
    private long sortBy;
    private ITextComponent name;
    private boolean synchronizing;
    private boolean fullUpdateRequired = true;

    public RequesterServerView(TileRequester requester, long id) {
        this.id = id;
        this.sortBy = requester.getRequesterSortValue();
        this.name = requester.getRequesterName();
        this.requester = requester;
        this.server = requester.getRequests();
        this.lastSentRevisions = new long[this.server.size()];
        this.pendingRevisions = new long[this.server.size()];
        this.lastSentData = new NBTTagCompound[this.server.size()];
    }

    public long getId() {
        return id;
    }

    public long getSortBy() {
        return sortBy;
    }

    public ITextComponent getName() {
        return name;
    }

    public TileRequester getRequester() {
        return requester;
    }

    public RequestList getServer() {
        return server;
    }

    /**
     * Retains the row identity when only its display metadata changes.
     */
    public boolean refreshMetadata() {
        if (this.server != this.requester.getRequests()) {
            return false;
        }
        ITextComponent name = this.requester.getRequesterName();
        long sortBy = this.requester.getRequesterSortValue();
        if (!this.name.equals(name) || this.sortBy != sortBy) {
            this.name = name;
            this.sortBy = sortBy;
            this.fullUpdateRequired = true;
        }
        return true;
    }

    void synchronize(boolean fullUpdate, Consumer<RequesterSyncPacket> sendPacket) {
        this.fullUpdateRequired |= fullUpdate;
        if (this.synchronizing) {
            return;
        }

        this.synchronizing = true;
        boolean sendFull = this.fullUpdateRequired;
        this.fullUpdateRequired = false;
        boolean sent = false;
        try {
            Int2ObjectOpenHashMap<NBTTagCompound> rows = null;
            for (int i = 0; i < this.server.size(); i++) {
                Request request = this.server.get(i);
                long revision = request.getSyncRevision();
                if (sendFull || revision != this.lastSentRevisions[i]) {
                    NBTTagCompound data = request.writeToNBT();
                    if (!sendFull && data.equals(this.lastSentData[i])) {
                        this.lastSentRevisions[i] = revision;
                        continue;
                    }
                    if (rows == null) {
                        rows = new Int2ObjectOpenHashMap<>(this.server.size());
                    }
                    rows.put(i, data);
                    this.pendingRevisions[i] = revision;
                }
            }

            if (rows == null) {
                if (!sendFull) {
                    return;
                }
                rows = new Int2ObjectOpenHashMap<>();
            }
            sendPacket.accept(sendFull
                ? RequesterSyncPacket.fullUpdate(this.id, this.name, this.sortBy, this.server.size(), rows)
                : RequesterSyncPacket.incrementalUpdate(this.id, rows));
            for (var row : rows.int2ObjectEntrySet()) {
                int index = row.getIntKey();
                this.lastSentRevisions[index] = this.pendingRevisions[index];
                this.lastSentData[index] = row.getValue();
            }
            sent = true;
        } finally {
            if (sendFull && !sent) {
                this.fullUpdateRequired = true;
            }
            this.synchronizing = false;
        }
    }
}
