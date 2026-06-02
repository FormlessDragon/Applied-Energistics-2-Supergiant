package ae2.requester.abstraction;

import ae2.requester.Request;
import ae2.requester.RequestManager;
import ae2.tile.crafting.TileRequester;
import net.minecraft.util.text.ITextComponent;

/**
 * Simplified representation of a {@link Request} and its parent {@link TileRequester} for synchronization in menus.
 */
public final class RequestTracker {

    private final long id;
    private final long sortBy;
    private final ITextComponent name;
    private final RequestManager server;
    private final RequestManager client;

    public RequestTracker(TileRequester requester, long id) {
        this.id = id;
        this.sortBy = requester.getRequesterSortValue();
        this.name = requester.getRequesterName();
        this.server = requester.getRequestManager();
        this.client = new RequestManager(null, this.server.size());
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

    public RequestManager getServer() {
        return server;
    }

    public RequestManager getClient() {
        return client;
    }
}
