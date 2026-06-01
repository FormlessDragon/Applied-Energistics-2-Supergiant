package ae2.requester.status;

public enum RequestStatus {
    IDLE,
    MISSING,
    REQUESTING,
    PLANNING,
    CRAFTING,
    EXPORTING,
    BLOCKING,
    CPU;

    public RequestStatus translateToClient() {
        return this == REQUESTING || this == PLANNING ? IDLE : this;
    }

    public boolean locksRequest() {
        return this == CRAFTING || this == EXPORTING;
    }
}
