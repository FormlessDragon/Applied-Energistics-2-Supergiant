package ae2.requester.abstraction;

import ae2.requester.RequestManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

public final class RequesterReference implements Comparable<RequesterReference> {
    private final long requesterId;
    private @Nullable ITextComponent displayName;
    private long sortValue;
    private final RequestManager requestManager;

    public RequesterReference(long requesterId, @Nullable ITextComponent displayName, long sortValue, int requestCount) {
        this.requesterId = requesterId;
        this.displayName = displayName;
        this.sortValue = sortValue;
        this.requestManager = new RequestManager(null, requestCount);
        for (int i = 0; i < this.requestManager.size(); i++) {
            this.requestManager.get(i).setRequesterReference(this, i);
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

    public RequestManager getRequestManager() {
        return this.requestManager;
    }

    @Override
    public int compareTo(RequesterReference other) {
        int sortCompare = Long.compare(this.sortValue, other.sortValue);
        return sortCompare != 0 ? sortCompare : Long.compare(this.requesterId, other.requesterId);
    }
}
