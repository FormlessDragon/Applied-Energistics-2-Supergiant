package ae2.requester.status;

import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.tile.crafting.TileRequester;

public final class LinkState implements StatusState {
    private final ICraftingLink link;

    public LinkState(ICraftingLink link) {
        this.link = link;
    }

    public ICraftingLink link() {
        return link;
    }

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
