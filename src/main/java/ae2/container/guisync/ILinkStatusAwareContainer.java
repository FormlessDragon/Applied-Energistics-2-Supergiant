package ae2.container.guisync;

import ae2.api.storage.ILinkStatus;

public interface ILinkStatusAwareContainer {
    void setLinkStatus(ILinkStatus linkStatus);
}

