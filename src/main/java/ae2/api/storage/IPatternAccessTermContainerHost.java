package ae2.api.storage;

import ae2.api.networking.IGridNode;
import ae2.api.util.IConfigurableObject;
import org.jetbrains.annotations.Nullable;

public interface IPatternAccessTermContainerHost extends IConfigurableObject {
    @Nullable
    IGridNode getGridNode();

    ILinkStatus getLinkStatus();
}
