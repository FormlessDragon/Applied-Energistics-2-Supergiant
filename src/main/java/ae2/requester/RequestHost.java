package ae2.requester;

import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import net.minecraft.util.text.ITextComponent;

public interface RequestHost extends IActionHost {
    RequestManager getRequestManager();

    IActionSource getActionSource();

    ITextComponent getRequesterName();

    long getRequesterSortValue();

    void onRequestChanged(int index);

    void onRequestUpdated(int index);

    @Override
    IGridNode getActionableNode();
}
