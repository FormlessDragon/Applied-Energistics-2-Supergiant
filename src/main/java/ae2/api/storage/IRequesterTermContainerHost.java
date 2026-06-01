package ae2.api.storage;

import ae2.api.networking.IGridNode;
import org.jetbrains.annotations.Nullable;

public interface IRequesterTermContainerHost {
    @Nullable
    IGridNode getGridNode();

    default ILinkStatus getLinkStatus() {
        IGridNode node = getGridNode();
        return node != null && node.isOnline() ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected();
    }
}
