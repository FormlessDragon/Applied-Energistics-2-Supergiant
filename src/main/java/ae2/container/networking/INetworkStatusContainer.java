package ae2.container.networking;

public interface INetworkStatusContainer {
    NetworkStatus getStatus();

    void setStatus(NetworkStatus status);

    void setCanExportGrid(boolean canExportGrid);

    boolean canExportGrid();

    void exportGrid();
}
