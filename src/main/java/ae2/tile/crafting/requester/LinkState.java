package ae2.tile.crafting.requester;

import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

public record LinkState(ICraftingLink link) implements StatusState {

    @Override
    public StatusState handle(TileRequester host, int slot) {
        if (this.link.isDone()) {
            return EXPORT;
        }
        if (this.link.isCanceled()) {
            return IDLE;
        }
        return this;
    }

    @Override
    public RequestStatus type() {
        return RequestStatus.CRAFTING;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.SAME;
    }
}
