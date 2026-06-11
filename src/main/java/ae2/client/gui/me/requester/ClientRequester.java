package ae2.client.gui.me.requester;

import ae2.core.localization.GuiText;
import ae2.tile.crafting.requester.RequestList;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public final class ClientRequester implements Comparable<ClientRequester> {
    public static final int MAX_REQUEST_COUNT = 64;

    private final long requesterId;
    private final RequestList requestManager;
    private @Nullable ITextComponent displayName;
    private long sortValue;

    public ClientRequester(long requesterId, @Nullable ITextComponent displayName, long sortValue, int requestCount) {
        this.requesterId = requesterId;
        this.displayName = displayName;
        this.sortValue = sortValue;
        this.requestManager = new RequestList(null, sanitizeRequestCount(requestCount));
        for (int i = 0; i < this.requestManager.size(); i++) {
            this.requestManager.get(i).setRequesterLocation(this.requesterId, i);
        }
    }

    private static int sanitizeRequestCount(int requestCount) {
        return Math.clamp(requestCount, 0, MAX_REQUEST_COUNT);
    }

    public void update(@Nullable ITextComponent displayName, long sortValue) {
        this.displayName = displayName;
        this.sortValue = sortValue;
    }

    public long getRequesterId() {
        return this.requesterId;
    }

    public ITextComponent getDisplayName() {
        return this.displayName == null ? GuiText.RequesterFallbackName.text() : this.displayName;
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
