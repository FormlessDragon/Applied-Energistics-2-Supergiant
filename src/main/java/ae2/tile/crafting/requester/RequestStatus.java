package ae2.tile.crafting.requester;

public enum RequestStatus {
    IDLE,
    MISSING,
    NO_PATTERN,
    REQUESTING,
    PLANNING,
    CRAFTING,
    EXPORTING,
    CPU;

    public RequestStatus translateToClient() {
        return this == REQUESTING || this == PLANNING ? IDLE : this;
    }

    public boolean locksRequest() {
        return this == CRAFTING || this == EXPORTING;
    }
}
