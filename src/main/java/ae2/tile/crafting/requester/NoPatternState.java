package ae2.tile.crafting.requester;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

public final class NoPatternState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        return host.getRequests().get(slot).isEnabled() ? IDLE : this;
    }

    @Override
    public RequestStatus type() {
        return RequestStatus.NO_PATTERN;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.IDLE;
    }
}
