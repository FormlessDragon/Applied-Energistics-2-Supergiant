package ae2.client.gui.me.requester;

import ae2.tile.crafting.requester.RequestList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

public final class ClientRequester implements Comparable<ClientRequester> {
    private final long requesterId;
    private final RequestList requestManager;
    private @Nullable ITextComponent displayName;
    private long sortValue;

    public ClientRequester(long requesterId, @Nullable ITextComponent displayName, long sortValue, int requestCount) {
        this.requesterId = requesterId;
        this.displayName = displayName;
        this.sortValue = sortValue;
        this.requestManager = new RequestList(null, requestCount);
        for (int i = 0; i < this.requestManager.size(); i++) {
            this.requestManager.get(i).setRequesterLocation(this.requesterId, i);
        }
    }

    public void update(@Nullable ITextComponent displayName, long sortValue) {
        this.displayName = displayName;
        this.sortValue = sortValue;
    }

    public long getRequesterId() {
        return this.requesterId;
    }

    public ITextComponent getDisplayName() {
        return this.displayName == null ? new TextComponentString("Requester") : this.displayName;
    }

    public String getSearchName() {
        return getDisplayName().getFormattedText().toLowerCase();
    }

    public RequestList getRequests() {
        return this.requestManager;
    }

    @Override
    public int compareTo(ClientRequester other) {
        int sortCompare = Long.compare(this.sortValue, other.sortValue);
        return sortCompare != 0 ? sortCompare : Long.compare(this.requesterId, other.requesterId);
    }
}
