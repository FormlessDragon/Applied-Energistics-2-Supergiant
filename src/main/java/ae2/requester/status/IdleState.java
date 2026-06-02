package ae2.requester.status;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.requester.Request;
import ae2.tile.crafting.TileRequester;

public final class IdleState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        AEKey bufferedKey = host.getStorageManager().getBufferedKey(slot);
        if (bufferedKey != null && host.getStorageManager().getBufferedAmount(slot, bufferedKey) > 0) {
            return EXPORT;
        }

        Request request = host.getRequestManager().get(slot);
        AEKey key = request.getKey();
        host.getStorageManager().watch(slot, key);
        if (!request.isEnabled() || request.isEmpty() || key == null) {
            return this;
        }

        if (host.getStorageManager().getPendingInsertionAmount(slot, key) == 0) {
            host.getStorageManager().setKnownAmount(slot, key, host.getStoredAmount(key));
        }

        if (host.getStorageManager().computeAmountToCraft(slot, request) > 0) {
            return REQUEST;
        }

        return this;
    }

    @Override
    public RequestStatus type() {
        return RequestStatus.IDLE;
    }

    @Override
    public TickRateModulation getTickRateModulation() {
        return TickRateModulation.IDLE;
    }
}
