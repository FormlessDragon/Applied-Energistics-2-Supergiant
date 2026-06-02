package ae2.container.me.common;

import ae2.tile.crafting.TileRequester;
import ae2.tile.crafting.requester.Request;
import ae2.tile.crafting.requester.RequestList;
import net.minecraft.util.text.ITextComponent;

/**
 * Simplified representation of a {@link Request} and its parent {@link TileRequester} for synchronization in menus.
 */
public final class RequesterServerView {

    private final long id;
    private final long sortBy;
    private final ITextComponent name;
    private final TileRequester requester;
    private final RequestList server;
    private final RequestList client;

    public RequesterServerView(TileRequester requester, long id) {
        this.id = id;
        this.sortBy = requester.getRequesterSortValue();
        this.name = requester.getRequesterName();
        this.requester = requester;
        this.server = requester.getRequests();
        this.client = new RequestList(null, this.server.size());
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

    public RequestList getClient() {
        return client;
    }
}
