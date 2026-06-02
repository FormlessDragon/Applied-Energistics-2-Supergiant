package ae2.tile.crafting.requester;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.tile.crafting.TileRequester;

public final class ExportState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        AEKey key = host.getStorageTracker().getBufferedKey(slot);
        if (key == null) {
            return IDLE;
        }

        long bufferedAmount = host.getStorageTracker().getBufferedAmount(slot, key);
        long inserted = host.insert(key, bufferedAmount);
        host.getStorageTracker().markExported(slot, key, inserted);
        if (host.getStorageTracker().getBufferedAmount(slot, key) > 0) {
            return this;
        }
        if (host.getStorageTracker().getRemainingTotalAmount(slot, key) > 0) {
            return this;
        }
        return inserted > 0 ? REQUEST : IDLE;
    }

    @Override
    public RequestStatus type() {
        return RequestStatus.EXPORTING;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.URGENT;
    }
}
