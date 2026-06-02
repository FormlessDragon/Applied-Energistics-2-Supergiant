package ae2.tile.crafting.requester;

import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.tile.crafting.TileRequester;

public final class IdleState implements StatusState {
    @Override
    public StatusState handle(TileRequester host, int slot) {
        AEKey bufferedKey = host.getStorageTracker().getBufferedKey(slot);
        if (bufferedKey != null && host.getStorageTracker().getBufferedAmount(slot, bufferedKey) > 0) {
            return EXPORT;
        }

        Request request = host.getRequests().get(slot);
        AEKey key = request.getKey();
        host.getStorageTracker().watch(slot, key);
        if (!request.isEnabled() || request.isEmpty() || key == null) {
            return this;
        }

        if (host.getStorageTracker().getPendingInsertionAmount(slot, key) == 0) {
            host.getStorageTracker().setKnownAmount(slot, key, host.getStoredAmount(key));
        }

        if (host.getStorageTracker().computeAmountToCraft(slot, request) > 0) {
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
