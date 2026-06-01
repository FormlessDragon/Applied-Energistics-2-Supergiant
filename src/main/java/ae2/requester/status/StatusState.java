package ae2.requester.status;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

public interface StatusState {
    StatusState IDLE = new IdleState();
    StatusState REQUEST = new RequestState();
    StatusState EXPORT = new ExportState();

    StatusState handle(TileRequester host, int slot);

    RequestStatus type();

    TickRateModulation getTickRateModulation();

    default RequestStatus visibleStatus() {
        return type().translateToClient();
    }
}
