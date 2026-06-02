package ae2.requester.status;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.tile.crafting.TileRequester;

public final class ExportState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        AEKey key = host.getStorageManager().getBufferedKey(slot);
        if (key == null) {
            return IDLE;
        }

        long bufferedAmount = host.getStorageManager().getBufferedAmount(slot, key);
        long inserted = host.insert(key, bufferedAmount);
        host.getStorageManager().markExported(slot, key, inserted);
        if (host.getStorageManager().getBufferedAmount(slot, key) > 0) {
            return this;
        }
        if (host.getStorageManager().getRemainingTotalAmount(slot, key) > 0) {
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
