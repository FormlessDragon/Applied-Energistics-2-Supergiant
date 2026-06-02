package ae2.helpers;

import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import org.jetbrains.annotations.Nullable;

public interface RequesterTerminalHost {
    @Nullable
    IGridNode getGridNode();

    default ILinkStatus getLinkStatus() {
        IGridNode node = getGridNode();
        return node != null && node.isOnline() ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected();
    }
}
